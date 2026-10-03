package com.genshincalc.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genshincalc.core.Defaults
import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.EnemyConfig
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Holds the party being calculated and recomputes damage whenever it changes. */
class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("calc", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _data = MutableStateFlow<GameDataSet?>(null)
    val data: StateFlow<GameDataSet?> = _data.asStateFlow()

    private val _team = MutableStateFlow(Team())
    val team: StateFlow<Team> = _team.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val result: StateFlow<TeamResult?> = combine(_data, _team) { d, t ->
        if (d == null) null else runCatching { TeamCalculator(d, GameEffects).calculate(t) }
            .onFailure { _error.value = it.message ?: it.toString() }
            .getOrNull()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val d = GameDataSet.load()
            _team.value = loadTeam(d) ?: Defaults.sampleTeam(d)
            _data.value = d
        }
    }

    private fun loadTeam(d: GameDataSet): Team? {
        val raw = prefs.getString(KEY_TEAM, null) ?: return null
        val team = runCatching { json.decodeFromString(Team.serializer(), raw) }.getOrNull() ?: return null
        // Drop anything the bundled data no longer knows about.
        val members = team.members.filter { d.characterOrNull(it.characterId) != null }.map { m ->
            val weapon = m.weapon?.takeIf { d.weaponOrNull(it.weaponId) != null }
                ?: Defaults.build(d, m.characterId).weapon
            m.copy(weapon = weapon)
        }
        return team.copy(members = members, activeIndex = team.activeIndex.coerceIn(0, (members.size - 1).coerceAtLeast(0)))
    }

    private fun save(team: Team) {
        prefs.edit().putString(KEY_TEAM, json.encodeToString(Team.serializer(), team)).apply()
    }

    fun updateTeam(transform: (Team) -> Team) {
        _team.update { old -> transform(old).also(::save) }
    }

    fun updateMember(index: Int, transform: (MemberBuild) -> MemberBuild) = updateTeam { t ->
        if (index !in t.members.indices) t
        else t.copy(members = t.members.toMutableList().also { it[index] = transform(it[index]) })
    }

    fun setActive(index: Int) = updateTeam { it.copy(activeIndex = index.coerceIn(0, (it.members.size - 1).coerceAtLeast(0))) }

    fun addMember(characterId: String) {
        val d = _data.value ?: return
        updateTeam { t ->
            if (t.members.size >= Team.MAX_SIZE || t.members.any { it.characterId == characterId }) t
            else t.copy(members = t.members + Defaults.build(d, characterId), activeIndex = t.members.size)
        }
    }

    /** Swaps the character in a slot, keeping the weapon if the new character can use it. */
    fun replaceCharacter(index: Int, characterId: String) {
        val d = _data.value ?: return
        updateMember(index) { old ->
            val fresh = Defaults.build(d, characterId)
            val oldWeapon = old.weapon?.let { d.weaponOrNull(it.weaponId) }
            if (oldWeapon != null && oldWeapon.type == d.character(characterId).weapon) fresh.copy(weapon = old.weapon) else fresh
        }
    }

    fun removeMember(index: Int) = updateTeam { t ->
        if (index !in t.members.indices) t
        else {
            val members = t.members.toMutableList().also { it.removeAt(index) }
            t.copy(members = members, activeIndex = t.activeIndex.coerceAtMost((members.size - 1).coerceAtLeast(0)))
        }
    }

    fun setWeapon(index: Int, weaponId: String) = updateMember(index) { m ->
        m.copy(weapon = (m.weapon ?: WeaponBuild(weaponId)).copy(weaponId = weaponId), effectStates = m.effectStates.filterKeys { !it.startsWith("weapon.") })
    }

    fun setEffect(memberIndex: Int, effectId: String, value: Int) = updateMember(memberIndex) { m ->
        m.copy(effectStates = m.effectStates + (effectId to value))
    }

    fun setTeamEffect(effectId: String, value: Int) = updateTeam { it.copy(teamEffectStates = it.teamEffectStates + (effectId to value)) }

    fun setEnemy(transform: (EnemyConfig) -> EnemyConfig) = updateTeam { it.copy(enemy = transform(it.enemy)) }

    fun resetToSample() {
        val d = _data.value ?: return
        updateTeam { Defaults.sampleTeam(d) }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        private const val KEY_TEAM = "team_v1"
    }
}
