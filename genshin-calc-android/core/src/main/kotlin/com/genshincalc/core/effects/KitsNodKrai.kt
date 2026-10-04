package com.genshincalc.core.effects

import com.genshincalc.core.calc.Effect
import com.genshincalc.core.calc.EffectControl
import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectSource
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.StatSheet
import com.genshincalc.core.calc.TeamInfo
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

/** Characters whose "Moonsign Benediction" raises the party's Moonsign level. */
internal val MOONSIGN_CHARACTERS = setOf("ineffa", "flins", "lauma", "columbina", "aino", "nefer", "illuga", "jahoda", "linnea", "zibai")

/** Party Moonsign level: 1 = Nascent Gleam, 2+ = Ascendant Gleam. */
internal fun TeamInfo.moonsign(): Int = characterIds.count { it in MOONSIGN_CHARACTERS }

internal val ASCENDANT_GLEAM = Req("Ascendant Gleam") { it.team.moonsign() >= 2 }

private val LUNAR = listOf(Reaction.LUNAR_CHARGED, Reaction.LUNAR_BLOOM, Reaction.LUNAR_CRYSTALLIZE)

/** Lunar reaction DMG bonus a non-Moonsign character grants under Ascendant Gleam (before the 36% cap). */
private fun ascendantBonus(element: Element, s: StatSheet): Double = when (element) {
    Element.PYRO, Element.ELECTRO, Element.CRYO -> 0.009 * s.atk / 100
    Element.HYDRO -> 0.006 * s.hp / 1000
    Element.GEO -> 0.01 * s.def / 100
    Element.ANEMO, Element.DENDRO -> 0.0225 * s.em / 100
    Element.PHYSICAL -> 0.0
}

/** Party-wide Moonsign mechanic. */
internal fun moonsignEffects(): List<Effect> = listOf(
    Effect(
        id = "party.moonsign",
        name = "Moonsign: Ascendant Gleam",
        description = "After a non-Moonsign member uses a Skill or Burst, party Lunar reaction DMG increases by that member's stats " +
            "(Pyro/Electro/Cryo: 0.9% per 100 ATK, Hydro: 0.6% per 1,000 HP, Geo: 1% per 100 DEF, Anemo/Dendro: 2.25% per 100 EM; " +
            "max 36%, the highest applies).",
        source = EffectSource.PARTY,
        target = EffectTarget.TEAM,
        control = EffectControl.Toggle(true),
        phase = EffectPhase.TEAM_STAT,
        requirement = { o -> o.team.moonsign() >= 2 && o.team.characterIds.any { it !in MOONSIGN_CHARACTERS } },
        requirementLabel = "2 Moonsign",
        apply = {
            val best = teamMaxOf { m -> if (m.characterId in MOONSIGN_CHARACTERS) 0.0 else ascendantBonus(m.element, m.selfStats) }
            for (r in LUNAR) reactionBonus(r, best.coerceAtMost(0.36))
        },
    ),
)

/** "Moonsign Benediction": party Lunar reaction Base DMG increase from the owner's stats. */
private fun EffectListBuilder.benediction(name: String, reactions: List<Reaction>, description: String, amount: (StatSheet) -> Double) {
    effect("benediction", "Moonsign Benediction: $name", description, target = EffectTarget.TEAM, phase = EffectPhase.TEAM_STAT) {
        val bonus = amount(ownerStats)
        for (r in reactions) lunarBaseBonus(r, bonus)
    }
}

