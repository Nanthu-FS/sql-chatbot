package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.calc.InfusionPriority
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType

/** Normal Attacks that deal Elemental (non-Physical) DMG. */
private val ELEMENTAL_NORMAL = HitFilter(categories = setOf(AttackCategory.NORMAL), elements = Element.elemental.toSet())

/** Attacks that can trigger Faruzan's Hurricane Guard: Anemo DMG from any talent attack. */
private val ANEMO_ATTACKS = HitFilter(
    categories = setOf(AttackCategory.NORMAL, AttackCategory.CHARGED, AttackCategory.PLUNGE, AttackCategory.SKILL, AttackCategory.BURST),
    elements = setOf(Element.ANEMO),
)

/** Nilou's Golden Chalice needs a party of only Hydro and Dendro characters, with at least one of each. */
private val HYDRO_DENDRO_PARTY = Req("Hydro/Dendro party") { o ->
    o.team.elements.all { it == Element.HYDRO || it == Element.DENDRO } &&
        o.team.count(Element.HYDRO) > 0 && o.team.count(Element.DENDRO) > 0
}

internal fun EffectTable.sumeruKits() {
    this("nahida") {
        val triKarma = HitFilter.hits("skill/tri-karma-purification-dmg", "skill/c6-karmic-oblivion")
        effect(
            "pyro", "Shrine of Maya (Pyro)", "Tri-Karma Purification DMG Bonus by the number of Pyro party members (C1 counts one more).",
            control = toggle(),
        ) {
            val pyro = team.count(Element.PYRO) + if (constellation >= 1) 1 else 0
            val bonus = when {
                pyro >= 2 -> param(TalentType.BURST, "param2")
                pyro == 1 -> param(TalentType.BURST, "param1")
                else -> 0.0
            }
            dmgBonus(bonus, triKarma)
        }
        effect(
            "a1", "Compassion Illuminated", "In the Shrine of Maya, the active character gains 25% of the party's highest EM (max 250).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A1,
        ) {
            stat(Stat.EM, (0.25 * teamMax { it.em }).coerceAtMost(250.0))
        }
        effect(
            "a4", "Awakening Elucidated", "Each EM above 200: Tri-Karma Purification +0.1% DMG (max 80%) and +0.03% CRIT Rate (max 24%).",
            phase = EffectPhase.CONVERSION, requires = A4,
        ) {
            val over = (targetStats.em - 200).coerceAtLeast(0.0)
            dmgBonus((0.001 * over).coerceAtMost(0.80), triKarma)
            critRate((0.0003 * over).coerceAtMost(0.24), triKarma)
        }
        effect(
            "c2", "The Root of All Fullness", "Burning, Bloom, Hyperbloom and Burgeon can CRIT (20% / 100%); Lunar-Bloom CRIT +10% / +20%.",
            target = EffectTarget.TEAM, requires = cons(2),
        ) {
            for (r in listOf(Reaction.BURNING, Reaction.BLOOM, Reaction.HYPERBLOOM, Reaction.BURGEON)) reactionCrit(r, 0.20, 1.00)
            reactionCrit(Reaction.LUNAR_BLOOM, 0.10, 0.20)
        }
        effect("c2def", "The Root of All Fullness (DEF)", "Opponents marked by Seeds of Skandha lose 30% DEF after Quicken, Aggravate or Spread.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2)) {
            defReduction(0.30)
        }
        effect("c4", "The Stem of Manifest Inference", "EM +100/120/140/160 when 1/2/3/4+ opponents are marked by Seeds of Skandha.",
            control = choice("1 opponent", "2 opponents", "3 opponents", "4+ opponents"), requires = cons(4)) {
            stat(Stat.EM, 100.0 + 20 * value)
        }
    }

    this("alhaitham") {
        val projection = HitFilter.hits(
            "skill/1-mirror-projection-attack-dmg", "skill/2-mirror-projection-attack-dmg", "skill/3-mirror-projection-attack-dmg",
            "burst/single-instance-dmg",
        )
        effect("mirror", "Chisel-Light Mirror", "With at least one Chisel-Light Mirror, attacks are infused with Dendro.", control = toggle()) {
            infuse(Element.DENDRO, InfusionPriority.NON_OVERRIDABLE)
        }
        effect("a4", "Mysteries Laid Bare", "Projection Attacks and Fetters of Phenomena DMG +0.1% per point of EM (max 100%).",
            phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus((0.001 * targetStats.em).coerceAtMost(1.0), projection)
        }
        effect("c2", "Debate", "EM +50 per Chisel-Light Mirror generated (max 4 stacks).", control = stacks(4), requires = cons(2)) {
            stat(Stat.EM, 50.0 * stacks)
        }
        effect("c4", "Elucidation", "Each Mirror consumed by the burst: other party members gain 30 EM.",
            target = EffectTarget.TEAM_OTHERS, control = stacks(3, 3, "Mirrors consumed"), requires = cons(4)) {
            stat(Stat.EM, 30.0 * stacks)
        }
        effect("c4b", "Elucidation (Dendro)", "Each Mirror generated by the burst: Alhaitham gains 10% Dendro DMG Bonus.",
            control = stacks(3, 0, "Mirrors generated"), requires = cons(4)) {
            elementDmg(Element.DENDRO, 0.10 * stacks)
        }
        effect("c6", "Structuration", "Generating Mirrors at max count: CRIT Rate +10%, CRIT DMG +70%.", control = toggle(), requires = cons(6)) {
            critRate(0.10)
            critDmg(0.70)
        }
    }

    this("tighnari") {
        effect("a1", "Keen Sight", "EM +50 for 4s after firing a Wreath Arrow.", control = toggle(), requires = A1) {
            stat(Stat.EM, 50.0)
        }
        effect("a4", "Scholarly Blade", "Charged Attack and burst DMG +0.06% per point of EM (max 60%).",
            phase = EffectPhase.CONVERSION, requires = A4) {
            dmgBonus((0.0006 * targetStats.em).coerceAtMost(0.60), HitFilter.of(AttackCategory.CHARGED, AttackCategory.BURST))
        }
        effect("c1", "Beginnings Determined at the Roots", "Charged Attack CRIT Rate +15%.", requires = cons(1)) {
            critRate(0.15, HitFilter.CHARGED)
        }
        effect("c2", "Origins Known From the Stem", "Dendro DMG Bonus +20% while opponents are in the Vijnana-Khanda Field.",
            control = toggle(), requires = cons(2)) {
            elementDmg(Element.DENDRO, 0.20)
        }
        effect("c4", "Withering Glimpsed in the Leaves", "Party EM +60 after the burst, +120 if it triggered a Dendro reaction.",
            target = EffectTarget.TEAM, control = choice("Burst (+60)", "After reaction (+120)", default = 1), requires = cons(4)) {
            stat(Stat.EM, 60.0 * (value + 1))
        }
    }

    this("cyno") {
        val pathclearerNormal = HitFilter(categories = setOf(AttackCategory.NORMAL), talents = setOf(TalentType.BURST))
        val bolts = HitFilter.hits("skill/a1-duststalker-bolt")
        effect("burst", "Pactsworn Pathclearer", "EM bonus while in the Pactsworn Pathclearer state.", control = toggle()) {
            stat(Stat.EM, param(TalentType.BURST, "param12"))
        }
        effect("a1", "Featherfall Judgment", "Judication: Mortuary Rite DMG +35% (also fires 3 Duststalker Bolts).", control = toggle(), requires = A1) {
            dmgBonus(0.35, HitFilter.hits("skill/mortuary-rite-dmg"))
        }
        effect("a4", "Authority Over the Nine Bows", "Pactsworn Normal Attacks +150% of EM; Duststalker Bolts +250% of EM.",
            phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(1.5 * targetStats.em, pathclearerNormal)
            flatDmg(2.5 * targetStats.em, bolts)
        }
        effect("c2", "Ceremony: Homecoming of Spirits", "Electro DMG Bonus +10% per Normal Attack hit (max 5 stacks).",
            control = stacks(5), requires = cons(2)) {
            elementDmg(Element.ELECTRO, 0.10 * stacks)
        }
    }

    this("nilou") {
        effect(
            "a1", "Golden Chalice's Bounty", "Characters hit by Dendro attacks gain 100 EM; Bloom creates Bountiful Cores.",
            target = EffectTarget.TEAM, control = toggle(), requires = A1 and HYDRO_DENDRO_PARTY,
        ) {
            stat(Stat.EM, 100.0)
        }
        effect(
            "a4", "Dreamy Dance of Aeons", "Bountiful Core DMG +9% per 1,000 Max HP above 30,000 (max 400%).",
            target = EffectTarget.TEAM, phase = EffectPhase.TEAM_STAT, requires = A4 and HYDRO_DENDRO_PARTY,
        ) {
            reactionBonus(Reaction.BLOOM, (0.09 * (ownerStats.hp - 30000) / 1000).coerceIn(0.0, 4.0))
        }
        effect("c1", "Dance of the Waning Moon", "Luminous Illusion DMG +65%.", requires = cons(1)) {
            dmgBonus(0.65, HitFilter.hits("skill/luminous-illusion-dmg"))
        }
        effect(
            "c2", "The Starry Skies Their Flowers Rain", "Golden Chalice: Hydro DMG lowers Hydro RES by 35%, Bloom lowers Dendro RES by 35%.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(2) and A1 and HYDRO_DENDRO_PARTY,
        ) {
            resShred(Element.HYDRO, 0.35)
            resShred(Element.DENDRO, 0.35)
        }
        effect("c4", "Fricative Pulse", "Burst DMG +50% for 8s after the third Pirouette step hits.", control = toggle(), requires = cons(4)) {
            dmgBonus(0.50, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c6", "Frostbreaker's Melody", "Per 1,000 Max HP: CRIT Rate +0.6% (max 30%) and CRIT DMG +1.2% (max 60%).",
            static = true, phase = EffectPhase.CONVERSION, requires = cons(6)) {
            val k = targetStats.hp / 1000
            critRate((0.006 * k).coerceAtMost(0.30))
            critDmg((0.012 * k).coerceAtMost(0.60))
        }
    }

    this("wanderer") {
        effect("windfavored", "Windfavored State", "Normal and Charged Attacks become Kuugo: Fushoudan / Toufukai (multiplied DMG).", control = toggle()) {
            multiplier(param(TalentType.SKILL, "param2"), HitFilter.NORMAL)
            multiplier(param(TalentType.SKILL, "param3"), HitFilter.CHARGED)
        }
        effect("a1pyro", "Jade-Claimed Flower (Pyro)", "Skill touched Pyro: ATK +30%.", control = toggle(false), requires = A1) {
            stat(Stat.ATK_PCT, 0.30)
        }
        effect("a1cryo", "Jade-Claimed Flower (Cryo)", "Skill touched Cryo: CRIT Rate +20%.", control = toggle(false), requires = A1) {
            critRate(0.20)
        }
        effect("c1", "Shoban: Ostentatious Plumage", "Gales of Reverie wind arrows deal an additional 25% of ATK.",
            phase = EffectPhase.CONVERSION, requires = cons(1) and A4) {
            flatDmg(0.25 * targetStats.atk, HitFilter.hits("skill/a4-wind-arrow"))
        }
        effect("c2", "Niban: Isle Amidst White Waves", "Burst DMG +4% per Kuugoryoku point spent (max 200%).",
            control = stacks(50, 50, "Points spent", step = 5), requires = cons(2)) {
            dmgBonus(0.04 * stacks, HitFilter.BURST)
        }
        effect("c6", "Shugen: The Curtains' Melancholic Sway", "Kuugo: Fushoudan hits deal an extra 40% of their DMG.",
            control = toggle(), requires = cons(6)) {
            multiplier(1.40, HitFilter.NORMAL)
        }
    }

    this("faruzan") {
        effect("burst", "Prayerful Wind's Benefit", "Party Anemo DMG Bonus.", target = EffectTarget.TEAM, control = toggle()) {
            elementDmg(Element.ANEMO, param(TalentType.BURST, "param2"))
        }
        effect("shred", "Perfidious Wind's Bale", "Opponents' Anemo RES is decreased.", target = EffectTarget.TEAM, control = toggle()) {
            resShred(Element.ANEMO, param(TalentType.BURST, "param4"))
        }
        effect(
            "a4", "Hurricane Guard", "Anemo attacks deal additional DMG equal to 32% of Faruzan's Base ATK.",
            target = EffectTarget.TEAM, control = toggle(), requires = A4,
        ) {
            flatDmg(0.32 * ownerBaseAtk, ANEMO_ATTACKS)
        }
        effect("c6", "The Wondrous Path of Truth", "Anemo CRIT DMG +40% under Prayerful Wind's Benefit.",
            target = EffectTarget.TEAM, control = toggle(), requires = cons(6)) {
            critDmg(0.40, HitFilter.element(Element.ANEMO))
        }
    }

    this("collei") {
        effect("c1", "Deepwood Patrol", "Energy Recharge +20% while off-field.", control = toggle(), requires = cons(1)) {
            stat(Stat.ER, 0.20)
        }
        effect("c4", "Gift of the Woods", "Other party members gain 60 EM for 12s after the burst.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), requires = cons(4)) {
            stat(Stat.EM, 60.0)
        }
    }

    this("candace") {
        effect(
            "burst", "Prayer of the Crimson Crown", "Hydro infusion for melee characters; Elemental Normal Attack DMG bonus.",
            target = EffectTarget.TEAM, control = toggle(),
        ) {
            infuse(Element.HYDRO, InfusionPriority.TEAM)
            dmgBonus(param(TalentType.BURST, "param3"), ELEMENTAL_NORMAL)
        }
        effect(
            "a4", "Celestial Dome of Sand", "Elemental Normal Attack DMG +0.5% per 1,000 of Candace's Max HP.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = A4,
        ) {
            dmgBonus(0.005 * ownerStats.hp / 1000, ELEMENTAL_NORMAL)
        }
        effect("c2", "Moon-Piercing Brilliance", "Max HP +20% for 15s after her skill hits.", control = toggle(), requires = cons(2)) {
            stat(Stat.HP_PCT, 0.20)
        }
    }

    this("layla") {
        effect("a4", "Sweet Slumber Undisturbed", "Shooting Star DMG +1.5% of Max HP.", phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(0.015 * targetStats.hp, HitFilter.hits("skill/shooting-star-dmg"))
        }
        effect(
            "c4", "Starry Illumination", "Party Normal and Charged Attack DMG +5% of Layla's Max HP (Dawn Star).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT, requires = cons(4),
        ) {
            flatDmg(0.05 * ownerStats.hp, HitFilter.NORMAL_CHARGED)
        }
        effect("c6", "Radiant Soulfire", "Shooting Star and Starlight Slug DMG +40%.", requires = cons(6)) {
            dmgBonus(0.40, HitFilter.hits("skill/shooting-star-dmg", "burst/starlight-slug-dmg"))
        }
    }

    this("dehya") {
        effect("c1", "The Flame Incandescent", "Max HP +20%.", static = true, requires = cons(1)) {
            stat(Stat.HP_PCT, 0.20)
        }
        effect("c1dmg", "The Flame Incandescent (DMG)", "Molten Inferno DMG +3.6% of Max HP; Leonine Bite DMG +6% of Max HP.",
            phase = EffectPhase.CONVERSION, requires = cons(1)) {
            flatDmg(0.036 * targetStats.hp, HitFilter(talents = setOf(TalentType.SKILL)))
            flatDmg(0.06 * targetStats.hp, HitFilter(talents = setOf(TalentType.BURST)))
        }
        effect("c2", "The Sand-Blades Glittering", "Fiery Sanctum's next coordinated attack deals 50% more DMG.",
            control = toggle(false), requires = cons(2)) {
            dmgBonus(0.50, HitFilter.hits("skill/field-dmg"))
        }
        effect("c6", "The Burning Claws Cleaving", "Leonine Bite CRIT Rate +10%; CRIT DMG +15% per CRIT hit (max 4 stacks).",
            control = stacks(4), requires = cons(6)) {
            val leonine = HitFilter(talents = setOf(TalentType.BURST))
            critRate(0.10, leonine)
            critDmg(0.15 * stacks, leonine)
        }
    }

    this("kaveh") {
        effect("dome", "Painted Dome", "Dendro infusion; party Dendro Core (Bloom, Hyperbloom, Burgeon) DMG bonus.", control = toggle()) {
            infuse(Element.DENDRO, InfusionPriority.NON_OVERRIDABLE)
        }
        effect("core", "Painted Dome (Dendro Cores)", "Dendro Core burst DMG is increased while Painted Dome lasts.",
            target = EffectTarget.TEAM, control = toggle()) {
            val bonus = param(TalentType.BURST, "param3")
            for (r in listOf(Reaction.BLOOM, Reaction.HYPERBLOOM, Reaction.BURGEON)) reactionBonus(r, bonus)
        }
        effect("a4", "A Craftsman's Curious Conceptions", "EM +25 per Normal/Charged/Plunging hit in Painted Dome (max 4).",
            control = stacks(4), requires = A4) {
            stat(Stat.EM, 25.0 * stacks)
        }
        effect("c4", "Feast of Apadana", "Dendro Cores from Kaveh's Bloom deal 60% more DMG.", requires = cons(4)) {
            reactionBonus(Reaction.BLOOM, 0.60)
        }
    }

    this("sethos") {
        val shadowpiercing = HitFilter.hits("normal/shadowpiercing-shot-dmg")
        effect("a4", "Scorching Sandshade", "Shadowpiercing Shot DMG +700% of EM.", control = toggle(), phase = EffectPhase.CONVERSION, requires = A4) {
            flatDmg(7.0 * targetStats.em, shadowpiercing)
        }
        effect("burst", "Twilight Meditation", "Normal Attacks become Electro Dusk Bolts with DMG increased by EM.",
            control = toggle(), phase = EffectPhase.CONVERSION) {
            infuse(Element.ELECTRO, InfusionPriority.NON_OVERRIDABLE, setOf(AttackCategory.NORMAL))
            flatDmg(param(TalentType.BURST, "param1") * targetStats.em, HitFilter.NORMAL)
        }
        effect("c1", "Sealed Shrine's Spiritsong", "Shadowpiercing Shot CRIT Rate +15%.", requires = cons(1)) {
            critRate(0.15, shadowpiercing)
        }
        effect("c2", "Papyrus Scripture of Silent Secrets", "Electro DMG Bonus +15% per stack (max 2).", control = stacks(2), requires = cons(2)) {
            elementDmg(Element.ELECTRO, 0.15 * stacks)
        }
        effect("c4", "Beneficent Plumage", "Party EM +80 when a Shadowpiercing Shot or Dusk Bolt hits 2+ opponents.",
            target = EffectTarget.TEAM, control = toggle(false), requires = cons(4)) {
            stat(Stat.EM, 80.0)
        }
    }

    this("dori") {
        effect("c4", "Discretionary Supplement", "The connected character gains 30% Energy Recharge while their Energy is below 50%.",
            target = EffectTarget.TEAM, control = toggle(false), requires = cons(4)) {
            stat(Stat.ER, 0.30)
        }
        effect("c6", "Sprinkling Weight", "Electro infusion for 3s after her skill.", control = toggle(), requires = cons(6)) {
            infuse(Element.ELECTRO)
        }
    }
}
