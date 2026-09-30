package com.spendlens.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spendlens.app.data.AppSettings
import com.spendlens.app.ocr.ScreenshotFinder
import com.spendlens.app.ui.components.BottomBar
import com.spendlens.app.ui.components.Emphasized
import com.spendlens.app.ui.components.EmphasizedAccelerate
import com.spendlens.app.ui.components.EmphasizedDecelerate
import com.spendlens.app.ui.components.rememberHaptics
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.NavItem
import com.spendlens.app.ui.screens.ScanSheet
import com.spendlens.app.ui.screens.activity.ActivityScreen
import com.spendlens.app.ui.screens.detail.DetailScreen
import com.spendlens.app.ui.screens.edit.EditScreen
import com.spendlens.app.ui.screens.home.HomeScreen
import com.spendlens.app.ui.screens.review.ReviewScreen
import com.spendlens.app.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val SETTINGS = "settings"
    const val REVIEW = "review"
    const val DETAIL = "detail/{id}"
    const val EDIT = "edit/{id}"
    fun detail(id: Long) = "detail/$id"
    fun edit(id: Long) = "edit/$id"
}

private val topLevel = listOf(
    NavItem(Routes.HOME, "Home"),
    NavItem(Routes.ACTIVITY, "Activity"),
    NavItem(Routes.SETTINGS, "Settings"),
)

private fun tabIndex(route: String?) = topLevel.indexOfFirst { it.route == route }

// ---- Transitions -----------------------------------------------------------------
// Tabs: shared X axis in the direction of travel. Pushed screens: slide over from the
// right (modal ones from below) while the page underneath recedes and dims.

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun Scope.tabDirection(): Int {
    val from = tabIndex(initialState.destination.route)
    val to = tabIndex(targetState.destination.route)
    return if (from >= 0 && to >= 0 && to < from) -1 else 1
}

