package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat

internal fun EffectTable.polearmEffects() {
    this("bloodsoakedruins") {
        effect("lc", "Mournful Tribute", "Lunar-Charged DMG increased for 3.5s after a Burst.", control = toggle()) {
            reactionBonus(Reaction.LUNAR_CHARGED, r(0))
        }
        effect("requiem", "Requiem of Ruin", "CRIT DMG increased after a Lunar-Charged reaction.", control = toggle()) { stat(Stat.CRIT_DMG, r(1)) }
    }
    this("calamityqueller") {
        effect("ele", "Extinguishing Precept", "All Elemental DMG Bonus.", static = true) { allElementalDmg(r(0)) }
        effect("consummation", "Consummation", "ATK per second after a Skill (max 6; doubled off-field, count 12).", control = stacks(12, 6)) {
            stat(Stat.ATK_PCT, r(1) * stacks)
        }
    }
    this("crimsonmoonssemblance") {
        effect("bond", "Ashen Sun's Shadow", "DMG Bonus with a Bond of Life; more at 30% or above.",
            control = choice("Bond of Life >= 30%", "Bond of Life < 30%", "No Bond of Life")) {
            when (value) {
                0 -> stat(Stat.ALL_DMG, r(0) + r(1))
                1 -> stat(Stat.ALL_DMG, r(0))
            }
        }
    }
    this("disasterandremorse") {
        effect("unforgivable", "Unforgivable", "Normal and Charged Attack DMG after a Skill.", control = toggle()) {
            dmgBonus(r(0), HitFilter.NORMAL_CHARGED)
        }
        effect("irreparable", "Irreparable", "Elemental Skill and Burst DMG after a Skill.", control = toggle()) {
            dmgBonus(r(1), HitFilter.SKILL_BURST)
        }
        effect("hexerei", "Hexerei: Secret Rite", "The DMG boosts are increased by 75%.", control = toggle(false)) {
            dmgBonus(0.75 * r(0), HitFilter.NORMAL_CHARGED)
            dmgBonus(0.75 * r(1), HitFilter.SKILL_BURST)
        }
    }
    this("engulfinglightning") {
        effect("atk", "Timeless Dream: Eternal Stove", "ATK from Energy Recharge above 100% (capped).", static = true, phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK_PCT, (r(0) * (targetStats.er - 1)).coerceIn(0.0, r(1)))
        }
        effect("er", "Timeless Dream (after Burst)", "Energy Recharge increased after a Burst.", control = toggle()) { stat(Stat.ER, r(2)) }
    }
    this("fracturedhalo") {
        effect("atk", "Purifying Crown", "ATK increased after a Skill or Burst.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
        effect("edict", "Electrifying Edict", "After creating a shield: party Lunar-Charged DMG increased.", target = EffectTarget.TEAM, control = toggle()) {
            reactionBonus(Reaction.LUNAR_CHARGED, r(1))
        }
    }
    this("lumidouceelegy") {
        effect("atk", "Bright Dawn Overture", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("burning", "Bright Dawn Overture (Burning)", "DMG per stack after Burning (max 2).", control = stacks(2)) { stat(Stat.ALL_DMG, r(1) * stacks) }
    }
    this("primordialjadewingedspear") {
        effect("eagle", "Eagle Spear of Justice", "ATK per hit (max 7); DMG bonus at 7 stacks.", control = stacks(7)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            if (stacks >= 7) stat(Stat.ALL_DMG, r(1))
        }
    }
    this("skywardspine") {
        effect("cr", "Black Wing", "CRIT Rate increased.", static = true) { stat(Stat.CRIT_RATE, r(0)) }
    }
    this("staffofhoma") {
        effect("hp", "Reckless Cinnabar", "HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("atk", "Reckless Cinnabar (ATK from HP)", "ATK Bonus based on Max HP.",
            static = true, phase = EffectPhase.CONVERSION) { stat(Stat.ATK, r(1) * targetStats.hp) }
        effect("lowhp", "Reckless Cinnabar (HP < 50%)", "Additional ATK Bonus based on Max HP while HP is below 50%.",
            control = toggle(), phase = EffectPhase.CONVERSION) { stat(Stat.ATK, r(2) * targetStats.hp) }
    }
    this("staffofthescarletsands") {
        effect("atk", "Heat Haze at Horizon's End", "ATK equal to a % of EM.", static = true, phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK, r(0) * targetStats.em)
        }
        effect("dream", "Dream of the Scarlet Sands", "Further ATK equal to a % of EM per stack (max 3).",
            control = stacks(3), phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK, r(1) * stacks * targetStats.em)
        }
    }
    this("symphonistofscents") {
        effect("atk", "Seasoned Symphony", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("off", "Seasoned Symphony (off-field)", "Further ATK while off-field.", control = toggle(false)) { stat(Stat.ATK_PCT, r(1)) }
        effect("echoes", "Sweet Echoes", "After healing: the wielder and healed characters gain ATK.", target = EffectTarget.TEAM, control = toggle()) {
            stat(Stat.ATK_PCT, r(2))
        }
    }
    this("vortexvanquisher") {
        effect("shield", "Golden Majesty", "Shield Strength increased.", static = true) { stat(Stat.SHIELD_STRENGTH, r(0)) }
        effect("atk", "Golden Majesty (ATK)", "ATK per stack (max 5); doubled while shielded (count 10).", control = stacks(10)) {
            stat(Stat.ATK_PCT, r(1) * stacks)
        }
    }

    // 4-star
    this("balladofthefjords") {
        effect("em", "Tales of the Tundra", "EM with at least 3 Elemental Types in the party.", static = true) {
            if (team.distinctElements >= 3) stat(Stat.EM, r(0))
        }
    }
    this("blackcliffpole") {
        effect("atk", "Press the Advantage", "ATK per defeated opponent (max 3).", control = stacks(3, 0)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("crescentpike") {
        effect("dmg", "Infusion Needle", "Normal and Charged hits deal additional DMG (% of ATK) after picking up particles.",
            control = toggle(), phase = EffectPhase.CONVERSION) {
            flatDmg(r(0) * targetStats.atk, HitFilter.NORMAL_CHARGED)
        }
    }
    this("deathmatch") {
        effect("gladiator", "Gladiator", "2+ opponents: ATK and DEF; fewer: more ATK.", control = choice("Single opponent", "2+ opponents")) {
            if (value == 0) stat(Stat.ATK_PCT, r(2)) else {
                stat(Stat.ATK_PCT, r(0))
                stat(Stat.DEF_PCT, r(1))
            }
        }
    }
    this("dialoguesofthedesertsages") {}
    this("dragonsbane") {
        effect("dmg", "Bane of Flame and Water", "DMG vs opponents affected by Hydro or Pyro.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("dragonspinespear") {}
    this("favoniuslance") {}
    this("footprintoftherainbow") {
        effect("def", "Pact of Flowing Springs", "DEF increased after a Skill.", control = toggle()) { stat(Stat.DEF_PCT, r(0)) }
    }
    this("frostbreath") {
        effect("atk", "A Cast Real Far", "ATK increased after a Cryo or Hydro reaction.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("kitaincrossspear") {
        effect("skill", "Samurai Conduct", "Elemental Skill DMG increased.") { dmgBonus(r(0), HitFilter.SKILL) }
    }
    this("lithicspear") {
        effect("liyue", "Lithic Axiom: Unity", "ATK and CRIT Rate per Liyue party member.", static = true) {
            val n = team.regions.count { it == "Liyue" }.coerceAtMost(4)
            stat(Stat.ATK_PCT, r(0) * n)
            stat(Stat.CRIT_RATE, r(1) * n)
        }
    }
    this("missivewindspear") {
        effect("buff", "The Wind Unattained", "ATK and EM after a reaction.", control = toggle()) {
            stat(Stat.ATK_PCT, r(0))
            stat(Stat.EM, r(1))
        }
    }
    this("moonpiercer") {
        effect("atk", "Leaf of Revival", "ATK increased after picking up the Leaf.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("mountainbracingbolt") {
        effect("skill", "Hope Beyond the Peaks", "Elemental Skill DMG increased.") { dmgBonus(r(0), HitFilter.SKILL) }
        effect("others", "Hope Beyond the Peaks (party Skills)", "Further Skill DMG after other members use Skills.", control = toggle()) {
            dmgBonus(r(1), HitFilter.SKILL)
        }
    }
    this("prospectorsdrill") {
        effect("struggle", "Struggle", "ATK and All Elemental DMG per Unity's Symbol consumed (max 3).", control = stacks(3)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            allElementalDmg(r(1) * stacks)
        }
    }
    this("prospectorsshovel") {
        effect("ec", "Swift and Sure", "Electro-Charged and Lunar-Charged DMG increased (more in Ascendant Gleam).") {
            reactionBonus(Reaction.ELECTRO_CHARGED, r(0))
            reactionBonus(Reaction.LUNAR_CHARGED, r(1) + if (team.moonsign() >= 2) r(2) else 0.0)
        }
    }
    this("prototypestarglitter") {
        effect("dmg", "Magic Affinity", "Normal and Charged Attack DMG per Skill use (max 2).", control = stacks(2)) {
            dmgBonus(r(0) * stacks, HitFilter.NORMAL_CHARGED)
        }
    }
    this("rightfulreward") {}
    this("royalspear") {
        effect("cr", "Focus", "CRIT Rate per stack until a CRIT (max 5).", control = stacks(5, 2)) { stat(Stat.CRIT_RATE, r(0) * stacks) }
    }
    this("sacrificersstaff") {
        effect("desire", "Untainted Desire", "ATK and Energy Recharge per Skill hit (max 3).", control = stacks(3)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            stat(Stat.ER, r(1) * stacks)
        }
    }
    this("songofthevigil") {
        effect("atk", "Cadence of Days Gone By", "ATK increased after a Stellar Glimmer reaction.", control = toggle(false)) { stat(Stat.ATK_PCT, r(1)) }
    }
    this("tamayurateinoohanashi") {
        effect("atk", "Busybody's Running Light", "ATK increased after a Skill.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("thecatch") {
        effect("burst", "Shanty", "Elemental Burst DMG and CRIT Rate increased.") {
            dmgBonus(r(0), HitFilter.BURST)
            critRate(r(1), HitFilter.BURST)
        }
    }
    this("wavebreakersfin") {
        effect("burst", "Watatsumi Wavewalker", "Burst DMG per point of the party's combined Energy capacity (capped).") {
            dmgBonus((r(0) * teamSumOf { it.burstEnergyCost }).coerceAtMost(r(1)), HitFilter.BURST)
        }
    }

    // 3-star
    this("blacktassel") {
        effect("dmg", "Bane of the Soft", "DMG vs slimes.", control = toggle(false)) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("halberd") {}
    this("whitetassel") {
        effect("na", "Sharp", "Normal Attack DMG increased.") { dmgBonus(r(0), HitFilter.NORMAL) }
    }
}
