package com.pulse.tracker

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.pulse.tracker.data.Meal
import com.pulse.tracker.ui.components.PulseNavBar
import com.pulse.tracker.ui.components.Tab
import com.pulse.tracker.ui.screens.EssentialsScreen
import com.pulse.tracker.ui.screens.InsightsScreen
import com.pulse.tracker.ui.screens.LogFoodScreen
import com.pulse.tracker.ui.screens.SettingsScreen
import com.pulse.tracker.ui.screens.TodayScreen
import com.pulse.tracker.ui.screens.WaterScreen
import com.pulse.tracker.ui.theme.PulseProvider
import com.pulse.tracker.ui.theme.themeFor

/** What fills the screen: a tab, or a full-screen flow on top of the tabs. */
private sealed interface Route {
    data class Tabs(val tab: Tab) : Route
    data class Log(val meal: Meal) : Route
    data object Settings : Route
}

@Composable
fun PulseApp(vm: PulseViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val date by vm.selectedDate.collectAsStateWithLifecycle()
    val theme = themeFor(data.themeId)

    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var logMeal by rememberSaveable { mutableStateOf<Meal?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }

    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(theme.isDark) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (theme.isDark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Keep Health Connect status fresh and pull steps whenever Pulse is on screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(data.stepsFromHealthConnect) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refreshHealth()
            while (true) {
                vm.syncSteps()
                delay(120_000)
            }
        }
    }

    BackHandler(enabled = settings || logMeal != null || tab != Tab.TODAY) {
        when {
            settings -> settings = false
            logMeal != null -> logMeal = null
            else -> tab = Tab.TODAY
        }
    }

    val route: Route = when {
        settings -> Route.Settings
        logMeal != null -> Route.Log(logMeal!!)
        else -> Route.Tabs(tab)
    }

    Crossfade(targetState = theme, animationSpec = tween(450), label = "theme") { th ->
        PulseProvider(th) {
            Box(Modifier.fillMaxSize().background(th.c.bg)) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val overlay = targetState !is Route.Tabs
                        (fadeIn(tween(220)) + if (overlay) slideInVertically(tween(260)) { it / 12 } else fadeIn(tween(0)))
                            .togetherWith(fadeOut(tween(160)))
                    },
                    label = "route",
                ) { r ->
                    when (r) {
                        is Route.Tabs -> when (r.tab) {
                            Tab.TODAY -> TodayScreen(
                                data = data, date = date, onSelectDate = vm::selectDate,
                                onLog = { logMeal = it }, onSettings = { settings = true },
                                onWater = { tab = Tab.WATER }, onHabits = { tab = Tab.HABITS },
                            )
                            Tab.INSIGHTS -> InsightsScreen(vm, data)
                            Tab.WATER -> WaterScreen(vm, data, date)
                            Tab.HABITS -> EssentialsScreen(vm, data, date)
                        }
                        is Route.Log -> LogFoodScreen(vm, data, date, r.meal, onBack = { logMeal = null })
                        Route.Settings -> SettingsScreen(vm, data, onBack = { settings = false })
                    }
                }
                if (route is Route.Tabs) {
                    PulseNavBar(
                        current = tab,
                        onSelect = { tab = it },
                        onAdd = { logMeal = Meal.forNow() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
