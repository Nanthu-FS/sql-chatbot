package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat

private val STELLAR_REACTIONS = listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)

internal fun EffectTable.claymoreEffects() {
    this("ateaspoonoftranscendence") {
        effect("atk", "White Fairy's Queening", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("stellar", "Transcendence", "Stellar-Conduct and Stellar Swirl DMG per stack after Charged Attacks (max 3).", control = stacks(3)) {
            for (rx in STELLAR_REACTIONS) reactionBonus(rx, r(1) * stacks)
        }
    }
    this("athousandblazingsuns") {
        effect("brilliance", "Scorching Brilliance", "CRIT DMG and ATK after a Skill or Burst (+75% in Nightsoul's Blessing).",
            control = choice("Active", "Active (Nightsoul's Blessing)", "Off")) {
            if (value < 2) {
                val k = if (value == 1) 1.75 else 1.0
                stat(Stat.CRIT_DMG, r(0) * k)
                stat(Stat.ATK_PCT, r(1) * k)
            }
        }
    }
    this("beaconofthereedsea") {
        effect("skill", "Desert Watch (Skill)", "ATK increased after a Skill hit.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
        effect("hurt", "Desert Watch (taking DMG)", "ATK increased after taking DMG.", control = toggle(false)) { stat(Stat.ATK_PCT, r(1)) }
        effect("hp", "Desert Watch (unshielded)", "Max HP increased while not shielded.", control = toggle()) { stat(Stat.HP_PCT, r(2)) }
    }
    this("fangofthemountainking") {
        effect("favor", "Canopy's Favor", "Skill and Burst DMG per stack (max 6).", control = stacks(6)) { dmgBonus(r(0) * stacks, HitFilter.SKILL_BURST) }
    }
    this("gestofthemightywolf") {
        effect("hymn", "Four Winds' Hymn", "DMG per stack (max 4).", control = stacks(4)) { stat(Stat.ALL_DMG, r(0) * stacks) }
        effect("hexerei", "Four Winds' Hymn (Hexerei)", "With Hexerei: Secret Rite: CRIT DMG per stack (4 stacks).", control = toggle(false)) {
            stat(Stat.CRIT_DMG, r(1) * 4)
        }
    }
    this("redhornstonethresher") {
        effect("def", "Gokadaiou Otogibanashi", "DEF increased.", static = true) { stat(Stat.DEF_PCT, r(0)) }
        effect("dmg", "Gokadaiou Otogibanashi (DMG)", "Normal and Charged Attack DMG increased by a % of DEF.", phase = EffectPhase.CONVERSION) {
            flatDmg(r(1) * targetStats.def, HitFilter.NORMAL_CHARGED)
        }
    }
    this("skywardpride") {
        effect("dmg", "Sky-ripping Dragon Spine", "All DMG increased.") { stat(Stat.ALL_DMG, r(0)) }
    }
    this("songofbrokenpines") {
        effect("atk", "Rebel's Banner-Hymn", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("hymn", "Millennial Movement: Banner-Hymn", "Party ATK increased.", target = EffectTarget.TEAM, control = toggle()) {
            stat(Stat.ATK_PCT, r(2))
        }
    }
    this("theunforged") {
        effect("shield", "Golden Majesty", "Shield Strength increased.", static = true) { stat(Stat.SHIELD_STRENGTH, r(0)) }
        effect("atk", "Golden Majesty (ATK)", "ATK per stack (max 5); doubled while shielded (count 10).", control = stacks(10)) {
            stat(Stat.ATK_PCT, r(1) * stacks)
        }
    }
    this("verdict") {
        effect("atk", "Many Oaths of Dawn and Dusk", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("seal", "Seal", "Elemental Skill DMG per Seal (max 2).", control = stacks(2)) { dmgBonus(r(1) * stacks, HitFilter.SKILL) }
    }
    this("wolfsgravestone") {
        effect("atk", "Wolfish Tracker", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("party", "Wolfish Tracker (low HP target)", "Party ATK increased after hitting an opponent below 30% HP.",
            target = EffectTarget.TEAM, control = toggle(false)) {
            stat(Stat.ATK_PCT, r(1))
        }
    }

    // 4-star
    this("akuoumaru") {
        effect("burst", "Watatsumi Wavewalker", "Burst DMG per point of the party's combined Energy capacity (capped).") {
            dmgBonus((r(0) * teamSumOf { it.burstEnergyCost }).coerceAtMost(r(1)), HitFilter.BURST)
        }
    }
    this("blackcliffslasher") {
        effect("atk", "Press the Advantage", "ATK per defeated opponent (max 3).", control = stacks(3, 0)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("bladeofatonement") {
        effect("em", "Repentance and Redemption", "EM increased after a reaction.", control = toggle()) { stat(Stat.EM, r(0)) }
        effect("atk", "Repentance and Redemption (Stellar)", "ATK increased after a Stellar Glimmer reaction.", control = toggle(false)) {
            stat(Stat.ATK_PCT, r(1))
        }
    }
    this("earthshaker") {
        effect("skill", "Oath of Qhapaq Nan", "Elemental Skill DMG increased after a party Pyro reaction.", control = toggle()) {
            dmgBonus(r(0), HitFilter.SKILL)
        }
    }
    this("favoniusgreatsword") {}
    this("flameforgedinsight") {
        effect("em", "Mind in Bloom", "EM increased after Electro-Charged/Bloom/Crystallize (or Lunar) reactions.", control = toggle()) {
            stat(Stat.EM, r(1))
        }
    }
    this("forestregalia") {
        effect("em", "Leaf of Consciousness", "EM increased after picking up the Leaf.", control = toggle()) { stat(Stat.EM, r(0)) }
    }
    this("forgedbythegoldenmelody") {
        effect("movement", "Harmonic Movement", "Cycles: ATK > EM > Stellar Glimmer DMG.", control = choice("ATK", "Elemental Mastery", "Stellar Glimmer DMG")) {
            when (value) {
                0 -> stat(Stat.ATK_PCT, r(0))
                1 -> stat(Stat.EM, r(1))
                else -> for (rx in STELLAR_REACTIONS) reactionBonus(rx, r(2))
            }
        }
    }
    this("fruitfulhook") {
        effect("cr", "The Weight of Falling Branches", "Plunging Attack CRIT Rate increased.") { critRate(r(0), HitFilter.PLUNGE) }
        effect("dmg", "The Weight of Falling Branches (after Plunge)", "Normal/Charged/Plunging Attack DMG increased after a Plunge hits.",
            control = toggle()) {
            dmgBonus(r(1), HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }
    this("katsuragikirinagamasa") {
        effect("skill", "Samurai Conduct", "Elemental Skill DMG increased.") { dmgBonus(r(0), HitFilter.SKILL) }
    }
    this("lithicblade") {
        effect("liyue", "Lithic Axiom: Unity", "ATK and CRIT Rate per Liyue party member.", static = true) {
            val n = team.regions.count { it == "Liyue" }.coerceAtMost(4)
            stat(Stat.ATK_PCT, r(0) * n)
            stat(Stat.CRIT_RATE, r(1) * n)
        }
    }
    this("luxurioussealord") {
        effect("burst", "Oceanic Victory", "Elemental Burst DMG increased.") { dmgBonus(r(0), HitFilter.BURST) }
    }
    this("mailedflower") {
        effect("buff", "Whispers of Wind and Flower", "ATK and EM increased after a Skill hit or reaction.", control = toggle()) {
            stat(Stat.ATK_PCT, r(0))
            stat(Stat.EM, r(1))
        }
    }
    this("makhairaaquamarine") {
        effect("atk", "Desert Pavilion", "ATK equal to a % of EM.", control = toggle(), phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK, r(0) * targetStats.em)
        }
        effect("party", "Desert Pavilion (party)", "Other members gain 30% of the ATK bonus.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT) {
            stat(Stat.ATK, 0.3 * r(0) * ownerStats.em)
        }
    }
    this("masterkey") {
        effect("em", "Fall Into Place", "EM increased after a reaction (more in Ascendant Gleam).", control = toggle()) {
            stat(Stat.EM, r(0) + if (team.moonsign() >= 2) r(1) else 0.0)
        }
    }
    this("portablepowersaw") {
        effect("em", "Roused", "EM per Stoic's Symbol consumed (max 3).", control = stacks(3)) { stat(Stat.EM, r(0) * stacks) }
    }
    this("prototypearchaic") {}
    this("rainslasher") {
        effect("dmg", "Bane of Storm and Tide", "DMG vs opponents affected by Hydro or Electro.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("royalgreatsword") {
        effect("cr", "Focus", "CRIT Rate per stack until a CRIT (max 5).", control = stacks(5, 2)) { stat(Stat.CRIT_RATE, r(0) * stacks) }
    }
    this("sacrificialgreatsword") {}
    this("serpentspine") {
        effect("dmg", "Wavesplitter", "DMG per stack while on-field (max 5).", control = stacks(5)) { stat(Stat.ALL_DMG, r(0) * stacks) }
    }
    this("snowtombedstarsilver") {}
    this("talkingstick") {
        effect("atk", "The Silver Tongue (Pyro)", "ATK increased after being affected by Pyro.", control = toggle(false)) { stat(Stat.ATK_PCT, r(0)) }
        effect("ele", "The Silver Tongue (other elements)", "All Elemental DMG increased after being affected by Hydro/Cryo/Electro/Dendro.",
            control = toggle(false)) {
            allElementalDmg(r(1))
        }
    }
    this("thebell") {
        effect("dmg", "Rebellious Guardian", "DMG increased while shielded.", control = toggle()) { stat(Stat.ALL_DMG, r(2)) }
    }
    this("tidalshadow") {
        effect("atk", "White Cruising Wave", "ATK increased after being healed.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("ultimateoverlordsmegamagicsword") {
        effect("atk", "Melussistance!", "ATK increased (including the maximum bonus from helped Melusines).", static = true) {
            stat(Stat.ATK_PCT, r(0) + r(1))
        }
    }
    this("whiteblind") {
        effect("stacks", "Infusion Blade", "ATK and DEF per stack (max 4).", control = stacks(4)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            stat(Stat.DEF_PCT, r(0) * stacks)
        }
    }

    // 3-star
    this("bloodtaintedgreatsword") {
        effect("dmg", "Bane of Fire and Thunder", "DMG vs opponents affected by Pyro or Electro.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("debateclub") {}
    this("ferrousshadow") {
        effect("ca", "Unbending", "Charged Attack DMG increased while HP is low.", control = toggle(false)) {
            dmgBonus(r(1), HitFilter.of(AttackCategory.CHARGED))
        }
    }
    this("skyridergreatsword") {
        effect("atk", "Courage", "ATK per Normal/Charged hit (max 4).", control = stacks(4)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("whiteirongreatsword") {}
}
