package com.genshincalc.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactSetData
import com.genshincalc.core.model.CharacterData
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.EnemyData
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.WeaponData
import com.genshincalc.core.model.WeaponType
import com.genshincalc.core.text.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
fun ElementFilter(selected: Element?, onSelect: (Element?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        Element.elemental.forEach { e ->
            FilterChip(
                selected = selected == e,
                onClick = { onSelect(if (selected == e) null else e) },
                label = { Text(e.display) },
                leadingIcon = { ElementDot(e) },
            )
        }
    }
}

@Composable
fun WeaponTypeFilter(selected: WeaponType?, onSelect: (WeaponType?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        WeaponType.entries.forEach { t ->
            FilterChip(selected = selected == t, onClick = { onSelect(if (selected == t) null else t) }, label = { Text(t.display) })
        }
    }
}

@Composable
fun CharacterRow(c: CharacterData, onClick: () -> Unit, enabled: Boolean = true, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("char_${c.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CharacterAvatar(c, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stars(c.rarity)
                Spacer(Modifier.width(6.dp))
                Text("${c.element.display} · ${c.weapon.display}" + (c.region?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (c.id in GameEffects.modelledCharacters) Pill("Kit")
        trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
fun CharacterPicker(data: GameDataSet, disabled: Set<String>, onBack: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var element by remember { mutableStateOf<Element?>(null) }
    BackScaffold("Choose character", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, "Search characters", Modifier.testTag("search"))
                ElementFilter(element) { element = it }
            }
            val list = data.characters.filter {
                (element == null || it.element == element) && it.name.contains(query.trim(), ignoreCase = true)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { c ->
                    CharacterRow(c, onClick = { onPick(c.id) }, enabled = c.id !in disabled, trailing = if (c.id in disabled) "In party" else null)
                }
            }
        }
    }
}

@Composable
fun WeaponRow(w: WeaponData, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("weapon_${w.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(w.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = rarityColor(w.rarity))
            Text(
                "${w.type.display} · ${Format.int(w.baseAtk)} base ATK at Lv1" + (w.substat?.let { " · ${it.display}" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Stars(w.rarity)
        if (w.id in GameEffects.modelledWeapons) {
            Spacer(Modifier.width(6.dp))
            Pill("Passive")
        }
    }
}

@Composable
fun WeaponPicker(data: GameDataSet, type: WeaponType, onBack: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    BackScaffold("Choose ${type.display.lowercase()}", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, "Search weapons", Modifier.padding(horizontal = 12.dp).testTag("search"))
            val list = data.weaponsOfType(type).filter { it.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { w ->
                    WeaponRow(w) { onPick(w.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun SetRow(s: ArtifactSetData, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("set_${s.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                color = rarityColor(s.maxRarity))
            if (s.id in GameEffects.modelledSets) Pill("Modelled")
        }
        s.twoPiece?.let { Text("2pc: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
    }
}

@Composable
fun SetPicker(data: GameDataSet, onBack: () -> Unit, onPick: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    BackScaffold("Choose artifact set", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, "Search sets", Modifier.padding(horizontal = 12.dp).testTag("search"))
            val list = data.artifactSets.filter { it.maxRarity >= 4 && it.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text("None", Modifier.fillMaxWidth().clickable { onPick(null) }.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(list, key = { it.id }) { s ->
                    SetRow(s) { onPick(s.id) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun EnemyPicker(data: GameDataSet, onBack: () -> Unit, onPick: (EnemyData) -> Unit) {
    var query by remember { mutableStateOf("") }
    BackScaffold("Choose enemy", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, { query = it }, "Search enemies", Modifier.padding(horizontal = 12.dp).testTag("search"))
            val list = data.enemies.filter { it.name.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(e) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("enemy_${e.id}")) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge)
                        val unusual = e.res.filter { (_, v) -> v != 0.10 }
                        Text(
                            (e.category ?: "") + if (unusual.isEmpty()) " · 10% all RES" else " · " +
                                unusual.entries.joinToString { (el, v) -> "${el.display} ${Format.pct(v, 0)}" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
