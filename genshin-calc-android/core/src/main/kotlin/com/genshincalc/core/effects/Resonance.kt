package com.genshincalc.core.effects

import com.genshincalc.core.calc.Effect
import com.genshincalc.core.calc.EffectControl
import com.genshincalc.core.calc.EffectSource
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Stat

/** Elemental resonance: needs a full party of 4 with two characters of the element. */
internal fun resonanceEffects(): List<Effect> {
    fun res(
        key: String, name: String, description: String, element: Element,
        control: EffectControl = EffectControl.Always,
        apply: com.genshincalc.core.calc.EffectScope.() -> Unit,
    ) = Effect(
        id = "resonance.$key", name = name, description = description, source = EffectSource.RESONANCE,
        target = EffectTarget.TEAM, control = control,
        requirement = { it.team.size == 4 && it.team.count(element) >= 2 },
        requirementLabel = "2 ${element.display}",
        apply = apply,
    )
    return listOf(
        res("pyro", "Fervent Flames", "ATK +25%.", Element.PYRO) { stat(Stat.ATK_PCT, 0.25) },
        res("hydro", "Soothing Water", "Max HP +25%.", Element.HYDRO) { stat(Stat.HP_PCT, 0.25) },
        res("cryo", "Shattering Ice", "CRIT Rate +15% against enemies that are Frozen or affected by Cryo.",
            Element.CRYO, EffectControl.Toggle(true)) { stat(Stat.CRIT_RATE, 0.15) },
        res("geo", "Enduring Rock", "Shield Strength +15%. While shielded: DMG +15%, and hits lower the enemy's Geo RES by 20%.",
            Element.GEO, EffectControl.Toggle(true)) {
            stat(Stat.SHIELD_STRENGTH, 0.15)
            stat(Stat.ALL_DMG, 0.15)
            resShred(Element.GEO, 0.20)
        },
        res("dendro", "Sprawling Greenery", "Elemental Mastery +50.", Element.DENDRO) { stat(Stat.EM, 50.0) },
        res("dendro2", "Sprawling Greenery (Burning/Quicken/Bloom)", "EM +30 for 6s after triggering Burning, Quicken or Bloom.",
            Element.DENDRO, EffectControl.Toggle(true)) { stat(Stat.EM, 30.0) },
        res("dendro3", "Sprawling Greenery (Aggravate/Spread/Hyperbloom/Burgeon)", "EM +20 for 6s after triggering Aggravate, Spread, Hyperbloom or Burgeon.",
            Element.DENDRO, EffectControl.Toggle(true)) { stat(Stat.EM, 20.0) },
    )
}
