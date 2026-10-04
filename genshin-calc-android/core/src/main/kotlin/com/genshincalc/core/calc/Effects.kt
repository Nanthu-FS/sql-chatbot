package com.genshincalc.core.calc

import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

/** Who receives an effect. */
enum class EffectTarget(val display: String) {
    SELF("Self"),
    TEAM("Party"),
    TEAM_OTHERS("Party (excl. self)"),
}

/** How the user controls an effect. The control value is stored per member as an Int. */
sealed interface EffectControl {
    val defaultValue: Int

    /** Always on (once its requirements are met). */
    data object Always : EffectControl {
        override val defaultValue: Int get() = 1
    }

    data class Toggle(val default: Boolean = true) : EffectControl {
        override val defaultValue: Int get() = if (default) 1 else 0
    }

    data class Stacks(val max: Int, val default: Int = max, val label: String = "Stacks", val step: Int = 1) : EffectControl {
        override val defaultValue: Int get() = default
    }

    data class Choice(val options: List<String>, val default: Int = 0) : EffectControl {
        override val defaultValue: Int get() = default
    }
}

/**
 * When an effect is evaluated. Later phases can read stats produced by earlier ones:
 * - TALENT: talent level changes.
 * - BASE: constant buffs and buffs that scale off the owner's *base* stats (Bennett, Sara...).
 * - TEAM_STAT: buffs scaling off the owner's own stats (Kazuha EM, Shenhe ATK, Yun Jin DEF...).
 * - CONVERSION: buffs scaling off the receiver's final stats (Homa, Hu Tao E, Raiden passive...).
 */
enum class EffectPhase { TALENT, BASE, TEAM_STAT, CONVERSION }

enum class EffectSource(val display: String) {
    CHARACTER("Character"),
    WEAPON("Weapon"),
    ARTIFACT("Artifact set"),
    RESONANCE("Elemental resonance"),
    /** Party-wide mechanics such as Moonsign. */
    PARTY("Party"),
    CUSTOM("Custom"),
}

/** Read-only facts about the member that provides an effect, used for requirements. */
interface EffectOwner {
    val characterId: String
    val element: Element
    val level: Int
    val ascension: Int
    val constellation: Int
    val refinement: Int
    val team: TeamInfo
    fun setPieces(setId: String): Int
}

/** Party composition facts. */
interface TeamInfo {
    val size: Int
    val elements: List<Element>
    val regions: List<String?>
    val characterIds: List<String>
    fun count(element: Element): Int = elements.count { it == element }
    val distinctElements: Int get() = elements.distinct().size
}

class Effect(
    /** Unique per owner, e.g. "char.bennett.burst". */
    val id: String,
    val name: String,
    val description: String,
    val source: EffectSource,
    val target: EffectTarget = EffectTarget.SELF,
    val control: EffectControl = EffectControl.Always,
    val phase: EffectPhase = EffectPhase.BASE,
    /**
     * True if the bonus is already included in the in-game attribute screen (unconditional bonuses
     * to HP/ATK/DEF/EM/CR/CD/ER/Elemental DMG/Healing). Such effects are skipped for members whose
     * stats were typed in manually.
     */
    val static: Boolean = false,
    val requirement: (EffectOwner) -> Boolean = { true },
    /** Short label of the requirement shown in the UI, e.g. "C2" or "A4". */
    val requirementLabel: String? = null,
    val apply: EffectScope.() -> Unit,
)

/** Which hits a hit-modifier applies to. Null fields match everything. */
data class HitFilter(
    val categories: Set<AttackCategory>? = null,
    val elements: Set<Element>? = null,
    val talents: Set<TalentType>? = null,
    val hitIds: Set<String>? = null,
) {
    fun matches(hit: HitContext): Boolean =
        (categories == null || hit.category in categories) &&
            (elements == null || hit.element in elements) &&
            (talents == null || hit.talent in talents) &&
            (hitIds == null || hit.hitId in hitIds)

    companion object {
        val ANY = HitFilter()
        fun of(vararg categories: AttackCategory) = HitFilter(categories = categories.toSet())
        fun element(vararg elements: Element) = HitFilter(elements = elements.toSet())
        fun hits(vararg ids: String) = HitFilter(hitIds = ids.toSet())
        val NORMAL = of(AttackCategory.NORMAL)
        val CHARGED = of(AttackCategory.CHARGED)
        val PLUNGE = of(AttackCategory.PLUNGE)
        val SKILL = of(AttackCategory.SKILL)
        val BURST = of(AttackCategory.BURST)
        val NORMAL_CHARGED = of(AttackCategory.NORMAL, AttackCategory.CHARGED)
        val NORMAL_CHARGED_PLUNGE = of(AttackCategory.NORMAL, AttackCategory.CHARGED, AttackCategory.PLUNGE)
        val SKILL_BURST = of(AttackCategory.SKILL, AttackCategory.BURST)
    }
}

