package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat

internal fun EffectTable.swordEffects() {
    this("absolution") {
        effect("cd", "Deathly Pact", "CRIT DMG increased.", static = true) { stat(Stat.CRIT_DMG, r(0)) }
        effect("bond", "Deathly Pact (Bond of Life)", "DMG +X% per Bond of Life increase (max 3 stacks).", control = stacks(3)) {
            stat(Stat.ALL_DMG, r(1) * stacks)
        }
    }
    this("aquilafavonia") {
        effect("atk", "Falcon's Defiance", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("athameartis") {
        effect("cd", "Day King's Splendor Solis", "Elemental Burst CRIT DMG increased.") { critDmg(r(0), HitFilter.BURST) }
        effect("blade", "Blade of the Daylight Hours", "After a burst hit: ATK increased; other active party members gain ATK.",
            target = EffectTarget.TEAM, control = toggle()) {
            stat(Stat.ATK_PCT, if (isSelf) r(1) else r(2))
        }
    }
    this("azurelight") {
        effect("atk", "Whitehill's Bestowal", "ATK increased for 12s after an Elemental Skill.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
        effect("zero", "Whitehill's Bestowal (0 Energy)", "With 0 Energy: further ATK and CRIT DMG.", control = toggle(false)) {
            stat(Stat.ATK_PCT, r(1))
            stat(Stat.CRIT_DMG, r(2))
        }
    }
    this("beyondthechrysalis") {
        effect("devotion", "Winds of Devotion", "CRIT DMG increased after Skill/Burst (sequence).", control = toggle()) { stat(Stat.CRIT_DMG, r(0)) }
        effect("defiance", "Winds of Defiance", "Stellar Swirl DMG increased (sequence).", control = toggle()) {
            reactionBonus(Reaction.STELLAR_SWIRL, r(1))
        }
    }
    this("exaiphanesblade") {
        effect("atk", "Traveler's Path", "ATK increased for 8s after hitting an opponent.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("freedomsworn") {
        effect("dmg", "Revolutionary Chorale", "DMG increased.") { stat(Stat.ALL_DMG, r(0)) }
        effect("song", "Millennial Movement: Song of Resistance", "Party Normal/Charged/Plunging Attack DMG and ATK increased.",
            target = EffectTarget.TEAM, control = toggle()) {
            dmgBonus(r(1), HitFilter.NORMAL_CHARGED_PLUNGE)
            stat(Stat.ATK_PCT, r(2))
        }
    }
    this("harangeppakufutsu") {
        effect("ele", "Honed Flow", "All Elemental DMG Bonus.", static = true) { allElementalDmg(r(0)) }
        effect("wave", "Rippling Upheaval", "Normal Attack DMG per Wavespike stack (max 2).", control = stacks(2)) {
            dmgBonus(r(1) * stacks, HitFilter.NORMAL)
        }
    }
    this("keyofkhajnisut") {
        effect("hp", "Sunken Song of the Sands", "HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("hymn", "Grand Hymn", "EM increased by a % of Max HP per stack (max 3).", control = stacks(3), phase = EffectPhase.CONVERSION) {
            stat(Stat.EM, r(1) * stacks * targetStats.hp)
        }
        effect("party", "Grand Hymn (3 stacks)", "Party EM increased by a % of the wielder's Max HP.",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT) {
            stat(Stat.EM, r(2) * ownerStats.hp)
        }
    }
    this("lightbearingmoonshard") {
        effect("def", "Legacy of Lang-Gan", "DEF increased.", static = true) { stat(Stat.DEF_PCT, r(0)) }
        effect("lcr", "Legacy of Lang-Gan (after Skill)", "Lunar-Crystallize DMG increased for 5s after a Skill.", control = toggle()) {
            reactionBonus(Reaction.LUNAR_CRYSTALLIZE, r(1))
        }
    }
    this("lightoffoliarincision") {
        effect("cr", "Whitemoon Bristle", "CRIT Rate increased.", static = true) { stat(Stat.CRIT_RATE, r(0)) }
        effect("foliar", "Foliar Incision", "Normal Attack and Skill DMG increased by a % of EM.", control = toggle(), phase = EffectPhase.CONVERSION) {
            flatDmg(r(1) * targetStats.em, HitFilter.of(com.genshincalc.core.model.AttackCategory.NORMAL, com.genshincalc.core.model.AttackCategory.SKILL))
        }
    }
    this("mistsplitterreforged") {
        effect("ele", "Mistsplitter's Edge", "All Elemental DMG Bonus.", static = true) { allElementalDmg(r(0)) }
        effect("emblem", "Mistsplitter's Emblem", "Elemental DMG Bonus for the wielder's element at 1/2/3 stacks.", control = stacks(3)) {
            if (stacks > 0) elementDmg(targetElement, r(1) * listOf(1.0, 2.0, 3.5)[stacks - 1])
        }
    }
    this("peakpatrolsong") {
        effect("ode", "Ode to Flowers", "DEF and All Elemental DMG per stack (max 2).", control = stacks(2)) {
            stat(Stat.DEF_PCT, r(0) * stacks)
            allElementalDmg(r(1) * stacks)
        }
        effect("party", "Ode to Flowers (2 stacks)", "Party All Elemental DMG per 1,000 of the wielder's DEF (capped).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT) {
            allElementalDmg((r(2) * ownerStats.def / 1000).coerceAtMost(r(3)))
        }
    }
    this("primordialjadecutter") {
        effect("hp", "Protector's Virtue", "HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("atk", "Protector's Virtue (ATK)", "ATK bonus based on Max HP.", static = true, phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK, r(1) * targetStats.hp)
        }
    }
    this("skywardblade") {
        effect("cr", "Sky-Piercing Fang", "CRIT Rate increased.", static = true) { stat(Stat.CRIT_RATE, r(0)) }
        effect("might", "Skypiercing Might", "Normal and Charged hits deal additional DMG (% of ATK) after a burst.",
            control = toggle(), phase = EffectPhase.CONVERSION) {
            flatDmg(r(3) * targetStats.atk, HitFilter.NORMAL_CHARGED)
        }
    }
    this("splendoroftranquilwaters") {
        effect("skill", "Dawn and Dusk by the Lake", "Skill DMG per stack when the wielder's HP changes (max 3).", control = stacks(3)) {
            dmgBonus(r(0) * stacks, HitFilter.SKILL)
        }
        effect("hp", "Dawn and Dusk by the Lake (HP)", "Max HP per stack when others' HP changes (max 2).", control = stacks(2)) {
            stat(Stat.HP_PCT, r(1) * stacks)
        }
    }
    this("summitshaper") {
        effect("shield", "Golden Majesty", "Shield Strength increased.", static = true) { stat(Stat.SHIELD_STRENGTH, r(0)) }
        effect("atk", "Golden Majesty (ATK)", "ATK per stack (max 5); doubled while shielded (count 10).", control = stacks(10)) {
            stat(Stat.ATK_PCT, r(1) * stacks)
        }
    }
    this("urakumisugiri") {
        effect("def", "Brocade Bloom, Shrine Sword", "DEF increased.", static = true) { stat(Stat.DEF_PCT, r(2)) }
        effect("dmg", "Brocade Bloom (DMG)", "Normal Attack and Skill DMG increased; doubled after party Geo DMG.",
            control = choice("Base", "After Geo DMG (x2)", default = 1)) {
            val k = if (value == 1) 2.0 else 1.0
            dmgBonus(r(0) * k, HitFilter.NORMAL)
            dmgBonus(r(1) * k, HitFilter.SKILL)
        }
    }
    this("whitelakefrostfeather") {
        effect("lament", "Lake-Hued Lament", "ATK per stack (max 3); at 3 stacks Stellar Glimmer CRIT DMG increased.", control = stacks(3)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            if (stacks >= 3) for (rx in listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)) reactionCrit(rx, 0.0, r(1))
        }
    }

    // 4-star
    this("amenomakageuchi") {}
    this("blackclifflongsword") {
        effect("atk", "Press the Advantage", "ATK per defeated opponent (max 3).", control = stacks(3, 0)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("calamityofeshu") {
        effect("shield", "Diffusing Boundary", "While shielded: Normal/Charged DMG and CRIT Rate increased.", control = toggle()) {
            dmgBonus(r(0), HitFilter.NORMAL_CHARGED)
            critRate(r(1), HitFilter.NORMAL_CHARGED)
        }
    }
    this("cinnabarspindle") {
        effect("skill", "Spotless Heart", "Elemental Skill DMG increased by a % of DEF.", control = toggle(), phase = EffectPhase.CONVERSION) {
            flatDmg(r(0) * targetStats.def, HitFilter.SKILL)
        }
    }
    this("emberwell") {
        effect("atk", "Starfire Upon the Snowplains", "ATK increased after an Elemental Reaction.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
        effect("stellar", "Starfire (Stellar Glimmer)", "Stellar Glimmer DMG increased after a Stellar reaction.", control = toggle(false)) {
            for (rx in listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)) reactionBonus(rx, r(1))
        }
    }
    this("favoniussword") {}
    this("festeringdesire") {
        effect("skill", "Undying Admiration", "Elemental Skill DMG and CRIT Rate increased.") {
            dmgBonus(r(0), HitFilter.SKILL)
            critRate(r(1), HitFilter.SKILL)
        }
    }
    this("finaleofthedeep") {
        effect("atk", "An End Sublime", "ATK increased after an Elemental Skill.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
        effect("bond", "An End Sublime (Bond cleared)", "Flat ATK from the cleared Bond of Life (25% of Max HP), capped.",
            control = toggle(), phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK, (r(2) * 0.25 * targetStats.hp).coerceAtMost(r(1)))
        }
    }
    this("fleuvecendreferryman") {
        effect("cr", "Ironbone", "Elemental Skill CRIT Rate increased.") { critRate(r(0), HitFilter.SKILL) }
        effect("er", "Ironbone (ER)", "Energy Recharge increased after a Skill.", control = toggle()) { stat(Stat.ER, r(1)) }
    }
    this("fluteofezpitzal") {
        effect("def", "Smoke-and-Mirror Mystery", "DEF increased after a Skill.", control = toggle()) { stat(Stat.DEF_PCT, r(0)) }
    }
    this("hereticsmoltenblade") {
        effect("atk", "Gleam of First Light", "ATK bonus by distance travelled (min to max).", control = choice("Minimum", "Maximum", default = 1)) {
            stat(Stat.ATK_PCT, if (value == 1) r(1) else r(0))
        }
    }
    this("ironsting") {
        effect("dmg", "Infusion Stinger", "DMG per stack after Elemental DMG (max 2).", control = stacks(2)) { stat(Stat.ALL_DMG, r(0) * stacks) }
    }
    this("kagotsurubeisshin") {
        effect("atk", "Isshin Art Clarity", "ATK +15% for 8s after a Hewing Gale.", control = toggle()) { stat(Stat.ATK_PCT, 0.15) }
    }
    this("lionsroar") {
        effect("dmg", "Bane of Fire and Thunder", "DMG vs opponents affected by Pyro or Electro.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("moonweaversdawn") {
        effect("burst", "Secret Silver's Testament", "Burst DMG increased; more for Energy costs of 60 or 40.") {
            val cost = target.burstEnergyCost
            val extra = when {
                cost <= 40 -> refine(0.28, 0.35, 0.42, 0.49, 0.56)
                cost <= 60 -> r(1)
                else -> 0.0
            }
            dmgBonus(r(0) + extra, HitFilter.BURST)
        }
    }
    this("newbough") {
        effect("verdant", "Verdant (3 stacks)", "ATK and EM per stack; in Radiance: ATK and Stellar Glimmer DMG instead.",
            control = choice("Verdant", "Radiance: Stellar Glimmer", "Off")) {
            when (value) {
                0 -> {
                    stat(Stat.ATK_PCT, 3 * r(0))
                    stat(Stat.EM, 3 * r(1))
                }
                1 -> {
                    stat(Stat.ATK_PCT, 3 * r(2))
                    for (rx in listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)) reactionBonus(rx, 3 * r(3))
                }
            }
        }
    }
    this("prizedisshinblade") {}
    this("prizedisshinblade-01") {}
    this("prizedisshinblade-02") {}
    this("prototyperancour") {
        effect("stacks", "Smashed Stone", "ATK and DEF per stack (max 4).", control = stacks(4)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            stat(Stat.DEF_PCT, r(0) * stacks)
        }
    }
    this("royallongsword") {
        effect("cr", "Focus", "CRIT Rate per stack until a CRIT (max 5).", control = stacks(5, 2)) { stat(Stat.CRIT_RATE, r(0) * stacks) }
    }
    this("sacrificialsword") {}
    this("sapwoodblade") {
        effect("em", "Leaf of Consciousness", "EM increased after picking up the Leaf.", control = toggle()) { stat(Stat.EM, r(0)) }
    }
    this("serenityscall") {
        effect("hp", "Solemn Silence", "Max HP increased after a reaction (more in Ascendant Gleam).", control = toggle()) {
            stat(Stat.HP_PCT, r(0) + if (team.moonsign() >= 2) r(1) else 0.0)
        }
    }
    this("silverlight") {
        effect("em", "Radiance on the Water", "EM per stack after a Skill (max 2).", control = stacks(2)) { stat(Stat.EM, r(0) * stacks) }
    }
    this("sturdybone") {
        effect("na", "Trapper's Pride", "Normal Attack DMG increased by a % of ATK after sprinting.", control = toggle(), phase = EffectPhase.CONVERSION) {
            flatDmg(r(0) * targetStats.atk, HitFilter.NORMAL)
        }
    }
    this("swordofdescension") {
        effect("atk", "Descension", "Traveler: ATK +66.", static = true) {
            if (targetCharacterId.startsWith("traveler")) stat(Stat.ATK, 66.0)
        }
    }
    this("swordofnarzissenkreuz") {}
    this("thealleyflash") {
        effect("dmg", "Itinerant Hero", "DMG increased (disabled for 5s after taking DMG).", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("theblacksword") {
        effect("dmg", "Justice", "Normal and Charged Attack DMG increased.") { dmgBonus(r(0), HitFilter.NORMAL_CHARGED) }
    }
    this("thedockhandsassistant") {
        effect("em", "Roused", "EM per Stoic's Symbol consumed (max 3).", control = stacks(3)) { stat(Stat.EM, r(0) * stacks) }
    }
    this("theflute") {}
    this("toukaboushigure") {
        effect("dmg", "Cursed Parasol", "DMG vs the opponent with Cursed Parasol.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("wolffang") {
        effect("dmg", "Northwind Wolf", "Skill and Burst DMG increased.") { dmgBonus(r(0), HitFilter.SKILL_BURST) }
        effect("skillcr", "Northwind Wolf (Skill CRIT)", "Skill CRIT Rate per stack (max 4).", control = stacks(4)) {
            critRate(r(1) * stacks, HitFilter.SKILL)
        }
        effect("burstcr", "Northwind Wolf (Burst CRIT)", "Burst CRIT Rate per stack (max 4).", control = stacks(4)) {
            critRate(r(2) * stacks, HitFilter.BURST)
        }
    }
    this("xiphosmoonlight") {
        effect("er", "Jinni's Whisper", "Energy Recharge per point of EM.", control = toggle(), phase = EffectPhase.CONVERSION) {
            stat(Stat.ER, r(0) * targetStats.em)
        }
        effect("party", "Jinni's Whisper (party)", "Other members gain 30% of the Energy Recharge buff.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT) {
            stat(Stat.ER, 0.3 * r(0) * ownerStats.em)
        }
    }

    // 3-star
    this("coolsteel") {
        effect("dmg", "Bane of Water and Ice", "DMG vs opponents affected by Hydro or Cryo.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("darkironsword") {
        effect("atk", "Overloaded", "ATK increased after Electro-related reactions.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("filletblade") {}
    this("harbingerofdawn") {
        effect("cr", "Vigorous", "CRIT Rate increased while HP is above 90%.", control = toggle()) { stat(Stat.CRIT_RATE, r(0)) }
    }
    this("skyridersword") {
        effect("atk", "Determination", "ATK increased after a burst.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("travelershandysword") {}
}
