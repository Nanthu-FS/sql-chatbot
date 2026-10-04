package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat

private val STELLAR_REACTIONS = listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)
private val NORMAL_SKILL_BURST = HitFilter.of(AttackCategory.NORMAL, AttackCategory.SKILL, AttackCategory.BURST)

internal fun EffectTable.bowEffects() {
    this("amosbow") {
        effect("dmg", "Strong-Willed", "Normal and Charged Attack DMG increased.") { dmgBonus(r(0), HitFilter.NORMAL_CHARGED) }
        effect("flight", "Strong-Willed (flight time)", "Further DMG per 0.1s of arrow flight (max 5).", control = stacks(5)) {
            dmgBonus(r(1) * stacks, HitFilter.NORMAL_CHARGED)
        }
    }
    this("aquasimulacra") {
        effect("hp", "The Cleansing Form", "HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("dmg", "The Cleansing Form (DMG)", "DMG increased while opponents are nearby.") { stat(Stat.ALL_DMG, r(1)) }
    }
    this("astralvulturescrimsonplumage") {
        effect("atk", "The Moonring Sighted", "ATK increased for 12s after a Swirl.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
        effect("dmg", "The Moonring Sighted (party)", "Charged Attack and Burst DMG with 1 / 2+ party members of another element.") {
            val n = team.elements.count { it != targetElement }
            when {
                n >= 2 -> {
                    dmgBonus(refine(0.48, 0.60, 0.72, 0.84, 0.96), HitFilter.CHARGED)
                    dmgBonus(refine(0.24, 0.30, 0.36, 0.42, 0.48), HitFilter.BURST)
                }
                n == 1 -> {
                    dmgBonus(r(1), HitFilter.CHARGED)
                    dmgBonus(r(2), HitFilter.BURST)
                }
            }
        }
    }
    this("elegyfortheend") {
        effect("em", "The Parting Refrain", "EM increased.", static = true) { stat(Stat.EM, r(0)) }
        effect("song", "Millennial Movement: Farewell Song", "Party EM and ATK increased.", target = EffectTarget.TEAM, control = toggle()) {
            stat(Stat.EM, r(1))
            stat(Stat.ATK_PCT, r(2))
        }
    }
    this("goldenfrostboundoath") {
        effect("def", "Dawn's Salutation Returned", "DEF increased.", static = true) { stat(Stat.DEF_PCT, r(0)) }
        effect("favor", "Frost Fae's Favor", "Geo DMG and Lunar-Crystallize DMG increased after Skill/Lunar-Crystallize hits.", control = toggle()) {
            elementDmg(Element.GEO, r(1))
            reactionBonus(Reaction.LUNAR_CRYSTALLIZE, r(2))
        }
        effect("mischief", "Frost Fae's Mischief", "Other members near Moondrifts: Geo DMG and Lunar-Crystallize DMG increased.",
            target = EffectTarget.TEAM_OTHERS, control = toggle()) {
            elementDmg(Element.GEO, r(3))
            reactionBonus(Reaction.LUNAR_CRYSTALLIZE, r(4))
        }
    }
    this("hunterspath") {
        effect("ele", "At the End of the Beast-Paths", "All Elemental DMG Bonus.", static = true) { allElementalDmg(r(0)) }
        effect("hunt", "Tireless Hunt", "Charged Attack DMG increased by a % of EM.", control = toggle(), phase = EffectPhase.CONVERSION) {
            flatDmg(r(1) * targetStats.em, HitFilter.CHARGED)
        }
    }
    this("polarstar") {
        effect("dmg", "Daylight's Augury", "Elemental Skill and Burst DMG increased.") { dmgBonus(r(0), HitFilter.SKILL_BURST) }
        effect("atk", "Ashen Nightstar", "ATK at 1/2/3/4 stacks.", control = stacks(4)) {
            if (stacks > 0) stat(Stat.ATK_PCT, r(1) * listOf(1.0, 2.0, 3.0, 4.8)[stacks - 1])
        }
    }
    this("silvershowerheartstrings") {
        effect("remedy", "Remedy", "Max HP at 1/2/3 stacks; at 3 stacks Burst CRIT Rate increased.", control = stacks(3)) {
            if (stacks > 0) stat(Stat.HP_PCT, r(0) * listOf(1.0, 2.0, 10.0 / 3)[stacks - 1])
            if (stacks >= 3) critRate(r(1), HitFilter.BURST)
        }
    }
    this("skywardharp") {
        effect("cd", "Echoing Ballad", "CRIT DMG increased.", static = true) { stat(Stat.CRIT_DMG, r(0)) }
    }
    this("thedaybreakchronicles") {
        effect("breeze", "Stirring Dawn Breeze", "Normal Attack, Skill and Burst DMG bonus (maximum while hitting regularly).", control = toggle()) {
            dmgBonus(r(3), NORMAL_SKILL_BURST)
        }
    }
    this("thefirstgreatmagic") {
        effect("ca", "Parsifal the Great", "Charged Attack DMG increased.") { dmgBonus(r(0), HitFilter.CHARGED) }
        effect("gimmick", "Gimmick", "ATK by the number of party members sharing the wielder's element (1/2/3+).", static = true) {
            val n = team.elements.count { it == targetElement }.coerceIn(0, 3)
            stat(Stat.ATK_PCT, r(1) * n)
        }
    }
    this("thunderingpulse") {
        effect("atk", "Rule by Thunder", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("emblem", "Thunder Emblem", "Normal Attack DMG at 1/2/3 stacks.", control = stacks(3)) {
            if (stacks > 0) dmgBonus(r(1) * listOf(1.0, 2.0, 10.0 / 3)[stacks - 1], HitFilter.NORMAL)
        }
    }

    // 4-star
    this("alleyhunter") {
        effect("dmg", "Oppidan Ambush", "DMG builds up while off-field (maximum shown).", control = toggle(false)) { stat(Stat.ALL_DMG, r(1)) }
    }
    this("blackcliffwarbow") {
        effect("atk", "Press the Advantage", "ATK per defeated opponent (max 3).", control = stacks(3, 0)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("breezebornerefrain") {
        effect("er", "Viper's Ballad", "Energy Recharge increased.", static = true) { stat(Stat.ER, r(0)) }
        effect("viper", "Thus Lied the Viper", "Party Stellar Glimmer DMG increased.", target = EffectTarget.TEAM, control = toggle()) {
            for (rx in STELLAR_REACTIONS) reactionBonus(rx, r(1))
        }
    }
    this("chainbreaker") {
        effect("buff", "Flower-Feather Song", "ATK per Natlan or other-element party member; EM with 3 or more.", static = true) {
            val n = team.elements.indices.count { i -> team.regions.getOrNull(i) == "Natlan" || team.elements[i] != targetElement }
            stat(Stat.ATK_PCT, r(0) * n)
            if (n >= 3) stat(Stat.EM, r(1))
        }
    }
    this("cloudforged") {
        effect("em", "Crag-Chiseled Forge", "EM per stack after Energy decreases (max 2).", control = stacks(2)) { stat(Stat.EM, r(0) * stacks) }
    }
    this("compoundbow") {
        effect("atk", "Infusion Arrow", "ATK per Normal/Charged hit (max 4).", control = stacks(4)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("covenantoffrostandsnow") {
        effect("em", "The Law's Equilibrium", "EM increased after a Skill.", control = toggle()) { stat(Stat.EM, r(0)) }
    }
    this("endoftheline") {}
    this("fadingtwilight") {
        effect("state", "Radiance of the Deeps", "DMG by state: Evengleam / Afterglow / Dawnblaze.",
            control = choice("Evengleam", "Afterglow", "Dawnblaze", default = 2)) {
            stat(Stat.ALL_DMG, r(0) * listOf(1.0, 5.0 / 3, 7.0 / 3)[value])
        }
    }
    this("favoniuswarbow") {}
    this("flowerwreathedfeathers") {
        effect("ca", "Inflorescence Unattainable", "Charged Attack DMG per 0.5s of aiming (max 6).", control = stacks(6)) {
            dmgBonus(r(0) * stacks, HitFilter.CHARGED)
        }
    }
    this("hamayumi") {
        effect("dmg", "Full Draw", "Normal and Charged Attack DMG increased; doubled at full Energy.",
            control = choice("Base", "Full Energy (x2)")) {
            val k = if (value == 1) 2.0 else 1.0
            dmgBonus(r(0) * k, HitFilter.NORMAL)
            dmgBonus(r(1) * k, HitFilter.CHARGED)
        }
    }
    this("ibispiercer") {
        effect("em", "Secret Wisdom's Favor", "EM per Charged Attack hit (max 2).", control = stacks(2)) { stat(Stat.EM, r(0) * stacks) }
    }
    this("jadevista") {
        effect("buff", "A Candle Woven From the Night", "EM per same-element member, ATK per other-element member (max 3 in total).", static = true) {
            val others = team.elements.size - 1
            val same = (team.elements.count { it == targetElement } - 1).coerceAtLeast(0).coerceAtMost(3)
            val diff = (others - same).coerceAtLeast(0).coerceAtMost(3 - same)
            stat(Stat.EM, r(0) * same)
            stat(Stat.ATK_PCT, r(1) * diff)
        }
    }
    this("kingssquire") {
        effect("em", "Teachings of the Forest", "EM increased after a Skill or Burst.", control = toggle()) { stat(Stat.EM, r(0)) }
    }
    this("mitternachtswaltz") {
        effect("skill", "Evernight Duet (Skill)", "Elemental Skill DMG increased after a Normal Attack hit.", control = toggle()) {
            dmgBonus(r(0), HitFilter.SKILL)
        }
        effect("normal", "Evernight Duet (Normal)", "Normal Attack DMG increased after a Skill hit.", control = toggle()) {
            dmgBonus(r(1), HitFilter.NORMAL)
        }
    }
    this("mouunsmoon") {
        effect("burst", "Watatsumi Wavewalker", "Burst DMG per point of the party's combined Energy capacity (capped).") {
            dmgBonus((r(0) * teamSumOf { it.burstEnergyCost }).coerceAtMost(r(1)), HitFilter.BURST)
        }
    }
    this("predator") {
        effect("aloy", "Strong Strike (Aloy)", "Aloy: ATK +66.", static = true) { if (targetCharacterId == "aloy") stat(Stat.ATK, 66.0) }
        effect("cryo", "Strong Strike", "Normal and Charged Attack DMG +10% per stack after dealing Cryo DMG (max 2).", control = stacks(2)) {
            dmgBonus(0.10 * stacks, HitFilter.NORMAL_CHARGED)
        }
    }
    this("prototypecrescent") {
        effect("atk", "Unreturning", "ATK increased after a Charged Attack hits a weak point.", control = toggle(false)) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("rainbowserpentsrainbow") {
        effect("atk", "Astral Whispers", "ATK increased after off-field hits.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("rangegauge") {
        effect("struggle", "Struggle", "ATK and All Elemental DMG per Unity's Symbol consumed (max 3).", control = stacks(3)) {
            stat(Stat.ATK_PCT, r(0) * stacks)
            allElementalDmg(r(1) * stacks)
        }
    }
    this("royalbow") {
        effect("cr", "Focus", "CRIT Rate per stack until a CRIT (max 5).", control = stacks(5, 2)) { stat(Stat.CRIT_RATE, r(0) * stacks) }
    }
    this("rust") {
        effect("dmg", "Rapid Firing", "Normal Attack DMG increased; Charged Attack DMG -10%.") {
            dmgBonus(r(0), HitFilter.NORMAL)
            dmgBonus(-0.10, HitFilter.CHARGED)
        }
    }
    this("sacrificialbow") {}
    this("scionoftheblazingsun") {
        effect("heartsearer", "Heartsearer", "Charged Attack DMG vs opponents with Heartsearer.", control = toggle()) {
            dmgBonus(r(1), HitFilter.CHARGED)
        }
    }
    this("sequenceofsolitude") {}
    this("snarehook") {
        effect("em", "Phantom Flash", "EM increased after a reaction (more in Ascendant Gleam).", control = toggle()) {
            stat(Stat.EM, r(0) + if (team.moonsign() >= 2) r(1) else 0.0)
        }
    }
    this("songofstillness") {
        effect("dmg", "Benthic Pulse", "DMG increased after being healed.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("thestringless") {
        effect("dmg", "Arrowless Song", "Elemental Skill and Burst DMG increased.") { dmgBonus(r(0), HitFilter.SKILL_BURST) }
    }
    this("theviridescenthunt") {}
    this("windblumeode") {
        effect("atk", "Windblume Wish", "ATK increased after a Skill.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }

    // 3-star
    this("messenger") {}
    this("ravenbow") {
        effect("dmg", "Bane of Flame and Water", "DMG vs opponents affected by Hydro or Pyro.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("recurvebow") {}
    this("sharpshootersoath") {
        effect("dmg", "Precise", "DMG against weak spots.", control = toggle(false)) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("slingshot") {
        effect("dmg", "Slingshot", "Normal/Charged DMG if the hit lands within 0.3s (otherwise -10%).", control = choice("Within 0.3s", "Later (-10%)")) {
            dmgBonus(if (value == 0) r(0) else -0.10, HitFilter.NORMAL_CHARGED)
        }
    }
}