internal fun EffectTable.nodKraiKits() {
    this("ineffa") {
        benediction("Assemblage Hub", listOf(Reaction.LUNAR_CHARGED), "Party Lunar-Charged Base DMG +0.7% per 100 ATK (max 14%).") {
            (0.007 * it.atk / 100).coerceAtMost(0.14)
        }
        effect(
            "a4", "Parameter Permutation", "After the burst, Ineffa and the active character gain EM equal to 6% of Ineffa's ATK.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            stat(Stat.EM, 0.06 * ownerStats.atk)
        }
        effect(
            "c1", "Carrier Flow Composite", "Party Lunar-Charged DMG +2.5% per 100 of Ineffa's ATK (max 50%).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(1),
        ) {
            reactionBonus(Reaction.LUNAR_CHARGED, (0.025 * ownerStats.atk / 100).coerceAtMost(0.50))
        }
    }

    this("flins") {
        benediction("Old World Secrets", listOf(Reaction.LUNAR_CHARGED), "Party Lunar-Charged Base DMG +0.7% per 100 ATK (max 14%).") {
            (0.007 * it.atk / 100).coerceAtMost(0.14)
        }
        effect("a1", "Symphony of Winter", "Ascendant Gleam: Lunar-Charged DMG triggered by Flins +20%.", requires = A1 and ASCENDANT_GLEAM) {
            reactionBonus(Reaction.LUNAR_CHARGED, 0.20)
        }
        effect("a4", "Whispering Flame", "EM +8% of ATK (max 160); C4: 10% of ATK (max 220).",
            static = true, phase = EffectPhase.CONVERSION, requires = A4) {
            val (k, cap) = if (constellation >= 4) 0.10 to 220.0 else 0.08 to 160.0
            stat(Stat.EM, (k * targetStats.atk).coerceAtMost(cap))
        }
        effect("c2", "The Devil's Wall", "Ascendant Gleam: Flins's Electro hits lower Electro RES by 25%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2) and ASCENDANT_GLEAM) {
            resShred(Element.ELECTRO, 0.25)
        }
        effect("c4", "Night on Bald Mountain", "ATK +20%.", static = true, requires = cons(4)) {
            stat(Stat.ATK_PCT, 0.20)
        }
        effect("c6", "Songs and Dances of Death", "Flins's Lunar-Charged DMG is elevated by 35%; Ascendant Gleam: party Lunar-Charged elevated by 10%.",
            target = EffectTarget.TEAM, requires = cons(6)) {
            if (isSelf) lunarElevate(Reaction.LUNAR_CHARGED, 0.35)
            if (team.moonsign() >= 2) lunarElevate(Reaction.LUNAR_CHARGED, 0.10)
        }
    }

    this("lauma") {
        benediction("Nature's Chorus", listOf(Reaction.LUNAR_BLOOM), "Party Lunar-Bloom Base DMG +0.0175% per EM (max 14%).") {
            (0.000175 * it.em).coerceAtMost(0.14)
        }
        effect("skill", "Frostgrove Sanctuary", "Skill hits lower Dendro and Hydro RES.", target = EffectTarget.TEAM, control = toggle()) {
            val shred = param(TalentType.SKILL, "param8")
            resShred(Element.DENDRO, shred)
            resShred(Element.HYDRO, shred)
        }
        effect(
            "burst", "Pale Hymn", "Party Bloom/Hyperbloom/Burgeon and Lunar-Bloom DMG increased by Lauma's EM (C2: +500% / +400% EM).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            val em = ownerStats.em
            val c2 = constellation >= 2
            val bloom = (param(TalentType.BURST, "param3") + if (c2) 5.0 else 0.0) * em
            for (r in listOf(Reaction.BLOOM, Reaction.HYPERBLOOM, Reaction.BURGEON)) reactionFlat(r, bloom)
            reactionFlat(Reaction.LUNAR_BLOOM, (param(TalentType.BURST, "param4") + if (c2) 4.0 else 0.0) * em)
        }
        effect(
            "a1", "Light for the Frosty Night", "Nascent Gleam: Bloom/Hyperbloom/Burgeon can CRIT (15% / 100%). " +
                "Ascendant Gleam: Lunar-Bloom CRIT Rate +10%, CRIT DMG +20%.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1,
        ) {
            if (team.moonsign() >= 2) reactionCrit(Reaction.LUNAR_BLOOM, 0.10, 0.20)
            else for (r in listOf(Reaction.BLOOM, Reaction.HYPERBLOOM, Reaction.BURGEON)) reactionCrit(r, 0.15, 1.00)
        }
        effect("a4", "Cleansing for the Spring", "Skill DMG +0.04% per EM (max 32%).", phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus((0.0004 * targetStats.em).coerceAtMost(0.32), HitFilter.SKILL)
        }
        effect("c2", "Twine Warnings and Tales From the North", "Ascendant Gleam: party Lunar-Bloom DMG +40%.",
            target = EffectTarget.TEAM, requires = cons(2) and ASCENDANT_GLEAM) {
            reactionBonus(Reaction.LUNAR_BLOOM, 0.40)
        }
        effect("c6", "I Offer Blood and Tears to the Moonlight", "Ascendant Gleam: party Lunar-Bloom DMG elevated by 25%.",
            target = EffectTarget.TEAM, requires = cons(6) and ASCENDANT_GLEAM) {
            lunarElevate(Reaction.LUNAR_BLOOM, 0.25)
        }
    }

    this("columbina") {
        benediction("Moonlight, Lent Unto You", LUNAR, "Party Lunar reaction Base DMG +0.2% per 1,000 Max HP (max 7%).") {
            (0.002 * it.hp / 1000).coerceAtMost(0.07)
        }
        effect("burst", "Lunar Domain", "Party Lunar reaction DMG bonus.", target = EffectTarget.TEAM, control = toggle()) {
            for (r in LUNAR) reactionBonus(r, param(TalentType.BURST, "param2"))
        }
        effect("a1", "Lunacy's Lure", "CRIT Rate +5% per Lunacy stack (max 3).", control = stacks(3), requires = A1) {
            critRate(0.05 * stacks)
        }
        effect("elevate", "Lunar elevation (C1/C2/C4/C6)", "Party Lunar reaction DMG elevated by 1.5% (C1), 7% (C2), 1.5% (C4), 7% (C6).",
            target = EffectTarget.TEAM, requires = cons(1)) {
            val e = listOf(1 to 0.015, 2 to 0.07, 4 to 0.015, 6 to 0.07).filter { constellation >= it.first }.sumOf { it.second }
            for (r in LUNAR) lunarElevate(r, e)
        }
        effect("c2", "Lunar Brilliance", "Max HP +40% for 8s after Gravity Interference.", control = toggle(), requires = cons(2)) {
            stat(Stat.HP_PCT, 0.40)
        }
        effect(
            "c2b", "Lunar Brilliance (Ascendant Gleam)", "The active character gains ATK (1% of Max HP), EM (0.35%) or DEF (1%) by the dominant Lunar reaction.",
            target = EffectTarget.TEAM, control = choice("Lunar-Charged", "Lunar-Bloom", "Lunar-Crystallize"), phase = EffectPhase.TEAM_STAT,
            requires = cons(2) and ASCENDANT_GLEAM,
        ) {
            val hp = ownerStats.hp
            when (value) {
                0 -> stat(Stat.ATK, 0.01 * hp)
                1 -> stat(Stat.EM, 0.0035 * hp)
                else -> stat(Stat.DEF, 0.01 * hp)
            }
        }
        effect("c4", "Cloudveiled Ridges in Floral Mists", "Gravity Interference DMG +12.5% (Lunar-Charged/Crystallize) or +2.5% (Lunar-Bloom) of Max HP.",
            phase = EffectPhase.CONVERSION, requires = cons(4)) {
            val hp = targetStats.hp
            flatDmg(0.125 * hp, HitFilter.hits("skill/gravity-interference-lunar-charged-dmg", "skill/gravity-interference-lunar-crystallize-dmg"))
            flatDmg(0.025 * hp, HitFilter.hits("skill/gravity-interference-lunar-bloom-dmg"))
        }
        effect(
            "c6", "Through Darkness Led by Moonlight", "Party CRIT DMG +80% for the elements of the triggered Lunar reaction.",
            target = EffectTarget.TEAM,
            control = choice("Lunar-Charged (Hydro/Electro)", "Lunar-Bloom (Hydro/Dendro)", "Lunar-Crystallize (Hydro/Geo)"),
            requires = cons(6),
        ) {
            val other = when (value) {
                0 -> Element.ELECTRO
                1 -> Element.DENDRO
                else -> Element.GEO
            }
            critDmg(0.80, HitFilter.element(Element.HYDRO, other))
        }
    }

    this("aino") {
        effect("a4", "Structured Power Booster", "Burst DMG +50% of EM.", phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.50 * targetStats.em, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c1", "The Theory of Ash—Field Equilibrium", "Aino and the active character gain 80 EM after her Skill or Burst.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(1)) {
            stat(Stat.EM, 80.0)
        }
        effect(
            "c6", "The Burden of Creative Genius", "Active characters' Electro-Charged, Bloom and Lunar reaction DMG +15% (Ascendant Gleam: +35%).",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6),
        ) {
            val bonus = if (team.moonsign() >= 2) 0.35 else 0.15
            for (r in listOf(Reaction.ELECTRO_CHARGED, Reaction.BLOOM) + LUNAR) reactionBonus(r, bonus)
        }
    }

    this("nefer") {
        val phantasm = HitFilter.hits(
            "skill/phantasm-performance-1-hit-dmg-nefer", "skill/phantasm-performance-2-hit-dmg-nefer",
            "skill/phantasm-performance-1-hit-dmg-shades", "skill/phantasm-performance-2-hit-dmg-shades",
            "skill/phantasm-performance-3-hit-dmg-shades", "skill/c6-phantasm-2-lunar", "skill/c6-phantasm-end",
        )
        benediction("Dusklit Eaves", listOf(Reaction.LUNAR_BLOOM), "Party Lunar-Bloom Base DMG +0.0175% per EM (max 14%).") {
            (0.000175 * it.em).coerceAtMost(0.14)
        }
        effect(
            "a1", "Veil of Falsehood", "Ascendant Gleam: Phantasm Performance deals +8% DMG per Veil (max 3, 5 at C2); " +
                "EM +100 at 3 Veils (+200 at 5, C2); the burst consumes Veils for a DMG bonus.",
            control = stacks(5, 3, "Veils"), requires = A1 and ASCENDANT_GLEAM,
        ) {
            val veils = stacks.coerceAtMost(if (constellation >= 2) 5 else 3)
            if (veils > 0) multiplier(1 + 0.08 * veils, phantasm)
            if (constellation >= 2 && veils >= 5) stat(Stat.EM, 200.0) else if (veils >= 3) stat(Stat.EM, 100.0)
            dmgBonus(param(TalentType.BURST, "param5") * veils, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c1", "Planning Breeds Success", "Shades' Lunar-Bloom DMG +60% of EM (base).", requires = cons(1)) {
            for ((id, p) in listOf(
                "skill/phantasm-performance-1-hit-dmg-shades" to "param9",
                "skill/phantasm-performance-2-hit-dmg-shades" to "param10",
                "skill/phantasm-performance-3-hit-dmg-shades" to "param11",
            )) {
                val base = param(TalentType.SKILL, p)
                if (base > 0) multiplier((base + 0.6) / base, HitFilter.hits(id))
            }
        }
        effect("c4", "Delusion Ensnares Reason", "Dendro RES -20% in the Shadow Dance state.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            resShred(Element.DENDRO, 0.20)
        }
        effect("c6", "Victory Flows from the Turning of Tides", "Ascendant Gleam: Nefer's Lunar-Bloom DMG is elevated by 15%.",
            requires = cons(6) and ASCENDANT_GLEAM) {
            lunarElevate(Reaction.LUNAR_BLOOM, 0.15)
        }
    }

    this("linnea") {
        val crush = HitFilter.hits("skill/lumi-million-ton-crush-dmg")
        benediction("Habitat Survey", listOf(Reaction.LUNAR_CRYSTALLIZE), "Party Lunar-Crystallize Base DMG +0.7% per 100 DEF (max 14%).") {
            (0.007 * it.def / 100).coerceAtMost(0.14)
        }
        effect("a1", "Field Observation Notes", "Opponents near Lumi: Geo RES -15% (-30% in Ascendant Gleam).",
            target = EffectTarget.TEAM, control = toggle(), requires = A1) {
            resShred(Element.GEO, if (team.moonsign() >= 2) 0.30 else 0.15)
        }
        effect(
            "a4", "Universal Naturalist Archive", "Moonsign characters (and Linnea) gain EM equal to 5% of Linnea's DEF.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            if (targetCharacterId in MOONSIGN_CHARACTERS) stat(Stat.EM, 0.05 * ownerStats.def)
        }
        effect(
            "c1", "Field Catalog", "Party Lunar-Crystallize DMG +75% of Linnea's DEF; Million Ton Crush +150% of DEF per stack (5). C6: x1.5.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(1),
        ) {
            val k = if (constellation >= 6) 1.5 else 1.0
            reactionFlat(Reaction.LUNAR_CRYSTALLIZE, 0.75 * k * ownerStats.def)
            if (isSelf) flatDmg(5 * 1.5 * k * ownerStats.def, crush)
        }
        effect("c2", "Tidings of Joy and Sorrow", "Hydro and Geo members CRIT DMG +40% after Moondrift Harmony; Million Ton Crush CRIT DMG +150%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            if (targetElement == Element.HYDRO || targetElement == Element.GEO) critDmg(0.40)
            if (isSelf) critDmg(1.50, crush)
        }
        effect("c4", "Expert Instinct", "Linnea and the active character gain 25% DEF after Moondrift Harmony.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            stat(Stat.DEF_PCT, 0.25)
        }
        effect("c6", "Golden Beagle's Dream", "Ascendant Gleam: party Lunar-Crystallize DMG elevated by 25%.",
            target = EffectTarget.TEAM, requires = cons(6) and ASCENDANT_GLEAM) {
            lunarElevate(Reaction.LUNAR_CRYSTALLIZE, 0.25)
        }
    }

    this("illuga") {
        effect(
            "burst", "Nightingale's Song", "Party Geo DMG and Lunar-Crystallize DMG increased by Illuga's EM (more with Hydro/Geo members at A4).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            val em = ownerStats.em
            val n = (team.count(Element.HYDRO) + team.count(Element.GEO)).coerceAtMost(3)
            val (geoA4, lcrA4) = if (owner.ascension >= 4) when (n) {
                1 -> 0.07 to 0.48
                2 -> 0.14 to 0.96
                3 -> 0.24 to 1.60
                else -> 0.0 to 0.0
            } else 0.0 to 0.0
            flatDmg((param(TalentType.BURST, "param3") + geoA4) * em, HitFilter.element(Element.GEO))
            reactionFlat(Reaction.LUNAR_CRYSTALLIZE, (param(TalentType.BURST, "param4") + lcrA4) * em)
        }
        effect(
            "a1", "Lightkeeper's Oath", "Other members' Geo DMG: CRIT Rate +5%, CRIT DMG +10% (C6: +10% / +30%); " +
                "Ascendant Gleam: EM +50 (C6: +80).",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), requires = A1,
        ) {
            val c6 = constellation >= 6
            critRate(if (c6) 0.10 else 0.05, HitFilter.element(Element.GEO))
            critDmg(if (c6) 0.30 else 0.10, HitFilter.element(Element.GEO))
            if (team.moonsign() >= 2) stat(Stat.EM, if (c6) 80.0 else 50.0)
        }
        effect("c4", "Solarhunting Wolf", "Active characters gain 200 DEF during the burst.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            stat(Stat.DEF, 200.0)
        }
    }

    this("jahoda") {
        val robot = "burst/purrsonal-coordinated-assistance-robot-dmg"
        effect("robot", "Robot element", "Element the Purrsonal Coordinated Assistance Robots convert to.", control = ABSORB_CHOICE) {
            convertElement(absorbedElement(), robot)
        }
        effect("a1", "Plan to Get Paid", "With Pyro as the most common element, the robots deal 130% DMG.", requires = A1) {
            val counts = listOf(Element.PYRO, Element.HYDRO, Element.ELECTRO, Element.CRYO).map { team.count(it) }
            if (counts[0] > 0 && counts[0] >= counts.max()) multiplier(1.30, HitFilter.hits(robot))
        }
        effect("a4", "Sweet Berry Bounty", "Active characters above 70% HP healed by the robots gain 100 EM.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            stat(Stat.EM, 100.0)
        }
        effect("c6", "The Littlest Luck", "Ascendant Gleam: Moonsign characters gain 5% CRIT Rate and 40% CRIT DMG.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6) and ASCENDANT_GLEAM) {
            if (targetCharacterId in MOONSIGN_CHARACTERS) {
                critRate(0.05)
                critDmg(0.40)
            }
        }
    }

    this("zibai") {
        val stride2 = HitFilter.hits("skill/spirit-steed-s-stride-2-hit-dmg")
        benediction("The Coursing Sun and Moon", listOf(Reaction.LUNAR_CRYSTALLIZE), "Party Lunar-Crystallize Base DMG +0.7% per 100 DEF (max 14%).") {
            (0.007 * it.def / 100).coerceAtMost(0.14)
        }
        effect("a1", "Selenic Descent", "Spirit Steed's Stride 2nd hit +60% of DEF (C2 Ascendant Gleam: +550% more).",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = A1) {
            val k = 0.60 + if (constellation >= 2 && team.moonsign() >= 2) 5.5 else 0.0
            flatDmg(k * targetStats.def, stride2)
        }
        effect("a4", "Layered Peaks Pierce the Clouds", "DEF +15% per other Geo member; EM +60 per Hydro member.", static = true, requires = A4) {
            stat(Stat.DEF_PCT, 0.15 * (team.count(Element.GEO) - 1))
            stat(Stat.EM, 60.0 * team.count(Element.HYDRO))
        }
        effect("c1", "Burst Forth With Vigor", "The first Spirit Steed's Stride 2nd hit after entering Lunar Phase Shift deals +220% DMG.",
            control = toggle(false), requires = cons(1)) {
            dmgBonus(2.20, stride2)
        }
        effect("c2", "At Birth Are Souls Born", "Party Lunar-Crystallize DMG +30% in Lunar Phase Shift mode.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            reactionBonus(Reaction.LUNAR_CRYSTALLIZE, 0.30)
        }
        effect("c4", "Scattermoon Splendor", "The 4th hit's additional attack deals 250% DMG.", control = toggle(), requires = cons(4)) {
            multiplier(2.5, HitFilter.hits("skill/lunar-phase-shift-4-hit-additional-dmg"))
        }
        effect("c6", "The World, A Journey in Passing", "Zibai's Lunar-Crystallize DMG elevated by 1.6% per Radiance point above 70.",
            control = stacks(30, 30, "Radiance above 70", step = 5), requires = cons(6)) {
            lunarElevate(Reaction.LUNAR_CRYSTALLIZE, 0.016 * stacks)
        }
    }
}
