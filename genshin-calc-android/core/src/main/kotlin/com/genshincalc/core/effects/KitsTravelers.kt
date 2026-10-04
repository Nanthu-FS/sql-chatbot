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

/** Aloy and the seven Traveler elements. */
internal fun EffectTable.travelerKits() {
    this("aloy") {
        effect(
            "coil", "Coil / Rushing Ice", "Normal Attack DMG bonus from Coil stacks; at 4 stacks Rushing Ice also infuses Normal Attacks with Cryo.",
            control = choice("No Coil", "1 Coil", "2 Coils", "3 Coils", "Rushing Ice", default = 4),
        ) {
            if (value in 1..3) dmgBonus(param(TalentType.SKILL, "param${4 + value}"), HitFilter.NORMAL)
            if (value == 4) {
                dmgBonus(param(TalentType.SKILL, "param8"), HitFilter.NORMAL)
                infuse(Element.CRYO, InfusionPriority.NON_OVERRIDABLE, setOf(AttackCategory.NORMAL))
            }
        }
        effect("a1", "Combat Override", "After gaining Coil: Aloy ATK +16%, other party members +8%.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1) {
            stat(Stat.ATK_PCT, if (isSelf) 0.16 else 0.08)
        }
        effect("a4", "Strong Strike", "Cryo DMG +3.5% per second in Rushing Ice (max 35%).", control = stacks(10), requires = A4) {
            elementDmg(Element.CRYO, 0.035 * stacks)
        }
    }

    this("traveleranemo") {
        effect("absorb", "Gust Surge absorption", "Element absorbed by the tornado.", control = ABSORB_CHOICE) {
            convertElement(absorbedElement(), "burst/additional-elemental-dmg")
        }
        effect("c2", "Uprising Whirlwind", "Energy Recharge +16%.", static = true, requires = cons(2)) {
            stat(Stat.ER, 0.16)
        }
        effect("c6", "Intertwined Winds", "Gust Surge lowers Anemo RES by 20% and the absorbed element's RES by 20%.",
            target = EffectTarget.TEAM, control = ABSORB_CHOICE, requires = cons(6)) {
            resShred(Element.ANEMO, 0.20)
            resShred(absorbedElement(), 0.20)
        }
    }

    this("travelergeo") {
        effect("c1", "Invincible Stonewall", "Party CRIT Rate +10% within Wake of Earth.", target = EffectTarget.TEAM, control = toggle(), requires = cons(1)) {
            critRate(0.10)
        }
    }

    this("travelerelectro") {
        effect("c2", "Violet Vehemence", "Falling Thunder lowers Electro RES by 15%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            resShred(Element.ELECTRO, 0.15)
        }
    }

    this("travelerdendro") {
        effect("a1", "Verdant Overgrowth", "Active characters in the Lea Lotus Lamp gain 6 EM per second (max 10 stacks).",
            target = EffectTarget.TEAM, control = stacks(10), requires = A1) {
            stat(Stat.EM, 6.0 * stacks)
        }
        effect("a4", "Verdant Luxury", "Razorgrass Blade DMG +0.15% per EM; Surgent Manifestation +0.1% per EM.",
            phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus(0.0015 * targetStats.em, HitFilter(talents = setOf(TalentType.SKILL)))
            dmgBonus(0.001 * targetStats.em, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c6", "Withering Aggregation", "Characters in the Lamp gain 12% Dendro DMG Bonus.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            elementDmg(Element.DENDRO, 0.12)
        }
    }

    // Traveler (Hydro)'s passives and constellations do not change damage numbers.
    this("travelerhydro") {}

    this("travelerpyro") {
        effect("c1", "Starfire's Flowing Light", "The active character in the Threshold deals 6% more DMG (+9% in Nightsoul's Blessing).",
            target = EffectTarget.TEAM, control = choice("Not in Nightsoul's Blessing", "In Nightsoul's Blessing"), requires = cons(1)) {
            stat(Stat.ALL_DMG, if (value == 1) 0.15 else 0.06)
        }
        effect("c4", "Ravaging Flame", "Pyro DMG +20% for 9s after the burst.", control = toggle(), requires = cons(4)) {
            elementDmg(Element.PYRO, 0.20)
        }
        effect("c6", "The Sacred Flame Imperishable", "In Nightsoul's Blessing: Pyro infusion and +40% CRIT DMG for Normal/Charged/Plunging Attacks.",
            control = toggle(), requires = cons(6)) {
            infuse(Element.PYRO, InfusionPriority.NON_OVERRIDABLE)
            critDmg(0.40, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }

    this("travelercryo") {
        effect(
            "a1", "Ever-Keen Frost", "Radiance: Stellar-Conduct: Cryo infusion; Normal/Charged/Plunging DMG +80% of ATK.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = A1,
        ) {
            infuse(Element.CRYO, InfusionPriority.NON_OVERRIDABLE)
            flatDmg(0.80 * targetStats.atk, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
        effect("a4", "Lucent Ice", "EM +8% of ATK (max 160).", static = true, phase = EffectPhase.CONVERSION, requires = A4) {
            stat(Stat.EM, (0.08 * targetStats.atk).coerceAtMost(160.0))
        }
        effect("c2", "Frostfall Reverberation", "The active character gains 60 EM (120 after Stellar Glimmer reactions).",
            target = EffectTarget.TEAM, control = choice("60 EM", "120 EM", default = 1), requires = cons(2)) {
            stat(Stat.EM, 60.0 * (value + 1))
        }
        effect("c6", "Brumal Grimfrost", "Other members' Stellar Glimmer DMG +5% per Frostglow consumed (max 40%).",
            target = EffectTarget.TEAM_OTHERS, control = stacks(8), requires = cons(6)) {
            for (r in listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)) reactionBonus(r, 0.05 * stacks)
        }
    }
}
