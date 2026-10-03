package com.genshincalc.core.calc

import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.EnemyConfig
import com.genshincalc.core.model.HitData
import com.genshincalc.core.model.HitKind
import com.genshincalc.core.model.HitPart
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.ScalingStat
import com.genshincalc.core.model.SpecialDamage
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.WeaponType
import java.util.Locale

/** Turns a member's final stats and modifiers into numbers for each talent hit and reaction. */
internal class HitCalculator(
    private val enemyConfig: EnemyConfig,
    private val baseRes: Map<Element, Double>,
    private val enemy: EnemyModifiers,
) {
    fun enemyRes(element: Element): Double = (baseRes[element] ?: EnemyConfig.DEFAULT_RES) - (enemy.resShred[element] ?: 0.0)

    /** Element that infusions turn a "physical unless infused" attack of [category] into. */
    fun infusionFor(m: MemberState, category: AttackCategory): Element? =
        m.mods.infusions
            .filter { category in it.categories }
            .maxByOrNull { it.priority.ordinal }
            ?.element

    fun hits(m: MemberState): List<HitResult> = m.character.hits
        .filter { (it.constellation ?: 0) <= m.constellation && (it.ascension ?: 0) <= m.ascension }
        .map { hit(m, it) }

    private fun resolveElement(m: MemberState, hit: HitData, part: HitPart): Element {
        m.mods.elementOverrides[hit.id]?.let { return it }
        part.element?.let { return it }
        hit.element?.let { return it }
        // Physical-by-default attack: infusions may convert it. Bows only take their own infusions
        // (party infusions are filtered when they are added).
        return infusionFor(m, hit.category) ?: Element.PHYSICAL
    }

    private fun statValue(stats: StatSheet, stat: ScalingStat): Double = when (stat) {
        ScalingStat.ATK -> stats.atk
        ScalingStat.HP -> stats.hp
        ScalingStat.DEF -> stats.def
        ScalingStat.EM -> stats.em
    }

    private fun paramValue(m: MemberState, hit: HitData, param: String): Double = m.talentParam(hit.talent, param)

    fun hit(m: MemberState, hit: HitData): HitResult {
        val stats = m.final
        val level = m.talentLevel(hit.talent)
        val scaling = scalingText(m, hit)
        val firstElement = resolveElement(m, hit, hit.parts.first())
        when (hit.kind) {
            HitKind.HEAL -> {
                var amount = 0.0
                for (p in hit.parts) {
                    val base = p.terms.sumOf { paramValue(m, hit, it.param) * statValue(stats, it.stat) } +
                        (p.flat?.let { paramValue(m, hit, it) } ?: 0.0)
                    amount += base * p.count
                }
                amount *= 1 + stats[Stat.HEALING_BONUS]
                return HitResult(hit, hit.talent, level, hit.kind, hit.category, firstElement, null, scaling,
                    DamageNumbers.flat(amount), emptyList(), null)
            }
            HitKind.SHIELD -> {
                var amount = 0.0
                for (p in hit.parts) {
                    val base = p.terms.sumOf { paramValue(m, hit, it.param) * statValue(stats, it.stat) } +
                        (p.flat?.let { paramValue(m, hit, it) } ?: 0.0)
                    amount += base * p.count
                }
                amount *= 1 + stats[Stat.SHIELD_STRENGTH]
                return HitResult(hit, hit.talent, level, hit.kind, hit.category, m.element, null, scaling,
                    DamageNumbers.flat(amount), emptyList(), null)
            }
            HitKind.DMG -> Unit
        }

        var total = DamageNumbers.ZERO
        val reactionTotals = linkedMapOf<Reaction, DamageNumbers>()
        var breakdown: HitBreakdown? = null
        val special = hit.special
        for (part in hit.parts) {
            val element = resolveElement(m, hit, part)
            val ctx = HitContext(hit.id, hit.category, element, hit.talent)
            val talentPart = part.terms.sumOf { paramValue(m, hit, it.param) * statValue(stats, it.stat) }
            val numbers: DamageNumbers
            val variants: Map<Reaction, DamageNumbers>
            if (special != null) {
                numbers = specialDirect(m, ctx, talentPart, special)
                variants = emptyMap()
            } else {
                val calc = normalHit(m, ctx, talentPart)
                numbers = calc.first
                variants = calc.second
                if (breakdown == null) breakdown = calc.third.copy(talentMultiplier = scaling)
            }
            total += numbers * part.count.toDouble()
            // Reaction variants: parts that can't react contribute their plain damage.
            for (reaction in reactionsFor(element)) {
                val v = variants[reaction] ?: numbers
                reactionTotals[reaction] = (reactionTotals[reaction] ?: DamageNumbers.ZERO) + v * part.count.toDouble()
            }
        }
        // Keep only reactions that at least one part can trigger.
        val partElements = hit.parts.map { resolveElement(m, hit, it) }.toSet()
        val reactions = reactionTotals.filterKeys { r -> special == null && partElements.any { r in reactionsFor(it) } }
            .map { (r, n) -> ReactionDamage(r, n) }
        return HitResult(hit, hit.talent, level, hit.kind, hit.category, firstElement, special, scaling, total, reactions, breakdown)
    }

    private fun reactionsFor(element: Element): List<Reaction> = when (element) {
        Element.PYRO -> listOf(Reaction.VAPORIZE, Reaction.MELT)
        Element.HYDRO -> listOf(Reaction.VAPORIZE)
        Element.CRYO -> listOf(Reaction.MELT)
        Element.ELECTRO -> listOf(Reaction.AGGRAVATE)
        Element.DENDRO -> listOf(Reaction.SPREAD)
        else -> emptyList()
    }

    /** Regular damage formula; returns (no reaction, reaction variants, breakdown). */
    private fun normalHit(m: MemberState, ctx: HitContext, talentPart: Double): Triple<DamageNumbers, Map<Reaction, DamageNumbers>, HitBreakdown> {
        val stats = m.final
        val mods = m.mods
        var multiplier = 1.0
        mods.hitMods(HitModKind.MULTIPLIER, ctx).forEach { multiplier *= it.value }
        val flat = mods.hitMods(HitModKind.FLAT_DMG, ctx).sumOf { it.value }
        val base = talentPart * multiplier + flat
        val dmgBonus = stats[Stat.ALL_DMG] + stats.dmgBonus(ctx.element) + mods.hitMods(HitModKind.DMG_BONUS, ctx).sumOf { it.value }
        val critRate = stats.critRate + mods.hitMods(HitModKind.CRIT_RATE, ctx).sumOf { it.value }
        val critDmg = stats.critDmg + mods.hitMods(HitModKind.CRIT_DMG, ctx).sumOf { it.value }
        val defIgnore = mods.hitMods(HitModKind.DEF_IGNORE, ctx).sumOf { it.value }
        val defMult = Formulas.defMultiplier(m.level, enemyConfig.level, enemy.defReduction, defIgnore)
        val res = enemyRes(ctx.element)
        val resMult = Formulas.resMultiplier(res)

        fun numbers(baseDmg: Double, amp: Double): DamageNumbers {
            val nonCrit = baseDmg * (1 + dmgBonus) * defMult * resMult * amp
            return DamageNumbers(
                nonCrit = nonCrit,
                crit = nonCrit * (1 + critDmg),
                average = nonCrit * Formulas.averageCritMultiplier(critRate, critDmg),
            )
        }

        val plain = numbers(base, 1.0)
        val variants = mutableMapOf<Reaction, DamageNumbers>()
        for (reaction in reactionsFor(ctx.element)) {
            Formulas.amplifyingMultiplier(reaction, ctx.element)?.let { mult ->
                val amp = mult * (1 + Formulas.amplifyingEmBonus(stats.em) + (mods.reactionBonus[reaction] ?: 0.0))
                variants[reaction] = numbers(base, amp)
            }
            Formulas.additiveMultiplier(reaction, ctx.element)?.let { mult ->
                val add = Formulas.levelMultiplier(m.level) * mult *
                    (1 + Formulas.additiveEmBonus(stats.em) + (mods.reactionBonus[reaction] ?: 0.0))
                variants[reaction] = numbers(base + add, 1.0)
            }
        }
        val breakdown = HitBreakdown("", talentPart * multiplier, flat, dmgBonus, critRate, critDmg, defMult, res, resMult)
        return Triple(plain, variants, breakdown)
    }

    /** Lunar/Stellar damage dealt directly by a talent ("This DMG is considered Lunar-Charged DMG"). */
    private fun specialDirect(m: MemberState, ctx: HitContext, talentPart: Double, special: SpecialDamage): DamageNumbers {
        val stats = m.final
        val mods = m.mods
        val reaction = special.reaction
        val resElement = reaction?.let { Formulas.transformativeResElement(it) } ?: ctx.element
        val reactionMult = reaction?.let { Formulas.lunarDirectMultiplier(it) } ?: 1.0
        val bonus = reaction?.let { mods.reactionBonus[it] } ?: 0.0
        val baseBonus = reaction?.let { mods.lunarBase[it] } ?: 0.0
        val elevate = reaction?.let { mods.lunarElevate[it] } ?: 0.0
        val flat = reaction?.let { mods.reactionFlat[it] } ?: 0.0
        val base = talentPart * reactionMult * (1 + Formulas.lunarEmBonus(stats.em) + bonus) * (1 + baseBonus) + flat
        val critRate = stats.critRate + (reaction?.let { mods.reactionCritRate[it] } ?: 0.0)
        val critDmg = stats.critDmg + (reaction?.let { mods.reactionCritDmg[it] } ?: 0.0)
        val nonCrit = base * (1 + elevate) * Formulas.resMultiplier(enemyRes(resElement))
        return DamageNumbers(nonCrit, nonCrit * (1 + critDmg), nonCrit * Formulas.averageCritMultiplier(critRate, critDmg))
    }

    /** Reaction damage this member deals when it triggers each reaction its element allows. */
    fun transformative(m: MemberState): List<TransformativeResult> {
        val stats = m.final
        val mods = m.mods
        val out = mutableListOf<TransformativeResult>()
        val levelMult = Formulas.levelMultiplier(m.level)

        fun crittable(r: Reaction) = (mods.reactionCritRate[r] ?: 0.0) > 0 || (mods.reactionCritDmg[r] ?: 0.0) > 0

        fun trans(reaction: Reaction, resElement: Element = Formulas.transformativeResElement(reaction), note: String? = null) {
            val base = levelMult * Formulas.transformativeMultiplier(reaction) *
                (1 + Formulas.transformativeEmBonus(stats.em) + (mods.reactionBonus[reaction] ?: 0.0)) +
                (mods.reactionFlat[reaction] ?: 0.0)
            val nonCrit = base * Formulas.resMultiplier(enemyRes(resElement))
            val cr = mods.reactionCritRate[reaction] ?: 0.0
            val cd = mods.reactionCritDmg[reaction] ?: 0.0
            out += TransformativeResult(
                reaction, resElement,
                DamageNumbers(nonCrit, nonCrit * (1 + cd), nonCrit * Formulas.averageCritMultiplier(cr, cd)),
                canCrit = crittable(reaction), note = note,
            )
        }

        fun lunar(reaction: Reaction, note: String) {
            val base = Formulas.transformativeMultiplier(reaction) * levelMult *
                (1 + Formulas.lunarEmBonus(stats.em) + (mods.reactionBonus[reaction] ?: 0.0)) *
                (1 + (mods.lunarBase[reaction] ?: 0.0)) + (mods.reactionFlat[reaction] ?: 0.0)
            val resElement = Formulas.transformativeResElement(reaction)
            val nonCrit = base * (1 + (mods.lunarElevate[reaction] ?: 0.0)) * Formulas.resMultiplier(enemyRes(resElement))
            val cr = stats.critRate + (mods.reactionCritRate[reaction] ?: 0.0)
            val cd = stats.critDmg + (mods.reactionCritDmg[reaction] ?: 0.0)
            out += TransformativeResult(
                reaction, resElement,
                DamageNumbers(nonCrit, nonCrit * (1 + cd), nonCrit * Formulas.averageCritMultiplier(cr, cd)),
                canCrit = true, note = note,
            )
        }

        val moonsign = "Needs a Moonsign party (Nod-Krai characters)"
        when (m.element) {
            Element.PYRO -> {
                trans(Reaction.OVERLOADED); trans(Reaction.BURNING, note = "Per tick"); trans(Reaction.BURGEON)
            }
            Element.HYDRO -> {
                trans(Reaction.ELECTRO_CHARGED, note = "Per tick"); trans(Reaction.BLOOM)
                lunar(Reaction.LUNAR_CHARGED, moonsign); lunar(Reaction.LUNAR_CRYSTALLIZE, moonsign)
            }
            Element.ELECTRO -> {
                trans(Reaction.OVERLOADED); trans(Reaction.ELECTRO_CHARGED, note = "Per tick")
                trans(Reaction.SUPERCONDUCT); trans(Reaction.HYPERBLOOM)
                lunar(Reaction.LUNAR_CHARGED, moonsign)
            }
            Element.CRYO -> trans(Reaction.SUPERCONDUCT)
            Element.DENDRO -> {
                trans(Reaction.BLOOM); trans(Reaction.BURNING, note = "Per tick")
            }
            Element.ANEMO -> for (e in listOf(Element.PYRO, Element.HYDRO, Element.ELECTRO, Element.CRYO)) {
                trans(Reaction.SWIRL, e, note = "${e.display} Swirl")
            }
            Element.GEO -> {
                val shield = Formulas.crystallizeLevelMultiplier(m.level) * (1 + Formulas.crystallizeEmBonus(stats.em)) *
                    (1 + (mods.reactionBonus[Reaction.CRYSTALLIZE] ?: 0.0))
                out += TransformativeResult(Reaction.CRYSTALLIZE, null, DamageNumbers.flat(shield), false,
                    "Shield HP (absorbs 250% vs. its element)")
                lunar(Reaction.LUNAR_CRYSTALLIZE, moonsign)
            }
            Element.PHYSICAL -> Unit
        }
        if (m.element == Element.GEO || m.weaponType == WeaponType.CLAYMORE) trans(Reaction.SHATTERED)
        return out
    }

    private fun scalingText(m: MemberState, hit: HitData): String {
        fun pct(v: Double): String = String.format(Locale.US, "%.2f", v * 100).trimEnd('0').trimEnd('.') + "%"
        fun part(p: HitPart): String {
            val terms = p.terms.map { "${pct(m.talentParam(hit.talent, it.param))} ${it.stat.display}" }.toMutableList()
            p.flat?.let { terms += String.format(Locale.US, "%,.0f", m.talentParam(hit.talent, it)) }
            val body = if (terms.size > 1) terms.joinToString(" + ", "(", ")") else terms.joinToString()
            return if (p.count > 1) "$body ×${p.count}" else body
        }
        return hit.parts.joinToString(" + ") { part(it) }
    }
}
