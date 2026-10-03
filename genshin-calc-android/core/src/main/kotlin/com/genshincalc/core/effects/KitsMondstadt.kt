package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

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
}
