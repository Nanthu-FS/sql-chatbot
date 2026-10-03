package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Stat

/** Elements an Anemo character can absorb/swirl. */
internal val SWIRLABLE = listOf(Element.PYRO, Element.HYDRO, Element.ELECTRO, Element.CRYO)

/** "Party elements (auto)" choice + one option per swirlable element. */
internal val SWIRL_CHOICE = choice("Party elements", "Pyro", "Hydro", "Electro", "Cryo")

internal fun com.genshincalc.core.calc.EffectScope.swirlElements(): List<Element> =
    if (value == 0) SWIRLABLE.filter { team.count(it) > 0 } else listOf(SWIRLABLE[value - 1])

internal fun EffectTable.inazumaKits() {
    this("kaedeharakazuha") {
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
}
