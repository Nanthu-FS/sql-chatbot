package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.model.Stat

internal fun EffectTable.polearmEffects() {
    this("staffofhoma") {
        effect("hp", "Reckless Cinnabar", "HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("atk", "Reckless Cinnabar (ATK from HP)", "ATK Bonus based on Max HP.",
            static = true, phase = EffectPhase.CONVERSION) { stat(Stat.ATK, r(1) * targetStats.hp) }
        effect("lowhp", "Reckless Cinnabar (HP < 50%)", "Additional ATK Bonus based on Max HP while HP is below 50%.",
            control = toggle(), phase = EffectPhase.CONVERSION) { stat(Stat.ATK, r(2) * targetStats.hp) }
    }
}
