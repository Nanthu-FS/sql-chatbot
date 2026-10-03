package com.genshincalc.core

import com.genshincalc.core.calc.EffectControl
import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Team
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs every modelled kit, weapon and set with everything unlocked and switched on, and checks
 * that the effects only reference damage rows that exist and produce finite numbers.
 */
class EffectsIntegrityTest {
    private val data = GameDataSet.load()
    private val calc = TeamCalculator(data, GameEffects)

    private fun maxed(build: MemberBuild): MemberBuild {
        val ids = buildList {
            addAll(GameEffects.characterEffects(build.characterId))
            build.weapon?.let { addAll(GameEffects.weaponEffects(it.weaponId)) }
            build.artifacts.pieceCounts().keys.forEach { addAll(GameEffects.setEffects(it)) }
        }
        val states = ids.associate { e ->
            e.id to when (val c = e.control) {
                is EffectControl.Stacks -> c.max
                is EffectControl.Toggle -> 1
                is EffectControl.Choice -> 0
                EffectControl.Always -> 1
            }
        }
        return build.copy(constellation = 6, effectStates = states)
    }

    @Test
    fun everyCharacterKitReferencesExistingHits() {
        val problems = mutableListOf<String>()
        for (c in data.characters) {
            val build = maxed(Defaults.build(data, c.id))
            val attempt = runCatching { calc.calculate(Team(listOf(build))) }
            val result = attempt.getOrNull()
            if (result == null) {
                problems += "${c.id}: ${attempt.exceptionOrNull()}"
                continue
            }
            val m = result.members[0]
            val ids = c.hits.map { it.id }.toSet()
            for (mod in m.debugHitMods) {
                mod.filter.hitIds?.filter { it !in ids }?.forEach { problems += "${c.id}: '${mod.source}' references unknown hit '$it'" }
            }
            m.debugElementOverrides.keys.filter { it !in ids }.forEach { problems += "${c.id}: unknown hit '$it' in element override" }
            for (h in m.hits) {
                val n = h.numbers
                if (!n.average.isFinite() || n.average < 0) problems += "${c.id}: ${h.hit.id} = ${n.average}"
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun everyWeaponAndSetRuns() {
        val problems = mutableListOf<String>()
        for (w in data.weapons.filter { it.id in GameEffects.modelledWeapons }) {
            val holder = data.characters.first { it.weapon == w.type }
            val build = maxed(Defaults.build(data, holder.id).copy(weapon = com.genshincalc.core.model.WeaponBuild(w.id, w.maxLevel, refinement = 5)))
            runCatching { calc.calculate(Team(listOf(build))) }.onFailure { problems += "${w.id}: ${it.message}" }
                .onSuccess { r -> r.members[0].hits.filter { !it.numbers.average.isFinite() }.forEach { problems += "${w.id}: ${it.hit.id} NaN" } }
        }
        for (s in data.artifactSets.filter { it.id in GameEffects.modelledSets }) {
            val build = maxed(Defaults.build(data, "bennett").let { it.copy(artifacts = it.artifacts.copy(set4 = s.id)) })
            runCatching { calc.calculate(Team(listOf(build))) }.onFailure { problems += "${s.id}: ${it.message}" }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun effectIdsAreUnique() {
        val all = data.characters.flatMap { GameEffects.characterEffects(it.id) } +
            data.weapons.flatMap { GameEffects.weaponEffects(it.id) } +
            data.artifactSets.flatMap { GameEffects.setEffects(it.id) } + GameEffects.teamEffects()
        val dupes = all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "Duplicate effect ids: $dupes")
    }

    @Test
    fun modelledIdsExistInData() {
        val unknownChars = GameEffects.modelledCharacters.filter { data.characterOrNull(it) == null }
        val unknownWeapons = GameEffects.modelledWeapons.filter { data.weaponOrNull(it) == null }
        val unknownSets = GameEffects.modelledSets.filter { data.artifactSetOrNull(it) == null }
        assertTrue(unknownChars.isEmpty() && unknownWeapons.isEmpty() && unknownSets.isEmpty(),
            "Unknown ids: chars=$unknownChars weapons=$unknownWeapons sets=$unknownSets")
    }
}
