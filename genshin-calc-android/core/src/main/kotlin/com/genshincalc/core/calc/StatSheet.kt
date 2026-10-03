package com.genshincalc.core.calc

import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Stat

/** A bag of [Stat] values. Totals (Max HP / ATK / DEF) are derived, not stored. */
class StatSheet(private val values: DoubleArray = DoubleArray(Stat.entries.size)) {

    operator fun get(stat: Stat): Double = values[stat.ordinal]

    operator fun set(stat: Stat, value: Double) {
        values[stat.ordinal] = value
    }

    fun add(stat: Stat, value: Double) {
        values[stat.ordinal] += value
    }

    fun addAll(other: StatSheet) {
        for (i in values.indices) values[i] += other.values[i]
    }

    fun copy(): StatSheet = StatSheet(values.copyOf())

    val hp: Double get() = this[Stat.BASE_HP] * (1 + this[Stat.HP_PCT]) + this[Stat.HP]
    val atk: Double get() = this[Stat.BASE_ATK] * (1 + this[Stat.ATK_PCT]) + this[Stat.ATK]
    val def: Double get() = this[Stat.BASE_DEF] * (1 + this[Stat.DEF_PCT]) + this[Stat.DEF]
    val em: Double get() = this[Stat.EM]
    val er: Double get() = this[Stat.ER]
    val critRate: Double get() = this[Stat.CRIT_RATE]
    val critDmg: Double get() = this[Stat.CRIT_DMG]

    fun dmgBonus(element: Element): Double = this[element.dmgBonusStat]

    fun total(stat: Stat): Double = when (stat) {
        Stat.HP -> hp
        Stat.ATK -> atk
        Stat.DEF -> def
        else -> this[stat]
    }

    override fun toString(): String = Stat.entries.filter { this[it] != 0.0 }.joinToString { "${it.name}=${this[it]}" }

    companion object {
        /** A fresh sheet with the defaults every character has: 5% CR, 50% CD, 100% ER. */
        fun withDefaults(): StatSheet = StatSheet().apply {
            this[Stat.CRIT_RATE] = 0.05
            this[Stat.CRIT_DMG] = 0.5
            this[Stat.ER] = 1.0
        }
    }
}
