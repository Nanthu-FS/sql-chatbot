package app.monoworkspace.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.monoworkspace.AppContainer
import app.monoworkspace.model.Page
import app.monoworkspace.ui.common.Formats
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.BackToTopFor
import app.monoworkspace.ui.components.CoverArt
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.InkRow
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.MonoTopBar
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionHeader
import app.monoworkspace.ui.components.hoverFocus
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.components.rememberHoverFocusState
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val loaded: Boolean = false,
    val workspaceName: String = "",
    val roots: List<Page> = emptyList(),
    val favorites: List<Page> = emptyList(),
    val recents: List<Page> = emptyList(),
)

class HomeViewModel(private val c: AppContainer, @Suppress("unused") handle: SavedStateHandle) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        c.settings.settings, c.pages.observeTree(), c.pages.observeFavorites(), c.pages.observeRecents(),
    ) { settings, tree, favs, recents ->
        val ids = tree.map { it.id }.toSet()
        HomeState(true, settings.workspaceName, tree.filter { it.parentId == null || it.parentId !in ids }, favs, recents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun createPage(onCreated: (Page) -> Unit) = viewModelScope.launch { onCreated(c.pages.createPage(null)) }
    fun setFavorite(p: Page, fav: Boolean) = viewModelScope.launch { c.pages.setFavorite(p.id, fav) }
    fun moveFavorite(p: Page, up: Boolean) = viewModelScope.launch { c.pages.moveFavorite(state.value.favorites, p.id, up) }
}

@Composable
fun HomeScreen() {
    val vm = monoViewModel { c, h -> HomeViewModel(c, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val layout = LocalWindowLayout.current
    val listState = rememberLazyListState()
    val margin = layout.margin

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = if (!layout.usesSidebar) ({ MonoIconButton(MonoIcons.Menu, "Open navigation", nav::openDrawer) }) else null,
            title = { LabelText(state.workspaceName, Modifier.padding(start = if (layout.usesSidebar) Space.m else 0.dp)) },
            actions = { MonoIconButton(MonoIcons.Search, "Search", nav::openSearch) },
        )
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item("hero") {
                    Column(Modifier.padding(horizontal = margin).padding(top = Space.xxl, bottom = Space.xl)) {
                        Text("Home", style = MonoType.display)
                        Text(Formats.longToday(), style = MonoType.caption.tnum())
                    }
                }
                if (state.recents.isNotEmpty()) {
                    item("recent-h") { SectionHeader("Jump back in", Modifier.padding(horizontal = margin)) }
                    item("recent") { RecentCards(state.recents) { nav.openPage(it.id) } }
                }
                item("fav-h") {
                    SectionHeader("Favorites", Modifier.padding(horizontal = margin).padding(top = Space.xl)) {
                        Text("${state.favorites.size}", Modifier.padding(bottom = Space.s), style = MonoType.caption.tnum())
                    }
                }
                if (state.favorites.isEmpty() && state.loaded) {
                    item("fav-empty") {
                        Text(
                            "Star a page from its ⋯ menu to pin it here.",
                            Modifier.padding(horizontal = margin, vertical = Space.l),
                            style = MonoType.bodySmall.copy(color = MonoColors.Secondary),
                        )
                    }
                }
                items(state.favorites, key = { "fav-" + it.id }) { p ->
                    FavoriteRow(p, Modifier.padding(horizontal = margin), { nav.openPage(p.id) }, vm::moveFavorite, vm::setFavorite)
                }
                item("pages-h") {
                    SectionHeader("Pages", Modifier.padding(horizontal = margin).padding(top = Space.xl)) {
                        MonoButton("New page", { vm.createPage { nav.openPage(it.id) } }, style = MonoButtonStyle.Text, icon = MonoIcons.Plus, height = 36.dp)
                    }
                }
                if (state.roots.isEmpty() && state.loaded) {
                    item("pages-empty") {
                        EmptyState(
                            "Your workspace is empty. Start with a blank page.",
                            "New page", { vm.createPage { nav.openPage(it.id) } },
                            Modifier.padding(horizontal = margin),
                        )
                    }
                }
                items(state.roots, key = { "root-" + it.id }) { p ->
                    InkRow(
                        title = p.displayTitle,
                        caption = (if (p.isDatabase) "Database · " else "") + "Edited " + Formats.relative(p.editedAt),
                        onClick = { nav.openPage(p.id) },
                        modifier = Modifier.padding(horizontal = margin),
                        leading = { PageGlyph(p.icon, p.isDatabase) },
                        trailing = { app.monoworkspace.ui.components.MonoIcon(MonoIcons.ChevronRight, null, tint = MonoColors.Tertiary, size = 20.dp) },
                    )
                }
            }
            BackToTopFor(listState, Modifier.align(Alignment.BottomEnd).padding(Space.l))
        }
    }
}

@Composable
private fun RecentCards(pages: List<Page>, onOpen: (Page) -> Unit) {
    val focus = rememberHoverFocusState()
    val margin = LocalWindowLayout.current.margin
    LazyRow(
        contentPadding = PaddingValues(horizontal = margin, vertical = Space.l),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        items(pages, key = { it.id }) { p ->
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .width(176.dp)
                    .hoverable(interaction)
                    .hoverFocus(focus, p.id, interaction)
                    .background(MonoColors.Background)
                    .border(1.dp, MonoColors.Ink)
                    .inkClickable(onClick = { onOpen(p) }, showBar = false),
            ) {
                Box(Modifier.fillMaxWidth().height(64.dp).background(MonoColors.Tint)) {
                    if (p.cover != null) CoverArt(p.cover, Modifier.fillMaxSize())
                    Box(
                        Modifier.padding(Space.s).size(36.dp).background(MonoColors.Background).border(1.dp, MonoColors.Ink),
                        contentAlignment = Alignment.Center,
                    ) { PageGlyph(p.icon, p.isDatabase, size = 20.dp) }
                }
                Column(Modifier.padding(Space.m)) {
                    Text(p.displayTitle, style = MonoType.bodySmall.copy(fontSize = 15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Space.xs))
                    Text(Formats.relative(p.editedAt), style = MonoType.caption.tnum(), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(p: Page, modifier: Modifier, onOpen: () -> Unit, onMove: (Page, Boolean) -> Unit, onFavorite: (Page, Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        InkRow(
            title = p.displayTitle,
            caption = "Edited " + Formats.relative(p.editedAt),
            onClick = onOpen,
            onLongClick = { menu = true },
            leading = { PageGlyph(p.icon, p.isDatabase) },
            trailing = { MonoIconButton(MonoIcons.More, "Favorite actions", { menu = true }, tint = MonoColors.Secondary, iconSize = 20.dp) },
        )
        Box(Modifier.align(Alignment.TopEnd).widthIn(min = 1.dp)) {
            MonoMenu(
                menu, { menu = false },
                listOf(
                    MenuItem("Move up", MonoIcons.ChevronUp) { onMove(p, true) },
                    MenuItem("Move down", MonoIcons.ChevronDown) { onMove(p, false) },
                    MenuItem("Remove from favorites", MonoIcons.Star) { onFavorite(p, false) },
                ),
            )
        }
    }
}
