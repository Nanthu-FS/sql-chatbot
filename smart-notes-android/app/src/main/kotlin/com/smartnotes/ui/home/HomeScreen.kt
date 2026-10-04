package com.smartnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartnotes.data.NoteEntity
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.Routes
import com.smartnotes.ui.displayTitle
import com.smartnotes.ui.meta
import com.smartnotes.ui.preview
import com.smartnotes.ui.relativeTime
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.Skin
import com.smartnotes.ui.theme.SkinBanner
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinIconButton
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinTextField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavController) {
    val t = LocalSkin.current
    val context = LocalContext.current
    val notes by vm.notes.collectAsState()
    val clips by vm.clips.collectAsState()
    val resurfaced by vm.resurfaced.collectAsState()
    val meeting by vm.currentMeeting.collectAsState()
    var question by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.tidyNotes() }
    val submitSearch = {
        if (question.isNotBlank()) {
            nav.navigate(Routes.search(question))
            question = ""
        }
    }

    SkinScaffold(
        title = "Notes",
        subtitle = SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(Date()).uppercase(),
        actions = {
            SkinIconButton(Icons.Filled.Groups, "Meeting mode", { nav.navigate(Routes.MEETING) })
            SkinIconButton(Icons.Filled.ContentPaste, "Clipboard inbox (${clips.size})", { nav.navigate(Routes.CLIPS) })
            SkinIconButton(Icons.Filled.Settings, "Settings and themes", { nav.navigate(Routes.SETTINGS) })
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinTextField(
                    question, { question = it },
                    placeholder = if (t.skin == Skin.TERMINAL) "grep your notes…" else "Search your notes…",
                    modifier = Modifier.weight(1f),
                    prefix = when (t.skin) { Skin.SWISS -> "FIND"; Skin.TERMINAL -> "$"; else -> null },
                    onSubmit = submitSearch,
                )
                SkinIconButton(Icons.Filled.Mic, "Voice note", { nav.navigate(Routes.VOICE) })
                SkinIconButton(Icons.Filled.Add, "New note", { vm.createNote(onCreated = { nav.navigate(Routes.note(it)) }) }, primary = true)
            }
        },
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = if (t.skin == Skin.SWISS) 0.dp else 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(
                when (t.skin) { Skin.SWISS, Skin.TERMINAL, Skin.RETRO -> 0.dp; Skin.ROLODEX -> 14.dp; Skin.BRUTAL -> 14.dp },
            ),
        ) {
            meeting?.let { ev ->
                item {
                    Box(Modifier.padding(horizontal = if (t.skin == Skin.SWISS) 20.dp else 0.dp).padding(bottom = 12.dp)) {
                        SkinBanner("Meeting now", ev.title, "Tap to take meeting notes", onClick = { nav.navigate(Routes.MEETING) })
                    }
                }
            }
            resurfaced?.let { r ->
                item {
                    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - r.note.updatedAt)
                    Box(Modifier.padding(horizontal = if (t.skin == Skin.SWISS) 20.dp else 0.dp).padding(bottom = 12.dp)) {
                        SkinBanner("Resurfaced · ${r.reason}", r.note.title, "Last edited $days days ago", onClick = { nav.navigate(Routes.note(r.note.id)) })
                    }
                }
            }
            item {
                val header = if (t.skin == Skin.TERMINAL) "~ \$ ls --recent   (${notes.size})" else "Recent — ${notes.size}"
                if (t.skin == Skin.TERMINAL) Text(header, color = t.muted, modifier = Modifier.padding(bottom = 6.dp))
                else SkinLabel(header, Modifier.padding(horizontal = if (t.skin == Skin.SWISS) 20.dp else 0.dp).padding(bottom = 8.dp))
            }
            if (notes.isEmpty()) {
                item {
                    Text(
                        "No notes yet. Tap + to write, the mic to talk, or the camera to snap a whiteboard.",
                        color = t.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
            if (t.skin == Skin.RETRO) {
                item {
                    SkinCard(title = "All Notes — ${notes.size} items") {
                        notes.forEach { RetroRow(it) { nav.navigate(Routes.note(it.id)) } }
                    }
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    NoteRow(note, notes.indexOf(note)) { nav.navigate(Routes.note(note.id)) }
                }
            }
            if (t.skin == Skin.TERMINAL) item { Text("~ \$ █", modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, index: Int, onClick: () -> Unit) {
    val t = LocalSkin.current
    when (t.skin) {
        Skin.TERMINAL -> Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
            Text("  " + note.displayTitle().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_') + ".md", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note.meta().ifEmpty { relativeTime(note.updatedAt) }, color = t.muted, fontSize = 13.sp)
        }
        Skin.ROLODEX -> SkinCard(
            Modifier.graphicsLayer { rotationZ = if (index % 2 == 0) -0.8f else 0.8f },
            onClick = onClick,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(note.displayTitle(), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("No. %03d".format(note.id), fontSize = 12.sp)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).background(t.accent).padding(top = 2.dp))
            Text(note.preview(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.meta().isNotEmpty()) Text(note.meta(), color = t.accent, fontSize = 13.sp)
        }
        else -> SkinCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (t.upperCaseTitles) note.displayTitle().uppercase() else note.displayTitle(),
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(relativeTime(note.updatedAt), fontFamily = t.label, fontSize = 12.sp, color = t.muted)
            }
            if (note.preview().isNotEmpty()) Text(note.preview(), maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (t.skin == Skin.SWISS) androidx.compose.ui.graphics.Color(0xFF444444) else androidx.compose.ui.graphics.Color.Unspecified)
            if (note.meta().isNotEmpty()) Text(note.meta().uppercase(), fontFamily = t.label, fontSize = 11.sp, color = if (t.skin == Skin.BRUTAL) t.onSurface else t.accent)
        }
    }
}

@Composable
private fun RetroRow(note: NoteEntity, onClick: () -> Unit) {
    val glyph = when (note.source) { "voice" -> "♪"; "photo" -> "▣"; "meeting" -> "◎"; "clip" -> "✂"; else -> "▤" }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$glyph ${note.displayTitle()}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(note.meta().ifEmpty { relativeTime(note.updatedAt) }, fontSize = 13.sp)
    }
}

