package com.spendlens.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import com.spendlens.app.ui.components.FloatingNavBar
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
    NavItem(Routes.HOME, "Home", Icons.Rounded.Home),
    NavItem(Routes.ACTIVITY, "Activity", Icons.AutoMirrored.Rounded.ReceiptLong),
    NavItem(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

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
        container.importManager.startAutoFind()
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
                enterTransition = { fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(260)) },
                popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 4 } },
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
                    enterTransition = { slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(320)) },
                    popExitTransition = { slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(260)) },
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
                    enterTransition = { slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300)) },
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
                    enterTransition = { slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(320)) },
                    popExitTransition = { slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(260)) },
                ) { entry ->
                    EditScreen(id = entry.arguments?.getLong("id") ?: -1L, onDone = { nav.popBackStack() })
                }
            }

            AnimatedVisibility(
                visible = topLevel.any { it.route == route },
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FloatingNavBar(
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
                    .padding(bottom = if (topLevel.any { it.route == route }) 90.dp else 12.dp),
            )
        }

        if (showScanSheet) {
            ScanSheet(
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
