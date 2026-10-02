package com.smartnotes.ui.clips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smartnotes.core.ClipboardInbox
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.Routes
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import java.util.concurrent.TimeUnit

@Composable
fun ClipsScreen(vm: MainViewModel, nav: NavController) {
    val clips by vm.clips.collectAsState()
    SkinScaffold(title = "Clipboard inbox", subtitle = "Kept for 24 hours", onBack = { nav.popBackStack() }) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SkinLabel("Text you copy shows up here when you open the app. Or select text anywhere → “Save to Smart Notes”.")
            }
            if (clips.isEmpty()) item { Text("Nothing copied lately.") }
            items(clips, key = { it.id }) { clip ->
                val left = ClipboardInbox.DEFAULT_TTL_MS - (System.currentTimeMillis() - clip.copiedAt)
                SkinCard {
                    Text(clip.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    SkinLabel("Expires in ${TimeUnit.MILLISECONDS.toHours(left).coerceAtLeast(0)}h")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkinButton("Save as note", { vm.saveClip(clip) { nav.navigate(Routes.note(it)) } }, Modifier.weight(1f))
                        SkinButton("Discard", { vm.discardClip(clip.id) }, Modifier.weight(1f), primary = false)
                    }
                }
            }
        }
    }
}
