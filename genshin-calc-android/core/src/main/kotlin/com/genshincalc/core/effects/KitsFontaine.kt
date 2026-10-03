package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

private val SKILL_TALENT = HitFilter(talents = setOf(TalentType.SKILL))
private val BURST_TALENT = HitFilter(talents = setOf(TalentType.BURST))

private val PYRO_ELECTRO_PARTY = Req("Pyro/Electro party") { o ->
    o.team.elements.all { it == Element.PYRO || it == Element.ELECTRO } &&
        o.team.count(Element.PYRO) > 0 && o.team.count(Element.ELECTRO) > 0
}

private val HYDRO_CRYO_PARTY = Req("4 Hydro/Cryo members") { o ->
    o.team.size == 4 && o.team.elements.all { it == Element.HYDRO || it == Element.CRYO }
}

internal fun EffectTable.fontaineKits() {
    this("furina") {
        val members = HitFilter.hits(
            "skill/gentilhomme-usher-dmg", "skill/surintendante-chevalmarin-dmg", "skill/mademoiselle-crabaletta-dmg",
        )
        effect(
            "members", "Salon Members", "Members deal 110/120/130/140% DMG with 1/2/3/4 party members above 50% HP.",
            control = stacks(4, 4, "Members above 50% HP"),
        ) {
            if (stacks > 0) multiplier(1 + 0.1 * stacks, members)
        }
        effect(
            "fanfare", "Universal Revelry (Fanfare)", "Party DMG and Incoming Healing Bonus per point of Fanfare (max 300, 400 at C1).",
            target = EffectTarget.TEAM, control = stacks(400, 300, "Fanfare", step = 25),
        ) {
            val fanfare = stacks.coerceAtMost(if (constellation >= 1) 400 else 300)
            stat(Stat.ALL_DMG, param(TalentType.BURST, "param5") * fanfare)
            stat(Stat.INCOMING_HEALING, param(TalentType.BURST, "param6") * fanfare)
        }
        effect("a4", "Unheard Confession", "Salon Member DMG +0.7% per 1,000 Max HP (max 28%).", phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus((0.007 * targetStats.hp / 1000).coerceAtMost(0.28), members)
        }
        effect("c2", "A Woman Adapts Like Duckweed in Water", "Max HP +0.35% per Fanfare point above the limit (max 140%).",
            control = stacks(400, 400, "Fanfare over limit", step = 20), requires = cons(2)) {
            stat(Stat.HP_PCT, 0.0035 * stacks)
        }
        effect("c6", "Center of Attention", "Hydro infusion; Normal, Charged and Plunging Attack DMG +18% of Max HP.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(6)) {
            infuse(Element.HYDRO, InfusionPriority.NON_OVERRIDABLE)
            flatDmg(0.18 * targetStats.hp, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
        effect("c6p", "Center of Attention (Pneuma)", "In Pneuma alignment the attacks deal a further 25% of Max HP.",
            control = toggle(false), phase = EffectPhase.CONVERSION, requires = cons(6)) {
            flatDmg(0.25 * targetStats.hp, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }

    this("neuvillette") {
        val judgment = HitFilter.hits("normal/charged-attack-equitable-judgment", "normal/c6-current")
        effect(
            "a1", "Past Draconic Glories", "Equitable Judgment deals 110/125/160% DMG with 1/2/3 stacks (C2: +14% CRIT DMG per stack).",
            control = stacks(3, 2, "Glories"), requires = A1,
        ) {
            val mult = when (stacks) {
                1 -> 1.10
                2 -> 1.25
                3 -> 1.60
                else -> 1.0
            }
            if (stacks > 0) multiplier(mult, judgment)
            if (constellation >= 2) critDmg(0.14 * stacks, judgment)
        }
        effect(
            "a4", "Discipline of the Supreme Arbitration", "Hydro DMG Bonus +0.6% per 1% of current HP above 30% (max 30%).",
            control = stacks(100, 80, "Current HP %", step = 5), requires = A4,
        ) {
            elementDmg(Element.HYDRO, (0.006 * (stacks - 30)).coerceIn(0.0, 0.30))
        }
    }

    this("wriothesley") {
        effect("chilling", "Chilling Penalty", "Normal Attacks become Enhanced Repelling Fists.", control = toggle()) {
            multiplier(param(TalentType.SKILL, "param1"), HitFilter.NORMAL)
        }
        effect("a1", "Rebuke: Vaulting Fist", "The Charged Attack becomes Rebuke: Vaulting Fist with +50% DMG (C1: +200%; C6: CRIT Rate +10%, CRIT DMG +80%).",
            control = toggle(), requires = A1) {
            dmgBonus(if (constellation >= 1) 2.0 else 0.5, HitFilter.CHARGED)
            if (constellation >= 6) {
                critRate(0.10, HitFilter.CHARGED)
                critDmg(0.80, HitFilter.CHARGED)
            }
        }
        effect("a4", "Prosecution Edict", "ATK +6% per stack (max 5) while in Chilling Penalty; C2: burst DMG +40% per stack.",
            control = stacks(5), requires = A4) {
            stat(Stat.ATK_PCT, 0.06 * stacks)
            if (constellation >= 2) dmgBonus(0.40 * stacks, BURST_TALENT)
        }
    }

    this("navia") {
        effect(
            "shrapnel", "Crystal Shrapnel", "Shrapnel consumed: more Rosula Shardshots (all assumed to hit); +15% DMG per stack above 3. " +
                "C2: CRIT Rate +12% per stack (max 36%). C6: CRIT DMG +45% per stack above 3.",
            control = stacks(6, 3, "Shrapnel"),
        ) {
            val shardshot = HitFilter.hits("skill/rosula-shardshot-base-dmg")
            val shots = 5 + 2 * stacks.coerceAtMost(3)
            val hitMult = when (shots) {
                5 -> 1.2
                7 -> 1.4
                9 -> 1.66
                else -> 2.0
            }
            multiplier(hitMult, shardshot)
            val extra = (stacks - 3).coerceAtLeast(0)
            dmgBonus(0.15 * extra, shardshot)
            if (constellation >= 2) critRate(0.12 * stacks.coerceAtMost(3), shardshot)
            if (constellation >= 6) critDmg(0.45 * extra, shardshot)
        }
        effect("a1", "Undisclosed Distribution Channels", "For 4s after her skill: Geo infusion and Normal/Charged/Plunging DMG +40%.",
            control = toggle(), requires = A1) {
            infuse(Element.GEO, InfusionPriority.NON_OVERRIDABLE)
            dmgBonus(0.40, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
        effect("a4", "Mutual Assistance Network", "ATK +20% per Pyro/Electro/Cryo/Hydro party member (max 2).", static = true, requires = A4) {
            val n = listOf(Element.PYRO, Element.ELECTRO, Element.CRYO, Element.HYDRO).sumOf { team.count(it) }
            stat(Stat.ATK_PCT, 0.20 * n.coerceAtMost(2))
        }
        effect("c4", "The Oathsworn Never Capitulate", "Burst hits lower Geo RES by 20%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            resShred(Element.GEO, 0.20)
        }
    }

    this("lyney") {
        val pyrotechnic = HitFilter.hits("normal/pyrotechnic-strike-dmg", "normal/c6-pyrotechnic-strike-reprised")
        effect("prop", "Prop Surplus", "Bewildering Lights DMG increased by a % of ATK per Prop Surplus stack.",
            control = stacks(5, 4, "Prop Surplus"), phase = EffectPhase.CONVERSION) {
            flatDmg(param(TalentType.SKILL, "param2") * stacks * targetStats.atk, HitFilter.hits("skill/skill-dmg"))
        }
        effect("a1", "Perilous Performance", "Grin-Malkin Hat (Pyrotechnic Strike) DMG +80% of ATK when the Prop Arrow consumed HP.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = A1) {
            flatDmg(0.80 * targetStats.atk, pyrotechnic)
        }
        effect("a4", "Conclusive Ovation", "DMG vs Pyro-affected opponents +60%, +20% per other Pyro member (max 100%).",
            control = toggle(), requires = A4) {
            dmgBonus((0.60 + 0.20 * (team.count(Element.PYRO) - 1)).coerceAtMost(1.0))
        }
        effect("c2", "Loquacious Cajoling", "CRIT DMG +20% per Crisp Focus stack (max 3).", control = stacks(3), requires = cons(2)) {
            critDmg(0.20 * stacks)
        }
        effect("c4", "Well-Versed, Well-Rehearsed", "Pyro Charged Attacks lower Pyro RES by 20%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            resShred(Element.PYRO, 0.20)
        }
        effect("c6", "Guarded Smile", "Pyrotechnic Strike: Reprised deals 80% of a Pyrotechnic Strike's DMG.", requires = cons(6)) {
            multiplier(0.80, HitFilter.hits("normal/c6-pyrotechnic-strike-reprised"))
        }
    }

    this("clorinde") {
        effect(
            "a1", "Dark-Shattering Flame", "Electro Normal Attack and burst DMG +20% of ATK per stack (max 3, 1,800); C2: 30% (2,700).",
            control = stacks(3), phase = EffectPhase.CONVERSION, requires = A1,
        ) {
            val (k, cap) = if (constellation >= 2) 0.30 to 2700.0 else 0.20 to 1800.0
            val filter = HitFilter(categories = setOf(AttackCategory.NORMAL, AttackCategory.BURST), elements = setOf(Element.ELECTRO))
            flatDmg((k * stacks * targetStats.atk).coerceAtMost(cap), filter)
        }
        effect("a4", "Lawful Remuneration", "CRIT Rate +10% per stack (max 2) while Bond of Life is at least 100% Max HP.",
            control = stacks(2), requires = A4) {
            critRate(0.10 * stacks)
        }
        effect("c4", "To Enshrine Tears, Life, and Love", "Last Lightfall DMG +2% per 1% Bond of Life (max 200%).",
            control = stacks(100, 100, "Bond of Life %", step = 5), requires = cons(4)) {
            dmgBonus((0.02 * stacks).coerceAtMost(2.0), BURST_TALENT)
        }
        effect("c6", "And So Shall I Never Despair", "CRIT Rate +10% and CRIT DMG +70% for 12s after her skill.", control = toggle(), requires = cons(6)) {
            critRate(0.10)
            critDmg(0.70)
        }
    }

    this("chevreuse") {
        effect(
            "a1", "Coordinated Tactics", "Overloaded lowers Pyro and Electro RES by 40%.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1 and PYRO_ELECTRO_PARTY,
        ) {
            resShred(Element.PYRO, 0.40)
            resShred(Element.ELECTRO, 0.40)
        }
        effect(
            "a4", "Vertical Force Coordination", "Pyro and Electro members gain ATK +1% per 1,000 of Chevreuse's Max HP (max 40%).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            if (targetElement == Element.PYRO || targetElement == Element.ELECTRO) {
                stat(Stat.ATK_PCT, (0.01 * ownerStats.hp / 1000).coerceAtMost(0.40))
            }
        }
        effect("c6", "In Pursuit of Ending Evil", "Party Pyro and Electro DMG +20% per heal stack (max 3).",
            target = EffectTarget.TEAM, control = stacks(3), requires = cons(6)) {
            elementDmg(Element.PYRO, 0.20 * stacks)
            elementDmg(Element.ELECTRO, 0.20 * stacks)
        }
    }

    this("charlotte") {
        effect("a4", "Diversified Investigation", "Healing Bonus +5% per other Fontainian, Cryo DMG +5% per non-Fontainian member (max 3 each).",
            static = true, requires = A4) {
            val others = team.size - 1
            val fontaine = (team.regions.count { it == "Fontaine" } - 1).coerceIn(0, 3)
            stat(Stat.HEALING_BONUS, 0.05 * fontaine)
            elementDmg(Element.CRYO, 0.05 * (others - fontaine).coerceIn(0, 3))
        }
        effect("c2", "A Duty to Pursue Truth", "ATK +10/20/30% when Monsieur Verite hits 1/2/3+ opponents.",
            control = choice("1 opponent", "2 opponents", "3+ opponents"), requires = cons(2)) {
            stat(Stat.ATK_PCT, 0.10 * (value + 1))
        }
        effect("c4", "A Responsibility to Oversee", "Burst DMG +10% against marked opponents.", control = toggle(), requires = cons(4)) {
            dmgBonus(0.10, BURST_TALENT)
        }
    }

    this("freminet") {
        val shattering = HitFilter.hits(
            "skill/level-0-shattering-pressure-dmg", "skill/level-1-shattering-pressure-dmg", "skill/level-2-shattering-pressure-dmg",
            "skill/level-3-shattering-pressure-dmg", "skill/level-4-shattering-pressure-dmg",
        )
        effect("a4", "Parallel Condensers", "Shattering Pressure DMG +40% for 5s after triggering Shatter.", control = toggle(false), requires = A4) {
            dmgBonus(0.40, shattering)
        }
        effect("c1", "Dreams of the Foamy Deep", "Shattering Pressure CRIT Rate +15%.", requires = cons(1)) {
            critRate(0.15, shattering)
        }
        effect("c4", "Dance of the Snowy Moon and Flute", "ATK +9% per stack (max 2) after Frozen, Shatter or Superconduct.",
            control = stacks(2), requires = cons(4)) {
            stat(Stat.ATK_PCT, 0.09 * stacks)
        }
        effect("c6", "Moment of Waking and Resolve", "CRIT DMG +12% per stack (max 3) after Frozen, Shatter or Superconduct.",
            control = stacks(3), requires = cons(6)) {
            critDmg(0.12 * stacks)
        }
    }

    this("lynette") {
        effect("a1", "Sophisticated Synergy", "Party ATK +8/12/16/20% with 1/2/3/4 Elemental Types after the burst.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1) {
            stat(Stat.ATK_PCT, 0.04 + 0.04 * team.distinctElements.coerceIn(1, 4))
        }
        effect("a4", "Props Positively Prepped", "Burst DMG +15% after the Bogglecat Box converts its element.", control = toggle(), requires = A4) {
            dmgBonus(0.15, BURST_TALENT)
        }
        effect("c6", "Watchful Eye", "Anemo infusion and Anemo DMG +20% for 6s after Enigma Thrust.", control = toggle(), requires = cons(6)) {
            infuse(Element.ANEMO)
            elementDmg(Element.ANEMO, 0.20)
        }
    }

    this("sigewinne") {
        effect("a1", "Semi-Strict Bedrest", "Hydro DMG Bonus +8% for 18s after her skill.", control = toggle(), requires = A1) {
            elementDmg(Element.HYDRO, 0.08)
        }
        effect(
            "a1b", "Convalescence", "Off-field members' skill DMG +80 per 1,000 Max HP above 30,000 (max 2,800; C1: 100 / 3,500).",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A1,
        ) {
            val (k, cap) = if (constellation >= 1) 100.0 to 3500.0 else 80.0 to 2800.0
            flatDmg((k * (ownerStats.hp - 30000) / 1000).coerceIn(0.0, cap), HitFilter.SKILL)
        }
        effect("c2", "Can the Most Merciful of Spirits Defeat Its Foes?", "Skill and burst hits lower Hydro RES by 35%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            resShred(Element.HYDRO, 0.35)
        }
        effect("c6", "Can the Most Radiant of Spirits Pray For Me?", "Burst CRIT Rate +0.4% and CRIT DMG +2.2% per 1,000 Max HP (max 20% / 110%).",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(6)) {
            val k = targetStats.hp / 1000
            critRate((0.004 * k).coerceAtMost(0.20), BURST_TALENT)
            critDmg((0.022 * k).coerceAtMost(1.10), BURST_TALENT)
        }
    }

    this("emilie") {
        effect("a4", "Rectification", "DMG vs Burning opponents +15% per 1,000 ATK (max 36%).", control = toggle(), phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus((0.15 * targetStats.atk / 1000).coerceAtMost(0.36))
        }
        effect("c1", "Light Fragrance Leaching", "Fragrance Extraction and Cleardew Cologne DMG +20%.", requires = cons(1)) {
            dmgBonus(0.20, SKILL_TALENT)
        }
        effect("c2", "Lakelight Top Note", "Hits lower Dendro RES by 30%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            resShred(Element.DENDRO, 0.30)
        }
        effect("c6", "Marcotte Sillage", "Abiding Fragrance: Dendro infusion, Normal and Charged Attack DMG +300% of ATK.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(6)) {
            infuse(Element.DENDRO, InfusionPriority.NON_OVERRIDABLE, setOf(AttackCategory.NORMAL, AttackCategory.CHARGED))
            flatDmg(3.0 * targetStats.atk, HitFilter.NORMAL_CHARGED)
        }
    }

    this("escoffier") {
        effect(
            "a4", "Inspiration-Immersed Seasoning", "Skill/burst hits lower Hydro and Cryo RES by 5/10/15/55% with 1/2/3/4 Hydro or Cryo members.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4,
        ) {
            val shred = when (team.count(Element.HYDRO) + team.count(Element.CRYO)) {
                0 -> 0.0
                1 -> 0.05
                2 -> 0.10
                3 -> 0.15
                else -> 0.55
            }
            resShred(Element.HYDRO, shred)
            resShred(Element.CRYO, shred)
        }
        effect("c1", "Pre-Dinner Dance for Your Taste Buds", "Party Cryo CRIT DMG +60% (4 Hydro/Cryo members).",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(1) and A4 and HYDRO_CRYO_PARTY) {
            critDmg(0.60, HitFilter.element(Element.CRYO))
        }
        effect(
            "c2", "Freshly-Prepped Delicacy", "Other members' Cryo DMG +240% of Escoffier's ATK (5 Cold Dish stacks).",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(2),
        ) {
            flatDmg(2.4 * ownerStats.atk, HitFilter(
                categories = setOf(AttackCategory.NORMAL, AttackCategory.CHARGED, AttackCategory.PLUNGE, AttackCategory.SKILL, AttackCategory.BURST),
                elements = setOf(Element.CRYO),
            ))
        }
    }
}
