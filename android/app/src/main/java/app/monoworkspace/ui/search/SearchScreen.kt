package app.monoworkspace.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.monoworkspace.AppContainer
import app.monoworkspace.data.repo.DatabaseRef
import app.monoworkspace.data.repo.SearchFilters
import app.monoworkspace.data.repo.SearchHit
import app.monoworkspace.data.repo.SearchKind
import app.monoworkspace.ui.common.Formats
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.BackToTopFor
import app.monoworkspace.ui.components.DatePickerSheet
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MonoChip
import app.monoworkspace.ui.components.MonoDropdown
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoTopBar
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionHeader
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

enum class ModifiedRange(val label: String) { ANY("Any time"), WEEK("7 days"), MONTH("30 days"), CUSTOM("Custom") }

data class SearchState(
    val query: String = "",
    val kinds: Set<SearchKind> = emptySet(),
    val modified: ModifiedRange = ModifiedRange.ANY,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val databaseId: String? = null,
)

data class SearchResults(val query: String = "", val hits: List<SearchHit> = emptyList(), val searching: Boolean = false)

class SearchViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val input = MutableStateFlow(SearchState(query = handle["q"] ?: ""))

    val databases: StateFlow<List<DatabaseRef>> = c.databases.observeDatabases().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<SearchResults> = combine(input.debounce(150), c.pages.observeAll()) { s, _ -> s }
        .mapLatest { s ->
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val (after, before) = when (s.modified) {
                ModifiedRange.ANY -> null to null
                ModifiedRange.WEEK -> (now - 7L * 86_400_000) to null
                ModifiedRange.MONTH -> (now - 30L * 86_400_000) to null
                ModifiedRange.CUSTOM -> s.customFrom?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() to
                    s.customTo?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
            }
            SearchResults(s.query, c.search.search(s.query, SearchFilters(s.kinds, after, before, s.databaseId)))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun update(transform: (SearchState) -> SearchState) {
        input.value = transform(input.value)
        handle["q"] = input.value.query
    }
}

