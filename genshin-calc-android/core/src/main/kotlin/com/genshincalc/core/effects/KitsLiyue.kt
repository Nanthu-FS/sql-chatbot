package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.Element
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
}