/** The facts about one hit that filters look at. */
data class HitContext(
    val hitId: String,
    val category: AttackCategory,
    val element: Element,
    val talent: TalentType,
)

enum class HitModKind { DMG_BONUS, CRIT_RATE, CRIT_DMG, FLAT_DMG, MULTIPLIER, DEF_IGNORE }

data class HitMod(val kind: HitModKind, val filter: HitFilter, val value: Double, val source: String)

enum class InfusionPriority { OVERRIDABLE, TEAM, NON_OVERRIDABLE }

data class Infusion(
    val element: Element,
    val categories: Set<AttackCategory>,
    val priority: InfusionPriority,
    val source: String,
)

/** Modifiers collected for one member, besides plain stats. */
class Modifiers {
    val stats = StatSheet()
    val hitMods = mutableListOf<HitMod>()
    val infusions = mutableListOf<Infusion>()
    val reactionBonus = mutableMapOf<Reaction, Double>()
    val reactionCritRate = mutableMapOf<Reaction, Double>()
    val reactionCritDmg = mutableMapOf<Reaction, Double>()
    /** Flat DMG added to reaction DMG ("Lunar-Charged DMG increased by x% of EM"). */
    val reactionFlat = mutableMapOf<Reaction, Double>()
    /** Lunar reactions: Base DMG bonus. */
    val lunarBase = mutableMapOf<Reaction, Double>()
    /** Lunar reactions: "elevated" DMG, a separate multiplier. */
    val lunarElevate = mutableMapOf<Reaction, Double>()
    val talentBonus = mutableMapOf<TalentType, Int>()
    /** Hits whose element is replaced (Anemo absorption: "Additional Elemental DMG"). */
    val elementOverrides = mutableMapOf<String, Element>()

    fun hitMods(kind: HitModKind, hit: HitContext): List<HitMod> = hitMods.filter { it.kind == kind && it.filter.matches(hit) }
}

/** Debuffs on the enemy, shared by the whole party. */
class EnemyModifiers {
    val resShred = mutableMapOf<Element, Double>()
    var defReduction = 0.0
    val sources = mutableListOf<String>()

    /** A debuff from a party-wide effect must count once, not once per party member. */
    private val applied = mutableSetOf<String>()

    internal fun once(key: String): Boolean = applied.add(key)
}

/** Toggled/applied effect as shown in the UI. */
data class AppliedEffect(
    val effect: Effect,
    val ownerIndex: Int,
    val value: Int,
    /** Unlocked and switched on. */
    val active: Boolean,
    /** Requirement (ascension, constellation, set pieces...) met. */
    val unlocked: Boolean = true,
)