@Composable
fun SearchScreen() {
    val vm = monoViewModel { c, h -> SearchViewModel(c, h) }
    val input by vm.input.collectAsState()
    val results by vm.results.collectAsStateWithLifecycle()
    val databases by vm.databases.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val layout = LocalWindowLayout.current
    val margin = layout.margin
    val focus = remember { FocusRequester() }
    var pickRange by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = { MonoIconButton(MonoIcons.Back, "Back", nav::back) },
            title = { LabelText("Search") },
        )
        Column(Modifier.padding(horizontal = margin).padding(top = Space.l)) {
            LabelText("Search everything", color = MonoColors.Secondary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                MonoIcon(MonoIcons.Search, null)
                Spacer(Modifier.width(Space.s))
                BasicTextField(
                    value = input.query,
                    onValueChange = { q -> vm.update { it.copy(query = q) } },
                    modifier = Modifier.weight(1f).focusRequester(focus).padding(vertical = Space.m),
                    textStyle = MonoType.h3,
                    singleLine = true,
                    cursorBrush = SolidColor(MonoColors.Ink),
                    decorationBox = { inner ->
                        Box {
                            if (input.query.isEmpty()) Text("Pages, rows, text…", style = MonoType.h3.copy(color = MonoColors.Tertiary))
                            inner()
                        }
                    },
                )
                if (input.query.isNotEmpty()) MonoIconButton(MonoIcons.X, "Clear search", { vm.update { it.copy(query = "") } })
            }
            SectionRule()
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = margin, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            MonoChip("All", input.kinds.isEmpty(), { vm.update { it.copy(kinds = emptySet()) } })
            SearchKind.entries.forEach { k ->
                val label = when (k) { SearchKind.PAGE -> "Page"; SearchKind.ROW -> "Row"; SearchKind.BLOCK -> "Block"; SearchKind.DATABASE -> "Database" }
                MonoChip(label, k in input.kinds, { vm.update { s -> s.copy(kinds = if (k in s.kinds) s.kinds - k else s.kinds + k) } })
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = margin),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModifiedRange.entries.forEach { r ->
                val label = if (r == ModifiedRange.CUSTOM && input.customFrom != null) {
                    "${input.customFrom} → ${input.customTo ?: "now"}"
                } else r.label
                MonoChip(label, input.modified == r, {
                    if (r == ModifiedRange.CUSTOM) pickRange = 1 else vm.update { it.copy(modified = r) }
                }, leading = if (r == ModifiedRange.CUSTOM) MonoIcons.Calendar else null)
            }
        }
        if (databases.isNotEmpty()) {
            MonoDropdown(
                databases.firstOrNull { it.database.id == input.databaseId },
                listOf<DatabaseRef?>(null) + databases,
                { it?.page?.displayTitle ?: "Any database" },
                { d -> vm.update { it.copy(databaseId = d?.database?.id) } },
                Modifier.padding(horizontal = margin, vertical = Space.s).width(260.dp),
                placeholder = "Any database",
            )
        }
        SectionRule(Modifier.padding(top = Space.s))

        Box(Modifier.weight(1f)) {
            when {
                input.query.isBlank() -> EmptyState("Type to search titles, text and row values on this device.", null, null, Modifier.padding(horizontal = margin), icon = MonoIcons.Search)
                results.hits.isEmpty() && results.query == input.query -> EmptyState(
                    "Nothing matches “${input.query}”.", "Clear filters",
                    { vm.update { it.copy(kinds = emptySet(), modified = ModifiedRange.ANY, databaseId = null) } },
                    Modifier.padding(horizontal = margin),
                )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                    SearchKind.entries.forEach { kind ->
                        val hits = results.hits.filter { it.kind == kind }
                        if (hits.isNotEmpty()) {
                            item("h-" + kind.name) {
                                SectionHeader(kind.label, Modifier.padding(horizontal = margin).padding(top = Space.l)) {
                                    Text("${hits.size}", Modifier.padding(bottom = Space.s), style = MonoType.caption.tnum())
                                }
                            }
                            items(hits, key = { kind.name + it.refId }) { hit -> HitRow(hit, input.query) { nav.openPage(hit.pageId, if (hit.kind == SearchKind.BLOCK) hit.refId else null) } }
                        }
                    }
                }
            }
            BackToTopFor(listState, Modifier.align(Alignment.BottomEnd).padding(Space.l))
        }
    }

    pickRange?.let { step ->
        DatePickerSheet(null, { pickRange = null }, onSave = { picked ->
            pickRange = null
            if (picked != null) vm.update { it.copy(modified = ModifiedRange.CUSTOM, customFrom = picked.start, customTo = picked.end) }
        }, allowTime = false, title = if (step == 1) "Modified between" else "Modified")
    }
}

@Composable
private fun HitRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    val margin = LocalWindowLayout.current.margin
    val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
    Column(Modifier.padding(horizontal = margin)) {
        Row(
            Modifier.fillMaxWidth().inkClickable(onClick = onClick).padding(horizontal = Space.s, vertical = Space.m),
            verticalAlignment = Alignment.Top,
        ) {
            PageGlyph(hit.pageIcon, hit.kind == SearchKind.DATABASE, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                if (hit.kind == SearchKind.BLOCK) {
                    Text(highlight(hit.snippet, tokens), style = MonoType.bodySmall, maxLines = 3)
                    Text("in ${hit.pageTitle} · ${Formats.relative(hit.editedAt)}", style = MonoType.caption)
                } else {
                    Text(highlight(hit.pageTitle, tokens), style = MonoType.body, maxLines = 2)
                    val caption = buildString {
                        if (hit.snippet.isNotBlank()) append(hit.snippet).append(" · ")
                        append("Edited ").append(Formats.relative(hit.editedAt))
                    }
                    Text(caption, style = MonoType.caption, maxLines = 2)
                }
            }
        }
        Hairline()
    }
}

private fun highlight(text: String, tokens: List<String>) = buildAnnotatedString {
    val lower = text.lowercase()
    var i = 0
    while (i < text.length) {
        val match = tokens.mapNotNull { t -> lower.indexOf(t, i).takeIf { it >= 0 }?.let { it to t.length } }.minByOrNull { it.first }
        if (match == null) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, match.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(match.first, match.first + match.second)) }
        i = match.first + match.second
    }
}
