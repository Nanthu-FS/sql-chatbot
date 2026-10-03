package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat

/** Requirement: at least [n] pieces of the set being defined. */
private fun EffectListBuilder.pc(setId: String, n: Int) = Req("${n}pc") { it.setPieces(setId) >= n }

private fun EffectTable.set(id: String, block: EffectListBuilder.(two: Req, four: Req) -> Unit) {
    this(id) { block(pc(id, 2), pc(id, 4)) }
}

internal fun EffectTable.artifactSetEffects() {
    set("crimsonwitchofflames") { two, four ->
        effect("2", "Crimson Witch 2pc", "Pyro DMG Bonus +15%.", static = true, requires = two) { elementDmg(Element.PYRO, 0.15) }
        effect("4", "Crimson Witch 4pc", "Overloaded/Burning/Burgeon DMG +40%, Vaporize/Melt DMG +15%.", requires = four) {
            reactionBonus(Reaction.OVERLOADED, 0.40); reactionBonus(Reaction.BURNING, 0.40); reactionBonus(Reaction.BURGEON, 0.40)
            reactionBonus(Reaction.VAPORIZE, 0.15); reactionBonus(Reaction.MELT, 0.15)
        }
        effect("4s", "Crimson Witch 4pc stacks", "Each Elemental Skill use: 2pc bonus +50% (max 3 stacks).",
            control = stacks(3, 1), requires = four) { elementDmg(Element.PYRO, 0.075 * stacks) }
    }
    set("viridescentvenerer") { two, four ->
        effect("2", "Viridescent Venerer 2pc", "Anemo DMG Bonus +15%.", static = true, requires = two) { elementDmg(Element.ANEMO, 0.15) }
        effect("4", "Viridescent Venerer 4pc", "Swirl DMG +60%.", requires = four) { reactionBonus(Reaction.SWIRL, 0.60) }
        effect("4shred", "Viridescent Venerer 4pc shred", "Swirl lowers the enemy's RES to the swirled element by 40%.",
            target = EffectTarget.TEAM, control = SWIRL_CHOICE, requires = four) {
            for (e in swirlElements()) resShred(e, 0.40)
        }
    }
    set("noblesseoblige") { two, four ->
        effect("2", "Noblesse Oblige 2pc", "Elemental Burst DMG +20%.", requires = two) { dmgBonus(0.20, HitFilter.BURST) }
        effect("4", "Noblesse Oblige 4pc", "Party ATK +20% for 12s after using an Elemental Burst.",
            target = EffectTarget.TEAM, control = toggle(), requires = four) { stat(Stat.ATK_PCT, 0.20) }
    }
    set("emblemofseveredfate") { two, four ->
        effect("2", "Emblem of Severed Fate 2pc", "Energy Recharge +20%.", static = true, requires = two) { stat(Stat.ER, 0.20) }
        effect("4", "Emblem of Severed Fate 4pc", "Elemental Burst DMG +25% of Energy Recharge (max 75%).",
            phase = EffectPhase.CONVERSION, requires = four) {
            dmgBonus(minOf(0.25 * targetStats.er, 0.75), HitFilter.BURST)
        }
    }
    set("gladiatorsfinale") { two, four ->
        effect("2", "Gladiator's Finale 2pc", "ATK +18%.", static = true, requires = two) { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Gladiator's Finale 4pc", "Normal Attack DMG +35% for Sword, Claymore and Polearm users.", requires = four) {
            if (target.weaponType.isMelee) dmgBonus(0.35, HitFilter.NORMAL)
        }
    }
}
