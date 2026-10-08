package app.monoworkspace.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.monoworkspace.model.Page
import app.monoworkspace.ui.common.BackDispatcher
import app.monoworkspace.ui.common.BackStackEntry
import app.monoworkspace.ui.common.LayoutMode
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.common.LocalBackDispatcher
import app.monoworkspace.ui.common.LocalBackStackEntry
import app.monoworkspace.ui.common.LocalKeyRouter
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.Navigator
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.LocalOverlayHost
import app.monoworkspace.ui.components.Messenger
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoSnackbarHost
import app.monoworkspace.ui.components.OverlayHost
import app.monoworkspace.ui.components.OverlayLayer
import app.monoworkspace.ui.components.VerticalRule
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
import app.monoworkspace.ui.trash.TrashScreen
import kotlinx.coroutines.launch

private sealed interface ShellSheet {
    data class New(val parentId: String?) : ShellSheet
    data class Templates(val parentId: String?) : ShellSheet
    data class Move(val page: Page) : ShellSheet
    data object Palette : ShellSheet
}

/**
 * The desktop shell: resizable sidebar, animated history, overlays and the
 * global keyboard map. [onTitle] keeps the native window title in sync.
 */
@Composable
fun AppShell(initialPageId: String?, onTitle: (String) -> Unit, onLock: (() -> Unit)?, onNavigator: (Navigator) -> Unit = {}) {
    val container = LocalAppContainer.current
    val layout = LocalWindowLayout.current
    val scope = rememberCoroutineScope()
    val nav = remember { NavController(scope) }
    val shell = remember { ShellViewModel(container, BackStackEntry("shell", -1).handle) }
    DisposableEffect(shell) { onDispose { shell.clear() } }
    val state by shell.state.collectAsState()
    val expanded by shell.expanded.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val messenger = remember { Messenger(snackbar, scope) }
    val overlays = remember { OverlayHost() }
    val back = remember { BackDispatcher() }
    var sheet by remember { mutableStateOf<ShellSheet?>(null) }
    // Below 1000dp the sidebar folds into the rail unless opened on purpose.
    var openInNarrow by remember { mutableStateOf(false) }
    val narrow = layout.mode == LayoutMode.Landscape
    val collapsed = if (narrow) !openInNarrow else state.sidebarCollapsed
    fun setCollapsed(c: Boolean) {
        if (narrow) openInNarrow = !c else shell.setSidebarCollapsed(c)
    }

    val entry = nav.current
    val currentPageId = NavController.pageIdOf(entry.route)
    val section = when (entry.route) {
        HomeRoute -> "home"
        SearchRoute -> "search"
        TrashRoute -> "trash"
        SettingsRoute -> "settings"
        else -> null
    }

    val toggleState = rememberUpdatedState { setCollapsed(!collapsed) }
    fun toggleSidebar() = toggleState.value()

    val navigator = remember(nav) {
        object : Navigator {
            override fun openPage(pageId: String, blockId: String?) {
                scope.launch {
                    val page = container.pages.get(pageId) ?: run { messenger.show("That page no longer exists"); return@launch }
                    if (page.isTrashed) {
                        messenger.show("That page is in the trash")
                        return@launch
                    }
                    nav.navigate(if (page.isDatabase) DatabaseRoute(pageId) else PageRoute(pageId, blockId))
                }
            }

            override fun openHome() {
                if (!nav.popTo { it == HomeRoute }) nav.navigate(HomeRoute)
            }

            override fun openSearch() = nav.navigate(SearchRoute)
            override fun openTrash() = nav.navigate(TrashRoute)
            override fun openSettings() = nav.navigate(SettingsRoute)
            override fun back() {
                nav.back()
            }

            override fun openDrawer() {
                toggleSidebar()
            }

            override fun showNewSheet(parentId: String?) {
                sheet = ShellSheet.New(parentId)
            }

            override fun showTemplates(parentId: String?) {
                sheet = ShellSheet.Templates(parentId)
            }
        }
    }

    LaunchedEffect(navigator) { onNavigator(navigator) }

    // Cold start: reopen the last page, or the tour right after seeding.
    LaunchedEffect(initialPageId) {
        if (initialPageId != null) navigator.openPage(initialPageId)
    }

    // Native window title follows the open page.
    val titleSource by remember(currentPageId) {
        if (currentPageId == null) kotlinx.coroutines.flow.flowOf(null) else container.pages.observe(currentPageId)
    }.collectAsState(initial = null)
    val onTitleState by rememberUpdatedState(onTitle)
    LaunchedEffect(titleSource, section) {
        onTitleState(
            titleSource?.displayTitle ?: when (section) {
                "search" -> "Search"
                "trash" -> "Trash"
                "settings" -> "Settings"
                else -> state.workspaceName
            },
        )
    }

    fun newPage(parentId: String? = null) = shell.createPage(parentId) { navigator.openPage(it.id) }

    val commands = remember(collapsed, onLock) {
        listOfNotNull(
            Command("New page", "Command", MonoIcons.Plus, shortcut = "CTRL+N") { newPage() },
            Command("New database", "Command", MonoIcons.Table) { shell.createDatabase(null) { navigator.openPage(it.id) } },
            Command("New from template", "Command", MonoIcons.Template) { sheet = ShellSheet.Templates(null) },
            Command("Home", "Go to", MonoIcons.Home, shortcut = "CTRL+SHIFT+H") { navigator.openHome() },
            Command("Search everything", "Go to", MonoIcons.Search, shortcut = "CTRL+SHIFT+F") { navigator.openSearch() },
            Command("Trash", "Go to", MonoIcons.Trash) { navigator.openTrash() },
            Command("Settings", "Go to", MonoIcons.Settings, shortcut = "CTRL+,") { navigator.openSettings() },
            Command(if (collapsed) "Show sidebar" else "Hide sidebar", "View", MonoIcons.Sidebar, shortcut = "CTRL+\\") { navigator.openDrawer() },
            onLock?.let { lock -> Command("Lock now", "Security", MonoIcons.Lock, shortcut = "CTRL+L") { lock() } },
        )
    }

    // Global keyboard map; screens still see keys first through their own focus handlers.
    val router = LocalKeyRouter.current
    val onLockState by rememberUpdatedState(onLock)
    DisposableEffect(router, nav) {
        router.handler = handler@{ e: KeyEvent ->
            if (e.type != KeyEventType.KeyDown) return@handler false
            val ctrl = e.isCtrlPressed
            when {
                ctrl && e.key == Key.P -> { sheet = if (sheet == ShellSheet.Palette) null else ShellSheet.Palette; true }
                ctrl && e.isShiftPressed && e.key == Key.F -> { navigator.openSearch(); true }
                ctrl && e.isShiftPressed && e.key == Key.H -> { navigator.openHome(); true }
                ctrl && e.isShiftPressed && e.key == Key.N -> { sheet = ShellSheet.New(null); true }
                ctrl && e.key == Key.N -> { newPage(); true }
                ctrl && e.key == Key.Comma -> { navigator.openSettings(); true }
                ctrl && e.key == Key.Backslash -> { navigator.openDrawer(); true }
                ctrl && e.key == Key.L && onLockState != null -> { onLockState?.invoke(); true }
                e.isAltPressed && e.key == Key.DirectionLeft -> { nav.back(); true }
                e.isAltPressed && e.key == Key.DirectionRight -> { nav.forward(); true }
                e.key == Key.Escape -> back.dispatch()
                else -> false
            }
        }
        onDispose { router.handler = null }
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
        onAddChild = { p -> newPage(p.id) },
        onFavorite = { p, fav -> shell.setFavorite(p.id, fav) },
        onMoveFavorite = { p, up -> shell.moveFavorite(p.id, up) },
        onMove = { sheet = ShellSheet.Move(it) },
        onTrashPage = { p ->
            shell.trash(p.id) {
                nav.dropPage(p.id)
                messenger.show("Moved “${p.displayTitle}” to trash", "Undo") { shell.restore(p.id) }
            }
        },
        onCollapse = { setCollapsed(true) },
        onPalette = { sheet = ShellSheet.Palette },
    )

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalMessenger provides messenger,
        LocalOverlayHost provides overlays,
        LocalBackDispatcher provides back,
    ) {
        OverlayLayer(
            overlays,
            Modifier
                .background(MonoColors.Background)
                .onPointerEvent(PointerEventType.Press) { ev ->
                    when (ev.button) {
                        PointerButton.Back -> nav.back()
                        PointerButton.Forward -> nav.forward()
                        else -> Unit
                    }
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = collapsed,
                        transitionSpec = {
                            (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(90)))
                                .using(SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.86f, stiffness = 520f) })
                        },
                        label = "sidebar",
                    ) { isCollapsed ->
                        if (isCollapsed) {
                            Row(Modifier.fillMaxHeight()) {
                                SidebarRail(actions, { setCollapsed(false) }, section)
                                VerticalRule()
                            }
                        } else {
                            ResizableSidebar(state.sidebarWidthDp, onResized = shell::setSidebarWidth) { w ->
                                WorkspaceSidebar(state, expanded.toSet(), currentPageId, section, actions, Modifier.width(w.dp))
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { DesktopNavHost(nav) }
                }
                MonoSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }

        when (val s = sheet) {
            is ShellSheet.New -> NewSheet(
                onDismiss = { sheet = null },
                onNewPage = { sheet = null; newPage(s.parentId) },
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
            ShellSheet.Palette -> CommandPalette(commands) { sheet = null }
            null -> Unit
        }
    }
}

