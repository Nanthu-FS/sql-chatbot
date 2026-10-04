package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.TeamInfo
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

private val BURST_TALENT = HitFilter(talents = setOf(TalentType.BURST))

/** Low/High Plunge ground impact (not the collision DMG during the fall). */
private val PLUNGE_IMPACT = HitFilter.hits(
    "normal/low-plunge-dmg", "normal/high-plunge-dmg", "normal/fiery-passion-low-plunge-dmg", "normal/fiery-passion-high-plunge-dmg",
    "burst/volcano-kablam-dmg",
)

/** The Pyro/Hydro/Cryo/Electro elements present among the other party members. */
private fun otherSwirlable(team: TeamInfo, self: Element): List<Element> =
    SWIRLABLE.filter { e -> team.elements.count { it == e } - (if (e == self) 1 else 0) > 0 }

internal fun EffectTable.natlanKits() {
    this("mavuika") {
        val flamestriderNormal = HitFilter.hits(
            "skill/flamestrider-normal-attack-1-hit-dmg", "skill/flamestrider-normal-attack-2-hit-dmg",
            "skill/flamestrider-normal-attack-3-hit-dmg", "skill/flamestrider-normal-attack-4-hit-dmg",
            "skill/flamestrider-normal-attack-5-hit-dmg",
        )
        val flamestriderCharged = HitFilter.hits("skill/flamestrider-charged-attack-cyclic-dmg", "skill/flamestrider-charged-attack-final-dmg")
        val sunfell = HitFilter.hits("burst/skill-dmg")
        effect(
            "spirit", "Crucible of Death and Life", "Sunfell Slice and Flamestrider Normal/Charged Attack DMG increased by ATK per Fighting Spirit.",
            control = stacks(200, 200, "Fighting Spirit", step = 10), phase = EffectPhase.CONVERSION,
        ) {
            val atk = targetStats.atk
            flatDmg(param(TalentType.BURST, "param3") * stacks * atk, sunfell)
            flatDmg(param(TalentType.BURST, "param4") * stacks * atk, flamestriderNormal)
            flatDmg(param(TalentType.BURST, "param5") * stacks * atk, flamestriderCharged)
        }
        effect("a1", "Gift of Flaming Flowers", "ATK +30% for 10s after a party member triggers a Nightsoul Burst.", control = toggle(), requires = A1) {
            stat(Stat.ATK_PCT, 0.30)
        }
        effect(
            "a4", "Kiongozi", "After the burst, the active character deals up to 40% more DMG (0.2% per Fighting Spirit, decays); C4: no decay, +10%.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4,
        ) {
            stat(Stat.ALL_DMG, 0.40 + if (constellation >= 4) 0.10 else 0.0)
        }
        effect("c1", "The Night-Lord's Explication", "ATK +40% for 8s after gaining Fighting Spirit.", control = toggle(), requires = cons(1)) {
            stat(Stat.ATK_PCT, 0.40)
        }
        effect("c2", "The Ashen Price", "Base ATK +200 in Nightsoul's Blessing.", control = toggle(), requires = cons(2)) {
            stat(Stat.BASE_ATK, 200.0)
        }
        effect(
            "c2dmg", "The Ashen Price (Flamestrider)", "Flamestrider Normal/Charged Attacks and Sunfell Slice deal +60/90/120% of ATK.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(2),
        ) {
            val atk = targetStats.atk
            flatDmg(0.60 * atk, flamestriderNormal)
            flatDmg(0.90 * atk, flamestriderCharged)
            flatDmg(1.20 * atk, sunfell)
        }
        effect("c2def", "The Ashen Price (Ring)", "Ring of Searing Radiance (C2) or the Scorching Ring (C6) lowers DEF by 20%.",
            target = EffectTarget.TEAM, control = toggle(false), requires = cons(2)) {
            defReduction(0.20)
        }
    }

    this("citlali") {
        effect(
            "a1", "Mamaloaco's Frigid Rain", "Frozen or Melt lowers Pyro and Hydro RES by 20% (40% at C2).",
            target = EffectTarget.TEAM, control = toggle(), requires = A1,
        ) {
            val shred = if (constellation >= 2) 0.40 else 0.20
            resShred(Element.PYRO, shred)
            resShred(Element.HYDRO, shred)
        }
        effect("a4", "Itzpapalotl's Star Garments", "Frostfall Storm DMG +90% of EM; Ice Storm DMG +1200% of EM.",
            phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.9 * targetStats.em, HitFilter.hits("skill/frostfall-storm-dmg"))
            flatDmg(12.0 * targetStats.em, HitFilter.hits("burst/ice-storm-dmg"))
        }
        effect(
            "c1", "Opalstar Vestments", "Other active characters' attacks deal additional DMG equal to 200% of Citlali's EM (Stellar Blade).",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(1),
        ) {
            flatDmg(2.0 * ownerStats.em, HitFilter.of(
                AttackCategory.NORMAL, AttackCategory.CHARGED, AttackCategory.PLUNGE, AttackCategory.SKILL, AttackCategory.BURST,
            ))
        }
        effect("c2", "Heart Devourer's Travail", "Citlali gains 125 EM.", static = true, requires = cons(2)) {
            stat(Stat.EM, 125.0)
        }
        effect("c2b", "Heart Devourer's Travail (Party)", "Characters with the Opal Shield or Itzpapa gain 250 EM.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), requires = cons(2)) {
            stat(Stat.EM, 250.0)
        }
        effect("c6", "Cifra of the Secret Law", "Per point (max 40): party Pyro and Hydro DMG +1.5%, Citlali's DMG +2.5%.",
            target = EffectTarget.TEAM, control = stacks(40, 40, "Cifra points", step = 5), requires = cons(6)) {
            elementDmg(Element.PYRO, 0.015 * stacks)
            elementDmg(Element.HYDRO, 0.015 * stacks)
            if (isSelf) stat(Stat.ALL_DMG, 0.025 * stacks)
        }
    }

    this("xilonen") {
        effect(
            "samples", "Source Samples", "Opponents' RES is lowered for the elements of active Source Samples (Geo + the party's Pyro/Hydro/Cryo/Electro).",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            val converted = otherSwirlable(team, Element.GEO).take(3)
            val elements = converted + if (converted.size < 3 || constellation >= 2) listOf(Element.GEO) else emptyList()
            for (e in elements) resShred(e, param(TalentType.SKILL, "param2"))
        }
        effect("a1", "Netotiliztli's Echoes", "With fewer than 2 converted Source Samples: Normal and Plunging Attack DMG +30%.",
            control = toggle(), requires = A1) {
            if (otherSwirlable(team, Element.GEO).size < 2) dmgBonus(0.30, HitFilter.of(AttackCategory.NORMAL, AttackCategory.PLUNGE))
        }
        effect("a4", "Portable Armored Sheath", "DEF +20% for 15s after a party member triggers a Nightsoul Burst.", control = toggle(), requires = A4) {
            stat(Stat.DEF_PCT, 0.20)
        }
        effect(
            "c2", "Chiucue Mix", "Members matching an active Source Sample: Geo DMG +50%, Pyro ATK +45%, Hydro Max HP +45%, Cryo CRIT DMG +60%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2),
        ) {
            val active = otherSwirlable(team, Element.GEO).take(3) + Element.GEO
            if (targetElement in active) when (targetElement) {
                Element.GEO -> stat(Stat.ALL_DMG, 0.50)
                Element.PYRO -> stat(Stat.ATK_PCT, 0.45)
                Element.HYDRO -> stat(Stat.HP_PCT, 0.45)
                Element.CRYO -> stat(Stat.CRIT_DMG, 0.60)
                else -> {}
            }
        }
        effect(
            "c4", "Suchitl's Trance", "Party Normal, Charged and Plunging Attack DMG +65% of Xilonen's DEF.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(4),
        ) {
            flatDmg(0.65 * ownerStats.def, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
        effect("c6", "Imperishable Night Carnival", "Normal and Plunging Attack DMG +300% of DEF for 5s.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(6)) {
            flatDmg(3.0 * targetStats.def, HitFilter.of(AttackCategory.NORMAL, AttackCategory.PLUNGE))
        }
    }

    this("chasca") {
        val shining = HitFilter.hits("skill/shining-shadowhunt-shell-dmg")
        effect(
            "element", "Spiritbinding Conversion", "Element of the Shining Shadowhunt Shells and Radiant Soulseeker Shells.",
            control = ABSORB_CHOICE,
        ) {
            convertElement(
                absorbedElement(), "skill/shining-shadowhunt-shell-dmg", "burst/radiant-soulseeker-shell-dmg",
                "skill/a4-burning-shadowhunt-shot", "skill/c2-shining-shell-aoe", "burst/c4-radiant-shell-aoe",
            )
        }
        effect("a1", "Bullet Trick", "Shining Shadowhunt Shell DMG +15/35/65% for 1/2/3 Pyro/Hydro/Cryo/Electro types in the party (C2: +1).",
            requires = A1) {
            val n = (otherSwirlable(team, Element.ANEMO).size + if (constellation >= 2) 1 else 0).coerceAtMost(3)
            val bonus = when (n) {
                1 -> 0.15
                2 -> 0.35
                3 -> 0.65
                else -> 0.0
            }
            dmgBonus(bonus, shining)
        }
        effect("a4", "Intent to Cover", "Burning Shadowhunt Shot deals 150% of a Shining Shadowhunt Shell's DMG.", requires = A4) {
            multiplier(1.5, HitFilter.hits("skill/a4-burning-shadowhunt-shot"))
        }
        effect("c6", "Fatal Rounds", "Multitarget Fire's Shadowhunt and Shining Shadowhunt Shells gain 120% CRIT DMG.", control = toggle(), requires = cons(6)) {
            critDmg(1.20, HitFilter.hits("skill/shadowhunt-shell-dmg", "skill/shining-shadowhunt-shell-dmg"))
        }
    }

    this("kinich") {
        val cannon = HitFilter.hits("skill/scalespiker-cannon-dmg", "skill/c6-cannon-bounce")
        effect("a4", "Flame Spirit Pact", "Scalespiker Cannon DMG +320% of ATK per Hunter's Experience stack (max 2).",
            control = stacks(2), phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(3.2 * stacks * targetStats.atk, cannon)
        }
        effect("c1", "Parrot's Beak", "Scalespiker Cannon CRIT DMG +100%.", requires = cons(1)) {
            critDmg(1.00, cannon)
        }
        effect("c2", "Tiger Beetle's Palm", "Skill hits lower Dendro RES by 30%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            resShred(Element.DENDRO, 0.30)
        }
        effect("c2b", "Tiger Beetle's Palm (first Cannon)", "The first Scalespiker Cannon after entering Nightsoul's Blessing deals 100% more DMG.",
            control = toggle(false), requires = cons(2)) {
            dmgBonus(1.00, cannon)
        }
        effect("c4", "Hummingbird's Feather", "Burst DMG +70%.", requires = cons(4)) {
            dmgBonus(0.70, BURST_TALENT)
        }
    }

    this("mualani") {
        val bite = HitFilter.hits("skill/sharky-s-bite-base-dmg")
        effect(
            "momentum", "Wave Momentum", "Sharky's Bite DMG +% of Max HP per stack (max 3); at 3 stacks it becomes Sharky's Surging Bite.",
            control = stacks(3, 3, "Wave Momentum"), phase = EffectPhase.CONVERSION,
        ) {
            val hp = targetStats.hp
            flatDmg(param(TalentType.SKILL, "param2") * stacks * hp, bite)
            if (stacks >= 3) flatDmg(param(TalentType.SKILL, "param3") * hp, bite)
        }
        effect("a4", "Natlan's Greatest Guide", "Burst DMG +15/30/45% of Max HP with 1/2/3 Wavechaser's Exploits stacks.",
            control = stacks(3), phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.15 * stacks * targetStats.hp, BURST_TALENT)
        }
        effect("c1", "The Leisurely \"Meztli\"...", "The first Sharky's Surging Bite deals additional DMG equal to 66% of Max HP.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(1)) {
            flatDmg(0.66 * targetStats.hp, bite)
        }
        effect("c4", "Sharky Eats Puffies", "Burst DMG +75%.", requires = cons(4)) {
            dmgBonus(0.75, BURST_TALENT)
        }
    }

    this("varesa") {
        effect(
            "a1", "Rainbow Crash", "Plunge ground impact DMG +50% of ATK (+180% in Fiery Passion or at C1).",
            control = choice("Normal state", "Fiery Passion", default = 1), phase = EffectPhase.CONVERSION, requires = A1,
        ) {
            val k = if (value == 1 || constellation >= 1) 1.8 else 0.5
            flatDmg(k * targetStats.atk, PLUNGE_IMPACT)
        }
        effect("a4", "The Hero Twice-Returned!", "ATK +35% per party Nightsoul Burst (max 2 stacks).", control = stacks(2, 1), requires = A4) {
            stat(Stat.ATK_PCT, 0.35 * stacks)
        }
        effect(
            "c4", "The Courage to Press On", "In Fiery Passion/Apex Drive: burst DMG +100%. Otherwise: ground impact +500% of ATK (max 20,000).",
            control = choice("Fiery Passion / Apex Drive", "Neither"), phase = EffectPhase.CONVERSION, requires = cons(4),
        ) {
            if (value == 0) dmgBonus(1.00, HitFilter.hits("burst/flying-kick-dmg", "burst/fiery-passion-flying-kick-dmg", "burst/volcano-kablam-dmg"))
            else flatDmg((5.0 * targetStats.atk).coerceAtMost(20000.0), PLUNGE_IMPACT)
        }
        effect("c6", "A Hero of Justice's Triumph", "Plunging Attacks and burst: CRIT Rate +10%, CRIT DMG +100%.", requires = cons(6)) {
            val filter = HitFilter.of(AttackCategory.PLUNGE, AttackCategory.BURST)
            critRate(0.10, filter)
            critDmg(1.00, filter)
        }
    }

    this("iansan") {
        effect(
            "burst", "Kinetic Energy Scale", "The active character gains ATK equal to 27% of Iansan's ATK (high Nightsoul points), up to a cap.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            stat(Stat.ATK, (param(TalentType.BURST, "param2") * ownerStats.atk).coerceAtMost(param(TalentType.BURST, "param4")))
        }
        effect("a1", "Precise Movement", "ATK +20% for 15s after Swift Stormflight hits.", control = toggle(), requires = A1) {
            stat(Stat.ATK_PCT, 0.20)
        }
        effect("c2", "Laziness is the Enemy!", "While off-field with Precise Movement, the active character gains 30% ATK.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), requires = cons(2) and A1) {
            stat(Stat.ATK_PCT, 0.30)
        }
        effect("c6", "Extreme Force", "Overflowing Nightsoul restoration: the active character deals 25% more DMG for 3s.",
            target = EffectTarget.TEAM, control = toggle(false), requires = cons(6)) {
            stat(Stat.ALL_DMG, 0.25)
        }
    }

    this("ifa") {
        effect(
            "a1", "Rescue Essentials", "Party Swirl and Electro-Charged DMG +1.5%, Lunar-Charged +0.2% per point (max 150; 200 at C2).",
            target = EffectTarget.TEAM, control = stacks(200, 150, "Rescue Essentials", step = 10), requires = A1,
        ) {
            val points = stacks.coerceAtMost(if (constellation >= 2) 200 else 150)
            reactionBonus(Reaction.SWIRL, 0.015 * points)
            reactionBonus(Reaction.ELECTRO_CHARGED, 0.015 * points)
            reactionBonus(Reaction.LUNAR_CHARGED, 0.002 * points)
        }
        effect("a4", "Mutual Aid Agreement", "EM +80 for 10s after a party Nightsoul Burst.", control = toggle(), requires = A4) {
            stat(Stat.EM, 80.0)
        }
        effect("c4", "Decayed Vessel's Permutation", "EM +100 for 15s after the burst.", control = toggle(), requires = cons(4)) {
            stat(Stat.EM, 100.0)
        }
    }

    this("kachina") {
        effect("a1", "Mountain Echoes", "Geo DMG +20% for 12s after a party Nightsoul Burst.", control = toggle(), requires = A1) {
            elementDmg(Element.GEO, 0.20)
        }
        effect("a4", "The Weight of Stone", "Turbo Twirly DMG +20% of DEF.", phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.20 * targetStats.def, HitFilter.hits("skill/turbo-twirly-mounted-dmg", "skill/turbo-twirly-independent-dmg"))
        }
        effect("c4", "More Foes, More Caution", "The active character in the Turbo Drill Field gains 8/12/16/20% DEF (1/2/3/4+ opponents).",
            target = EffectTarget.TEAM, control = choice("1 opponent", "2 opponents", "3 opponents", "4+ opponents"), requires = cons(4)) {
            stat(Stat.DEF_PCT, 0.08 + 0.04 * value)
        }
    }

    this("ororon") {
        val hypersense = HitFilter.hits("skill/a1-hypersense", "burst/c6-hypersense")
        effect("c1", "Nighttide", "Hypersense DMG +50% against opponents marked by the Spirit Orb.", control = toggle(), requires = cons(1) and A1) {
            dmgBonus(0.50, hypersense)
        }
        effect("c2", "Spiritual Supersense", "Electro DMG +8% after the burst, +8% per additional opponent hit (max 32%).",
            control = stacks(4, 1, "Bonus x8%"), requires = cons(2)) {
            elementDmg(Element.ELECTRO, 0.08 * stacks)
        }
        effect("c6", "Ode to Deep Springs", "The active character's ATK +10% per Hypersense (max 3 stacks).",
            target = EffectTarget.TEAM, control = stacks(3), requires = cons(6) and A1) {
            stat(Stat.ATK_PCT, 0.10 * stacks)
        }
    }
}
