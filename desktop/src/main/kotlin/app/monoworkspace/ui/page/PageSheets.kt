package app.monoworkspace.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monoworkspace.data.repo.PageVersion
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Covers
import app.monoworkspace.model.Page
import app.monoworkspace.model.PageGlyphs
import app.monoworkspace.ui.common.Formats
import app.monoworkspace.ui.components.CoverArt
import app.monoworkspace.ui.components.DatePickerSheet
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoDialog
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.MonoTextField
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

@Composable
fun IconPickerSheet(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var custom by remember { mutableStateOf(if (current != null && current !in PageGlyphs.all) current else "") }
    MonoBottomSheet(onDismiss = onDismiss, title = "Icon") {
        FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            PageGlyphs.all.forEach { g ->
                Box(
                    Modifier
                        .size(52.dp)
                        .background(if (g == current) MonoColors.Ink else MonoColors.Background)
                        .border(1.dp, MonoColors.Ink)
                        .inkClickable(onClick = { onPick(g) }, showBar = false),
                    contentAlignment = Alignment.Center,
                ) { Text(g, style = MonoType.h3.copy(color = if (g == current) MonoColors.White else MonoColors.Ink)) }
            }
        }
        Column(Modifier.padding(Space.l)) {
            MonoTextField(custom, { custom = it.take(8) }, label = "Or type an emoji or character", imeDone = { if (custom.isNotBlank()) onPick(custom.trim()) })
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MonoButton("Remove", { onPick(null) }, style = MonoButtonStyle.Text)
                Spacer(Modifier.weight(1f))
                MonoButton("Use", { onPick(custom.trim().ifEmpty { null }) }, style = MonoButtonStyle.Filled, enabled = custom.isNotBlank())
            }
        }
    }
}

@Composable
fun CoverPickerSheet(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit, onPickImage: () -> Unit) {
    MonoBottomSheet(onDismiss = onDismiss, title = "Cover") {
        LabelText("Patterns", Modifier.padding(start = Space.l, bottom = Space.s), color = MonoColors.Secondary)
        FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Covers.patterns.forEach { p ->
                val value = Covers.pattern(p)
                Column(Modifier.width(104.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .border(if (value == current) 3.dp else 1.dp, MonoColors.Ink)
                            .inkClickable(onClick = { onPick(value) }, showBar = false),
                    ) { CoverArt(value, Modifier.fillMaxWidth().height(60.dp).padding(if (value == current) 3.dp else 1.dp)) }
                    Text(p.replaceFirstChar { it.uppercase() }, style = MonoType.caption)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            MonoButton("Image from device", onPickImage, icon = MonoIcons.Image)
            Spacer(Modifier.weight(1f))
            MonoButton("Remove", { onPick(null) }, style = MonoButtonStyle.Text)
        }
    }
}

@Composable
fun VersionHistorySheet(versions: Flow<List<PageVersion>>, onDismiss: () -> Unit, onRestore: (PageVersion) -> Unit) {
    val list by versions.collectAsState(initial = emptyList())
    var confirm by remember { mutableStateOf<PageVersion?>(null) }
    MonoBottomSheet(onDismiss = onDismiss, title = "Version history") {
        Text(
            "Snapshots are taken when you stop editing. The last 50 are kept. Restoring saves the current version first, so you can undo.",
            Modifier.padding(horizontal = Space.l, vertical = Space.s),
            style = MonoType.caption,
        )
        if (list.isEmpty()) {
            EmptyState("No versions yet. They appear after you edit and pause.", null, null, Modifier.padding(horizontal = Space.l), icon = MonoIcons.History)
        }
        list.forEach { v ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Formats.dateTime(v.createdAt), style = MonoType.body.tnum())
                    Text("${v.title.ifBlank { "Untitled" }} · ${v.blockCount} blocks", style = MonoType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MonoButton("Restore", { confirm = v }, height = 36.dp)
            }
            Hairline()
        }
    }
    confirm?.let { v ->
        MonoDialog(
            onDismiss = { confirm = null },
            title = "Restore this version?",
            body = "The page's blocks and title are replaced with the version from ${Formats.dateTime(v.createdAt)}.",
            confirmLabel = "Restore",
            onConfirm = { confirm = null; onRestore(v); onDismiss() },
        )
    }
}

