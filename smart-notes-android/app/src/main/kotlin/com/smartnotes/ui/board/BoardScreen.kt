package com.smartnotes.ui.board

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smartnotes.data.NoteEntity
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.displayTitle
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinSegmented
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val json = Json { ignoreUnknownKeys = true }

/** Board (infinite canvas) view: each paragraph of the note becomes a card you can drag around. */
@Composable
fun BoardScreen(vm: MainViewModel, nav: NavController, noteId: Long) {
    val note by vm.repo.note(noteId).collectAsState(initial = null)
    val n = note ?: return
    Board(vm, nav, n)
}

@Composable
private fun Board(vm: MainViewModel, nav: NavController, note: NoteEntity) {
    val density = LocalDensity.current
    val blocks = remember(note.body) { note.body.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() } }
    val saved = remember(note.id) {
        runCatching { json.decodeFromString<Map<String, List<Float>>>(note.canvasJson) }.getOrDefault(emptyMap())
    }
    fun default(i: Int) = saved[i.toString()]?.let { Offset(it[0], it[1]) } ?: Offset(24f + (i % 2) * 200f, 24f + (i / 2) * 190f)

    // Positions in dp; only blocks the user has moved this session are stored here.
    val positions = remember(note.id) { mutableStateMapOf<Int, Offset>() }
    val latest by rememberUpdatedState(note)
    val latestBlocks by rememberUpdatedState(blocks)

    fun persist() {
        val map = latestBlocks.indices.associate { i -> i.toString() to (positions[i] ?: default(i)).let { listOf(it.x, it.y) } }
        vm.saveLayout(latest.copy(canvasJson = json.encodeToString(map)))
    }

    SkinScaffold(title = note.displayTitle(), subtitle = "Board", onBack = { nav.popBackStack() }) {
        SkinSegmented(listOf("Text", "Board"), 1, { if (it == 0) nav.popBackStack() }, Modifier.padding(16.dp))
        if (blocks.isEmpty()) {
            SkinLabel("Write a few paragraphs first — each one becomes a card.", Modifier.padding(16.dp))
            return@SkinScaffold
        }
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
            Box(Modifier.size(1400.dp, 2000.dp)) {
                blocks.forEachIndexed { i, text ->
                    val pos = positions[i] ?: default(i)
                    SkinCard(
                        Modifier
                            .offset { IntOffset(with(density) { pos.x.dp.roundToPx() }, with(density) { pos.y.dp.roundToPx() }) }
                            .width(180.dp)
                            .pointerInput(i) {
                                detectDragGestures(onDragEnd = { persist() }) { change, drag ->
                                    change.consume()
                                    val cur = positions[i] ?: default(i)
                                    positions[i] = Offset(
                                        (cur.x + drag.x / density.density).coerceAtLeast(0f),
                                        (cur.y + drag.y / density.density).coerceAtLeast(0f),
                                    )
                                }
                            },
                    ) {
                        val lines = text.lines()
                        val head = lines.first().removePrefix("## ").removePrefix("# ")
                        Text(head, fontWeight = FontWeight.Bold, maxLines = 3)
                        if (lines.size > 1) Text(lines.drop(1).joinToString("\n"), maxLines = 8)
                    }
                }
            }
        }
    }
}
