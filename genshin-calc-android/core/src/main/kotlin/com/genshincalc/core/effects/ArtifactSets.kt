package com.genshincalc.core.effects

import com.genshincalc.core.calc.EffectPhase
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.HitFilter
import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.WeaponType

/** Requirement: at least [n] pieces of the set being defined. */
private fun EffectListBuilder.pc(setId: String, n: Int) = Req("${n}pc") { it.setPieces(setId) >= n }

private fun EffectTable.set(id: String, block: EffectListBuilder.(two: Req, four: Req) -> Unit) {
    this(id) { block(pc(id, 2), pc(id, 4)) }
}

private val LUNAR = listOf(Reaction.LUNAR_CHARGED, Reaction.LUNAR_BLOOM, Reaction.LUNAR_CRYSTALLIZE)
private val STELLAR = listOf(Reaction.STELLAR_CONDUCT, Reaction.STELLAR_SWIRL)

/** 2pc bonuses that show on the character screen. */
private fun EffectListBuilder.static2(two: Req, name: String, description: String, apply: com.genshincalc.core.calc.EffectScope.() -> Unit) =
    effect("2", "$name 2pc", description, static = true, requires = two, apply = apply)

internal fun EffectTable.artifactSetEffects() {
    set("adaycarvedfromrisingwinds") { two, four ->
        static2(two, "A Day Carved From Rising Winds", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Blessing of Pastoral Winds", "ATK +25% for 6s after hitting an opponent.", control = toggle(), requires = four) {
            stat(Stat.ATK_PCT, 0.25)
        }
        effect("4w", "Resolve of Pastoral Winds", "With Witch's Homework completed: CRIT Rate +20%.", control = toggle(false), requires = four) {
            stat(Stat.CRIT_RATE, 0.20)
        }
    }
    set("archaicpetra") { two, four ->
        static2(two, "Archaic Petra", "Geo DMG Bonus +15%.") { elementDmg(Element.GEO, 0.15) }
        effect("4", "Archaic Petra 4pc", "Party gains 35% DMG Bonus for the element of the picked-up Crystallize shard.",
            target = EffectTarget.TEAM, control = choice("Pyro", "Hydro", "Electro", "Cryo", "Off"), requires = four) {
            if (value < 4) elementDmg(SWIRLABLE[value], 0.35)
        }
    }
    set("aubadeofmorningstarandmoon") { two, four ->
        static2(two, "Aubade of Morningstar and Moon", "EM +80.") { stat(Stat.EM, 80.0) }
        effect("4", "Aubade of Morningstar and Moon 4pc", "Off-field: Lunar reaction DMG +20% (+40% more in Ascendant Gleam).",
            control = toggle(false), requires = four) {
            for (r in LUNAR) reactionBonus(r, 0.20 + if (team.moonsign() >= 2) 0.40 else 0.0)
        }
    }
    set("blizzardstrayer") { two, four ->
        static2(two, "Blizzard Strayer", "Cryo DMG Bonus +15%.") { elementDmg(Element.CRYO, 0.15) }
        effect("4", "Blizzard Strayer 4pc", "CRIT Rate +20% vs Cryo-affected opponents, +40% if Frozen.",
            control = choice("Cryo-affected", "Frozen", "Off"), requires = four) {
            if (value < 2) stat(Stat.CRIT_RATE, if (value == 1) 0.40 else 0.20)
        }
    }
    set("bloodstainedchivalry") { two, four ->
        static2(two, "Bloodstained Chivalry", "Physical DMG +25%.") { elementDmg(Element.PHYSICAL, 0.25) }
        effect("4", "Bloodstained Chivalry 4pc", "Charged Attack DMG +50% after defeating an opponent.", control = toggle(false), requires = four) {
            dmgBonus(0.50, HitFilter.CHARGED)
        }
    }
    set("celestialgift") { two, four ->
        static2(two, "Celestial Gift", "Energy Recharge +20%.") { stat(Stat.ER, 0.20) }
        effect("4", "Light's Guidance", "With Witch's Homework: party DMG Bonus of the wearer's element +20% (Mortal Hymn with Hexerei: +40%).",
            target = EffectTarget.TEAM, control = choice("Off", "Light's Guidance (+20%)", "Mortal Hymn (+40%)"), requires = four) {
            if (value > 0 && owner.element != Element.PHYSICAL) elementDmg(owner.element, if (value == 2) 0.40 else 0.20)
        }
    }
    set("crimsonwitchofflames") { two, four ->
        static2(two, "Crimson Witch of Flames", "Pyro DMG Bonus +15%.") { elementDmg(Element.PYRO, 0.15) }
        effect("4", "Crimson Witch 4pc", "Overloaded/Burning/Burgeon DMG +40%, Vaporize/Melt DMG +15%.", requires = four) {
            reactionBonus(Reaction.OVERLOADED, 0.40); reactionBonus(Reaction.BURNING, 0.40); reactionBonus(Reaction.BURGEON, 0.40)
            reactionBonus(Reaction.VAPORIZE, 0.15); reactionBonus(Reaction.MELT, 0.15)
        }
        effect("4s", "Crimson Witch 4pc stacks", "Each Elemental Skill use: 2pc bonus +50% (max 3 stacks).",
            control = stacks(3, 1), requires = four) { elementDmg(Element.PYRO, 0.075 * stacks) }
    }
    set("deepwoodmemories") { two, four ->
        static2(two, "Deepwood Memories", "Dendro DMG Bonus +15%.") { elementDmg(Element.DENDRO, 0.15) }
        effect("4", "Deepwood Memories 4pc", "Skill or Burst hits lower Dendro RES by 30%.", target = EffectTarget.TEAM, control = toggle(), requires = four) {
            resShred(Element.DENDRO, 0.30)
        }
    }
    set("desertpavilionchronicle") { two, four ->
        static2(two, "Desert Pavilion Chronicle", "Anemo DMG Bonus +15%.") { elementDmg(Element.ANEMO, 0.15) }
        effect("4", "Desert Pavilion Chronicle 4pc", "Normal, Charged and Plunging Attack DMG +40% after a Charged Attack hits.",
            control = toggle(), requires = four) {
            dmgBonus(0.40, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }
    set("disenchantmentindeepshadow") { two, four ->
        static2(two, "Disenchantment in Deep Shadow", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Disenchantment in Deep Shadow 4pc", "Superconduct DMG +80%, Stellar-Conduct DMG +40%.", requires = four) {
            reactionBonus(Reaction.SUPERCONDUCT, 0.80)
            reactionBonus(Reaction.STELLAR_CONDUCT, 0.40)
        }
        effect("4c", "Disenchantment in Deep Shadow 4pc (CRIT)", "CRIT Rate +16% vs opponents affected by Superconduct or Stellar-Conduct.",
            control = toggle(false), requires = four) {
            stat(Stat.CRIT_RATE, 0.16)
        }
    }
    set("echoesofanoffering") { two, four ->
        static2(two, "Echoes of an Offering", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Valley Rite", "Normal Attacks deal +70% of ATK when Valley Rite triggers (~50% of hits on average).",
            control = choice("Average (~50% of hits)", "Every hit", "Off"), phase = EffectPhase.CONVERSION, requires = four) {
            if (value < 2) flatDmg(0.70 * targetStats.atk * if (value == 0) 0.5 else 1.0, HitFilter.NORMAL)
        }
    }
    set("emblemofseveredfate") { two, four ->
        static2(two, "Emblem of Severed Fate", "Energy Recharge +20%.") { stat(Stat.ER, 0.20) }
        effect("4", "Emblem of Severed Fate 4pc", "Elemental Burst DMG +25% of Energy Recharge (max 75%).",
            phase = EffectPhase.CONVERSION, requires = four) {
            dmgBonus(minOf(0.25 * targetStats.er, 0.75), HitFilter.BURST)
        }
    }
    set("finaleofthedeepgalleries") { two, four ->
        static2(two, "Finale of the Deep Galleries", "Cryo DMG Bonus +15%.") { elementDmg(Element.CRYO, 0.15) }
        effect("4n", "Finale of the Deep Galleries 4pc (Normal)", "At 0 Energy: Normal Attack DMG +60%.", control = toggle(), requires = four) {
            dmgBonus(0.60, HitFilter.NORMAL)
        }
        effect("4b", "Finale of the Deep Galleries 4pc (Burst)", "At 0 Energy: Elemental Burst DMG +60%.", control = toggle(), requires = four) {
            dmgBonus(0.60, HitFilter.BURST)
        }
    }
    set("flowerofparadiselost") { two, four ->
        static2(two, "Flower of Paradise Lost", "EM +80.") { stat(Stat.EM, 80.0) }
        effect("4", "Flower of Paradise Lost 4pc", "Bloom/Hyperbloom/Burgeon DMG +40% and Lunar-Bloom +10%; +25% of that per stack (max 4).",
            control = stacks(4), requires = four) {
            val k = 1 + 0.25 * stacks
            for (r in listOf(Reaction.BLOOM, Reaction.HYPERBLOOM, Reaction.BURGEON)) reactionBonus(r, 0.40 * k)
            reactionBonus(Reaction.LUNAR_BLOOM, 0.10 * k)
        }
    }
    set("fragmentofharmonicwhimsy") { two, four ->
        static2(two, "Fragment of Harmonic Whimsy", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Fragment of Harmonic Whimsy 4pc", "DMG +18% per Bond of Life change (max 3).", control = stacks(3), requires = four) {
            stat(Stat.ALL_DMG, 0.18 * stacks)
        }
    }
    set("gildeddreams") { two, four ->
        static2(two, "Gilded Dreams", "EM +80.") { stat(Stat.EM, 80.0) }
        effect("4", "Gilded Dreams 4pc", "After a reaction: ATK +14% per same-element member, EM +50 per other-element member (max 3).",
            control = toggle(), requires = four) {
            val same = (team.elements.count { it == targetElement } - 1).coerceIn(0, 3)
            val diff = (team.size - 1 - same).coerceIn(0, 3)
            stat(Stat.ATK_PCT, 0.14 * same)
            stat(Stat.EM, 50.0 * diff)
        }
    }
    set("gladiatorsfinale") { two, four ->
        static2(two, "Gladiator's Finale", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Gladiator's Finale 4pc", "Normal Attack DMG +35% for Sword, Claymore and Polearm users.", requires = four) {
            if (target.weaponType.isMelee) dmgBonus(0.35, HitFilter.NORMAL)
        }
    }
    set("goldentroupe") { two, four ->
        effect("2", "Golden Troupe 2pc", "Elemental Skill DMG +20%.", requires = two) { dmgBonus(0.20, HitFilter.SKILL) }
        effect("4", "Golden Troupe 4pc", "Elemental Skill DMG +25%.", requires = four) { dmgBonus(0.25, HitFilter.SKILL) }
        effect("4o", "Golden Troupe 4pc (off-field)", "While off-field: Elemental Skill DMG +25% more.", control = toggle(), requires = four) {
            dmgBonus(0.25, HitFilter.SKILL)
        }
    }
    set("heartofdepth") { two, four ->
        static2(two, "Heart of Depth", "Hydro DMG Bonus +15%.") { elementDmg(Element.HYDRO, 0.15) }
        effect("4", "Heart of Depth 4pc", "Normal and Charged Attack DMG +30% after a Skill.", control = toggle(), requires = four) {
            dmgBonus(0.30, HitFilter.NORMAL_CHARGED)
        }
    }
    set("heartofthefurnace") { two, four ->
        static2(two, "Heart of the Furnace", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Heart of the Furnace 4pc", "Party Stellar Glimmer DMG +50%.", target = EffectTarget.TEAM, control = toggle(), requires = four) {
            for (r in STELLAR) reactionBonus(r, 0.50)
        }
        effect("4a", "Heart of the Furnace 4pc (ATK)", "ATK +12% after Stellar Glimmer reactions.", control = toggle(false), requires = four) {
            stat(Stat.ATK_PCT, 0.12)
        }
    }
    set("huskofopulentdreams") { two, four ->
        static2(two, "Husk of Opulent Dreams", "DEF +30%.") { stat(Stat.DEF_PCT, 0.30) }
        effect("4", "Curiosity", "DEF +6% and Geo DMG +6% per stack (max 4).", control = stacks(4), requires = four) {
            stat(Stat.DEF_PCT, 0.06 * stacks)
            elementDmg(Element.GEO, 0.06 * stacks)
        }
    }
    set("lavawalker") { _, four ->
        effect("4", "Lavawalker 4pc", "DMG +35% vs opponents affected by Pyro.", control = toggle(false), requires = four) { stat(Stat.ALL_DMG, 0.35) }
    }
    set("longnightsoath") { two, four ->
        effect("2", "Long Night's Oath 2pc", "Plunging Attack DMG +25%.", requires = two) { dmgBonus(0.25, HitFilter.PLUNGE) }
        effect("4", "Radiance Everlasting", "Plunging Attack DMG +15% per stack (max 5).", control = stacks(5), requires = four) {
            dmgBonus(0.15 * stacks, HitFilter.PLUNGE)
        }
    }
    set("maidenbeloved") { two, four ->
        static2(two, "Maiden Beloved", "Healing Bonus +15%.") { stat(Stat.HEALING_BONUS, 0.15) }
        effect("4", "Maiden Beloved 4pc", "Party incoming healing +20% after a Skill or Burst.", target = EffectTarget.TEAM, control = toggle(), requires = four) {
            stat(Stat.INCOMING_HEALING, 0.20)
        }
    }
    set("marechausseehunter") { two, four ->
        effect("2", "Marechaussee Hunter 2pc", "Normal and Charged Attack DMG +15%.", requires = two) { dmgBonus(0.15, HitFilter.NORMAL_CHARGED) }
        effect("4", "Marechaussee Hunter 4pc", "CRIT Rate +12% per HP change (max 3).", control = stacks(3), requires = four) {
            stat(Stat.CRIT_RATE, 0.12 * stacks)
        }
    }
    set("nightoftheskysunveiling") { two, four ->
        static2(two, "Night of the Sky's Unveiling", "EM +80.") { stat(Stat.EM, 80.0) }
        effect("4", "Gleaming Moon: Intent", "On-field after party Lunar reactions: CRIT Rate +15% (Nascent) / +30% (Ascendant Gleam).",
            control = toggle(), requires = four) {
            stat(Stat.CRIT_RATE, if (team.moonsign() >= 2) 0.30 else 0.15)
        }
        effect("4p", "Gleaming Moon (Intent)", "Party Lunar reaction DMG +10% for this Gleaming Moon effect.",
            target = EffectTarget.TEAM, control = toggle(), requires = four) {
            for (r in LUNAR) reactionBonus(r, 0.10)
        }
    }
    set("nighttimewhispersintheechoingwoods") { two, four ->
        static2(two, "Nighttime Whispers in the Echoing Woods", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Nighttime Whispers 4pc", "Geo DMG +20% after a Skill; +50% under a Crystallize shield or near Moondrifts.",
            control = choice("After Skill (+20%)", "Shielded / Moondrifts (+50%)", "Off"), requires = four) {
            if (value < 2) elementDmg(Element.GEO, if (value == 1) 0.50 else 0.20)
        }
    }
    set("noblesseoblige") { two, four ->
        effect("2", "Noblesse Oblige 2pc", "Elemental Burst DMG +20%.", requires = two) { dmgBonus(0.20, HitFilter.BURST) }
        effect("4", "Noblesse Oblige 4pc", "Party ATK +20% for 12s after using an Elemental Burst.",
            target = EffectTarget.TEAM, control = toggle(), requires = four) { stat(Stat.ATK_PCT, 0.20) }
    }
    set("nymphsdream") { two, four ->
        static2(two, "Nymph's Dream", "Hydro DMG Bonus +15%.") { elementDmg(Element.HYDRO, 0.15) }
        effect("4", "Mirrored Nymph", "ATK +7/16/25% and Hydro DMG +4/9/15% at 1/2/3 stacks.", control = stacks(3), requires = four) {
            if (stacks > 0) {
                stat(Stat.ATK_PCT, listOf(0.07, 0.16, 0.25)[stacks - 1])
                elementDmg(Element.HYDRO, listOf(0.04, 0.09, 0.15)[stacks - 1])
            }
        }
    }
    set("obsidiancodex") { two, four ->
        effect("2", "Obsidian Codex 2pc", "On-field in Nightsoul's Blessing: DMG +15%.", control = toggle(), requires = two) { stat(Stat.ALL_DMG, 0.15) }
        effect("4", "Obsidian Codex 4pc", "CRIT Rate +40% after consuming Nightsoul points on-field.", control = toggle(), requires = four) {
            stat(Stat.CRIT_RATE, 0.40)
        }
    }
    set("oceanhuedclam") { two, _ ->
        static2(two, "Ocean-Hued Clam", "Healing Bonus +15%.") { stat(Stat.HEALING_BONUS, 0.15) }
    }
    set("paleflame") { two, four ->
        static2(two, "Pale Flame", "Physical DMG +25%.") { elementDmg(Element.PHYSICAL, 0.25) }
        effect("4", "Pale Flame 4pc", "ATK +9% per Skill hit (max 2); at 2 stacks the 2pc bonus doubles.", control = stacks(2), requires = four) {
            stat(Stat.ATK_PCT, 0.09 * stacks)
            if (stacks >= 2) elementDmg(Element.PHYSICAL, 0.25)
        }
    }
    set("retracingbolide") { two, four ->
        static2(two, "Retracing Bolide", "Shield Strength +35%.") { stat(Stat.SHIELD_STRENGTH, 0.35) }
        effect("4", "Retracing Bolide 4pc", "While shielded: Normal and Charged Attack DMG +40%.", control = toggle(), requires = four) {
            dmgBonus(0.40, HitFilter.NORMAL_CHARGED)
        }
    }
    set("scarletproof") { two, four ->
        static2(two, "Scarletproof", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Scarletproof 4pc", "After a Stellar Swirl: CRIT Rate +16%, Stellar Swirl DMG +40%.", control = toggle(false), requires = four) {
            stat(Stat.CRIT_RATE, 0.16)
            reactionBonus(Reaction.STELLAR_SWIRL, 0.40)
        }
    }
    set("scrolloftheheroofcindercity") { _, four ->
        effect(
            "4", "Scroll of the Hero of Cinder City 4pc",
            "After the wearer's reactions: party DMG Bonus +12% (+40% in Nightsoul's Blessing) for the elements involved.",
            target = EffectTarget.TEAM, control = choice("Reaction (+12%)", "Nightsoul's Blessing (+40%)", "Off"), requires = four,
        ) {
            if (value < 2) {
                val k = if (value == 1) 0.40 else 0.12
                val elements = mutableSetOf(owner.element)
                if (owner.element == Element.ANEMO || owner.element == Element.GEO) elements += SWIRLABLE.filter { team.count(it) > 0 }
                else elements += targetElement
                for (e in elements - Element.PHYSICAL) elementDmg(e, k)
            }
        }
    }
    set("shimenawasreminiscence") { two, four ->
        static2(two, "Shimenawa's Reminiscence", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Shimenawa's Reminiscence 4pc", "Normal, Charged and Plunging Attack DMG +50% after a Skill (costs 15 Energy).",
            control = toggle(), requires = four) {
            dmgBonus(0.50, HitFilter.NORMAL_CHARGED_PLUNGE)
        }
    }
    set("silkenmoonsserenade") { two, four ->
        static2(two, "Silken Moon's Serenade", "Energy Recharge +20%.") { stat(Stat.ER, 0.20) }
        effect("4", "Gleaming Moon: Devotion", "Party EM +60 (Nascent) / +120 (Ascendant Gleam); party Lunar reaction DMG +10% for this effect.",
            target = EffectTarget.TEAM, control = toggle(), requires = four) {
            stat(Stat.EM, if (team.moonsign() >= 2) 120.0 else 60.0)
            for (r in LUNAR) reactionBonus(r, 0.10)
        }
    }
    set("songofdayspast") { two, four ->
        static2(two, "Song of Days Past", "Healing Bonus +15%.") { stat(Stat.HEALING_BONUS, 0.15) }
        effect(
            "4", "Waves of Days Past", "The active character's attacks deal +8% of the recorded healing (max 15,000) as extra DMG (5 hits).",
            target = EffectTarget.TEAM, control = stacks(15, 15, "Healing recorded (x1,000)"), requires = four,
        ) {
            flatDmg(80.0 * stacks, ALL_TALENT_ATTACKS)
        }
    }
    set("tenacityofthemillelith") { two, four ->
        static2(two, "Tenacity of the Millelith", "HP +20%.") { stat(Stat.HP_PCT, 0.20) }
        effect("4", "Tenacity of the Millelith 4pc", "Party ATK +20% and Shield Strength +30% after a Skill hit.",
            target = EffectTarget.TEAM, control = toggle(), requires = four) {
            stat(Stat.ATK_PCT, 0.20)
            stat(Stat.SHIELD_STRENGTH, 0.30)
        }
    }
    set("thunderingfury") { two, four ->
        static2(two, "Thundering Fury", "Electro DMG Bonus +15%.") { elementDmg(Element.ELECTRO, 0.15) }
        effect("4", "Thundering Fury 4pc", "Overloaded/Electro-Charged/Superconduct/Hyperbloom +40%, Aggravate +20%, Lunar-Charged/Stellar-Conduct +20%.",
            requires = four) {
            for (r in listOf(Reaction.OVERLOADED, Reaction.ELECTRO_CHARGED, Reaction.SUPERCONDUCT, Reaction.HYPERBLOOM)) reactionBonus(r, 0.40)
            reactionBonus(Reaction.AGGRAVATE, 0.20)
            reactionBonus(Reaction.LUNAR_CHARGED, 0.20)
            reactionBonus(Reaction.STELLAR_CONDUCT, 0.20)
        }
    }
    set("thundersoother") { _, four ->
        effect("4", "Thundersoother 4pc", "DMG +35% vs opponents affected by Electro.", control = toggle(false), requires = four) { stat(Stat.ALL_DMG, 0.35) }
    }
    set("unfinishedreverie") { two, four ->
        static2(two, "Unfinished Reverie", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Unfinished Reverie 4pc", "DMG +50% while Burning opponents are nearby.", control = toggle(), requires = four) {
            stat(Stat.ALL_DMG, 0.50)
        }
    }
    set("vermillionhereafter") { two, four ->
        static2(two, "Vermillion Hereafter", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Nascent Light", "After a Burst: ATK +8%, +10% per HP decrease (stacks 2-5 = 1-4 decreases).",
            control = stacks(5, 5, "Stacks (1 = after Burst)"), requires = four) {
            if (stacks > 0) stat(Stat.ATK_PCT, 0.08 + 0.10 * (stacks - 1))
        }
    }
    set("viridescentvenerer") { two, four ->
        static2(two, "Viridescent Venerer", "Anemo DMG Bonus +15%.") { elementDmg(Element.ANEMO, 0.15) }
        effect("4", "Viridescent Venerer 4pc", "Swirl DMG +60%, Stellar Swirl DMG +20%.", requires = four) {
            reactionBonus(Reaction.SWIRL, 0.60)
            reactionBonus(Reaction.STELLAR_SWIRL, 0.20)
        }
        effect("4shred", "Viridescent Venerer 4pc shred", "Swirl lowers the enemy's RES to the swirled element by 40%.",
            target = EffectTarget.TEAM, control = SWIRL_CHOICE, requires = four) {
            for (e in swirlElements()) resShred(e, 0.40)
        }
    }
    set("vourukashasglow") { two, four ->
        static2(two, "Vourukasha's Glow", "HP +20%.") { stat(Stat.HP_PCT, 0.20) }
        effect("4", "Vourukasha's Glow 4pc", "Skill and Burst DMG +10%, +8% per stack after taking DMG (max 5).",
            control = stacks(5, 0), requires = four) {
            dmgBonus(0.10 + 0.08 * stacks, HitFilter.SKILL_BURST)
        }
    }
    set("wandererstroupe") { two, four ->
        static2(two, "Wanderer's Troupe", "EM +80.") { stat(Stat.EM, 80.0) }
        effect("4", "Wanderer's Troupe 4pc", "Charged Attack DMG +35% for Catalyst and Bow users.", requires = four) {
            if (target.weaponType == WeaponType.CATALYST || target.weaponType == WeaponType.BOW) dmgBonus(0.35, HitFilter.CHARGED)
        }
    }

    // 4-star sets
    set("berserker") { two, four ->
        static2(two, "Berserker", "CRIT Rate +12%.") { stat(Stat.CRIT_RATE, 0.12) }
        effect("4", "Berserker 4pc", "CRIT Rate +24% while HP is below 70%.", control = toggle(false), requires = four) { stat(Stat.CRIT_RATE, 0.24) }
    }
    set("braveheart") { two, four ->
        static2(two, "Brave Heart", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Brave Heart 4pc", "DMG +30% vs opponents above 50% HP.", control = toggle(), requires = four) { stat(Stat.ALL_DMG, 0.30) }
    }
    set("defenderswill") { two, _ ->
        static2(two, "Defender's Will", "DEF +30%.") { stat(Stat.DEF_PCT, 0.30) }
    }
    set("gambler") { two, _ ->
        effect("2", "Gambler 2pc", "Elemental Skill DMG +20%.", requires = two) { dmgBonus(0.20, HitFilter.SKILL) }
    }
    set("instructor") { two, four ->
        static2(two, "Instructor", "EM +80.") { stat(Stat.EM, 80.0) }
        effect("4", "Instructor 4pc", "Party EM +120 after a reaction.", target = EffectTarget.TEAM, control = toggle(), requires = four) {
            stat(Stat.EM, 120.0)
        }
    }
    set("martialartist") { two, four ->
        effect("2", "Martial Artist 2pc", "Normal and Charged Attack DMG +15%.", requires = two) { dmgBonus(0.15, HitFilter.NORMAL_CHARGED) }
        effect("4", "Martial Artist 4pc", "Normal and Charged Attack DMG +25% after a Skill.", control = toggle(), requires = four) {
            dmgBonus(0.25, HitFilter.NORMAL_CHARGED)
        }
    }
    set("resolutionofsojourner") { two, four ->
        static2(two, "Resolution of Sojourner", "ATK +18%.") { stat(Stat.ATK_PCT, 0.18) }
        effect("4", "Resolution of Sojourner 4pc", "Charged Attack CRIT Rate +30%.", requires = four) { critRate(0.30, HitFilter.of(AttackCategory.CHARGED)) }
    }
    set("scholar") { two, _ ->
        static2(two, "Scholar", "Energy Recharge +20%.") { stat(Stat.ER, 0.20) }
    }
    set("theexile") { two, _ ->
        static2(two, "The Exile", "Energy Recharge +20%.") { stat(Stat.ER, 0.20) }
    }
    set("tinymiracle") { _, _ -> }
}
