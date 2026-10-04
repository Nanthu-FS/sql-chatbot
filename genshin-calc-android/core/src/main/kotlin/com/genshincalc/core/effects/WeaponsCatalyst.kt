package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat

private val STELLAR_REACTIONS = listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)
private val LUNAR_REACTIONS = listOf(Reaction.LUNAR_CHARGED, Reaction.LUNAR_BLOOM, Reaction.LUNAR_CRYSTALLIZE)

internal fun EffectTable.catalystEffects() {
    this("angelosheptades") {
        effect("atk", "Crown of the Final Scion", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("light", "Pathfinder's Light", "After creating a shield: the active member's DMG + X per 1,000 of the wielder's ATK (capped).",
            target = EffectTarget.TEAM, control = toggle(), phase = EffectPhase.TEAM_STAT) {
            stat(Stat.ALL_DMG, (r(1) * ownerStats.atk / 1000).coerceAtMost(r(2)))
        }
    }
    this("athousandfloatingdreams") {
        effect("self", "A Thousand Nights' Dawnsong", "Per other member: same element EM, different element own-element DMG (3 stacks).", static = true) {
            val same = (team.elements.count { it == targetElement } - 1).coerceAtLeast(0)
            val diff = (team.size - 1 - same).coerceAtLeast(0)
            stat(Stat.EM, r(0) * same)
            if (targetElement != Element.PHYSICAL) elementDmg(targetElement, r(1) * diff)
        }
        effect("party", "A Thousand Nights' Dawnsong (party)", "Other party members gain EM.", target = EffectTarget.TEAM_OTHERS) {
            stat(Stat.EM, r(2))
        }
    }
    this("cashflowsupervision") {
        effect("atk", "Golden Blood-Tide", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("tide", "Golden Blood-Tide (HP change)", "Normal, Charged and Stellar-Conduct DMG per stack (max 3).", control = stacks(3)) {
            dmgBonus(r(1) * stacks, HitFilter.NORMAL)
            dmgBonus(r(2) * stacks, HitFilter.CHARGED)
            reactionBonus(Reaction.STELLAR_CONDUCT, r(3) * stacks)
        }
    }
    this("cranesechoingcall") {
        effect("plunge", "Cloudfall Axiom", "Party Plunging Attack DMG increased after the wielder's Plunge hits.",
            target = EffectTarget.TEAM, control = toggle()) {
            dmgBonus(r(0), HitFilter.PLUNGE)
        }
    }
    this("everlastingmoonglow") {
        effect("heal", "Byakuya Kougetsu", "Healing Bonus increased.", static = true) { stat(Stat.HEALING_BONUS, r(0)) }
        effect("na", "Byakuya Kougetsu (Normal Attack)", "Normal Attack DMG increased by a % of Max HP.", phase = EffectPhase.CONVERSION) {
            flatDmg(r(1) * targetStats.hp, HitFilter.NORMAL)
        }
    }
    this("hymnofthemaelstrom") {
        effect("heal", "Rondo of Slumber", "Healing Bonus increased.", static = true) { stat(Stat.HEALING_BONUS, r(0)) }
        effect(
            "vintage", "Vatsamonga's Vatic Vintage", "Per stack (max 3): wielder Max HP, and the active member's ATK per 1,000 Max HP above 40,000 (capped).",
            target = EffectTarget.TEAM, control = stacks(3), phase = EffectPhase.TEAM_STAT,
        ) {
            if (isSelf) stat(Stat.HP_PCT, r(1) * stacks)
            val atk = (r(2) * (ownerStats.hp - 40000).coerceAtLeast(0.0) / 1000).coerceAtMost(r(3))
            stat(Stat.ATK_PCT, atk * stacks)
        }
    }
    this("jadefallssplendor") {
        effect("regalia", "Primordial Jade Regalia", "Own-element DMG per 1,000 Max HP (capped) after a Burst or shield.",
            control = toggle(), phase = EffectPhase.CONVERSION) {
            if (targetElement != Element.PHYSICAL) elementDmg(targetElement, (r(1) * targetStats.hp / 1000).coerceAtMost(r(2)))
        }
    }
    this("kagurasverity") {
        effect("dance", "Kagura Dance", "Skill and Stellar-Conduct DMG per stack (max 3); all Elemental DMG at 3 stacks.", control = stacks(3)) {
            dmgBonus(r(0) * stacks, HitFilter.SKILL)
            reactionBonus(Reaction.STELLAR_CONDUCT, r(1) * stacks)
            if (stacks >= 3) allElementalDmg(r(2))
        }
    }
    this("lostprayertothesacredwinds") {
        effect("ele", "Boundless Blessing", "Elemental DMG Bonus every 4s in combat (max 4).", control = stacks(4)) { allElementalDmg(r(0) * stacks) }
    }
    this("memoryofdust") {
        effect("shield", "Golden Majesty", "Shield Strength increased.", static = true) { stat(Stat.SHIELD_STRENGTH, r(0)) }
        effect("atk", "Golden Majesty (ATK)", "ATK per stack (max 5); doubled while shielded (count 10).", control = stacks(10)) {
            stat(Stat.ATK_PCT, r(1) * stacks)
        }
    }
    this("nightweaverslookingglass") {
        effect("prayer", "Prayer of the Far North", "EM increased after Hydro/Dendro Skill DMG.", control = toggle()) { stat(Stat.EM, r(0)) }
        effect("verse", "New Moon Verse", "EM increased after party Lunar-Bloom reactions.", control = toggle()) { stat(Stat.EM, r(1)) }
        effect("both", "Millennial Hymn (both active)", "Party Bloom, Hyperbloom/Burgeon and Lunar-Bloom DMG increased.",
            target = EffectTarget.TEAM, control = toggle()) {
            reactionBonus(Reaction.BLOOM, r(2))
            reactionBonus(Reaction.HYPERBLOOM, r(3))
            reactionBonus(Reaction.BURGEON, r(3))
            reactionBonus(Reaction.LUNAR_BLOOM, r(4))
        }
    }
    this("nocturnescurtaincall") {
        effect("hp", "Ballad of the Crossroads", "Max HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("wine", "Bountiful Sea's Sacred Wine", "Max HP and Lunar reaction CRIT DMG increased after Lunar reactions.", control = toggle()) {
            stat(Stat.HP_PCT, r(2))
            for (rx in LUNAR_REACTIONS) reactionCrit(rx, 0.0, r(3))
        }
    }
    this("reliquaryoftruth") {
        effect("cr", "Essence of Falsity", "CRIT Rate increased.", static = true) { stat(Stat.CRIT_RATE, r(0)) }
        effect("truth", "Secret of Lies / Moon of Truth", "EM after a Skill, CRIT DMG after Lunar-Bloom DMG; +50% each when both are active.",
            control = choice("Both", "Secret of Lies only", "Moon of Truth only", "None")) {
            val k = if (value == 0) 1.5 else 1.0
            if (value == 0 || value == 1) stat(Stat.EM, r(1) * k)
            if (value == 0 || value == 2) stat(Stat.CRIT_DMG, r(2) * k)
        }
    }
    this("skywardatlas") {
        effect("ele", "Wandering Clouds", "Elemental DMG Bonus.", static = true) { allElementalDmg(r(0)) }
    }
    this("starcallerswatch") {
        effect("em", "Offering Unto Wind and Sun", "EM increased.", static = true) { stat(Stat.EM, r(0)) }
        effect("mirror", "Mirror of Night", "After creating a shield: the active member deals more DMG.", target = EffectTarget.TEAM, control = toggle()) {
            stat(Stat.ALL_DMG, r(1))
        }
    }
    this("sunnymorningsleepin") {
        effect("swirl", "Bathhouses (Swirl)", "EM increased after a Swirl.", control = toggle()) { stat(Stat.EM, r(0)) }
        effect("skill", "Hawks (Skill hit)", "EM increased after a Skill hit.", control = toggle()) { stat(Stat.EM, r(1)) }
        effect("burst", "Narukami (Burst hit)", "EM increased after a Burst hit.", control = toggle()) { stat(Stat.EM, r(2)) }
    }
    this("surfsup") {
        effect("hp", "Aqua Remembrance", "Max HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("summer", "Scorching Summer", "Normal Attack DMG per stack (max 4).", control = stacks(4)) { dmgBonus(r(1) * stacks, HitFilter.NORMAL) }
    }
    this("tomeoftheeternalflow") {
        effect("hp", "Aeon Wave", "HP increased.", static = true) { stat(Stat.HP_PCT, r(0)) }
        effect("ca", "Aeon Wave (HP change)", "Charged Attack DMG per stack (max 3).", control = stacks(3)) { dmgBonus(r(1) * stacks, HitFilter.CHARGED) }
    }
    this("tulaytullahsremembrance") {
        effect("na", "Bygone Azure Teardrop", "Normal Attack DMG builds up after a Skill (maximum shown).", control = toggle()) {
            dmgBonus(r(3), HitFilter.NORMAL)
        }
    }
    this("vividnotions") {
        effect("atk", "Falling Rainbow's Wish", "ATK increased.", static = true) { stat(Stat.ATK_PCT, r(0)) }
        effect("dawn", "Dawn's First Hue", "Plunging Attack CRIT DMG after a Plunge.", control = toggle()) { critDmg(r(1), HitFilter.PLUNGE) }
        effect("twilight", "Twilight's Splendor", "Plunging Attack CRIT DMG after a Skill or Burst.", control = toggle()) { critDmg(r(2), HitFilter.PLUNGE) }
    }

    // 4-star
    this("ashgravendrinkinghorn") {}
    this("balladoftheboundlessblue") {
        effect("sky", "Azure Skies", "Normal and Charged Attack DMG per stack (max 3).", control = stacks(3)) {
            dmgBonus(r(0) * stacks, HitFilter.NORMAL)
            dmgBonus(r(1) * stacks, HitFilter.CHARGED)
        }
    }
    this("blackcliffagate") {
        effect("atk", "Press the Advantage", "ATK per defeated opponent (max 3).", control = stacks(3, 0)) { stat(Stat.ATK_PCT, r(0) * stacks) }
    }
    this("blackmarrowlantern") {
        effect("bloom", "Token of Covenant", "Bloom and Lunar-Bloom DMG increased (more Lunar-Bloom in Ascendant Gleam).") {
            reactionBonus(Reaction.BLOOM, r(0))
            reactionBonus(Reaction.LUNAR_BLOOM, r(1) + if (team.moonsign() >= 2) r(2) else 0.0)
        }
    }
    this("clashofkings") {
        effect("laws", "Laws of the Board", "ATK and EM after a Skill.", control = toggle()) {
            stat(Stat.ATK_PCT, r(0))
            stat(Stat.EM, r(1))
        }
    }
    this("dawningfrost") {
        effect("ca", "Nocturnal Dreams (Charged)", "EM after a Charged Attack hit.", control = toggle()) { stat(Stat.EM, r(0)) }
        effect("skill", "Nocturnal Dreams (Skill)", "EM after a Skill hit.", control = toggle()) { stat(Stat.EM, r(1)) }
    }
    this("dodocotales") {
        effect("ca", "Dodoventure! (Charged)", "Charged Attack DMG after a Normal Attack hit.", control = toggle()) { dmgBonus(r(0), HitFilter.CHARGED) }
        effect("atk", "Dodoventure! (ATK)", "ATK after a Charged Attack hit.", control = toggle()) { stat(Stat.ATK_PCT, r(1)) }
    }
    this("echoesoftheheart") {
        effect("em", "Echo of a Vow", "EM after a reaction.", control = toggle()) { stat(Stat.EM, r(0)) }
        effect("stellar", "Echo of a Vow (Stellar)", "Stellar Glimmer DMG after a Stellar reaction.", control = toggle(false)) {
            for (rx in STELLAR_REACTIONS) reactionBonus(rx, r(1))
        }
    }
    this("etherlightspindlelute") {
        effect("em", "Last Singer", "EM after a Skill.", control = toggle()) { stat(Stat.EM, r(0)) }
    }
    this("eyeofperception") {}
    this("favoniuscodex") {}
    this("flowingpurity") {
        effect("ele", "Unfinished Masterpiece", "All Elemental DMG after a Skill.", control = toggle()) { allElementalDmg(r(0)) }
        effect("bond", "Unfinished Masterpiece (Bond cleared)", "All Elemental DMG per 1,000 HP of Bond cleared (24% of Max HP), capped.",
            control = toggle(), phase = EffectPhase.CONVERSION) {
            allElementalDmg((r(1) * 0.24 * targetStats.hp / 1000).coerceAtMost(r(2)))
        }
    }
    this("frostbearer") {}
    this("fruitoffulfillment") {
        effect("wax", "Wax and Wane", "EM +X and ATK -5% per stack (max 5).", control = stacks(5)) {
            stat(Stat.EM, r(0) * stacks)
            stat(Stat.ATK_PCT, -0.05 * stacks)
        }
    }
    this("hakushinring") {
        effect("ele", "Sakura Saiguu", "After the wielder's Electro reactions, members of the involved elements gain own-element DMG.",
            target = EffectTarget.TEAM, control = toggle()) {
            if (targetElement != Element.GEO && targetElement != Element.PHYSICAL) elementDmg(targetElement, r(0))
        }
    }
    this("mappamare") {
        effect("ele", "Infusion Scroll", "Elemental DMG per reaction (max 2).", control = stacks(2)) { allElementalDmg(r(0) * stacks) }
    }
    this("oathsworneye") {
        effect("er", "People of the Faltering Light", "Energy Recharge after a Skill.", control = toggle()) { stat(Stat.ER, r(0)) }
    }
    this("prototypeamber") {}
    this("ringofyaxche") {
        effect("na", "Jade-Forged Crown", "Normal Attack DMG per 1,000 Max HP (capped) after a Skill.", control = toggle(), phase = EffectPhase.CONVERSION) {
            dmgBonus((r(0) * targetStats.hp / 1000).coerceAtMost(r(1)), HitFilter.NORMAL)
        }
    }
    this("royalgrimoire") {
        effect("cr", "Focus", "CRIT Rate per stack until a CRIT (max 5).", control = stacks(5, 2)) { stat(Stat.CRIT_RATE, r(0) * stacks) }
    }
    this("sacrificialfragments") {}
    this("sacrificialjade") {
        effect("off", "Jade Circulation", "While off-field for over 5s: Max HP and EM increased.", control = toggle(false)) {
            stat(Stat.HP_PCT, r(0))
            stat(Stat.EM, r(1))
        }
    }
    this("solarpearl") {
        effect("skill", "Solar Shine (Skill/Burst)", "Skill and Burst DMG after a Normal Attack hit.", control = toggle()) {
            dmgBonus(r(0), HitFilter.SKILL_BURST)
        }
        effect("normal", "Solar Shine (Normal)", "Normal Attack DMG after a Skill or Burst hit.", control = toggle()) { dmgBonus(r(1), HitFilter.NORMAL) }
    }
    this("thewidsith") {
        effect("song", "Debut", "Random theme song on switch-in.", control = choice("Recitative (ATK)", "Aria (Elemental DMG)", "Interlude (EM)", "None")) {
            when (value) {
                0 -> stat(Stat.ATK_PCT, r(0))
                1 -> allElementalDmg(r(1))
                2 -> stat(Stat.EM, r(2))
            }
        }
    }
    this("wanderingevenstar") {
        effect("atk", "Wildling Nightstar", "ATK equal to a % of EM.", control = toggle(), phase = EffectPhase.CONVERSION) {
            stat(Stat.ATK, r(0) * targetStats.em)
        }
        effect("party", "Wildling Nightstar (party)", "Other members gain 30% of the ATK bonus.",
            target = EffectTarget.TEAM_OTHERS, control = toggle(), phase = EffectPhase.TEAM_STAT) {
            stat(Stat.ATK, 0.3 * r(0) * ownerStats.em)
        }
    }
    this("waveridingwhirl") {
        effect("hp", "Fangs Flying To and Fro", "Max HP after a Skill, more per Hydro member (capped).", control = toggle()) {
            stat(Stat.HP_PCT, r(0) + (r(1) * team.count(Element.HYDRO)).coerceAtMost(r(2)))
        }
    }
    this("wineandsong") {
        effect("atk", "Ever-Changing", "ATK after sprinting.", control = toggle(false)) { stat(Stat.ATK_PCT, r(1)) }
    }
    this("wintersheavyheart") {
        effect("pact", "Silver-Tinged Blood Pact", "EM per Cryo member and ATK per Electro member; in Radiance: EM and Stellar Glimmer DMG per Cryo/Electro member.",
            control = choice("Normal", "Radiance: Stellar Glimmer")) {
            val cryo = team.count(Element.CRYO)
            val electro = team.count(Element.ELECTRO)
            if (value == 0) {
                stat(Stat.EM, r(0) * cryo)
                stat(Stat.ATK_PCT, r(1) * electro)
            } else {
                val n = (cryo + electro).coerceAtMost(4)
                stat(Stat.EM, r(2) * n)
                for (rx in STELLAR_REACTIONS) reactionBonus(rx, r(3) * n)
            }
        }
    }

    // 3-star
    this("emeraldorb") {
        effect("atk", "Rapids", "ATK after Hydro-related reactions.", control = toggle()) { stat(Stat.ATK_PCT, r(0)) }
    }
    this("magicguide") {
        effect("dmg", "Bane of Storm and Tide", "DMG vs opponents affected by Hydro or Electro.", control = toggle()) { stat(Stat.ALL_DMG, r(0)) }
    }
    this("otherworldlystory") {}
    this("thrillingtalesofdragonslayers") {
        effect("atk", "Heritage", "The next character to take the field gains ATK.", target = EffectTarget.TEAM_OTHERS, control = toggle()) {
            stat(Stat.ATK_PCT, r(0))
        }
    }
    this("twinnephrite") {
        effect("atk", "Guerilla Tactics", "ATK after defeating an opponent.", control = toggle(false)) { stat(Stat.ATK_PCT, r(0)) }
    }
}
