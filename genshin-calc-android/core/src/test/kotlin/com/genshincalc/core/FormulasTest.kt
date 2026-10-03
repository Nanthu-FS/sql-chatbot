package com.genshincalc.core

import com.genshincalc.core.calc.Formulas
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormulasTest {
    private val eps = 1e-9

    @Test
    fun defMultiplier() {
        // Same level: exactly 50%.
        assertEquals(0.5, Formulas.defMultiplier(90, 90), eps)
        // Lv90 attacker vs Lv100 enemy: 190 / (190 + 200).
        assertEquals(190.0 / 390.0, Formulas.defMultiplier(90, 100), eps)
        // 30% DEF reduction and 60% DEF ignore multiply.
        assertEquals(190.0 / (190.0 + 190.0 * 0.7 * 0.4), Formulas.defMultiplier(90, 90, 0.3, 0.6), eps)
        // DEF reduction is capped at 90%.
        assertEquals(Formulas.defMultiplier(90, 90, 0.9), Formulas.defMultiplier(90, 90, 1.5), eps)
        // Never above 1.
        assertEquals(1.0, Formulas.defMultiplier(90, 90, 0.0, 1.0), eps)
    }

    @Test
    fun resMultiplier() {
        assertEquals(0.9, Formulas.resMultiplier(0.10), eps)
        assertEquals(1.0, Formulas.resMultiplier(0.0), eps)
        // Negative RES is halved.
        assertEquals(1.15, Formulas.resMultiplier(-0.30), eps)
        // RES at or above 75%: 1 / (4 x RES + 1).
        assertEquals(1.0 / 4.0, Formulas.resMultiplier(0.75), eps)
        assertEquals(1.0 / (4 * 0.8 + 1), Formulas.resMultiplier(0.80), eps)
    }

    @Test
    fun emBonuses() {
        assertEquals(0.0, Formulas.amplifyingEmBonus(0.0), eps)
        assertEquals(2.78 * 100 / 1500, Formulas.amplifyingEmBonus(100.0), 0.0002)
        assertEquals(25.0 / 9.0 * 1000 / 2400, Formulas.amplifyingEmBonus(1000.0), eps)
        assertEquals(5.0 * 200 / 1400, Formulas.additiveEmBonus(200.0), eps)
        assertEquals(16.0 * 1000 / 3000, Formulas.transformativeEmBonus(1000.0), eps)
        assertEquals(6.0 * 500 / 2500, Formulas.lunarEmBonus(500.0), eps)
    }

    @Test
    fun reactionMultipliers() {
        assertEquals(2.0, Formulas.amplifyingMultiplier(Reaction.VAPORIZE, Element.HYDRO))
        assertEquals(1.5, Formulas.amplifyingMultiplier(Reaction.VAPORIZE, Element.PYRO))
        assertEquals(2.0, Formulas.amplifyingMultiplier(Reaction.MELT, Element.PYRO))
        assertEquals(1.5, Formulas.amplifyingMultiplier(Reaction.MELT, Element.CRYO))
        assertNull(Formulas.amplifyingMultiplier(Reaction.MELT, Element.HYDRO))
        assertEquals(1.15, Formulas.additiveMultiplier(Reaction.AGGRAVATE, Element.ELECTRO))
        assertEquals(1.25, Formulas.additiveMultiplier(Reaction.SPREAD, Element.DENDRO))
        assertNull(Formulas.additiveMultiplier(Reaction.SPREAD, Element.ELECTRO))
    }

    @Test
    fun levelMultipliers() {
        assertEquals(1446.8535, Formulas.levelMultiplier(90), eps)
        assertEquals(1077.4437, Formulas.levelMultiplier(80), eps)
        assertEquals(17.165606, Formulas.levelMultiplier(1), eps)
        assertEquals(1674.8092, Formulas.levelMultiplier(100), eps)
        // Level 90 Swirl without EM: 1446.85 x 0.6 = 868.1.
        assertEquals(868.1121, Formulas.levelMultiplier(90) * Formulas.transformativeMultiplier(Reaction.SWIRL), 1e-3)
        // Level 90 Aggravate bonus without EM: 1446.85 x 1.15 = 1663.88.
        assertEquals(1663.8815, Formulas.levelMultiplier(90) * 1.15, 1e-3)
        assertEquals(1851.0603, Formulas.crystallizeLevelMultiplier(90), eps)
    }

    @Test
    fun averageCrit() {
        assertEquals(1 + 0.6 * 1.2, Formulas.averageCritMultiplier(0.6, 1.2), eps)
        // CRIT Rate is capped at 100% for the average.
        assertEquals(2.0, Formulas.averageCritMultiplier(1.4, 1.0), eps)
        assertEquals(1.0, Formulas.averageCritMultiplier(-0.2, 1.0), eps)
    }
}
