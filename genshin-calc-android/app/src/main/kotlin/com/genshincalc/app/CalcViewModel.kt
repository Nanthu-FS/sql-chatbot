package com.genshincalc.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genshincalc.core.Defaults
import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.ArtifactPiece
import com.genshincalc.core.model.ArtifactSlot
import com.genshincalc.core.model.EnemyConfig
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.showcase.Showcase
import com.genshincalc.core.showcase.ShowcaseFormatException
import com.genshincalc.core.showcase.ShowcaseIds
import com.genshincalc.core.showcase.ShowcaseParser
import com.genshincalc.core.showcase.StatDifference
import com.genshincalc.core.text.ArtifactScanParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.abs

/** An artifact open in the editor; [equipOn] is the party member it gets equipped on when saved. */
data class PieceDraft(val piece: ArtifactPiece, val isNew: Boolean, val equipOn: Int? = null)

/** Result for one screenshot of an import. */
data class ScanItem(
    val index: Int,
    val pieceId: String? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    /** The same artifact was already in "My artifacts". */
    val duplicate: Boolean = false,
    /** Text that was recognized (shown when no artifact was found). */
    val text: List<String> = emptyList(),
)

/** Screenshots being read; [equipOn] is the character id the pieces get equipped on (null = just save them). */
data class ScanSession(val id: Int, val total: Int, val equipOn: String?, val items: List<ScanItem> = emptyList()) {
    val running: Boolean get() = items.size < total
}

