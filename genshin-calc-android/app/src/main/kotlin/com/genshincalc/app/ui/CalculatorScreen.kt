package com.genshincalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Team

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(data: GameDataSet, team: Team, result: TeamResult?, vm: CalcViewModel, nav: Nav) {
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Damage Calculator") },
            // The surrounding Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
            actions = {
                IconButton(onClick = { confirmReset = true }, modifier = Modifier.testTag("reset_team")) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset to sample team")
                }
            },
        )
        TeamBar(data, team, vm, nav)
        val tabs = listOf("Damage", "Build", "Buffs", "Enemy")
        TabRow(selectedTabIndex = subTab) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = subTab == i,
                    onClick = { subTab = i },
                    text = { Text(title) },
                    modifier = Modifier.testTag("tab_${title.lowercase()}"),
                )
            }
        }
        val active = team.activeIndex
        if (team.members.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add a character to start", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { nav.push(Route.PickCharacter(null)) }) { Text("Add character") }
            }
        } else {
            when (subTab) {
                0 -> DamageTab(data, team, result, active, nav)
                1 -> BuildTab(data, team, result, active, vm, nav)
                2 -> BuffsTab(data, team, result, active, vm)
                else -> EnemyTab(data, team, result, vm, nav)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = {
                TextButton(onClick = { vm.resetToSample(); confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            title = { Text("Reset team?") },
            text = { Text("Replace the current party with the sample team (Hu Tao, Xingqiu, Yelan, Zhongli).") },
        )
    }
}

@Composable
private fun TeamBar(data: GameDataSet, team: Team, vm: CalcViewModel, nav: Nav) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        team.members.forEachIndexed { i, member ->
            val c = data.character(member.characterId)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { vm.setActive(i) }
                    .testTag("member_$i"),
            ) {
                CharacterAvatar(c, 46.dp, selected = i == team.activeIndex)
                Spacer(Modifier.height(4.dp))
                Text(
                    c.name.substringBefore(" (").split(" ").last(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (i == team.activeIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        if (team.members.size < Team.MAX_SIZE) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                OutlinedIconButton(
                    onClick = { nav.push(Route.PickCharacter(null)) },
                    modifier = Modifier.size(46.dp).testTag("add_member"),
                    shape = CircleShape,
                ) { Icon(Icons.Filled.Add, contentDescription = "Add party member") }
                Spacer(Modifier.height(4.dp))
                Text("Add", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
