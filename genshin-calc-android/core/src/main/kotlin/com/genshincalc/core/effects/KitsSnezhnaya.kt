package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

private val STELLAR = listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)
private val SKILL_TALENT = HitFilter(talents = setOf(TalentType.SKILL))
private val BURST_TALENT = HitFilter(talents = setOf(TalentType.BURST))

/** "Stellar Jubilee": party Stellar reaction Base DMG +0.7% per 100 of the owner's ATK (max 14%). */
private fun EffectListBuilder.stellarJubilee(name: String, reactions: List<Reaction>) {
    effect("jubilee", "Stellar Jubilee: $name", "Party Stellar reaction Base DMG +0.7% per 100 ATK (max 14%).",
        target = EffectTarget.TEAM, phase = EffectPhase.TEAM_STAT) {
        val bonus = (0.007 * ownerStats.atk / 100).coerceAtMost(0.14)
        for (r in reactions) lunarBaseBonus(r, bonus)
    }
}

internal fun EffectTable.snezhnayaKits() {
    this("arlecchino") {
        effect(
            "masque", "Masque of the Red Death", "With Bond of Life >= 30%: Pyro infusion; Normal Attacks deal extra DMG of ATK x ratio x Bond of Life " +
                "(C1: +100% ratio). C6: burst DMG +700% of ATK x Bond of Life.",
            control = stacks(200, 145, "Bond of Life %", step = 5), phase = EffectPhase.CONVERSION,
        ) {
            if (stacks >= 30) {
                infuse(Element.PYRO, InfusionPriority.NON_OVERRIDABLE)
                val ratio = param(TalentType.NORMAL, "param12") + if (constellation >= 1) 1.0 else 0.0
                flatDmg(ratio * targetStats.atk * stacks / 100, HitFilter.NORMAL)
            }
            if (constellation >= 6) flatDmg(7.0 * targetStats.atk * stacks / 100, BURST_TALENT)
        }
        effect("p3", "The Balemoon Alone May Know", "Pyro DMG Bonus +40% while in combat.") {
            elementDmg(Element.PYRO, 0.40)
        }
        effect("c6", "From This Day On, We Shall Delight in New Life Together.", "Normal Attack and burst CRIT Rate +10%, CRIT DMG +70%.",
            control = toggle(), requires = cons(6)) {
            val filter = HitFilter.of(AttackCategory.NORMAL, AttackCategory.BURST)
            critRate(0.10, filter)
            critDmg(0.70, filter)
        }
    }

    // Tartaglia's passives and constellations do not change damage numbers.
    this("tartaglia") {}

    this("skirk") {
        val sevenPhaseNormal = HitFilter(categories = setOf(AttackCategory.NORMAL), talents = setOf(TalentType.SKILL))
        val burst = HitFilter.hits("burst/slash-dmg", "burst/final-slash-dmg")
        effect(
            "serpent", "Serpent's Subtlety", "Havoc: Ruin DMG increased by ATK per point above 50 (max 12; 22 at C2).",
            control = stacks(22, 12, "Points above 50"), phase = EffectPhase.CONVERSION,
        ) {
            val points = stacks.coerceAtMost(if (constellation >= 2) 22 else 12)
            val death = deathsCrossing(team)
            val burstMult = if (owner.ascension >= 4) listOf(1.0, 1.05, 1.15, 1.60)[death] else 1.0
            flatDmg(param(TalentType.BURST, "param3") * points * targetStats.atk * burstMult, burst)
        }
        effect(
            "rifts", "Havoc: Extinction", "Seven-Phase Flash Normal Attack DMG bonus by Void Rifts absorbed.",
            control = choice("0 Void Rifts", "1 Void Rift", "2 Void Rifts", "3 Void Rifts", default = 3),
        ) {
            dmgBonus(param(TalentType.BURST, "param${4 + value}"), sevenPhaseNormal)
        }
        effect(
            "a4", "Death's Crossing", "Each Hydro member and other Cryo member (max 3): Seven-Phase Flash Normal Attacks deal 110/120/170% DMG, " +
                "Havoc: Ruin 105/115/160%. C4: ATK +10/20/40%.",
            control = toggle(), requires = A4,
        ) {
            val death = deathsCrossing(team)
            if (death > 0) {
                multiplier(listOf(1.0, 1.10, 1.20, 1.70)[death], sevenPhaseNormal)
                if (constellation >= 4) stat(Stat.ATK_PCT, listOf(0.0, 0.10, 0.20, 0.40)[death])
            }
        }
        effect("a4burst", "Death's Crossing (Havoc: Ruin)", "Havoc: Ruin deals 105/115/160% DMG.", requires = A4) {
            val death = deathsCrossing(team)
            if (death > 0) multiplier(listOf(1.0, 1.05, 1.15, 1.60)[death], burst)
        }
        effect("c2", "Into the Abyss", "ATK +70% after Havoc: Extinction.", control = toggle(), requires = cons(2)) {
            stat(Stat.ATK_PCT, 0.70)
        }
    }

    this("alyosha") {
        effect(
            "precision", "Hunter's Precision", "The active character gains ATK (2 stacks at C6, plus 100 EM); in Radiance: Stellar-Conduct DMG +20% per stack.",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            val n = if (constellation >= 6) 2 else 1
            stat(Stat.ATK_PCT, param(TalentType.SKILL, "param5") * n)
            reactionBonus(Reaction.STELLAR_CONDUCT, 0.20 * n)
            if (constellation >= 6) stat(Stat.EM, 100.0)
        }
        effect("a4", "Suffer the Winter Wheat Will", "Skill and burst DMG +0.35% per 1% Energy Recharge (max 70%).",
            phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus((0.35 * targetStats.er).coerceAtMost(0.70), HitFilter(talents = setOf(TalentType.SKILL, TalentType.BURST)))
        }
    }

    this("nicole") {
        effect(
            "kenosis", "Grace of Kenosis", "Party ATK bonus based on Nicole's ATK (capped); C2: +300 ATK more.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            val bonus = (param(TalentType.SKILL, "param5") * ownerStats.atk).coerceAtMost(param(TalentType.SKILL, "param6"))
            stat(Stat.ATK, bonus + if (constellation >= 2) 300.0 else 0.0)
        }
        effect(
            "theosis", "Guidance of Theosis", "+300 ATK. C2: opponents' RES to the character's element -25%. C6: DMG ignores 40% DEF.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1,
        ) {
            stat(Stat.ATK, 300.0)
            if (constellation >= 2 && targetElement != Element.PHYSICAL) resShred(targetElement, 0.25)
            if (constellation >= 6) defIgnore(0.40)
        }
        effect(
            "c4", "Pathfinder's Blessing", "Party attacks deal additional DMG equal to 70% of Nicole's ATK (8 times).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(4),
        ) {
            flatDmg(0.70 * ownerStats.atk, ALL_TALENT_ATTACKS)
        }
    }

    this("odette") {
        val stellarHits = HitFilter.hits(
            "skill/coda-at-dawn-s-tolling-stellar-conduct", "skill/stellar-swirl-dmg", "skill/plume-dance-move-stellar-conduct",
            "skill/stellar-swirl-dmg-2", "skill/wing-dance-move-stellar-conduct", "skill/stellar-swirl-dmg-3",
            "skill/c1-duet-stellar-conduct", "skill/c1-duet-stellar-swirl", "burst/c4-coordinated-stellar-conduct",
            "burst/c4-coordinated-stellar-swirl",
        )
        stellarJubilee("Dance of Aurore", STELLAR)
        effect(
            "a1", "Marvelous Splendor", "Stellar Glimmer DMG +15% per stack (4, 6 at C1). C2: ATK +7% per stack. C6: elevated by 25% (Odette +20% more).",
            target = EffectTarget.TEAM, control = stacks(6, 4, "Marvelous Splendor"), requires = A1,
        ) {
            val n = stacks.coerceAtMost(if (constellation >= 1) 6 else 4)
            for (r in STELLAR) reactionBonus(r, 0.15 * n)
            if (constellation >= 2) stat(Stat.ATK_PCT, 0.07 * n)
            if (constellation >= 6) for (r in STELLAR) lunarElevate(r, 0.25 + if (isSelf) 0.20 else 0.0)
        }
        effect("a4", "Pathetique of Pateticheskaya", "Odette's Stellar Glimmer DMG +1.5% per 100 ATK above 1,000 (max 30%).",
            phase = EffectPhase.CONVERSION, requires = A4) {
            multiplier(1 + (0.015 * (targetStats.atk - 1000) / 100).coerceIn(0.0, 0.30), stellarHits)
        }
        effect(
            "burst", "Snow Swan's Dream", "Odette's Stellar Glimmer reaction DMG bonus; C4: other members gain 50% of it.",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            val bonus = param(TalentType.BURST, "param3")
            if (isSelf) for (r in STELLAR) reactionBonus(r, bonus)
            else if (constellation >= 4) for (r in STELLAR) reactionBonus(r, 0.5 * bonus)
        }
        effect(
            "c2", "Snow Swan's Unseen Dream", "Opponents near the Dance Double: Cryo and Electro (Stellar-Conduct) or Cryo and Anemo (Stellar Swirl) RES -20%.",
            target = EffectTarget.TEAM, control = choice("Radiance: Stellar-Conduct", "Radiance: Stellar Swirl"), requires = cons(2),
        ) {
            resShred(Element.CRYO, 0.20)
            resShred(if (value == 0) Element.ELECTRO else Element.ANEMO, 0.20)
        }
    }

    this("sandrone") {
        val ray = HitFilter.hits(
            "burst/convective-inhibition-ray-dmg", "burst/convective-inhibition-ray-stellar-conduct-dmg",
            "burst/convective-inhibition-ray-stellar-swirl-dmg",
        )
        val beams = HitFilter.hits(
            "normal/charged-attack-condensed-beam-dmg", "normal/charged-attack-condensed-beam-stellar-conduct-dmg",
            "normal/charged-attack-condensed-beam-stellar-swirl-dmg",
        )
        stellarJubilee("Light of Rationalisme", STELLAR)
        effect("a1", "Refined Tactics", "Radiance: the Convective Inhibition Ray deals +10% of its DMG per stack (max 10).",
            control = stacks(10), requires = A1) {
            if (stacks > 0) multiplier(1 + 0.10 * stacks, ray)
        }
        effect(
            "a1b", "Second Prism Shot", "Radiance with over 50 Decoding Power: the second Prism Shot deals 400% DMG.",
            control = toggle(false), requires = A1,
        ) {
            multiplier(4.0, HitFilter.hits("skill/prism-shot-dmg", "skill/prism-shot-stellar-conduct-dmg", "skill/prism-shot-stellar-swirl-dmg"))
        }
        effect("a4", "A Lady's Code of Conduct", "EM +8 per 100 ATK (max 160).", static = true, phase = EffectPhase.CONVERSION, requires = A4) {
            stat(Stat.EM, (0.08 * targetStats.atk).coerceAtMost(160.0))
        }
        effect("c1", "Morrow After the Golden Dusk", "Party Stellar Glimmer reaction DMG +30% in Decoding mode.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(1)) {
            for (r in STELLAR) reactionBonus(r, 0.30)
        }
        effect("c2", "An Heiress Gazed Into the Looking-Glass", "Condensed beam CRIT DMG +40%, +20% per beam fired (max 3).",
            control = stacks(3), requires = cons(2)) {
            critDmg(0.40 + 0.20 * stacks, beams)
        }
        effect("c6", "Narcissus Wakes, Her Eyes Upon the Dawn", "Sandrone's Stellar Glimmer reaction DMG is elevated by 20%.", requires = cons(6)) {
            for (r in STELLAR) lunarElevate(r, 0.20)
        }
    }

    this("vesna") {
        val blades = HitFilter.hits(
            "skill/windborne-sword-lv-2-spirit-blade-dmg", "skill/windborne-sword-lv-3-spirit-blade-dmg",
            "skill/windborne-sword-lv-3-spirit-blade-final-hit-dmg", "skill/windborne-sword-lv-2-spirit-blade-stellar-swirl-dmg",
            "skill/windborne-sword-lv-3-spirit-blade-stellar-swirl-dmg", "skill/windborne-sword-lv-3-spirit-blade-final-hit-stellar-swirl-dmg",
            "burst/spirit-blade-dmg", "burst/spirit-blade-stellar-swirl-dmg", "skill/c6-transpose-spirit-blade",
        )
        stellarJubilee("Splendid Prelude", listOf(Reaction.STELLAR_SWIRL))
        effect("a1", "Disciplinary Action", "Spirit Blades deal +10% of their DMG per stack (max 6).", control = stacks(6), requires = A1) {
            if (stacks > 0) multiplier(1 + 0.10 * stacks, blades)
        }
        effect(
            "a4", "Truth Prevails", "Radiance: Stellar Swirl: ATK +6% per Cryo/Anemo member, EM +25 per other member (C4: x3).",
            control = toggle(), requires = A4,
        ) {
            val k = if (constellation >= 4) 3 else 1
            val cryoAnemo = team.count(Element.CRYO) + team.count(Element.ANEMO)
            stat(Stat.ATK_PCT, 0.06 * k * cryoAnemo)
            stat(Stat.EM, 25.0 * k * (team.size - cryoAnemo))
        }
        effect("c1", "Winter's Farewell Feast", "Stellar Swirl DMG +20% in Armed for Action mode.", control = toggle(), requires = cons(1)) {
            reactionBonus(Reaction.STELLAR_SWIRL, 0.20)
        }
        effect("c2", "Kolo of Spring's Arrival", "ATK +40% with maximum Disciplinary Action stacks.", control = toggle(), requires = cons(2) and A1) {
            stat(Stat.ATK_PCT, 0.40)
        }
        effect("c6", "Unwavering Ardor", "Vesna's Stellar Swirl DMG is elevated by 20%.", requires = cons(6)) {
            lunarElevate(Reaction.STELLAR_SWIRL, 0.20)
        }
    }

    this("vodyanitsa") {
        effect("song", "Song of Ages Past", "Skill hits lower Hydro and Cryo RES; the burst deals more DMG.", target = EffectTarget.TEAM, control = toggle()) {
            val shred = param(TalentType.SKILL, "param8")
            resShred(Element.HYDRO, shred)
            resShred(Element.CRYO, shred)
            if (isSelf) dmgBonus(param(TalentType.BURST, "param2"), BURST_TALENT)
        }
        effect("a1", "Wandering Vortex", "Generating or detonating a Wandering Vortex lowers Anemo RES by 35%.",
            target = EffectTarget.TEAM, control = toggle(false), requires = A1) {
            resShred(Element.ANEMO, 0.35)
        }
        effect("c1", "Waters in Full Splendor", "Party ATK +0.8% of Vodyanitsa's Max HP after her healing.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(1)) {
            stat(Stat.ATK, 0.008 * ownerStats.hp)
        }
        effect("c2", "Two Voices in Black and White", "Active character's Hydro and Cryo CRIT DMG +50% (C6: whole party).",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            critDmg(0.50, HitFilter.element(Element.HYDRO, Element.CRYO))
        }
        effect("c4", "Melancholic Voice Upon the Gentle Waters", "Max HP +20% per stack (max 3).", control = stacks(3), requires = cons(4)) {
            stat(Stat.HP_PCT, 0.20 * stacks)
        }
        effect("c6", "Neverending Song of Revelry", "Party Stellar Swirl DMG elevated by 25%; Hydro and Cryo DMG +60%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            lunarElevate(Reaction.STELLAR_SWIRL, 0.25)
            elementDmg(Element.HYDRO, 0.60)
            elementDmg(Element.CRYO, 0.60)
        }
    }
}

/** Skirk's Death's Crossing stacks: one per Hydro member and per other Cryo member (max 3). */
private fun deathsCrossing(team: com.genshincalc.core.calc.TeamInfo): Int =
    (team.count(Element.HYDRO) + (team.count(Element.CRYO) - 1).coerceAtLeast(0)).coerceIn(0, 3)
