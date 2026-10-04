package com.genshincalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.app.ShowcaseState
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.showcase.ShowcaseCharacter
import com.genshincalc.core.showcase.StatDifference
import com.genshincalc.core.text.Format

/** Imports the characters of a player's in-game Character Showcase by UID. */
@Composable
fun ShowcaseScreen(data: GameDataSet, state: ShowcaseState, vm: CalcViewModel, nav: Nav) {
    var uid by rememberSaveable { mutableStateOf(state.uid) }
    val focus = LocalFocusManager.current
    val load = {
        focus.clearFocus()
        vm.loadShowcase(uid)
    }
    BackScaffold("Import from game", nav.pop) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("showcase_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Character Showcase", subtitle = "Loads the characters shown on your in-game profile (via Enka.Network)") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uid,
                            onValueChange = { t -> uid = t.filter { it.isDigit() }.take(10) },
                            label = { Text("UID") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { load() }),
                            modifier = Modifier.weight(1f).testTag("showcase_uid"),
                        )
                        Button(onClick = load, enabled = !state.loading, modifier = Modifier.testTag("showcase_load")) { Text("Load") }
                    }
                    Text(
                        "In the game: Profile › Character Showcase › turn on “Show Character Details”. " +
                            "Changes there take a few minutes to show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Loading showcase of ${state.uid}…")
                    }
                }
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("showcase_error")) }
            }
            val showcase = state.showcase
            if (showcase != null) {
                item {
                    val p = showcase.player
                    Column {
                        Text(p.nickname ?: "Player ${state.uid}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(p.level?.let { "AR $it" }, p.worldLevel?.let { "WL $it" }, "UID ${showcase.uid ?: state.uid}").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showcase.detailsHidden) {
                    item {
                        EmptyNote(
                            "This player's character details are hidden or the showcase is empty. In the game, add characters to " +
                                "the Character Showcase and turn on “Show Character Details”, then try again in a few minutes.",
                        )
                    }
                }
                if (showcase.unknownCharacters > 0) {
                    item {
                        Text(
                            "${showcase.unknownCharacters} showcased character(s) are newer than this app's data and were skipped.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showcase.characters.isNotEmpty()) {
                    item {
                        Text("Pick up to four (the first four are selected).", style = MaterialTheme.typography.bodySmall)
                    }
                }
                itemsIndexed(showcase.characters) { i, c ->
                    ShowcaseCharacterCard(
                        data, c, state.differences.getOrNull(i).orEmpty(),
                        selected = i in state.selected,
                        onToggle = { vm.toggleShowcaseCharacter(i) },
                        modifier = Modifier.testTag("showcase_char_$i"),
                        checkboxModifier = Modifier.testTag("showcase_check_$i"),
                    )
                }
                if (showcase.characters.isNotEmpty()) {
                    item {
                        Column {
                            Button(
                                onClick = {
                                    vm.applyShowcase()
                                    nav.pop()
                                },
                                enabled = state.selected.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().testTag("showcase_apply"),
                            ) { Text("Use as party (${state.selected.size})") }
                            Text(
                                "Replaces the current party. The artifacts are added to My artifacts and can be swapped on the Build tab.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseCharacterCard(
    data: GameDataSet,
    c: ShowcaseCharacter,
    differences: List<StatDifference>,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    checkboxModifier: Modifier = Modifier,
) {
    val b = c.build
    val character = data.character(b.characterId)
    val weapon = b.weapon?.let { w -> data.weaponOrNull(w.weaponId)?.let { "${it.name} R${w.refinement}" } } ?: "No weapon"
    val sets = b.artifacts.pieceCounts().filterValues { it >= 2 }.entries.sortedByDescending { it.value }
        .joinToString(" + ") { (id, n) -> "${data.artifactSetOrNull(id)?.name ?: id} (${if (n >= 4) 4 else 2})" }
        .ifEmpty { "${b.artifacts.pieces.size} artifacts" }
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() }, modifier = checkboxModifier)
            CharacterAvatar(character, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(character.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Lv ${b.level} · C${b.constellation} · Talents ${b.talents.normal}/${b.talents.skill}/${b.talents.burst}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("$weapon · $sets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (differences.isEmpty()) {
                    Text("Calculated stats match the game", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        "Game shows " + differences.joinToString { d ->
                            val f = { v: Double -> if (d.percent) Format.pct(v) else Format.int(v) }
                            "${d.label} ${f(d.game)} (calculated ${f(d.calculated)})"
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                c.warnings.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
