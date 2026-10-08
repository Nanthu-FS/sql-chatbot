package app.monoworkspace.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.monoworkspace.model.Page
import app.monoworkspace.ui.common.LayoutMode
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.Navigator
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.ExpandingActionButton
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.Messenger
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.MonoSnackbarHost
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.VerticalRule
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.database.DatabaseScreen
import app.monoworkspace.ui.home.HomeScreen
import app.monoworkspace.ui.page.PageScreen
import app.monoworkspace.ui.search.SearchScreen
import app.monoworkspace.ui.settings.SettingsScreen
import app.monoworkspace.ui.templates.NewSheet
import app.monoworkspace.ui.templates.TemplatesSheet
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.trash.TrashScreen
import kotlinx.coroutines.launch

private sealed interface ShellSheet {
    data class New(val parentId: String?) : ShellSheet
    data class Templates(val parentId: String?) : ShellSheet
    data class Move(val page: Page) : ShellSheet
}

@Composable
fun AppShell(initialPageId: String?) {
    val container = LocalAppContainer.current
    val layout = LocalWindowLayout.current
    val nav = rememberNavController()
    val shell = monoViewModel { c, h -> ShellViewModel(c, h) }
    val state by shell.state.collectAsStateWithLifecycle()
    val expanded by shell.expanded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val messenger = remember { Messenger(snackbar, scope) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var sheet by remember { mutableStateOf<ShellSheet?>(null) }

    val entry by nav.currentBackStackEntryAsState()
    val currentPageId = entry?.let { pageIdOf(it) }
    val section = entry?.destination?.let { d ->
        when {
            d.hasRoute<HomeRoute>() -> "home"
            d.hasRoute<SearchRoute>() -> "search"
            d.hasRoute<TrashRoute>() -> "trash"
            d.hasRoute<SettingsRoute>() -> "settings"
            else -> null
        }
    }

    val navigator = remember(nav) {
        object : Navigator {
            override fun openPage(pageId: String, blockId: String?) {
                scope.launch {
                    val page = container.pages.get(pageId) ?: run { messenger.show("That page no longer exists"); return@launch }
                    if (page.isTrashed) {
                        messenger.show("That page is in the trash")
                        return@launch
                    }
                    nav.navigate(if (page.isDatabase) DatabaseRoute(pageId) else PageRoute(pageId, blockId)) { launchSingleTop = true }
                    drawerState.close()
                }
            }

            override fun openHome() {
                if (!nav.popBackStack<HomeRoute>(inclusive = false)) nav.navigate(HomeRoute) { launchSingleTop = true }
                scope.launch { drawerState.close() }
            }

            override fun openSearch() = navTo(nav, SearchRoute).also { scope.launch { drawerState.close() } }
            override fun openTrash() = navTo(nav, TrashRoute).also { scope.launch { drawerState.close() } }
            override fun openSettings() = navTo(nav, SettingsRoute).also { scope.launch { drawerState.close() } }
            override fun back() {
                nav.popBackStack()
            }

            override fun openDrawer() {
                scope.launch { drawerState.open() }
            }

            override fun showNewSheet(parentId: String?) {
                sheet = ShellSheet.New(parentId)
            }

            override fun showTemplates(parentId: String?) {
                sheet = ShellSheet.Templates(parentId)
            }
        }
    }

    // Cold start: reopen the last page, or the tour right after seeding.
    LaunchedEffect(initialPageId) {
        if (initialPageId != null) navigator.openPage(initialPageId)
    }

    val actions = SidebarActions(
        onSearch = navigator::openSearch,
        onHome = navigator::openHome,
        onNew = { sheet = ShellSheet.New(null) },
        onTemplates = { sheet = ShellSheet.Templates(null) },
        onTrash = navigator::openTrash,
        onSettings = navigator::openSettings,
        onOpen = { navigator.openPage(it.id) },
        onToggle = shell::toggleExpanded,
        onAddChild = { p -> shell.createPage(p.id) { navigator.openPage(it.id) } },
        onFavorite = { p, fav -> shell.setFavorite(p.id, fav) },
        onMoveFavorite = { p, up -> shell.moveFavorite(p.id, up) },
        onMove = { sheet = ShellSheet.Move(it) },
        onTrashPage = { p ->
            shell.trash(p.id) {
                if (p.id == currentPageId) navigator.back()
                messenger.show("Moved “${p.displayTitle}” to trash", "Undo") { shell.restore(p.id) }
            }
        },
        onCollapse = if (layout.usesSidebar) ({ shell.setSidebarCollapsed(true) }) else null,
    )

    CompositionLocalProvider(LocalNavigator provides navigator, LocalMessenger provides messenger) {
        Box(Modifier.fillMaxSize().background(MonoColors.Background)) {
            if (layout.usesSidebar) {
                Row(Modifier.fillMaxSize()) {
                    if (state.sidebarCollapsed) {
                        SidebarRail(actions, { shell.setSidebarCollapsed(false) }, section)
                        VerticalRule()
                    } else {
                        val base = if (layout.mode == LayoutMode.Landscape) minOf(state.sidebarWidthDp, 260) else state.sidebarWidthDp
                        ResizableSidebar(base, onResized = shell::setSidebarWidth) { w ->
                            WorkspaceSidebar(state, expanded.toSet(), currentPageId, section, actions, Modifier.width(w.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { MonoNavHost(nav) }
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    scrimColor = MonoColors.Scrim,
                    drawerContent = {
                        Row(Modifier.fillMaxHeight().widthIn(max = 320.dp).background(MonoColors.Background)) {
                            WorkspaceSidebar(state, expanded.toSet(), currentPageId, section, actions, Modifier.weight(1f))
                            VerticalRule()
                        }
                    },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { MonoNavHost(nav) }
                        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                        if (!imeVisible) BottomBar(section, navigator) { sheet = ShellSheet.New(null) }
                    }
                }
            }
            MonoSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (layout.usesSidebar) 0.dp else 64.dp))
        }

        when (val s = sheet) {
            is ShellSheet.New -> NewSheet(
                onDismiss = { sheet = null },
                onNewPage = { sheet = null; shell.createPage(s.parentId) { navigator.openPage(it.id) } },
                onNewDatabase = { sheet = null; shell.createDatabase(s.parentId) { navigator.openPage(it.id) } },
                onFromTemplate = { sheet = ShellSheet.Templates(s.parentId) },
            )
            is ShellSheet.Templates -> TemplatesSheet(
                onDismiss = { sheet = null },
                onPick = { payload -> sheet = null; shell.fromTemplate(payload, s.parentId) { navigator.openPage(it.id) } },
            )
            is ShellSheet.Move -> MonoBottomSheet(onDismiss = { sheet = null }, title = "Move “${s.page.displayTitle}” to") {
                PagePickerList(state.tree, excludeSubtreeOf = s.page.id, onPick = { parent ->
                    sheet = null
                    shell.move(s.page.id, parent) { err -> messenger.show(err ?: "Moved") }
                })
            }
            null -> Unit
        }
    }
}

private fun navTo(nav: NavHostController, route: Any) {
    nav.navigate(route) { launchSingleTop = true }
}

private fun pageIdOf(entry: NavBackStackEntry): String? {
    val d = entry.destination
    return when {
        d.hasRoute<PageRoute>() -> entry.toRoute<PageRoute>().pageId
        d.hasRoute<DatabaseRoute>() -> entry.toRoute<DatabaseRoute>().pageId
        else -> null
    }
}

@Composable
private fun MonoNavHost(nav: NavHostController) {
    val reduce = LocalReduceMotion.current
    val ms = if (reduce) 0 else Motion.MEDIUM
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) EnterTransition.None else slideInHorizontally(tween(ms)) { it / 5 } + fadeIn(tween(ms))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) ExitTransition.None else slideOutHorizontally(tween(ms)) { -it / 10 } + fadeOut(tween(Motion.FAST))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduce) EnterTransition.None else slideInHorizontally(tween(ms)) { -it / 10 } + fadeIn(tween(ms))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduce) ExitTransition.None else slideOutHorizontally(tween(ms)) { it / 5 } + fadeOut(tween(Motion.FAST))
    }
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        modifier = Modifier.fillMaxSize().background(MonoColors.Background),
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable<HomeRoute> { HomeScreen() }
        composable<PageRoute> { PageScreen() }
        composable<DatabaseRoute> { DatabaseScreen() }
        composable<SearchRoute> { SearchScreen() }
        composable<TrashRoute> { TrashScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}