/** A player's in-game Character Showcase being imported. */
data class ShowcaseState(
    val uid: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val showcase: Showcase? = null,
    /** Indexes of the characters that go into the party (the first four by default). */
    val selected: List<Int> = emptyList(),
    /** Per character: where the calculated attribute screen differs from the game's. */
    val differences: List<List<StatDifference>> = emptyList(),
)

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

    /** "My artifacts": every individual artifact the user has added or scanned. */
    private val _inventory = MutableStateFlow<List<ArtifactPiece>>(emptyList())
    val inventory: StateFlow<List<ArtifactPiece>> = _inventory.asStateFlow()

    private val _draft = MutableStateFlow<PieceDraft?>(null)
    val draft: StateFlow<PieceDraft?> = _draft.asStateFlow()

    private val _scan = MutableStateFlow<ScanSession?>(null)
    val scan: StateFlow<ScanSession?> = _scan.asStateFlow()
    private var scanJob: Job? = null
    private var scanCount = 0

    private val _showcase = MutableStateFlow(ShowcaseState(uid = prefs.getString(KEY_UID, null).orEmpty()))
    val showcase: StateFlow<ShowcaseState> = _showcase.asStateFlow()
    private var showcaseJob: Job? = null
    /** UID -> (expiry time, response); the service refreshes a showcase only every "ttl" seconds. */
    private val showcaseCache = mutableMapOf<String, Pair<Long, String>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val result: StateFlow<TeamResult?> = combine(_data, _team) { d, t ->
        if (d == null) null else runCatching { TeamCalculator(d, GameEffects).calculate(t) }
            .onFailure { _error.value = it.message ?: it.toString() }
            .getOrNull()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val d = GameDataSet.load()
            val team = loadTeam(d) ?: Defaults.sampleTeam(d)
            _inventory.value = loadInventory(team)
            _team.value = team
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

    private fun loadInventory(team: Team): List<ArtifactPiece> {
        val saved = prefs.getString(KEY_INVENTORY, null)
            ?.let { raw -> runCatching { json.decodeFromString(inventorySerializer, raw) }.getOrNull() }
            .orEmpty()
        // Pieces worn in the saved team are always listed.
        return (saved + team.members.flatMap { it.artifacts.pieces.values }).distinctBy { it.id }
    }

    private fun saveInventory(pieces: List<ArtifactPiece>) {
        prefs.edit().putString(KEY_INVENTORY, json.encodeToString(inventorySerializer, pieces)).apply()
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

    /** Adds an artifact to "My artifacts", or updates it there and on every character wearing it. */
    fun upsertPiece(piece: ArtifactPiece) {
        val p = piece.normalized()
        _inventory.update { list ->
            (if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p).also(::saveInventory)
        }
        updateTeam { t ->
            t.copy(members = t.members.map { m ->
                val a = m.artifacts
                if (a.pieces.values.none { it.id == p.id }) m
                else m.copy(artifacts = a.copy(pieces = a.pieces.filterValues { it.id != p.id } + (p.slot to p)))
            })
        }
    }

    fun deletePiece(id: String) {
        _inventory.update { list -> list.filter { it.id != id }.also(::saveInventory) }
        updateTeam { t ->
            t.copy(members = t.members.map { m ->
                val a = m.artifacts
                if (a.pieces.values.none { it.id == id }) m else m.copy(artifacts = a.copy(pieces = a.pieces.filterValues { it.id != id }))
            })
        }
    }

    /** Puts the artifact in its slot on a member and switches the member to individual pieces. */
    fun equip(memberIndex: Int, piece: ArtifactPiece) = updateMember(memberIndex) { m ->
        val p = piece.normalized()
        m.copy(artifacts = m.artifacts.copy(mode = ArtifactMode.PIECES, pieces = m.artifacts.pieces + (p.slot to p)))
    }

    fun unequip(memberIndex: Int, slot: ArtifactSlot) = updateMember(memberIndex) { m ->
        m.copy(artifacts = m.artifacts.copy(pieces = m.artifacts.pieces - slot))
    }

    fun setArtifactMode(memberIndex: Int, mode: ArtifactMode) = updateMember(memberIndex) { m ->
        m.copy(artifacts = m.artifacts.copy(mode = mode))
    }

    fun editPiece(piece: ArtifactPiece) {
        _draft.value = PieceDraft(piece, isNew = false)
    }

    fun newPiece(slot: ArtifactSlot, equipOn: Int?) {
        _draft.value = PieceDraft(ArtifactPiece(id = newPieceId(), slot = slot), isNew = true, equipOn = equipOn)
    }

    fun updateDraft(transform: (ArtifactPiece) -> ArtifactPiece) = _draft.update { it?.copy(piece = transform(it.piece)) }

    fun saveDraft() {
        val d = _draft.value ?: return
        val p = d.piece.normalized()
        upsertPiece(p)
        d.equipOn?.let { equip(it, p) }
        _draft.value = null
    }

    fun discardDraft() {
        _draft.value = null
    }

    /**
     * Reads artifact screenshots and adds what it finds to "My artifacts"; with [equipOn] (a member index)
     * the pieces are also equipped on that member. Progress and results are in [scan].
     */
    fun importScreenshots(uris: List<Uri>, equipOn: Int? = null) {
        if (uris.isEmpty()) return
        val session = ScanSession(
            id = ++scanCount,
            total = uris.size,
            equipOn = equipOn?.let { _team.value.members.getOrNull(it)?.characterId },
        )
        scanJob?.cancel()
        _scan.value = session
        scanJob = viewModelScope.launch {
            val d = _data.filterNotNull().first()
            uris.forEachIndexed { i, uri ->
                val item = try {
                    readScreenshot(d, uri, i, session.equipOn)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ScanItem(i, error = "Couldn't read the image (${e.message ?: e.javaClass.simpleName})")
                }
                _scan.update { s -> if (s?.id == session.id) s.copy(items = s.items + item) else s }
            }
        }
    }

    private suspend fun readScreenshot(d: GameDataSet, uri: Uri, index: Int, equipOn: String?): ScanItem {
        val lines = ArtifactOcr.read(getApplication(), uri)
        val scan = withContext(Dispatchers.Default) { ArtifactScanParser.parse(lines, d) }
        val text = lines.map { it.text }
        val piece = scan.toPiece(newPieceId()) ?: return ScanItem(
            index,
            error = if (scan.recognized) "Couldn't tell which artifact slot this is" else "No artifact found in this screenshot",
            warnings = scan.warnings,
            text = text,
        )
        val existing = _inventory.value.firstOrNull { sameArtifact(it, piece) }
        val saved = existing ?: piece.also(::upsertPiece)
        if (equipOn != null) {
            val member = _team.value.members.indexOfFirst { it.characterId == equipOn }
            if (member >= 0) equip(member, saved)
        }
        return ScanItem(index, pieceId = saved.id, warnings = scan.warnings, duplicate = existing != null, text = text)
    }

    private fun sameArtifact(a: ArtifactPiece, b: ArtifactPiece): Boolean =
        a.setId == b.setId && a.slot == b.slot && a.mainStat == b.mainStat && a.rarity == b.rarity && a.level == b.level &&
            a.substats.keys == b.substats.keys && a.substats.all { (s, v) -> abs(v - (b.substats[s] ?: 0.0)) < 1e-6 }

    fun clearScan() {
        scanJob?.cancel()
        _scan.value = null
    }

    private fun newPieceId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    /** Loads the Character Showcase of [uid] from Enka.Network; the result is in [showcase]. */
    fun loadShowcase(uid: String) {
        val clean = uid.trim()
        if (!UID.matches(clean)) {
            _showcase.value = ShowcaseState(uid = clean, error = "A UID has 9 or 10 digits (shown in the game's Profile screen).")
            return
        }
        prefs.edit().putString(KEY_UID, clean).apply()
        showcaseJob?.cancel()
        _showcase.value = ShowcaseState(uid = clean, loading = true)
        showcaseJob = viewModelScope.launch {
            val text = try {
                showcaseCache[clean]?.takeIf { it.first > System.currentTimeMillis() }?.second
                    ?: ShowcaseClient.fetch(clean).also { body ->
                        val ttl = TTL.find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 60
                        showcaseCache[clean] = System.currentTimeMillis() + ttl * 1000 to body
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _showcase.value = ShowcaseState(uid = clean, error = e.message ?: "Couldn't load the showcase.")
                return@launch
            }
            readShowcase(clean, text)
        }
    }

    /** Shows a showcase response without downloading it (used by tests). */
    fun loadShowcaseJson(uid: String, text: String) {
        showcaseJob?.cancel()
        _showcase.value = ShowcaseState(uid = uid, loading = true)
        showcaseJob = viewModelScope.launch { readShowcase(uid, text) }
    }

    private suspend fun readShowcase(uid: String, text: String) {
        val d = _data.filterNotNull().first()
        _showcase.value = withContext(Dispatchers.Default) {
            try {
                val s = ShowcaseParser.parse(text, d, ShowcaseIds.load()) { newPieceId() }
                val differences = s.characters.map { c ->
                    runCatching {
                        val m = TeamCalculator(d, GameEffects).calculate(Team(listOf(c.build))).members[0]
                        ShowcaseParser.compare(c.gameStats, m.screenStats, m.character.element)
                    }.getOrDefault(emptyList())
                }
                ShowcaseState(uid = uid, showcase = s, selected = s.characters.indices.take(Team.MAX_SIZE), differences = differences)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ShowcaseFormatException) {
                ShowcaseState(uid = uid, error = e.message)
            } catch (e: Exception) {
                ShowcaseState(uid = uid, error = "Couldn't read the showcase (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    fun toggleShowcaseCharacter(index: Int) = _showcase.update { s ->
        val selected = when {
            index in s.selected -> s.selected - index
            s.selected.size < Team.MAX_SIZE -> (s.selected + index).sorted()
            else -> s.selected
        }
        s.copy(selected = selected)
    }

    /** Replaces the party with the selected showcase characters; their artifacts are added to My artifacts. */
    fun applyShowcase() {
        val s = _showcase.value
        val chosen = s.selected.mapNotNull { s.showcase?.characters?.getOrNull(it) }
        if (chosen.isEmpty()) return
        val pieces = _inventory.value.toMutableList()
        val members = chosen.map { c ->
            val a = c.build.artifacts
            // An artifact that is already in My artifacts keeps its entry.
            val equipped = a.pieces.mapValues { (_, p) -> pieces.firstOrNull { sameArtifact(it, p) } ?: p.also { pieces += it } }
            c.build.copy(artifacts = a.copy(pieces = equipped))
        }
        _inventory.value = pieces.also(::saveInventory)
        updateTeam { it.copy(members = members, activeIndex = 0) }
    }

    fun resetToSample() {
        val d = _data.value ?: return
        updateTeam { Defaults.sampleTeam(d) }
    }

    fun dismissError() {
        _error.value = null
    }

    companion object {
        private const val KEY_TEAM = "team_v1"
        private const val KEY_INVENTORY = "artifacts_v1"
        private const val KEY_UID = "showcase_uid"
        private val UID = Regex("^[0-9]{9,10}$")
        private val TTL = Regex("\"ttl\"\\s*:\\s*(\\d+)")
        private val inventorySerializer = ListSerializer(ArtifactPiece.serializer())
    }
}
