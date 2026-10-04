package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectScope
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

/** Element absorbed by an Anemo skill/burst: "Party element" picks the first swirlable party element. */
internal val ABSORB_CHOICE = choice("Party element", "Pyro", "Hydro", "Electro", "Cryo")

internal fun EffectScope.absorbedElement(): Element =
    if (value == 0) SWIRLABLE.firstOrNull { team.count(it) > 0 } ?: Element.PYRO else SWIRLABLE[value - 1]

internal fun EffectTable.mondstadtKits() {
    this("bennett") {
        effect(
            "burst", "Fantastic Voyage", "Characters in the field gain ATK equal to Bennett's Base ATK x the ATK Bonus Ratio (+20% Base ATK at C1).",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            val ratio = param(TalentType.BURST, "param4") + if (constellation >= 1) 0.2 else 0.0
            stat(Stat.ATK, ownerBaseAtk * ratio)
        }
        effect("c2", "Impasse Conqueror", "Energy Recharge +30% while HP is below 70%.", control = toggle(false), requires = cons(2)) {
            stat(Stat.ER, 0.30)
        }
        effect(
            "c6", "Fire Ventures With Me", "Sword, Claymore and Polearm users in the field gain 15% Pyro DMG Bonus and a Pyro infusion.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6),
        ) {
            if (target.weaponType.isMelee) {
                elementDmg(Element.PYRO, 0.15)
                infuse(Element.PYRO, InfusionPriority.TEAM)
            }
        }
    }

    this("venti") {
        effect("absorb", "Wind's Grand Ode absorption", "Element absorbed by the Stormeye (sets the Additional Elemental DMG).",
            control = ABSORB_CHOICE) {
            convertElement(absorbedElement(), "burst/additional-elemental-dmg")
        }
        effect(
            "c2", "Breeze of Reminiscence", "Skyward Sonnet lowers Anemo and Physical RES by 12% (24% while airborne).",
            target = EffectTarget.TEAM, control = choice("12%", "24% (airborne)", "Off"), requires = cons(2),
        ) {
            if (value < 2) {
                val shred = if (value == 1) 0.24 else 0.12
                resShred(Element.ANEMO, shred)
                resShred(Element.PHYSICAL, shred)
            }
        }
        effect("c4", "Hurricane of Freedom", "Anemo DMG Bonus +25% after picking up particles.", control = toggle(), requires = cons(4)) {
            elementDmg(Element.ANEMO, 0.25)
        }
        effect("c6", "Storm of Defiance", "Wind's Grand Ode lowers Anemo RES and the absorbed element's RES by 20%.",
            target = EffectTarget.TEAM, control = ABSORB_CHOICE, requires = cons(6)) {
            resShred(Element.ANEMO, 0.20)
            resShred(absorbedElement(), 0.20)
        }
    }

    this("sucrose") {
        effect("absorb", "Forbidden Creation absorption", "Element absorbed by the burst (sets the Additional Elemental DMG).",
            control = ABSORB_CHOICE) {
            convertElement(absorbedElement(), "burst/additional-elemental-dmg")
        }
        effect("a1", "Catalyst Conversion", "After a Swirl, party members of the swirled element gain 50 EM.",
            target = EffectTarget.TEAM_OTHERS, control = SWIRL_CHOICE, requires = A1) {
            if (targetElement in swirlElements()) stat(Stat.EM, 50.0)
        }
        effect("a4", "Mollis Favonius", "Skill/burst hits give the party (excl. Sucrose) EM equal to 20% of Sucrose's EM.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4) {
            stat(Stat.EM, 0.20 * ownerStats.em)
        }
        effect("c6", "Chaotic Entropy", "Party gains 20% DMG Bonus of the absorbed element.",
            target = EffectTarget.TEAM, control = ABSORB_CHOICE, requires = cons(6)) {
            elementDmg(absorbedElement(), 0.20)
        }
    }

    this("mona") {
        effect("omen", "Omen", "Enemies affected by Omen take more DMG from the party.", target = EffectTarget.TEAM, control = toggle()) {
            stat(Stat.ALL_DMG, param(TalentType.BURST, "param10"))
        }
        effect("a4", "Waterborne Destiny", "Hydro DMG Bonus equal to 20% of Energy Recharge.",
            static = true, phase = EffectPhase.CONVERSION, requires = A4) {
            elementDmg(Element.HYDRO, 0.20 * targetStats.er)
        }
        effect(
            "c1", "Prophecy of Submersion", "Hitting Omen'd enemies: Electro-Charged, Lunar-Charged, Vaporize and Lunar-Crystallize DMG +15%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(1),
        ) {
            for (r in listOf(Reaction.ELECTRO_CHARGED, Reaction.LUNAR_CHARGED, Reaction.VAPORIZE, Reaction.LUNAR_CRYSTALLIZE)) reactionBonus(r, 0.15)
        }
        effect("c4", "Prophecy of Oblivion", "Party CRIT Rate +15% vs Omen'd enemies.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            stat(Stat.CRIT_RATE, 0.15)
        }
        effect("c6", "Rhetorics of Calamitas", "Next Charged Attack DMG +60% per second of Illusory Torrent (max 180%).",
            control = stacks(3, 3, "Seconds"), requires = cons(6)) {
            dmgBonus(0.60 * stacks, HitFilter.CHARGED)
        }
    }

    this("jean") {
        effect("c1", "Spiraling Tempest", "Gale Blade held for over 1s deals 40% more DMG.", control = toggle(false), requires = cons(1)) {
            dmgBonus(0.40, HitFilter(talents = setOf(TalentType.SKILL)))
        }
        effect("c4", "Lands of Dandelion", "Enemies in the Dandelion Field: Anemo RES -40%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            resShred(Element.ANEMO, 0.40)
        }
    }

    this("diluc") {
        effect("burst", "Dawn", "Pyro infusion after Dawn (Blessing of Phoenix at A4: +20% Pyro DMG Bonus).", control = toggle()) {
            infuse(Element.PYRO)
            if (owner.ascension >= 4) elementDmg(Element.PYRO, 0.20)
        }
        effect("c1", "Conviction", "DMG +15% vs enemies above 50% HP.", control = toggle(), requires = cons(1)) {
            stat(Stat.ALL_DMG, 0.15)
        }
        effect("c2", "Searing Ember", "ATK +10% per stack after taking DMG (max 3).", control = stacks(3, 0), requires = cons(2)) {
            stat(Stat.ATK_PCT, 0.10 * stacks)
        }
        effect("c4", "Flowing Flame", "Searing Onslaught in rhythm: +40% DMG.", control = toggle(false), requires = cons(4)) {
            dmgBonus(0.40, HitFilter(talents = setOf(TalentType.SKILL)))
        }
        effect("c6", "Flaming Sword, Nemesis of the Dark", "Next 2 Normal Attacks after Searing Onslaught: DMG +30%.",
            control = toggle(), requires = cons(6)) {
            dmgBonus(0.30, HitFilter.NORMAL)
        }
    }

    this("klee") {
        effect("a1", "Pounding Surprise", "Charged Attacks with an Explosive Spark deal 50% more DMG.", control = toggle(), requires = A1) {
            dmgBonus(0.50, HitFilter.CHARGED)
        }
        effect("c2", "Explosive Frags", "Jumpy Dumpty mines lower DEF by 23%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            defReduction(0.23)
        }
        effect("c6", "Blazing Delight", "Party Pyro DMG Bonus +10% after Sparks 'n' Splash.", target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            elementDmg(Element.PYRO, 0.10)
        }
    }

    this("eula") {
        effect("grimheart", "Icetide Vortex (Grimheart consumed)", "Holding with Grimheart lowers Physical and Cryo RES.",
            target = EffectTarget.TEAM, control = toggle()) {
            resShred(Element.PHYSICAL, param(TalentType.SKILL, "param4"))
            resShred(Element.CRYO, param(TalentType.SKILL, "param5"))
        }
        effect("lightfall", "Lightfall Sword stacks", "Each energy stack adds the 'DMG Per Stack' multiplier to the Lightfall Sword.",
            control = stacks(30, 13, "Stacks"), phase = EffectPhase.CONVERSION) {
            flatDmg(param(TalentType.BURST, "param3") * stacks * targetStats.atk, HitFilter.hits("burst/lightfall-sword-base-dmg"))
        }
        effect("c1", "Tidal Illusion", "Physical DMG +30% after consuming Grimheart.", control = toggle(), requires = cons(1)) {
            elementDmg(Element.PHYSICAL, 0.30)
        }
        effect("c4", "The Obstinacy of One's Inferiors", "Lightfall Sword DMG +25% vs enemies below 50% HP.",
            control = toggle(false), requires = cons(4)) {
            dmgBonus(0.25, HitFilter.hits("burst/lightfall-sword-base-dmg", "burst/dmg-per-stack"))
        }
    }

    this("albedo") {
        effect("a1", "Calcite Might", "Transient Blossoms deal 25% more DMG to enemies below 50% HP.", control = toggle(false), requires = A1) {
            dmgBonus(0.25, HitFilter.hits("skill/transient-blossom-dmg"))
        }
        effect("a4", "Homuncular Nature", "Party Elemental Mastery +125 after Tectonic Tide.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            stat(Stat.EM, 125.0)
        }
        effect("c2", "Opening of Phanerozoic", "Each Fatal Reckoning stack adds 30% of DEF to Fatal Blossom and burst DMG (max 4).",
            control = stacks(4, 4), phase = EffectPhase.CONVERSION, requires = cons(2)) {
            flatDmg(0.30 * stacks * targetStats.def, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c4", "Descent of Divinity", "Active characters in Solar Isotoma: Plunging Attack DMG +30%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            dmgBonus(0.30, HitFilter.PLUNGE)
        }
        effect("c6", "Dust of Purification", "Shielded active characters in Solar Isotoma deal 17% more DMG.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            stat(Stat.ALL_DMG, 0.17)
        }
    }

    this("rosaria") {
        effect("a1", "Regina Probationum", "CRIT Rate +12% after Ravaging Confession from behind.", control = toggle(), requires = A1) {
            stat(Stat.CRIT_RATE, 0.12)
        }
        effect("a4", "Shadow Samaritan", "Other party members gain CRIT Rate equal to 15% of Rosaria's (max 15%).",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4) {
            stat(Stat.CRIT_RATE, minOf(0.15 * ownerStats.critRate, 0.15))
        }
        effect("c1", "Unholy Revelation", "Normal Attack DMG +10% after a CRIT Hit.", control = toggle(), requires = cons(1)) {
            dmgBonus(0.10, HitFilter.NORMAL)
        }
        effect("c6", "Divine Retribution", "Rites of Termination lowers Physical RES by 20%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            resShred(Element.PHYSICAL, 0.20)
        }
    }

    this("fischl") {
        effect("c2", "Devourer of All Sins", "Nightrider deals an additional 200% ATK as DMG.", phase = EffectPhase.CONVERSION, requires = cons(2)) {
            flatDmg(2.0 * targetStats.atk, HitFilter.hits("skill/summoning-dmg"))
        }
    }

    this("lisa") {
        effect("a4", "Static Electricity Field", "Lightning Rose lowers DEF by 15%.", target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            defReduction(0.15)
        }
    }

    this("razor") {
        effect("c1", "Wolf's Instinct", "DMG +10% after picking up particles.", control = toggle(), requires = cons(1)) {
            stat(Stat.ALL_DMG, 0.10)
        }
        effect("c2", "Suppression", "CRIT Rate +10% vs enemies below 30% HP.", control = toggle(false), requires = cons(2)) {
            stat(Stat.CRIT_RATE, 0.10)
        }
        effect("c4", "Bite", "Tapping Claw and Thunder lowers DEF by 15%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            defReduction(0.15)
        }
    }

    this("amber") {
        effect("a1", "Every Arrow Finds Its Target", "Fiery Rain CRIT Rate +10%.", requires = A1) {
            critRate(0.10, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c2", "Bunny Triggered", "Manually detonating Baron Bunny adds 200% DMG.", control = toggle(false), requires = cons(2)) {
            multiplier(3.0, HitFilter.hits("skill/explosion-dmg"))
        }
        effect("c6", "Wildfire", "Party ATK +15% during Fiery Rain.", target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            stat(Stat.ATK_PCT, 0.15)
        }
    }

    this("kaeya") {
        effect("c1", "Excellent Blood", "Normal and Charged Attack CRIT Rate +15% vs Cryo-affected enemies.", control = toggle(), requires = cons(1)) {
            critRate(0.15, HitFilter.NORMAL_CHARGED)
        }
    }

    this("noelle") {
        effect(
            "burst", "Sweeping Time", "ATK increased by a % of DEF (+50% DEF at C6); attacks become Geo and can't be overridden.",
            control = toggle(), phase = EffectPhase.CONVERSION,
        ) {
            val ratio = param(TalentType.BURST, "param3") + if (constellation >= 6) 0.5 else 0.0
            stat(Stat.ATK, ratio * targetStats.def)
            infuse(Element.GEO, InfusionPriority.NON_OVERRIDABLE)
        }
        effect("c2", "Combat Maid", "Charged Attack DMG +15%.", requires = cons(2)) {
            dmgBonus(0.15, HitFilter.CHARGED)
        }
    }

    this("diona") {
        effect("c2", "Shaken, Not Purred", "Icy Paw DMG +15%.", requires = cons(2)) {
            dmgBonus(0.15, HitFilter.hits("skill/icy-paw-dmg"))
        }
        effect("c6", "Cat's Tail Closing Time", "Characters in Signature Mix above 50% HP gain 200 EM.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            stat(Stat.EM, 200.0)
        }
    }

    this("mika") {
        effect("a1", "Suppressive Barrage", "Detector: Physical DMG +10% per stack (max 3, 4 at C6).",
            target = EffectTarget.TEAM, control = stacks(4, 3), requires = A1) {
            val max = if (constellation >= 6) 4 else 3
            elementDmg(Element.PHYSICAL, 0.10 * minOf(stacks, max))
        }
        effect("c6", "Companion's Counsel", "Active characters with Soulwind deal 60% more Physical CRIT DMG.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            critDmg(0.60, HitFilter(elements = setOf(Element.PHYSICAL)))
        }
    }

    this("barbara") {
        effect("c2", "Vitality Burst", "The active character gains 15% Hydro DMG Bonus during Melody Loop.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            elementDmg(Element.HYDRO, 0.15)
        }
    }

    this("dahlia") {
        effect("c2", "Revelation of Mercy", "The shielded character gains 25% Shield Strength.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            stat(Stat.SHIELD_STRENGTH, 0.25)
        }
    }

    this("durin") {
        val darkness = HitFilter.hits("burst/principle-of-darkness-as-the-stars-smolder-dmg", "burst/dragon-of-dark-decay-dmg")
        val dragons = HitFilter.hits("burst/dragon-of-white-flame-dmg", "burst/dragon-of-dark-decay-dmg")
        effect(
            "form", "Burst form",
            "White Flame: Pyro RES -20% (A1); others gain +60% of Durin's ATK as DMG (C1); DEF -30% (C6). " +
                "Dark Decay: Vaporize/Melt DMG +40% (A1); Durin's burst +150% of ATK (C1); +40% DEF ignore (C6).",
            target = EffectTarget.TEAM,
            control = choice("Principle of Purity (White Flame)", "Principle of Darkness (Dark Decay)"),
            phase = EffectPhase.TEAM_STAT,
        ) {
            val a1 = owner.ascension >= 1
            if (value == 0) {
                if (a1) resShred(Element.PYRO, 0.20)
                if (constellation >= 1 && !isSelf) flatDmg(0.60 * ownerStats.atk, ALL_TALENT_ATTACKS)
                if (constellation >= 6) defReduction(0.30)
            } else if (isSelf) {
                if (a1) {
                    reactionBonus(Reaction.VAPORIZE, 0.40)
                    reactionBonus(Reaction.MELT, 0.40)
                }
                if (constellation >= 1) flatDmg(1.50 * ownerStats.atk, HitFilter(talents = setOf(TalentType.BURST)))
                if (constellation >= 6) defIgnore(0.40, darkness)
            }
        }
        effect("a4", "Primordial Fusion", "Dragon attacks deal +3% of their DMG per 100 ATK (max 75%).",
            phase = EffectPhase.CONVERSION, requires = A4) {
            multiplier(1 + (0.03 * targetStats.atk / 100).coerceAtMost(0.75), dragons)
        }
        effect("c2", "Unground Visions", "Party Pyro DMG +50% for 6s after Pyro reactions during the burst.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            elementDmg(Element.PYRO, 0.50)
        }
        effect("c4", "Emanare's Source", "Burst DMG +40%.", requires = cons(4)) {
            dmgBonus(0.40, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c6", "Dual Birth", "Burst DMG ignores 30% DEF.", requires = cons(6)) {
            defIgnore(0.30, HitFilter(talents = setOf(TalentType.BURST)))
        }
    }

    this("lohen") {
        val etched = HitFilter.hits("skill/etched-into-bone-and-soul-dmg")
        val burst = HitFilter.hits("burst/skill-dmg")
        effect("will", "Will to Win", "Etched Into Bone and Soul and burst DMG x(1 + 0.4% per Will to Win; max 100, 300 at C1).",
            control = stacks(300, 100, "Will to Win", step = 20)) {
            val will = stacks.coerceAtMost(if (constellation >= 1) 300 else 100)
            multiplier(1 + param(TalentType.SKILL, "param18") * will, etched)
            multiplier(1 + param(TalentType.BURST, "param2") * will, burst)
        }
        effect("p3", "High Spirits", "Unforeseen Strike Level +1 for 9s after the skill.", control = toggle()) {
            talentLevel(TalentType.SKILL, 1)
        }
        effect("a4", "Flippant Masterpiece", "After a party Cryo reaction in Masterstroke: that character and Lohen gain 15% ATK.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            stat(Stat.ATK_PCT, 0.15)
        }
        effect("c2", "In Flight, I Strike Whatever Flies", "Other party members gain 200 EM after Evilsbane Blade.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), requires = cons(2)) {
            stat(Stat.EM, 200.0)
        }
        effect("c6", "To Drown, to Sink, Unconscious", "Etched Into Bone and Soul and burst CRIT DMG +175%.", requires = cons(6)) {
            critDmg(1.75, etched)
            critDmg(1.75, burst)
        }
    }

    this("prune") {
        effect("element", "Elemental Conversion", "Element of the Banehunter Oathhammer (from the Swirl).", control = ABSORB_CHOICE, requires = A1) {
            convertElement(absorbedElement(), "burst/a1-banehunter-oathhammer", "burst/c4-oathhammer-ricochet")
        }
        effect(
            "a4", "Tolling Rally", "Other members' attacks deal +0.025% DMG per point of Prune's ATK above 2,000 (max 50%).",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            dmgBonus((0.00025 * (ownerStats.atk - 2000)).coerceIn(0.0, 0.50), ALL_TALENT_ATTACKS)
        }
        effect("c2", "Hunt the Witch", "ATK +10%, +5% per Oathhammer/Bell hit (max 40%).",
            control = stacks(7, 7, "Stacks"), requires = cons(2)) {
            if (stacks > 0) stat(Stat.ATK_PCT, 0.05 + 0.05 * stacks)
        }
        effect("c6", "And That's the Story!", "Prune and the active character gain 350 ATK after reactions.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            stat(Stat.ATK, 350.0)
        }
    }

    this("varka") {
        val sturm = HitFilter.hits(
            "skill/sturm-und-drang-1-hit-dmg", "skill/sturm-und-drang-2-hit-dmg", "skill/sturm-und-drang-3-hit-dmg",
            "skill/sturm-und-drang-4-hit-dmg", "skill/sturm-und-drang-5-hit-dmg", "skill/sturm-und-drang-charged-attack-dmg",
            "skill/four-winds-ascension-dmg", "skill/azure-devour-dmg",
        )
        effect(
            "a1", "Dawn Wind's March", "Anemo and one party element DMG +10% per 1,000 ATK (max 25%); Sturm und Drang attacks deal 140% DMG " +
                "(2 Anemo or 2 of one element) or 220% (both).",
            static = true, phase = EffectPhase.CONVERSION, requires = A1,
        ) {
            val bonus = (0.10 * targetStats.atk / 1000).coerceAtMost(0.25)
            elementDmg(Element.ANEMO, bonus)
            SWIRLABLE.firstOrNull { team.count(it) > 0 }?.let { elementDmg(it, bonus) }
            val anemoPair = team.count(Element.ANEMO) >= 2
            val otherPair = SWIRLABLE.any { team.count(it) >= 2 }
            val mult = if (anemoPair && otherPair) 2.2 else if (anemoPair || otherPair) 1.4 else 1.0
            if (mult > 1.0) multiplier(mult, sturm)
        }
        effect("a4", "Azure Fang's Oath", "Sturm und Drang attacks +7.5% DMG per stack (max 4); C6: +20% CRIT DMG per stack.",
            control = stacks(4), requires = A4) {
            dmgBonus(0.075 * stacks, sturm)
            if (constellation >= 6) critDmg(0.20 * stacks, sturm)
        }
        effect("c1", "Lyrical Libation", "The next Four Winds' Ascension or Azure Devour deals 200% DMG.", control = toggle(), requires = cons(1)) {
            multiplier(2.0, HitFilter.hits("skill/four-winds-ascension-dmg", "skill/azure-devour-dmg"))
        }
        effect("c4", "For None May Take From Us Our Freedom of Song", "Party Anemo and swirled element DMG +20% after Varka's Swirl.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            elementDmg(Element.ANEMO, 0.20)
            SWIRLABLE.firstOrNull { team.count(it) > 0 }?.let { elementDmg(it, 0.20) }
        }
    }
}

/** Normal, Charged, Plunging Attacks, Elemental Skills and Bursts. */
internal val ALL_TALENT_ATTACKS = HitFilter.of(
    com.genshincalc.core.model.AttackCategory.NORMAL, com.genshincalc.core.model.AttackCategory.CHARGED,
    com.genshincalc.core.model.AttackCategory.PLUNGE, com.genshincalc.core.model.AttackCategory.SKILL,
    com.genshincalc.core.model.AttackCategory.BURST,
)
