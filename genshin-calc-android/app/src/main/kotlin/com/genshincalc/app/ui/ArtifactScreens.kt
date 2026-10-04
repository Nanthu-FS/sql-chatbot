package com.genshincalc.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.app.PieceDraft
import com.genshincalc.app.ScanItem
import com.genshincalc.app.ScanSession
import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.ArtifactPiece
import com.genshincalc.core.model.ArtifactSlot
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.Team
import com.genshincalc.core.text.Format

private const val MAX_SCREENSHOTS = 20

/** Returns a function that opens the system photo picker for artifact screenshots. */
@Composable
fun rememberScreenshotPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    return { runCatching { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/** "Gladiator's Nostalgia", or the set name when the piece name is unknown. */
internal fun pieceName(data: GameDataSet, piece: ArtifactPiece): String {
    val set = data.artifactSetOrNull(piece.setId) ?: return "Unknown set"
    return set.pieces[piece.slot.key] ?: set.name
}

internal fun substatText(piece: ArtifactPiece): String =
    piece.substats.entries.joinToString("  ·  ") { (s, v) -> "${s.short} ${Format.stat(s, v)}" }

/** Piece id -> names of the party members wearing it. */
internal fun wearers(data: GameDataSet, team: Team): Map<String, List<String>> {
    val out = mutableMapOf<String, MutableList<String>>()
    team.members.filter { it.artifacts.mode == ArtifactMode.PIECES }.forEach { m ->
        val name = shortName(data.character(m.characterId).name)
        m.artifacts.pieces.values.forEach { out.getOrPut(it.id) { mutableListOf() } += name }
    }
    return out
}

@Composable
fun PieceRow(
    data: GameDataSet,
    piece: ArtifactPiece,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    note: String? = null,
    equipped: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pieceName(data, piece), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = rarityColor(piece.rarity), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text("+${piece.level}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Stars(piece.rarity)
            }
            Text(
                "${piece.slot.display} · ${piece.mainStat.display} ${Format.stat(piece.mainStat, BaseStats.pieceMain(data, piece))}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (piece.substats.isNotEmpty()) {
                Text(substatText(piece), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (equipped) {
            Spacer(Modifier.width(6.dp))
            Pill("Equipped")
        }
        trailing?.invoke()
    }
}

/** "Individual pieces" content of the Artifacts card on the Build tab. */
@Composable
fun PiecesSection(data: GameDataSet, build: MemberBuild, index: Int, vm: CalcViewModel, nav: Nav) {
    val pickScreenshots = rememberScreenshotPicker { vm.importScreenshots(it, equipOn = index) }
    ArtifactSlot.entries.forEach { slot ->
        val piece = build.artifacts.pieces[slot]
        val open = { nav.push(Route.PickPiece(index, slot)) }
        if (piece != null) {
            PieceRow(data, piece, Modifier.testTag("slot_${slot.key}"), padding = PaddingValues(vertical = 8.dp), onClick = open)
        } else {
            Column(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 10.dp).testTag("slot_${slot.key}")) {
                Text(slot.display, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Empty: tap to choose, add or scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    val bonuses = build.artifacts.pieceCounts().filterValues { it >= 2 }.entries.sortedByDescending { it.value }
    Text(
        if (bonuses.isEmpty()) "No set bonus"
        else "Set bonus: " + bonuses.joinToString(" + ") { (id, n) -> "${data.artifactSetOrNull(id)?.name ?: id} (${if (n >= 4) 4 else 2})" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = pickScreenshots, modifier = Modifier.testTag("scan_screenshots")) { Text("Scan screenshots") }
        TextButton(onClick = { nav.push(Route.MyArtifacts) }, modifier = Modifier.testTag("my_artifacts")) { Text("My artifacts") }
    }
}

/** Artifacts of one slot to equip on a member. */
@Composable
fun PickPieceScreen(data: GameDataSet, team: Team, inventory: List<ArtifactPiece>, memberIndex: Int, slot: ArtifactSlot, vm: CalcViewModel, nav: Nav) {
    val member = team.members.getOrNull(memberIndex)
    if (member == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val equipped = member.artifacts.pieces[slot]
    val pickScreenshots = rememberScreenshotPicker { vm.importScreenshots(it, equipOn = memberIndex) }
    val worn = wearers(data, team)
    val list = inventory.filter { it.slot == slot }.sortedWith(
        compareByDescending<ArtifactPiece> { it.id == equipped?.id }.thenByDescending { it.rarity }.thenByDescending { it.level },
    )
    BackScaffold("${shortName(data.character(member.characterId).name)}: ${slot.display}", nav.pop) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("piece_list")) {
            item {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = pickScreenshots) { Text("Scan") }
                    OutlinedButton(onClick = { vm.newPiece(slot, memberIndex); nav.push(Route.EditPiece) }, modifier = Modifier.testTag("new_piece")) {
                        Text("New")
                    }
                    if (equipped != null) {
                        OutlinedButton(onClick = { vm.unequip(memberIndex, slot); nav.pop() }) { Text("Unequip") }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        EmptyNote("No ${slot.display.lowercase()} in My artifacts yet. Scan a screenshot of its details screen or add it with New.")
                    }
                }
            }
            items(list, key = { it.id }) { p ->
                PieceRow(
                    data, p,
                    equipped = p.id == equipped?.id,
                    note = worn[p.id]?.let { "On ${it.joinToString()}" },
                    onClick = {
                        vm.equip(memberIndex, p)
                        nav.pop()
                    },
                    trailing = {
                        IconButton(onClick = { vm.editPiece(p); nav.push(Route.EditPiece) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit artifact")
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Every saved artifact. */
@Composable
fun MyArtifactsScreen(data: GameDataSet, team: Team, inventory: List<ArtifactPiece>, vm: CalcViewModel, nav: Nav) {
    var filter by rememberSaveable { mutableStateOf<ArtifactSlot?>(null) }
    val pickScreenshots = rememberScreenshotPicker { vm.importScreenshots(it) }
    val worn = wearers(data, team)
    val list = inventory.filter { filter == null || it.slot == filter }.sortedWith(
        compareBy<ArtifactPiece> { it.slot.ordinal }.thenBy { data.artifactSetOrNull(it.setId)?.name ?: "~" }.thenByDescending { it.level },
    )
    BackScaffold("My artifacts (${inventory.size})", nav.pop) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("my_artifacts_list")) {
            item {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickScreenshots) { Text("Scan screenshots") }
                        OutlinedButton(onClick = { vm.newPiece(filter ?: ArtifactSlot.FLOWER, null); nav.push(Route.EditPiece) }) { Text("New") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                        ArtifactSlot.entries.forEach { s ->
                            FilterChip(selected = filter == s, onClick = { filter = if (filter == s) null else s }, label = { Text(s.display) })
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        EmptyNote(
                            "No artifacts yet. Take screenshots of artifact details (Inventory › Artifacts, or a character's " +
                                "Artifacts page, game language English) and scan them, share them to this app, or add pieces by hand.",
                        )
                    }
                }
            }
            items(list, key = { it.id }) { p ->
                PieceRow(
                    data, p,
                    note = worn[p.id]?.let { "On ${it.joinToString()}" },
                    onClick = { vm.editPiece(p); nav.push(Route.EditPiece) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Create or edit one artifact. */
@Composable
fun PieceEditorScreen(data: GameDataSet, draft: PieceDraft?, vm: CalcViewModel, nav: Nav) {
    if (draft == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val p = draft.piece
    var confirmDelete by remember { mutableStateOf(false) }
    val set = data.artifactSetOrNull(p.setId)
    val cancel = {
        vm.discardDraft()
        nav.pop()
    }
    BackHandler(onBack = cancel)
    BackScaffold(if (draft.isNew) "New artifact" else "Edit artifact", onBack = cancel) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().testTag("piece_editor"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Set") {
                    SetBox("Artifact set", set?.name ?: "None", { nav.push(Route.PickPieceSet) }, Modifier.testTag("piece_set"))
                    set?.pieces?.get(p.slot.key)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                SectionCard(title = "Piece") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArtifactSlot.entries.forEach { s ->
                            FilterChip(
                                selected = p.slot == s,
                                onClick = {
                                    vm.updateDraft { it.copy(slot = s, mainStat = if (it.mainStat in s.mainStats) it.mainStat else s.mainStats.first()) }
                                },
                                label = { Text(s.display) },
                                modifier = Modifier.testTag("piece_slot_${s.key}"),
                            )
                        }
                    }
                    Spacer(Modifier.padding(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dropdown(
                            "Main stat", p.slot.mainStats, p.mainStat, { it.display },
                            { s -> vm.updateDraft { it.copy(mainStat = s, substats = it.substats - s) } },
                            Modifier.weight(1f),
                        )
                        Dropdown(
                            "Level", (0..BaseStats.artifactMaxLevel(p.rarity)).toList(), p.level, { "+$it" },
                            { l -> vm.updateDraft { it.copy(level = l) } },
                            Modifier.width(96.dp),
                        )
                    }
                    Text(
                        "${p.mainStat.display} ${Format.stat(p.mainStat, BaseStats.pieceMain(data, p))}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in 5 downTo 1) {
                            FilterChip(
                                selected = p.rarity == r,
                                onClick = { vm.updateDraft { it.copy(rarity = r, level = it.level.coerceAtMost(BaseStats.artifactMaxLevel(r))) } },
                                label = { Text("$r★") },
                            )
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Substats", subtitle = "Up to four, as shown on the artifact") {
                    val used = p.substats.keys
                    p.substats.entries.toList().forEach { (stat, value) ->
                        key(stat) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Dropdown(
                                    "Stat",
                                    Stat.substats.filter { it == stat || (it !in used && it != p.mainStat) },
                                    stat, { it.display },
                                    { s -> vm.updateDraft { pc -> pc.copy(substats = pc.substats.entries.associate { (k, v) -> (if (k == stat) s else k) to v }) } },
                                    Modifier.weight(1.2f),
                                )
                                NumberField(
                                    stat.short, value, { v -> vm.updateDraft { it.copy(substats = it.substats + (stat to v)) } },
                                    Modifier.weight(1f), percent = stat.percent, tag = "piece_sub_${stat.name}",
                                )
                                IconButton(onClick = { vm.updateDraft { it.copy(substats = it.substats - stat) } }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Remove substat")
                                }
                            }
                        }
                    }
                    val free = Stat.substats.filter { it !in used && it != p.mainStat }
                    if (p.substats.size < 4 && free.isNotEmpty()) {
                        TextButton(onClick = { vm.updateDraft { it.copy(substats = it.substats + (free.first() to 0.0)) } }, modifier = Modifier.testTag("add_substat")) {
                            Text("Add substat")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveDraft(); nav.pop() }, modifier = Modifier.testTag("piece_save")) {
                        Text(if (draft.equipOn != null) "Save and equip" else "Save")
                    }
                    if (!draft.isNew) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("piece_delete")) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deletePiece(p.id)
                    vm.discardDraft()
                    nav.pop()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete artifact?") },
            text = { Text("It is removed from My artifacts and from every character wearing it.") },
        )
    }
}

@Composable
private fun SetBox(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

/** Progress and results of a screenshot import. */
@Composable
fun ScanResultsScreen(data: GameDataSet, team: Team, inventory: List<ArtifactPiece>, session: ScanSession?, vm: CalcViewModel, nav: Nav) {
    if (session == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val close = {
        vm.clearScan()
        nav.pop()
    }
    BackHandler(onBack = close)
    val worn = wearers(data, team)
    val activeIndex = team.activeIndex
    val activeName = team.members.getOrNull(activeIndex)?.let { shortName(data.character(it.characterId).name) }
    BackScaffold("Scanned artifacts", close) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (session.running) {
                LinearProgressIndicator(progress = { session.items.size / session.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    "Reading screenshot ${session.items.size + 1} of ${session.total}…",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                )
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("scan_results"),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(session.items, key = { it.index }) { item ->
                    val piece = item.pieceId?.let { id -> inventory.firstOrNull { it.id == id } }
                    ScanItemCard(
                        data, item, piece, session.total,
                        wornBy = piece?.let { worn[it.id] }.orEmpty(),
                        activeName = activeName,
                        onEdit = { piece?.let { vm.editPiece(it); nav.push(Route.EditPiece) } },
                        onEquip = { piece?.let { vm.equip(activeIndex, it) } },
                        onRemove = { piece?.let { vm.deletePiece(it.id) } },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = close, modifier = Modifier.testTag("scan_done")) { Text(if (session.running) "Stop" else "Done") }
            }
        }
    }
}

@Composable
private fun ScanItemCard(
    data: GameDataSet,
    item: ScanItem,
    piece: ArtifactPiece?,
    total: Int,
    wornBy: List<String>,
    activeName: String?,
    onEdit: () -> Unit,
    onEquip: () -> Unit,
    onRemove: () -> Unit,
) {
    val status = when {
        item.error != null -> item.error
        piece == null -> "Removed"
        item.duplicate -> "Already in My artifacts"
        else -> "Added to My artifacts"
    }
    SectionCard(
        title = if (total > 1) "Screenshot ${item.index + 1}" else "Screenshot",
        subtitle = status + if (wornBy.isNotEmpty()) " · on ${wornBy.joinToString()}" else "",
        modifier = Modifier.testTag("scan_item_${item.index}"),
    ) {
        if (piece != null) {
            PieceRow(data, piece, padding = PaddingValues(vertical = 4.dp))
            item.warnings.forEach {
                Text("Check: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.testTag("scan_edit_${item.index}")) { Text("Edit") }
                if (activeName != null && activeName !in wornBy) {
                    TextButton(onClick = onEquip, modifier = Modifier.testTag("scan_equip_${item.index}")) { Text("Equip on $activeName") }
                }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        } else if (item.error != null) {
            Text(
                "Use a screenshot of the artifact details (Inventory › Artifacts, or a character's Artifacts page) with the game in English.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (item.text.isNotEmpty()) {
                Text(
                    "Text found: " + item.text.take(10).joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