/** Insert a page or date mention at the caret. */
@Composable
fun MentionSheet(pages: List<Page>, selfId: String, onDismiss: () -> Unit, onPage: (Page) -> Unit, onDate: (Long, Boolean) -> Unit) {
    var query by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    MonoBottomSheet(onDismiss = onDismiss, title = "Mention") {
        Column(Modifier.padding(horizontal = Space.l)) { MonoTextField(query, { query = it }, placeholder = "Search pages") }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { onDate(System.currentTimeMillis(), false) }).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoIcon(MonoIcons.Calendar, null, size = 20.dp)
            Spacer(Modifier.width(Space.m))
            Text("Today", style = MonoType.body)
        }
        Hairline()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { pickDate = true }).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoIcon(MonoIcons.Calendar, null, size = 20.dp)
            Spacer(Modifier.width(Space.m))
            Text("Pick a date…", style = MonoType.body)
        }
        Hairline()
        LabelText("Pages", Modifier.padding(start = Space.l, top = Space.l, bottom = Space.s), color = MonoColors.Secondary)
        pages.filter { it.id != selfId && it.displayTitle.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.editedAt }
            .take(40)
            .forEach { p ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { onPage(p) }).padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PageGlyph(p.icon, p.isDatabase)
                    Spacer(Modifier.width(Space.m))
                    Text(p.displayTitle, style = MonoType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Hairline()
            }
    }
    if (pickDate) {
        DatePickerSheet(null, { pickDate = false }, onSave = { picked ->
            pickDate = false
            if (picked != null) {
                val zone = ZoneId.systemDefault()
                val dt = if (picked.includeTime && picked.time != null) picked.start.atTime(picked.time).atZone(zone) else picked.start.atStartOfDay(zone)
                onDate(dt.toInstant().toEpochMilli(), picked.includeTime)
            }
        }, allowRange = false, title = "Mention a date")
    }
}

@Composable
fun TurnIntoSheet(onDismiss: () -> Unit, onPick: (BlockType) -> Unit) {
    MonoBottomSheet(onDismiss = onDismiss, title = "Turn into") {
        BlockType.turnIntoTargets.forEach { t ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { onPick(t) }).padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon(), null, Modifier.size(20.dp))
                Spacer(Modifier.width(Space.m))
                Text(t.label, style = MonoType.body)
            }
            Hairline()
        }
    }
}

@Composable
fun InlineLinkDialog(initial: String, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    MonoDialog(
        onDismiss = onDismiss, title = "Link", confirmLabel = "Apply",
        onConfirm = { onSave(url.trim().ifEmpty { null }); onDismiss() },
        dismissLabel = if (initial.isNotEmpty()) "Cancel" else "Cancel",
    ) {
        MonoTextField(url, { url = it }, placeholder = "https://", keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, warning = app.monoworkspace.engine.Validation.warning(app.monoworkspace.model.PropertyType.URL, url))
        if (initial.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            MonoButton("Remove link", { onSave(null); onDismiss() }, style = MonoButtonStyle.Text)
        }
    }
}

@Composable
fun LinkPreviewDialog(url: String, title: String, description: String, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var u by remember { mutableStateOf(url) }
    var t by remember { mutableStateOf(title) }
    var d by remember { mutableStateOf(description) }
    MonoDialog(onDismiss = onDismiss, title = "Link preview", body = "Nothing is fetched from the web. Describe the link yourself.", confirmLabel = "Save", onConfirm = { onSave(u, t, d); onDismiss() }) {
        MonoTextField(u, { u = it }, label = "URL", keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, warning = app.monoworkspace.engine.Validation.warning(app.monoworkspace.model.PropertyType.URL, u))
        Spacer(Modifier.height(Space.m))
        MonoTextField(t, { t = it.take(200) }, label = "Title")
        Spacer(Modifier.height(Space.m))
        MonoTextField(d, { d = it.take(500) }, label = "Description", singleLine = false, minLines = 2)
    }
}

@Composable
fun SaveTemplateDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    MonoDialog(onDismiss = onDismiss, title = "Save as template", body = "Subpages and databases are included. Relations are left out.", confirmLabel = "Save", onConfirm = { onSave(name); onDismiss() }) {
        MonoTextField(name, { name = it.take(80) }, label = "Template name")
    }
}

@Composable
fun GlyphHeader(icon: String?, isDatabase: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(72.dp)
            .background(MonoColors.Background)
            .border(1.dp, MonoColors.Ink)
            .inkClickable(onClick = onClick, showBar = false),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Text(icon, style = MonoType.h1.copy(fontSize = 36.sp, lineHeight = 40.sp))
        else PageGlyph(null, isDatabase, size = 32.dp)
    }
}
