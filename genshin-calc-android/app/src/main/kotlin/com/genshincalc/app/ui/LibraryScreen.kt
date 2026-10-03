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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.WeaponType
import com.genshincalc.core.text.ReactionGuide

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(data: GameDataSet, nav: Nav) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Library · Game ${data.gameVersion}") })
        val tabs = listOf("Characters", "Weapons", "Artifacts", "Reactions")
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) },
                    modifier = Modifier.testTag("lib_${t.lowercase()}"))
            }
        }
        when (tab) {
            0 -> CharacterList(data, nav)
            1 -> WeaponList(data, nav)
            2 -> ArtifactList(data, nav)
            else -> ReactionList(nav)
        }
    }
}

@Composable
private fun CharacterList(data: GameDataSet, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var element by rememberSaveable { mutableStateOf<Element?>(null) }
    var weapon by rememberSaveable { mutableStateOf<WeaponType?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(query, { query = it }, "Search ${data.characters.size} characters", Modifier.testTag("search"))
            ElementFilter(element) { element = it }
            WeaponTypeFilter(weapon) { weapon = it }
        }
        val list = data.characters.filter {
            (element == null || it.element == element) && (weapon == null || it.weapon == weapon) &&
                (it.name.contains(query.trim(), true) || (it.region ?: "").contains(query.trim(), true))
        }
        LazyColumn(Modifier.fillMaxSize().testTag("character_list")) {
            items(list, key = { it.id }) { c ->
                CharacterRow(c, onClick = { nav.push(Route.CharacterDetail(c.id)) })
            }
        }
    }
}

@Composable
private fun WeaponList(data: GameDataSet, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<WeaponType?>(null) }
    var rarity by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchField(query, { query = it }, "Search ${data.weapons.size} weapons", Modifier.testTag("search"))
            WeaponTypeFilter(type) { type = it }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = rarity == 0, onClick = { rarity = 0 }, label = { Text("Any rarity") })
                for (r in 5 downTo 1) FilterChip(selected = rarity == r, onClick = { rarity = r }, label = { Text("$r★") })
            }
        }
        val list = data.weapons.filter {
            (type == null || it.type == type) && (rarity == 0 || it.rarity == rarity) && it.name.contains(query.trim(), true)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { w ->
                WeaponRow(w) { nav.push(Route.WeaponDetail(w.id)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ArtifactList(data: GameDataSet, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, { query = it }, "Search ${data.artifactSets.size} sets", Modifier.padding(12.dp).testTag("search"))
        val list = data.artifactSets.filter { it.name.contains(query.trim(), true) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { s ->
                SetRow(s) { nav.push(Route.SetDetail(s.id)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ReactionList(nav: Nav) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ReactionGuide.all, key = { it.reaction.name }) { info ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.push(Route.ReactionDetail(info.reaction)) }
                    .padding(4.dp)
                    .testTag("reaction_row_${info.reaction.name}"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    info.elements.forEach { ElementDot(it, 12.dp); Spacer(Modifier.width(3.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Text(info.reaction.display, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Pill(info.reaction.type.name.lowercase().replaceFirstChar { it.uppercase() })
                }
                Text(info.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
