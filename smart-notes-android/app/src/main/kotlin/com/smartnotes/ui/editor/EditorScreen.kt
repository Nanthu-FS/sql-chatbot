package com.smartnotes.ui.editor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartnotes.core.AutoLinker
import com.smartnotes.core.Checklist
import com.smartnotes.core.Gesture
import com.smartnotes.data.NoteEntity
import com.smartnotes.features.places.Places
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.Routes
import com.smartnotes.ui.displayTitle
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinChip
import com.smartnotes.ui.theme.SkinIconButton
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinSegmented
import com.smartnotes.ui.theme.SkinTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Mode(val label: String) { WRITE("Write"), READ("Read"), DRAW("Draw") }

@Composable
fun EditorScreen(vm: MainViewModel, nav: NavController, noteId: Long) {
    val note by vm.repo.note(noteId).collectAsState(initial = null)
    val n = note ?: return
    EditorContent(vm, nav, n)
}

@Composable
private fun EditorContent(vm: MainViewModel, nav: NavController, note: NoteEntity) {
    val t = LocalSkin.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var mode by remember(note.id) { mutableStateOf(if (note.body.isBlank()) Mode.WRITE else Mode.READ) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var placeDialog by remember { mutableStateOf(false) }
    var gestureNote by remember { mutableStateOf<String?>(null) }
    var circled by remember { mutableStateOf<String?>(null) }
    val lineBounds = remember(note.id) { mutableStateMapOf<Int, Rect>() }
    val reminders by vm.repo.reminders(note.id).collectAsState(initial = emptyList())

    // Body edits from elsewhere (the widget, time travel) flow back in when we're not mid-edit.
    LaunchedEffect(note.body) { if (mode != Mode.WRITE) body = note.body }

    // Autosave, debounced.
    LaunchedEffect(title, body) {
        if (title == note.title && body == note.body) return@LaunchedEffect
        delay(600)
        vm.save(note.copy(title = title, body = body))
    }

    val suggestions = remember(body, note.id) { vm.linkSuggestions(note, body) }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) placeDialog = true
    }

    SkinScaffold(
        title = if (mode == Mode.WRITE) "Edit" else "Note",
        subtitle = note.source.uppercase(),
        onBack = { nav.popBackStack() },
        actions = {
            SkinIconButton(Icons.Filled.History, "Time travel", { nav.navigate(Routes.history(note.id)) })
            SkinIconButton(Icons.Filled.Dashboard, "Board view", { nav.navigate(Routes.board(note.id)) })
            Box {
                SkinIconButton(Icons.Filled.MoreVert, "More", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Pin checklist to home screen") }, onClick = { menu = false; vm.pinToWidget(note) })
                    DropdownMenuItem(text = { Text("Remind me when I'm here") }, onClick = {
                        menu = false
                        if (Places.hasForeground(context)) placeDialog = true
                        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkinSegmented(Mode.entries.map { it.label }, mode.ordinal, { mode = Mode.entries[it] })
            if (mode == Mode.DRAW) SkinLabel(gestureNote ?: "Draw ✓ on a line to make a todo · ○ to search · — to tick off")
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxWidth().verticalScroll(scroll, enabled = mode != Mode.DRAW).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (mode == Mode.WRITE) {
                    SkinTextField(
                        title, { title = it }, "Title", Modifier.fillMaxWidth(), bare = true,
                        textStyle = TextStyle(fontFamily = t.display, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkinChip("+ Todo", { body = appendLine(body, "- [ ] ") })
                        SkinChip("+ Heading", { body = appendLine(body, "## ") })
                        SkinChip("+ Timer", { body = appendLine(body, "{{timer 25}}") })
                        SkinChip("+ Weather", { body = appendLine(body, "{{weather Bengaluru}}") })
                        SkinChip("+ Diagram", { body = appendLine(body, "```mermaid\ngraph TD\n  A --> B\n```") })
                    }
                    SkinTextField(
                        body, { body = it }, "Start writing… use [[Note title]] to link", Modifier.fillMaxWidth(),
                        singleLine = false, bare = true, textStyle = TextStyle(fontFamily = t.cardBody, fontSize = 16.sp, lineHeight = 24.sp),
                    )
                } else {
                    Text(
                        if (t.upperCaseTitles) title.ifBlank { "Untitled" }.uppercase() else title.ifBlank { "Untitled" },
                        fontFamily = t.display, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    )
                    NoteRenderer(
                        body = body,
                        onToggleLine = { line ->
                            body = Checklist.toggle(body, line)
                            vm.save(note.copy(title = title, body = body))
                        },
                        onOpenLink = { linked -> vm.openByTitle(linked) { nav.navigate(Routes.note(it)) } },
                        onLineBounds = { i, r -> lineBounds[i] = r },
                    )
                }

                if (reminders.isNotEmpty()) {
                    SkinLabel("Place reminders")
                    reminders.forEach { r ->
                        SkinChip("◉ ${r.placeName}  ×", { scope.launch { Places.remove(context, r.id) } })
                    }
                    if (!Places.hasBackground(context)) {
                        SkinButton("Allow location all the time", {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }, primary = false)
                    }
                }

                if (suggestions.isNotEmpty()) {
                    SkinLabel("Related — tap to link")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            SkinChip("+ ${s.note.title}", {
                                body = AutoLinker.insertLink(body, s.note.title)
                                vm.save(note.copy(title = title, body = body))
                            })
                        }
                    }
                }
            }

            if (mode == Mode.DRAW) {
                GestureLayer(onStroke = { stroke ->
                    val ys = stroke.points.map { it.y }
                    val centerY = (ys.min() + ys.max()) / 2
                    val hitLine = lineBounds.entries.firstOrNull { centerY in it.value.top..it.value.bottom }?.key
                    when (stroke.gesture) {
                        Gesture.CHECKMARK -> hitLine?.let {
                            body = Checklist.makeTodo(body, it); vm.save(note.copy(title = title, body = body))
                            gestureNote = "✓ Made a todo"
                        }
                        Gesture.STRIKE -> hitLine?.let {
                            body = Checklist.toggle(body, it); vm.save(note.copy(title = title, body = body))
                            gestureNote = "— Toggled"
                        }
                        Gesture.CIRCLE -> {
                            val top = ys.min(); val bottom = ys.max()
                            val text = lineBounds.entries.filter { it.value.bottom >= top && it.value.top <= bottom }
                                .sortedBy { it.key }.mapNotNull { body.lines().getOrNull(it.key) }
                                .joinToString(" ").replace(Regex("[#*\\[\\]-]"), " ").trim()
                            if (text.isNotEmpty()) circled = text
                            gestureNote = "○ Search"
                        }
                        Gesture.UNKNOWN -> gestureNote = "Didn't recognise that — try ✓, ○ or —"
                    }
                })
            }
        }
    }

    circled?.let { q ->
        val hits = remember(q) { vm.search(q).filter { it.id != note.id } }
        AlertDialog(
            onDismissRequest = { circled = null },
            title = { Text("“${q.take(60)}”") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hits.isEmpty()) Text("No other notes mention this.")
                    hits.forEach { h ->
                        TextButton(onClick = { circled = null; nav.navigate(Routes.note(h.id)) }) { Text(h.displayTitle()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { circled = null; nav.navigate(Routes.search(q)) }) { Text("Search all") } },
            dismissButton = { TextButton(onClick = { circled = null }) { Text("Close") } },
        )
    }

    if (placeDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { placeDialog = false },
            title = { Text("Remind me here") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You'll get this note when you come back to where you are now.")
                    SkinTextField(name, { name = it }, "Name this place (e.g. Store)")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    placeDialog = false
                    scope.launch {
                        val saved = Places.remindHere(context, note.id, name.ifBlank { "this place" })
                        vm.notify(
                            when {
                                saved == null -> "Couldn't get your location. Is location turned on?"
                                !Places.hasBackground(context) -> "Saved. Allow location \"all the time\" so it can trigger."
                                else -> "Saved. You'll be reminded at ${saved.placeName}."
                            },
                        )
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { placeDialog = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${note.displayTitle()}\"?") },
            text = { Text("This also deletes its history.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(note) { nav.popBackStack() } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun appendLine(body: String, line: String) =
    if (body.isEmpty() || body.endsWith("\n")) body + line else "$body\n$line"
