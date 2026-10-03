package com.genshincalc.core.effects

import com.genshincalc.core.calc.Effect
import com.genshincalc.core.calc.EffectControl
import com.genshincalc.core.calc.EffectOwner
import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectScope
import com.genshincalc.core.calc.EffectSource
import com.genshincalc.core.calc.EffectTarget

/** Requirement of an effect: ascension passive, constellation, set pieces... */
class Req(val label: String?, val check: (EffectOwner) -> Boolean) {
    infix fun and(other: Req) = Req(listOfNotNull(label, other.label).joinToString(" ").ifEmpty { null }) {
        check(it) && other.check(it)
    }

    companion object {
        val NONE = Req(null) { true }
    }
}

/** First ascension passive (unlocked at ascension phase 1). */
val A1 = Req("A1") { it.ascension >= 1 }

/** Second ascension passive (unlocked at ascension phase 4). */
val A4 = Req("A4") { it.ascension >= 4 }

fun cons(n: Int) = Req("C$n") { it.constellation >= n }

val ALWAYS = EffectControl.Always
fun toggle(default: Boolean = true) = EffectControl.Toggle(default)
fun stacks(max: Int, default: Int = max, label: String = "Stacks", step: Int = 1) = EffectControl.Stacks(max, default, label, step)
fun choice(vararg options: String, default: Int = 0) = EffectControl.Choice(options.toList(), default)

class EffectListBuilder(private val idPrefix: String, private val source: EffectSource) {
    val effects = mutableListOf<Effect>()

    fun effect(
        key: String,
        name: String,
        description: String,
        target: EffectTarget = EffectTarget.SELF,
        control: EffectControl = ALWAYS,
        phase: EffectPhase = EffectPhase.BASE,
        static: Boolean = false,
        requires: Req = Req.NONE,
        apply: EffectScope.() -> Unit,
    ) {
        effects += Effect(
            id = "$idPrefix.$key",
            name = name,
            description = description,
            source = source,
            target = target,
            control = control,
            phase = phase,
            static = static,
            requirement = requires.check,
            requirementLabel = requires.label,
            apply = apply,
        )
    }
}

/** Collects effects per character/weapon/set id. */
class EffectTable(private val kind: String, private val source: EffectSource) {
    private val table = mutableMapOf<String, MutableList<Effect>>()

    operator fun invoke(id: String, block: EffectListBuilder.() -> Unit) {
        val builder = EffectListBuilder("$kind.$id", source)
        builder.block()
        table.getOrPut(id) { mutableListOf() } += builder.effects
    }

    fun build(): Map<String, List<Effect>> = table
}
