package com.genshincalc.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.core.model.Reaction

/** Screens shown on top of the two main tabs. */
sealed interface Route {
    data class CharacterDetail(val id: String) : Route
    data class WeaponDetail(val id: String) : Route
    data class SetDetail(val id: String) : Route
    data class ReactionDetail(val reaction: Reaction) : Route
    data class PickCharacter(val replaceIndex: Int?) : Route
    data class PickWeapon(val memberIndex: Int) : Route
    data class PickSet(val memberIndex: Int, val slot: SetSlot) : Route
    data object PickEnemy : Route
}

enum class SetSlot { FOUR, TWO_A, TWO_B }

class Nav(val push: (Route) -> Unit, val pop: () -> Unit)

@Composable
fun App(vm: CalcViewModel) {
    val data by vm.data.collectAsState()
    val team by vm.team.collectAsState()
    val result by vm.result.collectAsState()
    val error by vm.error.collectAsState()

    val gameData = data
    if (gameData == null) {
        Box(Modifier.fillMaxSize().testTag("loading"), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Loading game data…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    // Keeps each screen's rememberSaveable state (sub-tab, scroll, search) while another screen covers it.
    val screens = rememberSaveableStateHolder()
    val stack = remember { mutableStateListOf<Route>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val nav = remember {
        Nav(
            push = { stack.add(it) },
            pop = {
                if (stack.isNotEmpty()) {
                    screens.removeState(routeKey(stack.lastIndex, stack.last()))
                    stack.removeAt(stack.lastIndex)
                }
            },
        )
    }
    BackHandler(enabled = stack.isNotEmpty()) { nav.pop() }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } },
            title = { Text("Calculation error") },
            text = { Text(message) },
        )
    }

    val top = stack.lastOrNull()
    if (top != null) {
        screens.SaveableStateProvider(routeKey(stack.lastIndex, top)) {
            RouteScreen(top, gameData, team, vm, nav)
        }
        return
    }

    screens.SaveableStateProvider("main") { MainTabs(tab, { tab = it }, gameData, team, result, vm, nav) }
}

private fun routeKey(index: Int, route: Route) = "route$index:$route"

@Composable
private fun MainTabs(
    tab: Int,
    setTab: (Int) -> Unit,
    gameData: com.genshincalc.core.model.GameDataSet,
    team: com.genshincalc.core.model.Team,
    result: com.genshincalc.core.calc.TeamResult?,
    vm: CalcViewModel,
    nav: Nav,
) {
    val tabs = rememberSaveableStateHolder()
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { setTab(0) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Calculator") },
                    modifier = Modifier.testTag("nav_calculator"),
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { setTab(1) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Library") },
                    modifier = Modifier.testTag("nav_library"),
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            tabs.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> CalculatorScreen(gameData, team, result, vm, nav)
                    else -> LibraryScreen(gameData, nav)
                }
            }
        }
    }
}

@Composable
private fun RouteScreen(route: Route, data: com.genshincalc.core.model.GameDataSet, team: com.genshincalc.core.model.Team, vm: CalcViewModel, nav: Nav) {
    when (route) {
        is Route.CharacterDetail -> CharacterDetailScreen(data, route.id, team, vm, nav)
        is Route.WeaponDetail -> WeaponDetailScreen(data, route.id, nav)
        is Route.SetDetail -> SetDetailScreen(data, route.id, nav)
        is Route.ReactionDetail -> ReactionDetailScreen(route.reaction, nav)
        is Route.PickCharacter -> CharacterPicker(
            data = data,
            disabled = team.members.map { it.characterId }.toSet(),
            onBack = nav.pop,
            onPick = { id ->
                if (route.replaceIndex == null) vm.addMember(id) else vm.replaceCharacter(route.replaceIndex, id)
                nav.pop()
            },
        )
        is Route.PickWeapon -> {
            val member = team.members.getOrNull(route.memberIndex)
            if (member == null) {
                androidx.compose.runtime.LaunchedEffect(route) { nav.pop() }
            } else {
                WeaponPicker(data, data.character(member.characterId).weapon, onBack = nav.pop) { id ->
                    vm.setWeapon(route.memberIndex, id)
                    nav.pop()
                }
            }
        }
        is Route.PickSet -> SetPicker(data, onBack = nav.pop) { id ->
            vm.updateMember(route.memberIndex) { m ->
                val a = m.artifacts
                val updated = when (route.slot) {
                    SetSlot.FOUR -> a.copy(set4 = id, set2a = null, set2b = null)
                    SetSlot.TWO_A -> a.copy(set4 = null, set2a = id)
                    SetSlot.TWO_B -> a.copy(set4 = null, set2b = id)
                }
                m.copy(artifacts = updated, effectStates = m.effectStates.filterKeys { !it.startsWith("set.") })
            }
            nav.pop()
        }
        Route.PickEnemy -> EnemyPicker(data, onBack = nav.pop) { enemy ->
            vm.setEnemy { it.copy(enemyId = enemy.id, res = enemy.res) }
            nav.pop()
        }
    }
}
