package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectScope
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

/** Elements an Anemo character can absorb/swirl. */
internal val SWIRLABLE = listOf(Element.PYRO, Element.HYDRO, Element.ELECTRO, Element.CRYO)

/** "Party elements (auto)" choice + one option per swirlable element. */
internal val SWIRL_CHOICE = choice("Party elements", "Pyro", "Hydro", "Electro", "Cryo")

internal fun EffectScope.swirlElements(): List<Element> =
    if (value == 0) SWIRLABLE.filter { team.count(it) > 0 } else listOf(SWIRLABLE[value - 1])

private val BURST_TALENT = HitFilter(talents = setOf(TalentType.BURST))

internal fun EffectTable.inazumaKits() {
    this("kaedeharakazuha") {
        effect("absorb", "Kazuha Slash absorption", "Element absorbed by the Autumn Whirlwind (sets the Additional Elemental DMG).",
            control = ABSORB_CHOICE) {
            convertElement(absorbedElement(), "burst/additional-elemental-dmg")
        }
        effect(
            "a4", "Poetics of Fuubutsu", "After Swirl: party gains 0.04% DMG Bonus of the swirled element per point of Kazuha's EM.",
            target = EffectTarget.TEAM, control = SWIRL_CHOICE, phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            for (e in swirlElements()) elementDmg(e, 0.0004 * ownerStats.em)
        }
        effect("c2", "Yamaarashi Tailwind", "Kazuha and characters in the Autumn Whirlwind field gain 200 EM.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            stat(Stat.EM, 200.0)
        }
        effect("midare", "Midare Ranzan", "Plunging Attacks after Chihayaburu deal Anemo DMG.", control = toggle()) {
            infuse(Element.ANEMO, InfusionPriority.NON_OVERRIDABLE, setOf(AttackCategory.PLUNGE))
        }
        effect("c6", "Crimson Momiji", "Anemo infusion; Normal, Charged and Plunging Attack DMG +0.2% per point of EM.",
            control = toggle(), phase = EffectPhase.CONVERSION, requires = cons(6)) {
            infuse(Element.ANEMO)
            dmgBonus(0.002 * targetStats.em, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }

    this("raidenshogun") {
        effect(
            "eye", "Eye of Stormy Judgment", "Party Elemental Burst DMG increased by a % per point of the burst's Energy Cost.",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            dmgBonus(param(TalentType.SKILL, "param4") * target.burstEnergyCost, HitFilter.BURST)
        }
        effect(
            "resolve", "Chakra Desiderata (Resolve)", "Each Resolve stack (max 60) increases Musou no Hitotachi and Musou Isshin DMG.",
            control = stacks(60, 60, "Resolve"), phase = EffectPhase.CONVERSION,
        ) {
            val atk = targetStats.atk
            flatDmg(param(TalentType.BURST, "param2") * stacks * atk, HitFilter.hits("burst/musou-no-hitotachi-base-dmg"))
            flatDmg(
                param(TalentType.BURST, "param3") * stacks * atk,
                HitFilter.hits(
                    "burst/1-hit-dmg", "burst/2-hit-dmg", "burst/3-hit-dmg", "burst/4-hit-dmg", "burst/5-hit-dmg",
                    "burst/charged-attack-dmg", "burst/plunge-dmg", "burst/low-plunge-dmg", "burst/high-plunge-dmg",
                ),
            )
        }
        effect("a4", "Enlightened One", "Electro DMG Bonus +0.4% per 1% Energy Recharge above 100%.",
            static = true, phase = EffectPhase.CONVERSION, requires = A4) {
            elementDmg(Element.ELECTRO, 0.4 * (targetStats.er - 1).coerceAtLeast(0.0))
        }
        effect("c2", "Steelbreaker", "Musou no Hitotachi and Musou Isshin attacks ignore 60% of DEF.", requires = cons(2)) {
            defIgnore(0.60, BURST_TALENT)
        }
        effect("c4", "Pledge of Propriety", "After Musou Isshin ends, other party members gain 30% ATK.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(false), requires = cons(4)) {
            stat(Stat.ATK_PCT, 0.30)
        }
    }

    this("kujousara") {
        effect(
            "buff", "Tengu Juurai ATK Bonus", "The active character gains ATK equal to Sara's Base ATK x the ATK Bonus Ratio.",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            stat(Stat.ATK, ownerBaseAtk * param(TalentType.SKILL, "param2"))
        }
        effect("c6", "Sin of Pride", "Electro CRIT DMG +60% for characters buffed by Tengu Juurai.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            critDmg(0.60, HitFilter(elements = setOf(Element.ELECTRO)))
        }
    }

    this("gorou") {
        effect(
            "banner", "General's War Banner", "DEF bonus; with 3 Geo characters also +15% Geo DMG.",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            stat(Stat.DEF, param(TalentType.SKILL, "param2"))
            if (team.count(Element.GEO) >= 3) elementDmg(Element.GEO, param(TalentType.SKILL, "param3"))
        }
        effect("a1", "Heedless of the Wind and Weather", "Party DEF +25% after Juuga.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1) {
            stat(Stat.DEF_PCT, 0.25)
        }
        effect("a4", "A Favor Repaid", "Skill DMG +156% of DEF; burst and Crystal Collapse DMG +15.6% of DEF.",
            phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(1.56 * targetStats.def, HitFilter(talents = setOf(TalentType.SKILL)))
            flatDmg(0.156 * targetStats.def, BURST_TALENT)
        }
        effect(
            "c6", "Valiant Hound: Mountainous Fealty", "Party Geo CRIT DMG +10/20/40% by the banner's level (1/2/3 Geo characters).",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6),
        ) {
            val bonus = when (team.count(Element.GEO)) {
                1 -> 0.10
                2 -> 0.20
                else -> 0.40
            }
            critDmg(bonus, HitFilter(elements = setOf(Element.GEO)))
        }
    }

    this("kamisatoayaka") {
        effect("dash", "Kamisato Art: Senho", "After the alternate sprint, attacks are infused with Cryo.", control = toggle()) {
            infuse(Element.CRYO)
        }
        effect("a1", "Amatsumi Kunitsumi Sanctification", "Normal and Charged Attack DMG +30% for 6s after Hyouka.", control = toggle(), requires = A1) {
            dmgBonus(0.30, HitFilter.NORMAL_CHARGED)
        }
        effect("a4", "Kanten Senmyou Blessing", "Cryo DMG Bonus +18% after Senho's Cryo application hits.", control = toggle(), requires = A4) {
            elementDmg(Element.CRYO, 0.18)
        }
        effect("c4", "Ebb and Flow", "Soumetsu lowers DEF by 30%.", target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            defReduction(0.30)
        }
        effect("c6", "Dance of Suigetsu", "Usurahi Butou: Charged Attack DMG +298%.", control = toggle(), requires = cons(6)) {
            dmgBonus(2.98, HitFilter.CHARGED)
        }
    }

    this("yoimiya") {
        effect(
            "skill", "Niwabi Fire-Dance", "Normal Attacks become Blazing Arrows: Pyro DMG with the 'Blazing Arrow DMG' multiplier.",
            control = toggle(),
        ) {
            multiplier(param(TalentType.SKILL, "param4"), HitFilter.NORMAL)
            infuse(Element.PYRO, InfusionPriority.NON_OVERRIDABLE, setOf(AttackCategory.NORMAL))
        }
        effect("a1", "Tricks of the Trouble-Maker", "Pyro DMG Bonus +2% per Normal Attack hit (max 10 stacks).",
            control = stacks(10, 10), requires = A1) {
            elementDmg(Element.PYRO, 0.02 * stacks)
        }
        effect("a4", "Summer Night's Dawn", "After her burst, other party members gain 10% ATK +1% per A1 stack.",
            target = EffectTarget.TEAM_OTHERS, control = stacks(10, 10, "A1 stacks"), requires = A4) {
            stat(Stat.ATK_PCT, 0.10 + 0.01 * stacks)
        }
        effect("c1", "Agate Ryuukin", "ATK +20% after an Aurous Blaze target is defeated.", control = toggle(false), requires = cons(1)) {
            stat(Stat.ATK_PCT, 0.20)
        }
        effect("c2", "A Procession of Bonfires", "Pyro DMG Bonus +25% after a Pyro CRIT Hit.", control = toggle(), requires = cons(2)) {
            elementDmg(Element.PYRO, 0.25)
        }
    }

    this("aratakiitto") {
        effect(
            "burst", "Raging Oni King", "ATK increased by a % of DEF; Geo infusion that can't be overridden.",
            control = toggle(), phase = EffectPhase.CONVERSION,
        ) {
            stat(Stat.ATK, param(TalentType.BURST, "param2") * targetStats.def)
            infuse(Element.GEO, InfusionPriority.NON_OVERRIDABLE)
        }
        effect("a4", "Bloodline of the Crimson Oni", "Arataki Kesagiri DMG +35% of DEF.", phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.35 * targetStats.def, HitFilter.hits("normal/arataki-kesagiri-combo-slash-dmg", "normal/arataki-kesagiri-final-slash-dmg"))
        }
        effect("c4", "Jailhouse Bread and Butter", "Party DEF +20% and ATK +20% after Raging Oni King ends.",
            target = EffectTarget.TEAM, control = toggle(false), requires = cons(4)) {
            stat(Stat.DEF_PCT, 0.20)
            stat(Stat.ATK_PCT, 0.20)
        }
        effect("c6", "Arataki Itto, Present!", "Charged Attack CRIT DMG +70%.", requires = cons(6)) {
            critDmg(0.70, HitFilter.CHARGED)
        }
    }

    this("yaemiko") {
        effect("a4", "Enlightened Blessing", "Sesshou Sakura DMG +0.15% per point of EM.", phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus(0.0015 * targetStats.em, HitFilter(talents = setOf(TalentType.SKILL)))
        }
        effect("c4", "Sakura Channeling", "Party Electro DMG Bonus +20% when Sesshou Sakura hits.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(4)) {
            elementDmg(Element.ELECTRO, 0.20)
        }
        effect("c6", "Forbidden Art: Daisesshou", "Sesshou Sakura ignores 60% of DEF.", requires = cons(6)) {
            defIgnore(0.60, HitFilter(talents = setOf(TalentType.SKILL)))
        }
    }

    this("kamisatoayato") {
        effect(
            "namisen", "Namisen", "Each stack adds a % of Max HP to Shunsuiken DMG (max 4, 5 at C2).",
            control = stacks(5, 4), phase = EffectPhase.CONVERSION,
        ) {
            val max = if (constellation >= 2) 5 else 4
            val shunsuiken = HitFilter.hits("skill/shunsuiken-1-hit-dmg", "skill/shunsuiken-2-hit-dmg", "skill/shunsuiken-3-hit-dmg")
            flatDmg(param(TalentType.SKILL, "param5") * minOf(stacks, max) * targetStats.hp, shunsuiken)
        }
        effect("burst", "Kamisato Art: Suiyuu", "Normal Attack DMG Bonus for characters in the field.",
            target = EffectTarget.TEAM, control = toggle()) {
            dmgBonus(param(TalentType.BURST, "param2"), HitFilter.NORMAL)
        }
        effect("c1", "Kyouka Fuushi", "Shunsuiken DMG +40% vs enemies at or below 50% HP.", control = toggle(false), requires = cons(1)) {
            dmgBonus(0.40, HitFilter.hits("skill/shunsuiken-1-hit-dmg", "skill/shunsuiken-2-hit-dmg", "skill/shunsuiken-3-hit-dmg"))
        }
        effect("c2", "World Source", "Max HP +50% with at least 3 Namisen stacks.", control = toggle(), requires = cons(2)) {
            stat(Stat.HP_PCT, 0.50)
        }
    }

    this("sangonomiyakokomi") {
        effect("passive", "Flawless Strategy", "Healing Bonus +25%, CRIT Rate -100%.", static = true) {
            stat(Stat.HEALING_BONUS, 0.25)
            stat(Stat.CRIT_RATE, -1.0)
        }
        effect(
            "burst", "Nereid's Ascension", "Normal/Charged Attack and Bake-Kurage DMG increased by a % of Max HP (+15% of Healing Bonus at A4).",
            control = toggle(), phase = EffectPhase.CONVERSION,
        ) {
            val hp = targetStats.hp
            val a4 = if (owner.ascension >= 4) 0.15 * targetStats[Stat.HEALING_BONUS] else 0.0
            flatDmg((param(TalentType.BURST, "param4") + a4) * hp, HitFilter.NORMAL)
            flatDmg((param(TalentType.BURST, "param5") + a4) * hp, HitFilter.CHARGED)
            flatDmg(param(TalentType.BURST, "param9") * hp, HitFilter.hits("skill/ripple-dmg"))
        }
        effect("c6", "Sango Isshin", "Hydro DMG Bonus +40% after healing a character above 80% HP.", control = toggle(), requires = cons(6)) {
            elementDmg(Element.HYDRO, 0.40)
        }
    }

    this("thoma") {
        effect("a1", "Imbricated Armor", "Shield Strength +5% per stack (max 5).", target = EffectTarget.TEAM, control = stacks(5, 5), requires = A1) {
            stat(Stat.SHIELD_STRENGTH, 0.05 * stacks)
        }
        effect("a4", "Flaming Assault", "Fiery Collapse DMG +2.2% of Max HP.", phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.022 * targetStats.hp, HitFilter.hits("burst/fiery-collapse-dmg"))
        }
        effect("c6", "Burning Heart", "Party Normal, Charged and Plunging Attack DMG +15% when a Blazing Barrier is obtained.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            dmgBonus(0.15, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }

    this("sayu") {
        effect("absorb", "Fuufuu Windwheel absorption", "Element absorbed by Yoohoo Art: Fuuin Dash.", control = ABSORB_CHOICE) {
            convertElement(absorbedElement(), "skill/fuufuu-windwheel-elemental-dmg", "skill/fuufuu-whirlwind-kick-elemental-dmg")
        }
        effect("c2", "Egress Prep", "Fuufuu Whirlwind Kick DMG +3.3% per 0.5s in the Windwheel (max 66%).",
            control = stacks(20, 20), requires = cons(2)) {
            dmgBonus(0.033 * stacks, HitFilter.hits("skill/fuufuu-whirlwind-kick-press-dmg", "skill/fuufuu-whirlwind-kick-hold-dmg",
                "skill/fuufuu-whirlwind-kick-elemental-dmg"))
        }
        effect("c6", "Sleep O'Clock", "Muji-Muji Daruma DMG +0.2% of ATK per point of EM (max 400% ATK).",
            phase = EffectPhase.CONVERSION, requires = cons(6)) {
            flatDmg(minOf(0.002 * targetStats.em, 4.0) * targetStats.atk, HitFilter.hits("burst/muji-muji-daruma-dmg"))
        }
    }

    this("shikanoinheizou") {
        effect(
            "declension", "Declension / Conviction", "Heartstopper Strike gains a bonus per Declension stack; Conviction at 4 stacks (C6: +4% CRIT Rate per stack, +32% CRIT DMG).",
            control = stacks(4, 4, "Declension"), phase = EffectPhase.CONVERSION,
        ) {
            val filter = HitFilter.hits("skill/skill-dmg")
            var ratio = param(TalentType.SKILL, "param2") * stacks
            if (stacks >= 4) ratio += param(TalentType.SKILL, "param3")
            flatDmg(ratio * targetStats.atk, filter)
            if (constellation >= 6) {
                critRate(0.04 * stacks, filter)
                if (stacks >= 4) critDmg(0.32, filter)
            }
        }
        effect("a4", "Penetrative Reasoning", "Other party members gain 80 EM after Heartstopper Strike hits.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), requires = A4) {
            stat(Stat.EM, 80.0)
        }
    }

    this("kukishinobu") {
        effect("a1", "Breaking Free", "Healing Bonus +15% while HP is at or below 50%.", control = toggle(false), requires = A1) {
            stat(Stat.HEALING_BONUS, 0.15)
        }
        effect("a4", "Heart's Repose", "Sanctifying Ring DMG +25% of EM.", phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.25 * targetStats.em, HitFilter.hits("skill/grass-ring-of-sanctification-dmg"))
        }
        effect("c6", "To Ward Weakness", "EM +150 for 15s when HP drops below 25%.", control = toggle(false), requires = cons(6)) {
            stat(Stat.EM, 150.0)
        }
    }

    this("kirara") {
        effect("a4", "Pupillary Variance", "Per 1,000 Max HP: Meow-teor Kick DMG +0.4%, Surprise Dispatch DMG +0.3%.",
            phase = EffectPhase.CONVERSION, requires = A4) {
            val k = targetStats.hp / 1000
            dmgBonus(0.004 * k, HitFilter(talents = setOf(TalentType.SKILL)))
            dmgBonus(0.003 * k, BURST_TALENT)
        }
        effect("c6", "Countless Sights to See", "Party All Elemental DMG Bonus +12% after her skill or burst.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            allElementalDmg(0.12)
        }
    }

    this("chiori") {
        effect("a4", "The Finishing Touch", "Geo DMG Bonus +20% after a party member creates a Geo Construct.", control = toggle(), requires = A4) {
            elementDmg(Element.GEO, 0.20)
        }
        effect("c6", "Sole Principle Pursuit", "Normal Attack DMG increased by 235% of DEF.", phase = EffectPhase.CONVERSION, requires = cons(6)) {
            flatDmg(2.35 * targetStats.def, HitFilter.NORMAL)
        }
    }

    this("yumemizukimizuki") {
        effect(
            "dreamdrifter", "Dreamdrifter", "Party Swirl DMG increased based on Mizuki's EM (per 100 EM).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT,
        ) {
            reactionBonus(Reaction.SWIRL, param(TalentType.SKILL, "param2") * ownerStats.em / 100)
        }
        effect("a4", "Thoughts by Day Bring Dreams by Night", "EM +100 when party members hit with Pyro/Hydro/Cryo/Electro.",
            control = toggle(), requires = A4) {
            stat(Stat.EM, 100.0)
        }
        effect(
            "c2", "Your Echo I Meet in Dreams", "Party Pyro/Hydro/Cryo/Electro DMG +0.04% per point of Mizuki's EM.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(2),
        ) {
            for (e in SWIRLABLE) elementDmg(e, 0.0004 * ownerStats.em)
        }
        effect("c6", "The Heart Lingers Long", "Party Swirl DMG can CRIT (30% CRIT Rate, 100% CRIT DMG).",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            reactionCrit(Reaction.SWIRL, 0.30, 1.0)
        }
    }
}
