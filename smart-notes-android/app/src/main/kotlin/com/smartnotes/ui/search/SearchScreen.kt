package com.smartnotes.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.Routes
import com.smartnotes.ui.displayTitle
import com.smartnotes.ui.relativeTime
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.Skin
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinTextField

/** Ranked, on-device search across all notes (TF-IDF). */
@Composable
fun SearchScreen(vm: MainViewModel, nav: NavController, initial: String) {
    val t = LocalSkin.current
    val notes by vm.notes.collectAsState()
    var query by rememberSaveable { mutableStateOf(initial) }
    val hits = remember(query, notes) { if (query.isBlank()) emptyList() else vm.search(query) }

    SkinScaffold(title = if (t.skin == Skin.TERMINAL) "grep" else "Search", onBack = { nav.popBackStack() }) {
        SkinTextField(query, { query = it }, "Search your notes…", Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (query.isNotBlank()) item { SkinLabel("${hits.size} matching notes") }
            items(hits, key = { it.id }) { note ->
                SkinCard(onClick = { nav.navigate(Routes.note(note.id)) }) {
                    Row {
                        Text(note.displayTitle(), Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(relativeTime(note.updatedAt), fontSize = 12.sp, color = t.muted)
                    }
                    Text(snippet(note.body, query), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** The line that best matches the query, so results show why they matched. */
private fun snippet(body: String, query: String): String {
    val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
    return body.lines().maxByOrNull { line -> words.count { line.lowercase().contains(it) } }?.trim().orEmpty()
}
