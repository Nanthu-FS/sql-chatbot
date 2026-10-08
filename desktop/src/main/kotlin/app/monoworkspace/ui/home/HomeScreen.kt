package app.monoworkspace.ui.home

import androidx.compose.ui.draw.clip
import app.monoworkspace.ui.theme.CardSurface
import app.monoworkspace.ui.theme.HotSurface
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.LocalTheme
import app.monoworkspace.ui.theme.MonoShapes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import app.monoworkspace.ui.components.Entrance
import app.monoworkspace.ui.components.KineticText
import app.monoworkspace.ui.components.VerticalRule
import app.monoworkspace.ui.components.rememberEntrance
import app.monoworkspace.ui.components.staggerIn

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
import app.monoworkspace.ui.common.SavedStateHandle
import app.monoworkspace.ui.common.ViewModel
import androidx.compose.runtime.collectAsState
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
    val state by vm.state.collectAsState()
    val nav = LocalNavigator.current
    val layout = LocalWindowLayout.current
    val listState = rememberLazyListState()
    val margin = layout.margin
    val entrance = rememberEntrance()

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = if (!layout.usesSidebar) ({ MonoIconButton(MonoIcons.Menu, "Open navigation", nav::openDrawer) }) else null,
            title = { LabelText(state.workspaceName, Modifier.padding(start = if (layout.usesSidebar) Space.m else 0.dp)) },
            actions = { MonoIconButton(MonoIcons.Search, "Search (Ctrl+Shift+F)", nav::openSearch) },
        )
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 1120.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item("hero") {
                    Box(Modifier.padding(horizontal = margin).padding(top = Space.x3, bottom = Space.xl)) {
                        if (!LocalReduceMotion.current) LocalTheme.current.fx.HeroOrnament(Modifier.matchParentSize())
                        Column {
                            KineticText(greeting(), MonoType.display.copy(fontSize = 64.sp, lineHeight = 68.sp))
                            Spacer(Modifier.height(Space.s))
                            Text(Formats.longToday(), Modifier.staggerIn(entrance, 6), style = MonoType.body.tnum().copy(color = MonoColors.Secondary))
                        }
                    }
                }
                item("quick") {
                    QuickActions(
                        Modifier.padding(horizontal = margin).padding(bottom = Space.xl).staggerIn(entrance, 3),
                        onNewPage = { vm.createPage { nav.openPage(it.id) } },
                        onNew = { nav.showNewSheet(null) },
                        onTemplates = { nav.showTemplates(null) },
                        onSearch = nav::openSearch,
                    )
                }
                if (state.recents.isNotEmpty()) {
                    item("recent-h") { SectionHeader("Jump back in", Modifier.padding(horizontal = margin).staggerIn(entrance, 4)) }
                    item("recent") { RecentCards(state.recents, entrance) { nav.openPage(it.id) } }
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
                itemsIndexed(state.favorites, key = { _, p -> "fav-" + p.id }) { i, p ->
                    FavoriteRow(p, Modifier.padding(horizontal = margin).staggerIn(entrance, 6 + i), { nav.openPage(p.id) }, vm::moveFavorite, vm::setFavorite)
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
                itemsIndexed(state.roots, key = { _, p -> "root-" + p.id }) { i, p ->
                    InkRow(
                        title = p.displayTitle,
                        caption = (if (p.isDatabase) "Database · " else "") + "Edited " + Formats.relative(p.editedAt),
                        onClick = { nav.openPage(p.id) },
                        modifier = Modifier.padding(horizontal = margin).staggerIn(entrance, 8 + state.favorites.size + i),
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
private fun RecentCards(pages: List<Page>, entrance: Entrance, onOpen: (Page) -> Unit) {
    val focus = rememberHoverFocusState()
    val margin = LocalWindowLayout.current.margin
    LazyRow(
        contentPadding = PaddingValues(horizontal = margin, vertical = Space.l),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
            val interaction = remember { MutableInteractionSource() }
            val theme = LocalTheme.current
            val shape = MonoShapes.card
            CardSurface {
            Column(
                Modifier
                    .staggerIn(entrance, 4 + i)
                    .width(200.dp)
                    .hoverable(interaction)
                    .hoverFocus(focus, p.id, interaction)
                    .clip(shape)
                    .background(MonoColors.Background)
                    .border(1.dp, MonoColors.Rule, shape)
                    .inkClickable(onClick = { onOpen(p) }, showBar = false),
            ) {
                Box(Modifier.fillMaxWidth().height(84.dp).drawBehind { drawRect(theme.cover(size)) }) {
                    if (p.cover != null) CoverArt(p.cover, Modifier.fillMaxSize())
                    Box(
                        Modifier.padding(Space.s).size(36.dp).clip(MonoShapes.small).background(MonoColors.Background).border(1.dp, MonoColors.Rule, MonoShapes.small),
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

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..11 -> "Good morning."
        in 12..17 -> "Good afternoon."
        else -> "Good evening."
    }
}

/** Four big square tiles; an ink fill rises from the bottom edge on hover. */
@Composable
private fun QuickActions(modifier: Modifier, onNewPage: () -> Unit, onNew: () -> Unit, onTemplates: () -> Unit, onSearch: () -> Unit) {
    val shape = MonoShapes.card
    Row(modifier.fillMaxWidth().clip(shape).border(LocalTheme.current.ruleWidth, MonoColors.Rule, shape)) {
        HotSurface {
            Box(Modifier.weight(1f).background(LocalTheme.current.hotBrush)) {
                QuickTile(MonoIcons.Page, "New page", "Ctrl+N", Modifier.fillMaxWidth(), onNewPage)
            }
        }
        VerticalRule()
        QuickTile(MonoIcons.Table, "Create…", "Ctrl+Shift+N", Modifier.weight(1f), onNew)
        VerticalRule()
        QuickTile(MonoIcons.Template, "Templates", "Start from a layout", Modifier.weight(1f), onTemplates)
        VerticalRule()
        QuickTile(MonoIcons.Search, "Search", "Ctrl+Shift+F", Modifier.weight(1f), onSearch)
    }
}

@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val reduce = app.monoworkspace.ui.theme.LocalReduceMotion.current
    val fill by animateFloatAsState(if (hovered) 1f else 0f, if (reduce) tween(0) else spring(dampingRatio = 0.8f, stiffness = 320f), label = "tile")
    val ink = androidx.compose.ui.graphics.lerp(MonoColors.Ink, MonoColors.OnInk, fill)
    val sub = androidx.compose.ui.graphics.lerp(MonoColors.Secondary, MonoColors.Hairline, fill)
    val inkFill = MonoColors.Ink
    Column(
        modifier
            .height(112.dp)
            .hoverable(interaction)
            .drawBehind { drawRect(inkFill, androidx.compose.ui.geometry.Offset(0f, size.height * (1f - fill)), androidx.compose.ui.geometry.Size(size.width, size.height * fill)) }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interaction, null, onClick = onClick)
            .padding(Space.l),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, Modifier.size(24.dp).graphicsLayer { translationY = -fill * 4.dp.toPx(); rotationZ = fill * -6f }, tint = ink)
        Column {
            Text(title, style = MonoType.body.copy(fontWeight = FontWeight.SemiBold, color = ink))
            Text(hint.uppercase(), style = MonoType.label.copy(color = sub, fontSize = 10.sp))
        }
    }
}