/** Values exposed to effect bodies. */
class EffectScope internal constructor(
    val effect: Effect,
    val owner: MemberState,
    val target: MemberState,
    /** Toggle (0/1), stack count or choice index. */
    val value: Int,
    /** Owner's stats for the current phase (see [EffectPhase]). */
    val ownerStats: StatSheet,
    /** Receiver's stats for the current phase; for CONVERSION these are its final stats before conversions. */
    val targetStats: StatSheet,
    private val sink: Modifiers,
    private val enemy: EnemyModifiers,
    private val members: List<MemberState>,
) {
    val stacks: Int get() = value
    val on: Boolean get() = value > 0

    val constellation: Int get() = owner.constellation
    val refinement: Int get() = owner.refinement
    val team: TeamInfo get() = owner.team

    val ownerBaseAtk: Double get() = owner.sheet[Stat.BASE_ATK]
    val ownerBaseHp: Double get() = owner.sheet[Stat.BASE_HP]
    val ownerBaseDef: Double get() = owner.sheet[Stat.BASE_DEF]

    /** Weapon passive value [index] at the owner's refinement, e.g. "20%" -> 0.2. */
    fun r(index: Int): Double = owner.weaponRefinementValue(index)

    /** Picks the value for the owner's refinement (R1..R5). */
    fun refine(r1: Double, r2: Double, r3: Double, r4: Double, r5: Double): Double =
        listOf(r1, r2, r3, r4, r5)[(owner.refinement - 1).coerceIn(0, 4)]

    /** Talent scaling value of the owner at its current talent level. */
    fun param(talent: TalentType, param: String): Double = owner.talentParam(talent, param)

    /** Highest value of [selector] across the party members' own stats. */
    fun teamMax(selector: (StatSheet) -> Double): Double = members.maxOf { selector(it.selfStats) }

    /** Highest value of [selector] across party members (use [MemberState.selfStats] for their own stats); 0 if none. */
    fun teamMaxOf(selector: (MemberState) -> Double): Double = members.maxOfOrNull(selector) ?: 0.0

    /** Sum of [selector] over party members. */
    fun teamSumOf(selector: (MemberState) -> Double): Double = members.sumOf(selector)

    val isSelf: Boolean get() = owner === target
    val targetElement: Element get() = target.element
    val targetCharacterId: String get() = target.characterId

    // --- writers -------------------------------------------------------------------------------

    fun stat(stat: Stat, amount: Double) = sink.stats.add(stat, amount)

    fun dmgBonus(amount: Double, filter: HitFilter = HitFilter.ANY) = hitMod(HitModKind.DMG_BONUS, filter, amount)

    fun elementDmg(element: Element, amount: Double) = stat(element.dmgBonusStat, amount)

    fun allElementalDmg(amount: Double) = Element.elemental.forEach { elementDmg(it, amount) }

    fun critRate(amount: Double, filter: HitFilter = HitFilter.ANY) =
        if (filter == HitFilter.ANY) stat(Stat.CRIT_RATE, amount) else hitMod(HitModKind.CRIT_RATE, filter, amount)

    fun critDmg(amount: Double, filter: HitFilter = HitFilter.ANY) =
        if (filter == HitFilter.ANY) stat(Stat.CRIT_DMG, amount) else hitMod(HitModKind.CRIT_DMG, filter, amount)

    /** Flat DMG added to the base DMG of matching hits (Shenhe's quills, Yun Jin, Zhongli A4...). */
    fun flatDmg(amount: Double, filter: HitFilter = HitFilter.ANY) = hitMod(HitModKind.FLAT_DMG, filter, amount)

    /** Multiplies the talent multiplier of matching hits (Yoimiya's Blazing Arrows: x1.638). */
    fun multiplier(factor: Double, filter: HitFilter) = hitMod(HitModKind.MULTIPLIER, filter, factor)

    fun defIgnore(amount: Double, filter: HitFilter = HitFilter.ANY) = hitMod(HitModKind.DEF_IGNORE, filter, amount)

    fun resShred(element: Element, amount: Double) {
        if (!enemy.once("${effect.id}#${owner.index}#res#${element.name}")) return
        enemy.resShred[element] = (enemy.resShred[element] ?: 0.0) + amount
        enemy.sources += effect.name
    }

    fun resShredAll(amount: Double, includePhysical: Boolean = true) =
        (if (includePhysical) Element.entries else Element.elemental).forEach { resShred(it, amount) }

    fun defReduction(amount: Double) {
        if (!enemy.once("${effect.id}#${owner.index}#def")) return
        enemy.defReduction += amount
        enemy.sources += effect.name
    }

    fun reactionBonus(reaction: Reaction, amount: Double) = sink.reactionBonus.merge(reaction, amount, Double::plus)

    fun reactionCrit(reaction: Reaction, rate: Double, dmg: Double) {
        sink.reactionCritRate.merge(reaction, rate, Double::plus)
        sink.reactionCritDmg.merge(reaction, dmg, Double::plus)
    }

    fun reactionFlat(reaction: Reaction, amount: Double) = sink.reactionFlat.merge(reaction, amount, Double::plus)

    fun lunarBaseBonus(reaction: Reaction, amount: Double) = sink.lunarBase.merge(reaction, amount, Double::plus)

    fun lunarElevate(reaction: Reaction, amount: Double) = sink.lunarElevate.merge(reaction, amount, Double::plus)

    fun talentLevel(type: TalentType, delta: Int) = sink.talentBonus.merge(type, delta, Int::plus)

    /** Sets the element of specific hits (e.g. the absorbed element of an Anemo burst). */
    fun convertElement(element: Element, vararg hitIds: String) = hitIds.forEach { sink.elementOverrides[it] = element }

    /**
     * Converts matching attacks to [element]. Party-wide infusions only affect Sword, Claymore and
     * Polearm users, like in game.
     */
    fun infuse(
        element: Element,
        priority: InfusionPriority = InfusionPriority.OVERRIDABLE,
        categories: Set<AttackCategory> = setOf(AttackCategory.NORMAL, AttackCategory.CHARGED, AttackCategory.PLUNGE),
    ) {
        if (priority == InfusionPriority.TEAM && !target.weaponType.isMelee) return
        sink.infusions += Infusion(element, categories, priority, effect.name)
    }

    private fun hitMod(kind: HitModKind, filter: HitFilter, value: Double) {
        sink.hitMods += HitMod(kind, filter, value, effect.name)
    }
}