/** Sidebar with a drag handle on its edge (S Pen friendly) to resize it. */
@Composable
private fun ResizableSidebar(widthDp: Int, onResized: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    var width by remember(widthDp) { mutableFloatStateOf(widthDp.toFloat()) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragging by interaction.collectIsDraggedAsState()
    val onResizedState by rememberUpdatedState(onResized)
    Row(Modifier.fillMaxHeight()) {
        content(width.toInt())
        Box(
            Modifier
                .fillMaxHeight()
                .width(9.dp)
                .hoverable(interaction)
                .draggable(
                    state = rememberDraggableState { delta -> width = (width + with(density) { delta.toDp().value }).coerceIn(220f, 420f) },
                    orientation = Orientation.Horizontal,
                    interactionSource = interaction,
                    onDragStopped = { onResizedState(width.toInt()) },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxHeight().width(if (hovered || dragging) 3.dp else 1.dp).background(MonoColors.Rule))
        }
    }
}

@Composable
private fun BottomBar(section: String?, navigator: Navigator, onNew: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MonoColors.Background)) {
        SectionRule()
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            BottomItem(MonoIcons.Home, "Home", section == "home", Modifier.weight(1f), navigator::openHome)
            BottomItem(MonoIcons.Search, "Search", section == "search", Modifier.weight(1f), navigator::openSearch)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ExpandingActionButton(MonoIcons.Plus, "New", onNew, expandedWidth = 112.dp)
            }
        }
    }
}

@Composable
private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().inkClickable(onClick = onClick, selected = selected, showBar = false),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        MonoIcon(icon, null, tint = if (selected) MonoColors.Ink else MonoColors.Secondary)
        Text(label.uppercase(), style = MonoType.label.copy(color = if (selected) MonoColors.Ink else MonoColors.Secondary))
        Box(Modifier.padding(top = 4.dp).width(if (selected) 16.dp else 0.dp).height(2.dp).background(MonoColors.Ink))
    }
}
