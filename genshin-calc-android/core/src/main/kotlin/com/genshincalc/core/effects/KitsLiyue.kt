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

internal fun EffectTable.liyueKits() {
    this("hutao") {
        effect(
            "skill", "Paramita Papilio", "ATK increased by a % of Max HP (max 400% of Base ATK); attacks become Pyro and can't be overridden.",
            control = toggle(), phase = EffectPhase.CONVERSION,
        ) {
            val bonus = minOf(param(TalentType.SKILL, "param2") * targetStats.hp, 4 * target.sheet[Stat.BASE_ATK])
            stat(Stat.ATK, bonus)
            infuse(Element.PYRO, InfusionPriority.NON_OVERRIDABLE)
        }
        effect("a1", "Flutter By", "After Paramita Papilio ends, other party members gain 12% CRIT Rate.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(false), requires = A1) {
            stat(Stat.CRIT_RATE, 0.12)
        }
        effect("a4", "Sanguine Rouge", "Pyro DMG Bonus +33% while HP is at or below 50%.", control = toggle(), requires = A4) {
            elementDmg(Element.PYRO, 0.33)
        }
        effect("c2", "Ominous Rainfall", "Blood Blossom DMG increased by 10% of Hu Tao's Max HP.",
            phase = EffectPhase.CONVERSION, requires = cons(2)) {
            flatDmg(0.10 * targetStats.hp, HitFilter.hits("skill/blood-blossom-dmg"))
        }
        effect("c4", "Garden of Eternal Rest", "Other party members gain 12% CRIT Rate after an enemy with Blood Blossom is defeated.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(false), requires = cons(4)) {
            stat(Stat.CRIT_RATE, 0.12)
        }
        effect("c6", "Butterfly's Embrace", "CRIT Rate +100% for 10s when HP drops below 25%.", control = toggle(false), requires = cons(6)) {
            stat(Stat.CRIT_RATE, 1.0)
        }
    }

    this("xingqiu") {
        effect("a4", "Blades Amidst Raindrops", "Hydro DMG Bonus +20%.", static = true, requires = A4) {
            elementDmg(Element.HYDRO, 0.20)
        }
        effect("c2", "Rainbow Upon the Azure Sky", "Sword rain hits lower Hydro RES by 15%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            resShred(Element.HYDRO, 0.15)
        }
        effect("c4", "Evilsoother", "Fatal Rainscreen DMG +50% during Raincutter.", control = toggle(), requires = cons(4)) {
            multiplier(1.5, HitFilter.hits("skill/skill-dmg"))
        }
    }

    this("zhongli") {
        effect("shield", "Jade Shield", "Shielded party lowers nearby enemies' Elemental and Physical RES by 20%.",
            target = EffectTarget.TEAM, control = toggle()) {
            resShredAll(0.20)
        }
        effect("a1", "Resonant Waves", "Shield Strength +5% per stack while the Jade Shield takes DMG (max 5).",
            target = EffectTarget.TEAM, control = stacks(5, 0), requires = A1) {
            stat(Stat.SHIELD_STRENGTH, 0.05 * stacks)
        }
        effect("a4", "Dominance of Earth", "DMG increased by Max HP: Normal/Charged/Plunging 1.39%, Stele 1.9%, Planet Befall 33%.",
            phase = EffectPhase.CONVERSION, requires = A4) {
            val hp = targetStats.hp
            flatDmg(0.0139 * hp, HitFilter.NORMAL_CHARGED_PLUNGE)
            flatDmg(0.019 * hp, HitFilter(talents = setOf(TalentType.SKILL)))
            flatDmg(0.33 * hp, HitFilter(talents = setOf(TalentType.BURST)))
        }
    }

    this("yelan") {
        effect("a1", "Turn Control", "Max HP +6/12/18/30% for 1/2/3/4 Elemental Types in the party.", static = true, requires = A1) {
            val bonus = when (team.distinctElements) {
                1 -> 0.06
                2 -> 0.12
                3 -> 0.18
                else -> 0.30
            }
            stat(Stat.HP_PCT, bonus)
        }
        effect(
            "a4", "Adapt With Ease", "The active character deals 1% more DMG, +3.5% per second of Exquisite Throw (max 50%).",
            target = EffectTarget.TEAM, control = stacks(14, 14, "Seconds"), requires = A4,
        ) {
            stat(Stat.ALL_DMG, minOf(0.01 + 0.035 * stacks, 0.50))
        }
        effect("c4", "Bait-and-Switch", "Party Max HP +10% per enemy marked by Lifeline (max 40%).",
            target = EffectTarget.TEAM, control = stacks(4, 1, "Marked enemies"), requires = cons(4)) {
            stat(Stat.HP_PCT, 0.10 * stacks)
        }
        effect("c6", "Mastermind", "Normal Attacks become Breakthrough Barbs dealing 156% DMG (shown on the Breakthrough Barb row).",
            control = toggle(false), requires = cons(6)) {
            multiplier(1.56, HitFilter.hits("normal/breakthrough-barb-dmg"))
        }
    }

    this("xiangling") {
        effect("a4", "Beware, It's Super Hot!", "Picking up Guoba's chili pepper: ATK +10%.",
            target = EffectTarget.TEAM, control = toggle(false), requires = A4) {
            stat(Stat.ATK_PCT, 0.10)
        }
        effect("c1", "Crispy Outside, Tender Inside", "Guoba lowers Pyro RES by 15%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(1)) {
            resShred(Element.PYRO, 0.15)
        }
        effect("c6", "Condensed Pyronado", "Party Pyro DMG Bonus +15% during Pyronado.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            elementDmg(Element.PYRO, 0.15)
        }
    }

    this("shenhe") {
        effect(
            "quill", "Icy Quill", "Cryo Normal/Charged/Plunging Attacks, Skills and Bursts deal extra DMG equal to a % of Shenhe's ATK.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            flatDmg(param(TalentType.SKILL, "param3") * ownerStats.atk, HitFilter(elements = setOf(Element.CRYO)))
        }
        effect("burst", "Divine Maiden's Deliverance", "Lowers enemies' Cryo and Physical RES.",
            target = EffectTarget.TEAM, control = toggle()) {
            val shred = param(TalentType.BURST, "param2")
            resShred(Element.CRYO, shred)
            resShred(Element.PHYSICAL, shred)
        }
        effect("a1", "Deific Embrace", "The active character in the field gains 15% Cryo DMG Bonus.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1) {
            elementDmg(Element.CRYO, 0.15)
        }
        effect(
            "a4", "Spirit Communion Seal", "Press: Skill and Burst DMG +15%. Hold: Normal, Charged and Plunging Attack DMG +15%.",
            target = EffectTarget.TEAM, control = choice("Press (Skill/Burst)", "Hold (Normal/Charged/Plunge)", "Off"), requires = A4,
        ) {
            when (value) {
                0 -> dmgBonus(0.15, HitFilter.SKILL_BURST)
                1 -> dmgBonus(0.15, HitFilter.NORMAL_CHARGED_PLUNGE)
            }
        }
        effect("c2", "Centered Spirit", "Active characters in the field deal 15% more Cryo CRIT DMG.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            critDmg(0.15, HitFilter(elements = setOf(Element.CRYO)))
        }
        effect("c4", "Insight", "Spring Spirit Summoning DMG +5% per Skyfrost Mantra stack.",
            control = stacks(50, 50), requires = cons(4)) {
            dmgBonus(0.05 * stacks, HitFilter(talents = setOf(TalentType.SKILL)))
        }
    }

    this("yunjin") {
        effect(
            "burst", "Flying Cloud Flag Formation", "Normal Attack DMG increased by a % of Yun Jin's DEF (more with more Elemental Types at A4).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            var ratio = param(TalentType.BURST, "param2")
            if (owner.ascension >= 4) {
                ratio += when (team.distinctElements) {
                    1 -> 0.025
                    2 -> 0.05
                    3 -> 0.075
                    else -> 0.115
                }
            }
            flatDmg(ratio * ownerStats.def, HitFilter.NORMAL)
        }
        effect("c2", "Myriad Mise-En-Scène", "Party Normal Attack DMG +15% after Cliffbreaker's Banner.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            dmgBonus(0.15, HitFilter.NORMAL)
        }
        effect("c4", "Flower and a Fighter", "DEF +20% after triggering Crystallize.", control = toggle(false), requires = cons(4)) {
            stat(Stat.DEF_PCT, 0.20)
        }
    }

    this("ganyu") {
        effect("a1", "Undivided Heart", "Frostflake Arrows and their Blooms gain 20% CRIT Rate after firing one.", control = toggle(), requires = A1) {
            critRate(0.20, HitFilter.hits("normal/frostflake-arrow-dmg", "normal/frostflake-arrow-bloom-dmg"))
        }
        effect("a4", "Harmony Between Heaven and Earth", "Active characters in Celestial Shower gain 20% Cryo DMG Bonus.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            elementDmg(Element.CRYO, 0.20)
        }
        effect("c1", "Dew-Drinker", "Charge Level 2 arrows lower Cryo RES by 15%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(1)) {
            resShred(Element.CRYO, 0.15)
        }
        effect("c4", "Westward Sojourn", "Enemies in Celestial Shower take 5% more DMG, +5% every 3s (max 25%).",
            target = EffectTarget.TEAM, control = stacks(5, 5), requires = cons(4)) {
            stat(Stat.ALL_DMG, 0.05 * stacks)
        }
    }

    this("xiao") {
        effect(
            "burst", "Bane of All Evil", "Normal/Charged/Plunging Attack DMG Bonus and an Anemo infusion that can't be overridden.",
            control = toggle(),
        ) {
            dmgBonus(param(TalentType.BURST, "param1"), HitFilter.NORMAL_CHARGED_PLUNGE)
            infuse(Element.ANEMO, InfusionPriority.NON_OVERRIDABLE)
        }
        effect("a1", "Conqueror of Evil: Tamer of Demons", "During Bane of All Evil, DMG +5%, +5% every 3s (max 25%).",
            control = stacks(5, 5), requires = A1) {
            stat(Stat.ALL_DMG, 0.05 * stacks)
        }
        effect("a4", "Dissolution Eon: Heaven Fall", "Lemniscatic Wind Cycling DMG +15% per stack (max 3).",
            control = stacks(3, 0), requires = A4) {
            dmgBonus(0.15 * stacks, HitFilter(talents = setOf(TalentType.SKILL)))
        }
        effect("c4", "Transcension: Extinction of Suffering", "DEF +100% while HP is below 50%.", control = toggle(false), requires = cons(4)) {
            stat(Stat.DEF_PCT, 1.0)
        }
    }

    this("keqing") {
        effect("a1", "Thundering Penance", "Electro infusion for 5s after recasting Stellar Restoration.", control = toggle(), requires = A1) {
            infuse(Element.ELECTRO)
        }
        effect("a4", "Aristocratic Dignity", "CRIT Rate +15% and Energy Recharge +15% after Starward Sword.", control = toggle(), requires = A4) {
            stat(Stat.CRIT_RATE, 0.15)
            stat(Stat.ER, 0.15)
        }
        effect("c4", "Attunement", "ATK +25% after an Electro reaction.", control = toggle(), requires = cons(4)) {
            stat(Stat.ATK_PCT, 0.25)
        }
        effect("c6", "Tenacious Star", "Electro DMG Bonus +6% per stack (max 4).", control = stacks(4, 4), requires = cons(6)) {
            elementDmg(Element.ELECTRO, 0.06 * stacks)
        }
    }

    this("beidou") {
        effect(
            "counter", "Tidecaller: hits taken", "Each hit taken while holding (max 2) adds the 'DMG Bonus on Hit Taken' multiplier.",
            control = choice("0 hits", "1 hit", "2 hits (max)", default = 2), phase = EffectPhase.CONVERSION,
        ) {
            flatDmg(param(TalentType.SKILL, "param4") * value * targetStats.atk, HitFilter.hits("skill/base-dmg"))
        }
        effect("a4", "Lightning Storm", "Normal and Charged Attack DMG +15% after a max-bonus Tidecaller.", control = toggle(false), requires = A4) {
            dmgBonus(0.15, HitFilter.NORMAL_CHARGED)
        }
        effect("c6", "Bane of Evil", "Stormbreaker lowers Electro RES by 15%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            resShred(Element.ELECTRO, 0.15)
        }
    }

    this("ningguang") {
        effect("a4", "Strategic Reserve", "Passing through the Jade Screen: Geo DMG Bonus +12%.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            elementDmg(Element.GEO, 0.12)
        }
    }

    this("chongyun") {
        effect("field", "Chonghua's Layered Frost", "Sword, Claymore and Polearm users in the field gain a Cryo infusion.",
            target = EffectTarget.TEAM, control = toggle()) {
            infuse(Element.CRYO, InfusionPriority.TEAM)
        }
        effect("a4", "Rimechaser Blade", "The final spirit blade lowers Cryo RES by 10%.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            resShred(Element.CRYO, 0.10)
        }
        effect("c6", "Rally of Four Blades", "Cloud-Parting Star DMG +15% vs enemies with a lower HP% than Chongyun.",
            control = toggle(false), requires = cons(6)) {
            dmgBonus(0.15, HitFilter.BURST)
        }
    }

    this("qiqi") {
        effect("c2", "Frozen to the Bone", "Normal and Charged Attack DMG +15% vs Cryo-affected enemies.", control = toggle(), requires = cons(2)) {
            dmgBonus(0.15, HitFilter.NORMAL_CHARGED)
        }
    }

    this("xinyan") {
        effect("a4", "...Now That's Rock 'N' Roll!", "Shielded characters deal 15% more Physical DMG.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4) {
            elementDmg(Element.PHYSICAL, 0.15)
        }
        effect("c2", "Impromptu Opening", "Riff Revolution's Physical DMG: CRIT Rate +100%.", requires = cons(2)) {
            critRate(1.0, HitFilter.hits("burst/skill-dmg"))
        }
        effect("c4", "Wildfire Rhythm", "Sweeping Fervor lowers Physical RES by 15%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            resShred(Element.PHYSICAL, 0.15)
        }
    }

    this("yanfei") {
        effect("a1", "Proviso", "Pyro DMG Bonus +5% per Scarlet Seal consumed by a Charged Attack.",
            control = stacks(4, 4, "Seals"), requires = A1) {
            elementDmg(Element.PYRO, 0.05 * stacks)
        }
        effect("burst", "Brilliance", "Charged Attack DMG Bonus during Done Deal.", control = toggle()) {
            dmgBonus(param(TalentType.BURST, "param2"), HitFilter.CHARGED)
        }
        effect("c2", "Right of Final Interpretation", "Charged Attack CRIT Rate +20% vs enemies below 50% HP.",
            control = toggle(false), requires = cons(2)) {
            critRate(0.20, HitFilter.CHARGED)
        }
    }

    this("baizhu") {
        effect("a1", "Five Fortunes Forever", "Dendro DMG Bonus +25% while the active character's HP is at least 50%.",
            control = toggle(), requires = A1) {
            elementDmg(Element.DENDRO, 0.25)
        }
        effect(
            "a4", "All Things Are of the Earth",
            "Per 1,000 of Baizhu's Max HP (max 50,000): Burning/Bloom/Hyperbloom/Burgeon +2%, Lunar-Bloom +0.7%, Aggravate/Spread +0.8%.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            val k = minOf(ownerStats.hp, 50_000.0) / 1000
            for (r in listOf(Reaction.BURNING, Reaction.BLOOM, Reaction.HYPERBLOOM, Reaction.BURGEON)) reactionBonus(r, 0.02 * k)
            reactionBonus(Reaction.LUNAR_BLOOM, 0.007 * k)
            reactionBonus(Reaction.AGGRAVATE, 0.008 * k)
            reactionBonus(Reaction.SPREAD, 0.008 * k)
        }
        effect("c4", "Ancient Art of Perception", "Party Elemental Mastery +80 after Holistic Revivification.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            stat(Stat.EM, 80.0)
        }
        effect("c6", "Elimination of Malicious Qi", "Spiritvein DMG increased by 8% of Baizhu's Max HP.",
            phase = EffectPhase.CONVERSION, requires = cons(6)) {
            flatDmg(0.08 * targetStats.hp, HitFilter.hits("burst/spiritvein-dmg"))
        }
    }

}
