package com.genshincalc.core.text

import com.genshincalc.core.calc.Formulas
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.ReactionType

/** Reference text for the Reactions library. */
data class ReactionInfo(
    val reaction: Reaction,
    val elements: List<Element>,
    val summary: String,
    val formula: String,
    val details: List<String>,
)

object ReactionGuide {

    val all: List<ReactionInfo> = listOf(
        ReactionInfo(
            Reaction.VAPORIZE, listOf(Element.PYRO, Element.HYDRO),
            "Amplifying: multiplies the triggering hit. Hydro on Pyro x2, Pyro on Hydro x1.5.",
            "DMG x Multiplier x (1 + 2.78 x EM / (EM + 1400) + Reaction Bonus)",
            listOf(
                "Hydro trigger (reverse): 2.0x. Pyro trigger (forward): 1.5x.",
                "Uses the triggering character's EM. CRIT, DEF and RES apply to the hit as usual.",
                "Crimson Witch of Flames 4pc adds +15% Reaction Bonus.",
            ),
        ),
        ReactionInfo(
            Reaction.MELT, listOf(Element.PYRO, Element.CRYO),
            "Amplifying: Pyro on Cryo x2, Cryo on Pyro x1.5.",
            "DMG x Multiplier x (1 + 2.78 x EM / (EM + 1400) + Reaction Bonus)",
            listOf("Pyro trigger (forward): 2.0x. Cryo trigger (reverse): 1.5x."),
        ),
        ReactionInfo(
            Reaction.AGGRAVATE, listOf(Element.ELECTRO, Element.DENDRO),
            "Additive: Electro hits on Quicken enemies gain flat Base DMG.",
            "Bonus Base DMG = Level Multiplier x 1.15 x (1 + 5 x EM / (EM + 1200) + Reaction Bonus)",
            listOf(
                "The bonus is added before DMG Bonus, CRIT, DEF and RES, so it benefits from all of them.",
                "Quicken (Dendro + Electro) must be applied first.",
            ),
        ),
        ReactionInfo(
            Reaction.SPREAD, listOf(Element.DENDRO, Element.ELECTRO),
            "Additive: Dendro hits on Quicken enemies gain flat Base DMG.",
            "Bonus Base DMG = Level Multiplier x 1.25 x (1 + 5 x EM / (EM + 1200) + Reaction Bonus)",
            listOf("Like Aggravate but with a 1.25 multiplier."),
        ),
        trans(Reaction.OVERLOADED, listOf(Element.PYRO, Element.ELECTRO), "Pyro + Electro explosion. Deals Pyro DMG."),
        trans(Reaction.SUPERCONDUCT, listOf(Element.CRYO, Element.ELECTRO), "Cryo DMG and -40% Physical RES for 12s."),
        trans(Reaction.ELECTRO_CHARGED, listOf(Element.HYDRO, Element.ELECTRO), "Electro DMG over time while both auras remain."),
        trans(Reaction.SWIRL, listOf(Element.ANEMO), "Anemo spreads Pyro/Hydro/Electro/Cryo; DMG is of the swirled element and uses its RES."),
        trans(Reaction.SHATTERED, listOf(Element.GEO, Element.PHYSICAL), "Heavy attacks (claymores, Geo) on Frozen enemies. Physical DMG."),
        trans(Reaction.BURNING, listOf(Element.PYRO, Element.DENDRO), "Pyro DMG ticks (0.25x per tick). Can CRIT with Nahida C2."),
        trans(Reaction.BLOOM, listOf(Element.DENDRO, Element.HYDRO), "Creates Dendro Cores that burst for Dendro DMG. Can CRIT with Nahida C2."),
        trans(Reaction.HYPERBLOOM, listOf(Element.ELECTRO, Element.DENDRO), "Electro hits a Dendro Core: seeking Dendro projectile (3x)."),
        trans(Reaction.BURGEON, listOf(Element.PYRO, Element.DENDRO), "Pyro hits a Dendro Core: AoE Dendro DMG (3x)."),
        ReactionInfo(
            Reaction.LUNAR_CHARGED, listOf(Element.HYDRO, Element.ELECTRO),
            "Moonsign teams (Nod-Krai): Electro-Charged becomes Lunar-Charged. It ignores DEF and can CRIT.",
            "3 x Level Multiplier x (1 + 6 x EM / (EM + 2000) + Bonus) x (1 + Base DMG Bonus) x CRIT x RES",
            listOf(
                "Talents that deal Lunar-Charged DMG directly use: Talent% x Stat x 3 x (1 + 6 x EM / (EM + 2000) + Bonus).",
                "When several characters contribute, the game sums the highest contribution, half of the second and 1/12 of the rest.",
            ),
        ),
        ReactionInfo(
            Reaction.LUNAR_BLOOM, listOf(Element.DENDRO, Element.HYDRO),
            "Moonsign teams: Bloom becomes Lunar-Bloom; damage comes from talents that deal Lunar-Bloom DMG.",
            "Talent% x Stat x (1 + 6 x EM / (EM + 2000) + Bonus) x (1 + Base DMG Bonus) x CRIT x RES",
            listOf("Ignores DEF and can CRIT."),
        ),
        ReactionInfo(
            Reaction.LUNAR_CRYSTALLIZE, listOf(Element.GEO, Element.HYDRO),
            "Moonsign teams: Hydro + Geo creates Moondrifts that deal Geo DMG.",
            "1.6 x Level Multiplier x (1 + 6 x EM / (EM + 2000) + Bonus) x (1 + Base DMG Bonus) x CRIT x RES",
            listOf("Ignores DEF and can CRIT."),
        ),
        ReactionInfo(
            Reaction.CRYSTALLIZE, listOf(Element.GEO),
            "Geo + Pyro/Hydro/Electro/Cryo: a shard that grants a shield of the absorbed element.",
            "Shield HP = Shield Level Multiplier x (1 + 4.44 x EM / (EM + 1400))",
            listOf("The shield absorbs 250% damage of its own element and 100% of other types."),
        ),
    )

