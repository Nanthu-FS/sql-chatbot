package com.genshincalc.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.core.Defaults
import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.ReactionType
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.text.Format
import com.genshincalc.core.text.ReactionGuide
import com.genshincalc.core.text.TalentText
import kotlin.math.roundToInt

@Composable
fun CharacterDetailScreen(data: GameDataSet, id: String, team: Team, vm: CalcViewModel, nav: Nav) {
    val c = data.character(id)
    var level by remember { mutableFloatStateOf(90f) }
    var talentLevel by remember { mutableIntStateOf(9) }
    BackScaffold(c.name, nav.pop) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("character_detail"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = c.name, subtitle = c.title, action = { ElementTag(c.element) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharacterAvatar(c, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Stars(c.rarity)
                            Text("${c.weapon.display} · ${c.region ?: "Unknown region"}", style = MaterialTheme.typography.bodyMedium)
                            c.affiliation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Spacer(Modifier.padding(4.dp))
                    c.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.padding(4.dp))
                    c.birthday?.let { StatLine("Birthday", it) }
                    c.constellation?.let { StatLine("Constellation", it) }
                    c.release?.let { StatLine("Released", "Version $it") }
                    StatLine("Damage formulas", if (c.id in GameEffects.modelledCharacters) "Talents + passives" else "Talents")
                    val inTeam = team.members.any { it.characterId == c.id }
                    Button(
                        onClick = { vm.addMember(c.id); nav.pop() },
                        enabled = !inTeam && team.members.size < Team.MAX_SIZE,
                        modifier = Modifier.padding(top = 8.dp).testTag("add_to_team"),
                    ) { Text(if (inTeam) "In party" else "Add to party") }
                }
            }
            item {
                val lvl = level.roundToInt().coerceIn(1, 100)
                val base = BaseStats.character(data, c, lvl, true)
                SectionCard(title = "Base stats", subtitle = "Level $lvl (ascended)") {
                    Slider(value = level, onValueChange = { level = it }, valueRange = 1f..100f, steps = 98)
                    StatLine("Base HP", Format.int(base.hp))
                    StatLine("Base ATK", Format.int(base.atk))
                    StatLine("Base DEF", Format.int(base.def))
                    base.ascensionStat?.let { StatLine("Ascension: ${it.display}", Format.stat(it, base.ascensionValue)) }
                }
            }
            item {
                Column {
                    Text("Talent level: $talentLevel", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (l in listOf(1, 6, 8, 9, 10, 11, 12, 13, 15)) {
                            FilterChip(selected = talentLevel == l, onClick = { talentLevel = l }, label = { Text("$l") })
                        }
                    }
                }
            }
            for (t in c.talents) {
                item {
                    SectionCard(title = t.name ?: t.type.display, subtitle = t.type.display) {
                        t.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (t.attributes.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            t.attributes.forEach { a -> StatLine(a.label, TalentText.format(a, t.params, talentLevel)) }
                        }
                    }
                }
            }
            if (c.passives.isNotEmpty()) {
                item {
                    SectionCard(title = "Passive talents") {
                        c.passives.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            if (c.constellations.isNotEmpty()) {
                item {
                    SectionCard(title = "Constellations") {
                        c.constellations.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Text("C${i + 1} · ${p.name}", fontWeight = FontWeight.SemiBold)
                            p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item {
                val scaling = Defaults.primaryScaling(c)
                Text("Main damage scaling: ${scaling.display}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun WeaponDetailScreen(data: GameDataSet, id: String, nav: Nav) {
    val w = data.weapon(id)
    var refinement by remember { mutableIntStateOf(1) }
    BackScaffold(w.name, nav.pop) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("weapon_detail"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = w.name, subtitle = "${w.rarity}★ ${w.type.display}" + (w.release?.let { " · since $it" } ?: "")) {
                    w.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            item {
                SectionCard(title = "Stats by level") {
                    val levels = listOf(1, 20, 40, 50, 60, 70, 80, 90).filter { it <= w.maxLevel }
                    levels.forEach { lvl ->
                        val s = BaseStats.weapon(data, w, WeaponBuild(w.id, lvl, ascended = false))
                        StatLine("Lv $lvl", "${Format.int(s.atk)} ATK" + (s.substat?.let { " · ${Format.stat(it, s.substatValue)} ${it.short}" } ?: ""))
                    }
                }
            }
            if (w.effectTemplate != null) {
                item {
                    SectionCard(title = w.effectName ?: "Passive", subtitle = if (w.id in GameEffects.modelledWeapons) "Used by the calculator" else null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (r in 1..w.refinements.size.coerceAtLeast(1)) {
                                FilterChip(selected = refinement == r, onClick = { refinement = r }, label = { Text("R$r") })
                            }
                        }
                        Text(w.passiveText(refinement) ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SetDetailScreen(data: GameDataSet, id: String, nav: Nav) {
    val s = data.artifactSet(id)
    BackScaffold(s.name, nav.pop) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("set_detail"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = s.name,
                    subtitle = "Rarity ${s.rarities.joinToString("/")}★" + if (s.id in GameEffects.modelledSets) " · used by the calculator" else "",
                ) {
                    s.onePiece?.let { StatLine("1-piece", ""); Text(it, style = MaterialTheme.typography.bodyMedium) }
                    s.twoPiece?.let {
                        Text("2-piece bonus", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    s.fourPiece?.let {
                        Text("4-piece bonus", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (s.pieces.isNotEmpty()) {
                item {
                    SectionCard(title = "Pieces") {
                        s.pieces.forEach { (slot, name) -> StatLine(slot.replaceFirstChar { it.uppercase() }, name) }
                    }
                }
            }
            item {
                SectionCard(title = "Main stats at +20 (5★)") {
                    val rows = listOf(
                        "Flower: HP" to Format.int(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.HP, 5, 20)),
                        "Plume: ATK" to Format.int(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.ATK, 5, 20)),
                        "ATK% / HP%" to Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.ATK_PCT, 5, 20)),
                        "DEF%" to Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.DEF_PCT, 5, 20)),
                        "Elemental Mastery" to Format.int(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.EM, 5, 20)),
                        "Energy Recharge" to Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.ER, 5, 20)),
                        "Elemental DMG" to Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.PYRO_DMG, 5, 20)),
                        "Physical DMG" to Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.PHYSICAL_DMG, 5, 20)),
                        "CRIT Rate / DMG" to "${Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.CRIT_RATE, 5, 20))} / " +
                            Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.CRIT_DMG, 5, 20)),
                        "Healing Bonus" to Format.pct(BaseStats.artifactMain(data, com.genshincalc.core.model.Stat.HEALING_BONUS, 5, 20)),
                    )
                    rows.forEach { (k, v) -> StatLine(k, v) }
                }
            }
        }
    }
}

@Composable
fun ReactionDetailScreen(reaction: Reaction, nav: Nav) {
    val info = ReactionGuide.info(reaction)
    BackScaffold(reaction.display, nav.pop) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("reaction_detail"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (info == null) return@LazyColumn
            item {
                SectionCard(title = reaction.display, subtitle = reaction.type.name.lowercase().replaceFirstChar { it.uppercase() } + " reaction") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        info.elements.forEach { ElementTag(it); Spacer(Modifier.width(6.dp)) }
                    }
                    Text(info.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                SectionCard(title = "Formula") {
                    Text(info.formula, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    info.details.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (reaction != Reaction.LUNAR_BLOOM && reaction != Reaction.STELLAR_CONDUCT) {
                item {
                    val title = when (reaction.type) {
                        ReactionType.AMPLIFYING -> "Multiplier (strong direction, no bonus)"
                        ReactionType.ADDITIVE -> "Flat Base DMG added at Lv 90"
                        ReactionType.SHIELD -> "Shield HP at Lv 90"
                        else -> "DMG at Lv 90 vs 10% RES (no bonuses)"
                    }
                    SectionCard(title = title) {
                        ReactionGuide.examples(reaction, 90).forEach { (em, v) ->
                            StatLine("EM ${Format.int(em)}", if (reaction.type == ReactionType.AMPLIFYING) "×" + Format.trim(v) else Format.int(v))
                        }
                    }
                }
            }
        }
    }
}
