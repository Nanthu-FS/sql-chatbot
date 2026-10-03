package com.genshincalc.core.calc

import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.Team

/**
 * Applies every effect of the party in phases (see [EffectPhase]) and computes the damage of all
 * members.
 */
class TeamCalculator(
    private val data: GameDataSet,
    private val provider: EffectProvider,
) {
    private class Instance(val effect: Effect, val owner: MemberState, val value: Int, val teamWide: Boolean) {
        val active: Boolean
            get() = (value > 0 || effect.control is EffectControl.Choice) && effect.requirement(owner)
    }

    fun calculate(team: Team): TeamResult {
        val info = TeamInfoImpl(team, data)
        val members = team.members.mapIndexed { i, b ->
            MemberState(i, b, data.character(b.characterId), data.weaponOrNull(b.weapon?.weaponId), data, info)
        }
        if (members.isEmpty()) {
            return TeamResult(emptyList(), team.enemy.level, emptyMap(), emptyMap(), 0.0, emptyList())
        }
        val enemy = EnemyModifiers()
        val instances = buildList {
            for (m in members) for (e in ownEffects(m)) add(Instance(e, m, m.effectValue(e), teamWide = false))
            for (e in provider.teamEffects()) {
                add(Instance(e, members[0], members[0].effectValue(e, team.teamEffectStates), teamWide = true))
            }
        }
        val active = instances.filter { it.active }

        fun targets(inst: Instance): List<MemberState> = when (inst.effect.target) {
            EffectTarget.SELF -> listOf(inst.owner)
            EffectTarget.TEAM -> members
            EffectTarget.TEAM_OTHERS -> members.filter { it !== inst.owner }
        }

        fun run(inst: Instance, target: MemberState, ownerStats: StatSheet, targetStats: StatSheet, sink: Modifiers, enemyMods: EnemyModifiers) {
            EffectScope(inst.effect, inst.owner, target, inst.value, ownerStats, targetStats, sink, enemyMods, members)
                .apply(inst.effect.apply)
        }

        // 1. Talent levels.
        for (inst in active.filter { it.effect.phase == EffectPhase.TALENT }) {
            for (t in targets(inst)) run(inst, t, inst.owner.sheet, t.sheet, t.mods, enemy)
        }
        members.forEach { it.finalizeTalentLevels() }

        // 2. Character screen: base stats + static bonuses (skipped for manually entered stats).
        val staticConversionOnSheet = mutableMapOf<Pair<Instance, Int>, StatSheet>()
        for (m in members) {
            val sheet = m.baseSheet()
            val statics = active.filter { it.effect.static && it.owner === m }
            if (!m.manual) {
                val tmp = Modifiers()
                for (inst in statics.filter { it.effect.phase != EffectPhase.CONVERSION }) {
                    run(inst, m, sheet, sheet, tmp, EnemyModifiers())
                }
                sheet.addAll(tmp.stats)
            }
            val beforeConversions = sheet.copy()
            for (inst in statics.filter { it.effect.phase == EffectPhase.CONVERSION }) {
                val tmp = Modifiers()
                run(inst, m, beforeConversions, beforeConversions, tmp, EnemyModifiers())
                staticConversionOnSheet[inst to m.index] = tmp.stats
                if (!m.manual) sheet.addAll(tmp.stats)
            }
            m.sheet = sheet
        }

        // 3. Constant buffs.
        for (inst in active.filter { !it.effect.static && it.effect.phase == EffectPhase.BASE }) {
            for (t in targets(inst)) run(inst, t, inst.owner.sheet, t.sheet, t.mods, enemy)
        }

        // 4. Each member's own stats (for buffs that scale off the provider's stats).
        for (m in members) {
            val self = m.sheet.copy().apply { addAll(m.mods.stats) }
            val tmp = Modifiers()
            for (inst in active.filter { it.owner === m && it.effect.phase == EffectPhase.CONVERSION && !it.effect.static }) {
                if (EffectTarget.SELF == inst.effect.target || targets(inst).contains(m)) {
                    run(inst, m, self, self, tmp, EnemyModifiers())
                }
            }
            m.selfStats = self.copy().apply { addAll(tmp.stats) }
        }

        // 5. Buffs scaling off the provider's stats.
        for (inst in active.filter { !it.effect.static && it.effect.phase == EffectPhase.TEAM_STAT }) {
            for (t in targets(inst)) run(inst, t, inst.owner.selfStats, t.selfStats, t.mods, enemy)
        }
        for (m in members) m.total = m.sheet.copy().apply { addAll(m.mods.stats) }

        // 6. Conversions from final stats. Each one reads the stats before any conversion.
        for (m in members) {
            val snapshot = m.total.copy()
            val conv = Modifiers()
            for (inst in active.filter { it.effect.phase == EffectPhase.CONVERSION }) {
                if (!targets(inst).contains(m)) continue
                if (inst.effect.static) {
                    val now = Modifiers()
                    run(inst, m, snapshot, snapshot, now, EnemyModifiers())
                    val before = staticConversionOnSheet[inst to m.index] ?: StatSheet()
                    for (stat in Stat.entries) conv.stats.add(stat, now.stats[stat] - before[stat])
                } else {
                    val ownerStats = if (inst.owner === m) snapshot else inst.owner.selfStats
                    run(inst, m, ownerStats, snapshot, conv, enemy)
                }
            }
            m.final = snapshot.copy().apply { addAll(conv.stats) }
            m.mods.hitMods += conv.hitMods
            m.mods.infusions += conv.infusions
            conv.reactionBonus.forEach { (k, v) -> m.mods.reactionBonus.merge(k, v, Double::plus) }
            conv.reactionCritRate.forEach { (k, v) -> m.mods.reactionCritRate.merge(k, v, Double::plus) }
            conv.reactionCritDmg.forEach { (k, v) -> m.mods.reactionCritDmg.merge(k, v, Double::plus) }
            conv.reactionFlat.forEach { (k, v) -> m.mods.reactionFlat.merge(k, v, Double::plus) }
            conv.lunarBase.forEach { (k, v) -> m.mods.lunarBase.merge(k, v, Double::plus) }
            conv.lunarElevate.forEach { (k, v) -> m.mods.lunarElevate.merge(k, v, Double::plus) }
            m.mods.elementOverrides.putAll(conv.elementOverrides)
        }

        val enemyRes = Element.entries.associateWith { team.enemy.baseRes(it) }
        val hitCalc = HitCalculator(team.enemy, enemyRes, enemy)
        val results = members.map { m ->
            val own = instances.filter { it.owner === m && !it.teamWide }
            val received = instances.filter { it.active && targets(it).contains(m) }
            MemberResult(
                index = m.index,
                build = m.build,
                character = m.character,
                talentLevels = listOf(
                    com.genshincalc.core.model.TalentType.NORMAL,
                    com.genshincalc.core.model.TalentType.SKILL,
                    com.genshincalc.core.model.TalentType.BURST,
                ).associateWith { m.talentLevel(it) },
                screenStats = m.sheet,
                finalStats = m.final,
                hits = hitCalc.hits(m),
                transformative = hitCalc.transformative(m),
                ownEffects = own.map { AppliedEffect(it.effect, it.owner.index, it.value, it.active, it.effect.requirement(it.owner)) },
                receivedEffects = received.map { AppliedEffect(it.effect, it.owner.index, it.value, true, true) },
                infusion = hitCalc.infusionFor(m, com.genshincalc.core.model.AttackCategory.NORMAL),
            ).also {
                it.debugHitMods = m.mods.hitMods.toList()
                it.debugElementOverrides = m.mods.elementOverrides.toMap()
            }
        }
        return TeamResult(
            members = results,
            enemyLevel = team.enemy.level,
            enemyRes = enemyRes,
            resShred = enemy.resShred.toMap(),
            defReduction = enemy.defReduction,
            teamEffects = instances.filter { it.teamWide }.map {
                AppliedEffect(it.effect, 0, it.value, it.active, it.effect.requirement(it.owner))
            },
        )
    }

    private fun ownEffects(m: MemberState): List<Effect> = buildList {
        addAll(provider.characterEffects(m.characterId))
        m.build.weapon?.let { addAll(provider.weaponEffects(it.weaponId)) }
        m.build.artifacts.pieceCounts().keys.forEach { addAll(provider.setEffects(it)) }
        if (!m.build.customBuffs.isEmpty) add(customEffect(m))
    }

    private fun customEffect(m: MemberState): Effect {
        val c = m.build.customBuffs
        return Effect(
            id = "custom",
            name = "Custom buffs",
            description = "Extra buffs entered manually.",
            source = EffectSource.CUSTOM,
        ) {
            stat(Stat.ATK_PCT, c.atkPct)
            stat(Stat.ATK, c.atk)
            stat(Stat.HP_PCT, c.hpPct)
            stat(Stat.DEF_PCT, c.defPct)
            stat(Stat.EM, c.em)
            stat(Stat.CRIT_RATE, c.critRate)
            stat(Stat.CRIT_DMG, c.critDmg)
            stat(Stat.ALL_DMG, c.dmgBonus)
            if (c.flatDmg != 0.0) flatDmg(c.flatDmg)
            if (c.resShred != 0.0) resShredAll(c.resShred)
            if (c.defReduction != 0.0) defReduction(c.defReduction)
        }
    }
}