    private fun trans(r: Reaction, elements: List<Element>, summary: String) = ReactionInfo(
        r, elements, summary,
        "Level Multiplier x ${Format.trim(Formulas.transformativeMultiplier(r))} x (1 + 16 x EM / (EM + 2000) + Reaction Bonus) x RES",
        listOf(
            "Transformative: ignores DEF and DMG Bonus. Scales only with character level, EM and reaction bonuses.",
            "Reaction multiplier: ${Format.trim(Formulas.transformativeMultiplier(r))}" +
                if (r == Reaction.ELECTRO_CHARGED || r == Reaction.BURNING) " (per tick)." else ".",
        ),
    )

    fun info(reaction: Reaction): ReactionInfo? = all.firstOrNull { it.reaction == reaction }

    /** Example values at a level for a list of EM values (no bonuses, 10% RES). */
    fun examples(reaction: Reaction, level: Int, emValues: List<Double> = listOf(0.0, 100.0, 200.0, 500.0, 1000.0)): List<Pair<Double, Double>> =
        emValues.map { em ->
            val value = when (reaction.type) {
                ReactionType.TRANSFORMATIVE ->
                    Formulas.levelMultiplier(level) * Formulas.transformativeMultiplier(reaction) *
                        (1 + Formulas.transformativeEmBonus(em)) * Formulas.resMultiplier(0.1)
                ReactionType.ADDITIVE ->
                    Formulas.levelMultiplier(level) * (if (reaction == Reaction.AGGRAVATE) 1.15 else 1.25) * (1 + Formulas.additiveEmBonus(em))
                ReactionType.AMPLIFYING ->
                    (if (reaction == Reaction.VAPORIZE) 2.0 else 2.0) * (1 + Formulas.amplifyingEmBonus(em))
                ReactionType.LUNAR ->
                    Formulas.levelMultiplier(level) * Formulas.transformativeMultiplier(reaction) *
                        (1 + Formulas.lunarEmBonus(em)) * Formulas.resMultiplier(0.1)
                ReactionType.SHIELD -> Formulas.crystallizeLevelMultiplier(level) * (1 + Formulas.crystallizeEmBonus(em))
            }
            em to value
        }
}