private fun Scope.enter(): EnterTransition {
    val tabs = tabIndex(initialState.destination.route) >= 0 && tabIndex(targetState.destination.route) >= 0
    return if (tabs) {
        val d = tabDirection()
        slideInHorizontally(tween(420, easing = EmphasizedDecelerate)) { d * it / 6 } + fadeIn(tween(260, 80))
    } else {
        slideInHorizontally(tween(460, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(200))
    }
}

private fun Scope.exit(): ExitTransition {
    val tabs = tabIndex(initialState.destination.route) >= 0 && tabIndex(targetState.destination.route) >= 0
    return if (tabs) {
        val d = tabDirection()
        slideOutHorizontally(tween(300, easing = EmphasizedAccelerate)) { -d * it / 6 } + fadeOut(tween(160))
    } else {
        slideOutHorizontally(tween(460, easing = Emphasized)) { -it / 5 } + fadeOut(tween(300, 100))
    }
}

private fun Scope.popEnter(): EnterTransition =
    slideInHorizontally(tween(420, easing = EmphasizedDecelerate)) { -it / 5 } + fadeIn(tween(300))

private fun Scope.popExit(): ExitTransition =
    slideOutHorizontally(tween(320, easing = EmphasizedAccelerate)) { it } + fadeOut(tween(260, 60))

private fun Scope.modalEnter(): EnterTransition =
    slideInVertically(tween(480, easing = EmphasizedDecelerate)) { it / 3 } + fadeIn(tween(260))

private fun Scope.modalExit(): ExitTransition =
    slideOutVertically(tween(300, easing = EmphasizedAccelerate)) { it / 3 } + fadeOut(tween(220))

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun SpendLensRoot(settings: AppSettings) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showScanSheet by rememberSaveable { mutableStateOf(false) }
    val shared by container.sharedImages.collectAsStateWithLifecycle()

    val haptics = rememberHaptics()

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun deleteWithUndo(id: Long) {
        scope.launch {
            val entity = container.repository.get(id) ?: return@launch
            container.repository.delete(entity)
            val result = snackbar.showSnackbar(
                message = "Deleted ${settings.currency.format(entity.amountMinor)} · ${entity.merchant}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                haptics.confirm()
                container.repository.save(entity)
            } else {
                container.repository.deleteImage(entity.imagePath)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)) { uris ->
        if (uris.isNotEmpty()) {
            container.importManager.startPicked(uris)
            nav.navigate(Routes.REVIEW) { launchSingleTop = true }
        }
    }

    fun startAutoFind() {
        container.importManager.startAutoFind(settings.autoFindDays)
        nav.navigate(Routes.REVIEW) { launchSingleTop = true }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) startAutoFind() else message("Allow photo access to auto-find payment screenshots")
    }

    val pickScreenshots = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val autoFind = {
        if (ScreenshotFinder.hasAccess(context)) startAutoFind() else permissions.launch(ScreenshotFinder.permissions())
    }
    val addManually = { nav.navigate(Routes.edit(-1)) }

    // Screenshots shared into the app from other apps.
    LaunchedEffect(shared) {
        if (shared.isNotEmpty()) {
            container.importManager.startPicked(shared)
            container.sharedImages.value = emptyList()
            nav.navigate(Routes.REVIEW) { launchSingleTop = true }
        }
    }

    CompositionLocalProvider(LocalCurrency provides settings.currency) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                enterTransition = { enter() },
                exitTransition = { exit() },
                popEnterTransition = { if (tabIndex(targetState.destination.route) >= 0 && tabIndex(initialState.destination.route) >= 0) enter() else popEnter() },
                popExitTransition = { if (tabIndex(targetState.destination.route) >= 0 && tabIndex(initialState.destination.route) >= 0) exit() else popExit() },
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenTransaction = { nav.navigate(Routes.detail(it)) },
                        onScan = { showScanSheet = true },
                        onAutoFind = autoFind,
                        onAddManually = addManually,
                        onSeeAll = { nav.navigateTop(Routes.ACTIVITY) },
                        onSetBudget = { nav.navigateTop(Routes.SETTINGS) },
                    )
                }
                composable(Routes.ACTIVITY) {
                    ActivityScreen(
                        onOpenTransaction = { nav.navigate(Routes.detail(it)) },
                        onDelete = ::deleteWithUndo,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onMessage = ::message)
                }
                composable(
                    Routes.REVIEW,
                    enterTransition = { modalEnter() },
                    popExitTransition = { modalExit() },
                ) {
                    ReviewScreen(
                        onClose = { nav.popBackStack() },
                        onSaved = { count ->
                            nav.popBackStack()
                            if (count > 0) message(if (count == 1) "Saved 1 payment" else "Saved $count payments")
                        },
                    )
                }
                composable(
                    Routes.DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    DetailScreen(
                        id = entry.arguments?.getLong("id") ?: -1L,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(Routes.edit(it)) },
                        onDelete = { id ->
                            nav.popBackStack()
                            deleteWithUndo(id)
                        },
                    )
                }
                composable(
                    Routes.EDIT,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    enterTransition = { modalEnter() },
                    popExitTransition = { modalExit() },
                ) { entry ->
                    EditScreen(id = entry.arguments?.getLong("id") ?: -1L, onDone = { nav.popBackStack() })
                }
            }

            AnimatedVisibility(
                visible = topLevel.any { it.route == route },
                enter = slideInVertically(tween(420, easing = EmphasizedDecelerate)) { it } + fadeIn(),
                exit = slideOutVertically(tween(240, easing = EmphasizedAccelerate)) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBar(
                    items = topLevel,
                    currentRoute = route,
                    onNavigate = { nav.navigateTop(it) },
                    onScan = { showScanSheet = true },
                )
            }

            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (topLevel.any { it.route == route }) 76.dp else 12.dp),
            ) { data ->
                val ink = com.spendlens.app.ui.theme.Spend.ink
                Snackbar(
                    data,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                    containerColor = ink.text,
                    contentColor = ink.inverse,
                    actionColor = ink.inverse,
                )
            }
        }

        if (showScanSheet) {
            ScanSheet(
                autoFindDays = settings.autoFindDays,
                onDismiss = { showScanSheet = false },
                onPick = {
                    showScanSheet = false
                    pickScreenshots()
                },
                onAutoFind = {
                    showScanSheet = false
                    autoFind()
                },
                onManual = {
                    showScanSheet = false
                    addManually()
                },
            )
        }
    }
}