/**
 * Screen transitions: a shared-axis move with depth. Forward pushes the old
 * screen back (scale + blur) while the new one slides in; back reverses it.
 */
@Composable
private fun DesktopNavHost(nav: NavController) {
    val reduce = LocalReduceMotion.current
    AnimatedContent(
        targetState = nav.current,
        contentKey = { it.id },
        transitionSpec = {
            if (reduce) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                val pop = nav.lastWasPop
                val dir = if (pop) -1 else 1
                (
                    fadeIn(tween(220, delayMillis = 70)) +
                        scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = if (pop) 1.04f else 0.965f) +
                        slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { dir * it / 14 }
                    ) togetherWith (
                    fadeOut(tween(140)) + scaleOut(tween(260, easing = FastOutSlowInEasing), targetScale = if (pop) 0.965f else 1.03f)
                    )
            }
        },
        modifier = Modifier.fillMaxSize().background(MonoColors.Background),
        label = "nav",
    ) { entry ->
        val blur by transition.animateDp(
            transitionSpec = { tween(if (reduce) 0 else 220) },
            label = "navBlur",
        ) { s -> if (s == EnterExitState.Visible) 0.dp else 8.dp }
        Box(Modifier.fillMaxSize().then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)) {
            CompositionLocalProvider(LocalBackStackEntry provides entry) {
                when (entry.route) {
                    HomeRoute -> HomeScreen()
                    is PageRoute -> PageScreen()
                    is DatabaseRoute -> DatabaseScreen()
                    SearchRoute -> SearchScreen()
                    TrashRoute -> TrashScreen()
                    SettingsRoute -> SettingsScreen()
                }
            }
        }
    }
}

/** Sidebar with a drag handle on its edge to resize it (220–420dp). */
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
                .width(7.dp)
                .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
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
