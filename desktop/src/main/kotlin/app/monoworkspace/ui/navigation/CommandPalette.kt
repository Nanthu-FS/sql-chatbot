package app.monoworkspace.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monoworkspace.model.Page
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.components.Overlay
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.PanelCard
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One palette entry: a page to jump to or a command to run. */
data class Command(
    val title: String,
    val hint: String,
    val icon: ImageVector?,
    val page: Page? = null,
    val shortcut: String? = null,
    val run: () -> Unit,
)

/** Subsequence match score; higher is better, null when the query doesn't match. */
internal fun fuzzyScore(text: String, query: String): Int? {
    if (query.isEmpty()) return 0
    val t = text.lowercase()
    val q = query.lowercase()
    val direct = t.indexOf(q)
    if (direct >= 0) return 1000 - direct * 4 - (t.length - q.length) + if (direct == 0) 200 else 0
    var ti = 0
    var score = 0
    var streak = 0
    for (c in q) {
        val found = t.indexOf(c, ti)
        if (found < 0) return null
        streak = if (found == ti) streak + 1 else 0
        score += 10 + streak * 6 - (found - ti)
        ti = found + 1
    }
    return score
}

/**
 * Ctrl+P: type to jump to any page or run a command. Arrow keys move a
 * spring-loaded highlight; results cascade in with a short stagger.
 */
@Composable
fun CommandPalette(commands: List<Command>, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val nav = app.monoworkspace.ui.common.LocalNavigator.current
    val pages by remember { container.pages.observeAll() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val reduce = LocalReduceMotion.current

    val results = remember(query, pages, commands) {
        val pageCommands = pages.map { p ->
            Command(p.displayTitle, if (p.isDatabase) "Database" else if (p.databaseId != null) "Row" else "Page", null, page = p, run = { nav.openPage(p.id) })
        }
        val scored = (commands + pageCommands).mapNotNull { c -> fuzzyScore(c.title, query.trim())?.let { c to it } }
        if (query.isBlank()) commands + pageCommands.take(30) else scored.sortedByDescending { it.second }.map { it.first }.take(60)
    }
    LaunchedEffect(results) { index = 0 }
    LaunchedEffect(index) { if (results.isNotEmpty()) listState.animateScrollToItem((index - 3).coerceAtLeast(0)) }
    LaunchedEffect(Unit) { delay(30); focus.requestFocus() }

    fun pick(c: Command) {
        onDismiss()
        c.run()
    }

    Overlay(onDismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            PanelCard(Modifier.padding(top = 48.dp).widthIn(max = 640.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MonoIcons.Search, null, Modifier.size(22.dp), tint = MonoColors.Ink)
                    Spacer(Modifier.width(Space.m))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Jump to a page or run a command…", style = MonoType.h3.copy(color = MonoColors.Tertiary, fontWeight = FontWeight.Normal))
                        BasicTextField(
                            query, { query = it },
                            Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionDown -> { if (results.isNotEmpty()) index = (index + 1) % results.size; true }
                                    Key.DirectionUp -> { if (results.isNotEmpty()) index = (index - 1 + results.size) % results.size; true }
                                    Key.Enter, Key.NumPadEnter -> { results.getOrNull(index)?.let(::pick); true }
                                    else -> false
                                }
                            },
                            singleLine = true,
                            textStyle = MonoType.h3.copy(fontWeight = FontWeight.Normal),
                            cursorBrush = SolidColor(MonoColors.Ink),
                        )
                    }
                    Text("ESC", style = MonoType.label.copy(color = MonoColors.Tertiary))
                }
                SectionRule()
                if (results.isEmpty()) {
                    Text("Nothing matches “$query”.", Modifier.padding(Space.l), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                } else {
                    val rowH = 48.dp
                    val firstVisible = listState.firstVisibleItemIndex
                    val scrollOff = listState.firstVisibleItemScrollOffset
                    val target = rowH * (index - firstVisible)
                    val barY by animateDpAsState(target, if (reduce) tween(0) else spring(dampingRatio = 0.7f, stiffness = 700f), label = "palBar")
                    Box(Modifier.heightIn(max = 432.dp)) {
                        // The sliding highlight lives under the rows and springs between them.
                        Box(
                            Modifier
                                .offset(y = barY)
                                .graphicsLayer { translationY = -scrollOff.toFloat() }
                                .fillMaxWidth()
                                .height(rowH)
                                .background(MonoColors.Ink),
                        )
                        LazyColumn(state = listState) {
                            itemsIndexed(results, key = { _, c -> (c.page?.id ?: "cmd:" + c.title) }) { i, c ->
                                PaletteRow(c, i == index, i, reduce, rowH, onHover = { index = i }) { pick(c) }
                            }
                        }
                    }
                }
                SectionRule()
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                    Text("↑ ↓ to move   ·   Enter to open   ·   Ctrl+P anytime", style = MonoType.caption.copy(color = MonoColors.Tertiary))
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(c: Command, selected: Boolean, i: Int, reduce: Boolean, height: androidx.compose.ui.unit.Dp, onHover: () -> Unit, onClick: () -> Unit) {
    val appear = remember { Animatable(if (reduce) 1f else 0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay((i.coerceAtMost(10) * 18).toLong())
            scope.launch { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
        }
    }
    val ink = if (selected) MonoColors.OnInk else MonoColors.Ink
    val sub = if (selected) MonoColors.Hairline else MonoColors.Secondary
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 14f
            }
            .onPointerEvent(PointerEventType.Move) { onHover() }
            .inkClickable(onClick = onClick, showBar = false)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c.page != null) PageGlyph(c.page.icon, c.page.isDatabase, size = 18.dp, tint = ink)
        else if (c.icon != null) Icon(c.icon, null, Modifier.size(18.dp), tint = ink)
        Spacer(Modifier.width(Space.m))
        Text(c.title, Modifier.weight(1f), style = MonoType.body.copy(color = ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
        c.shortcut?.let { Text(it, Modifier.padding(start = Space.s), style = MonoType.label.copy(color = sub)) }
        Text(c.hint.uppercase(), Modifier.padding(start = Space.m), style = MonoType.label.copy(color = sub))
    }
}
