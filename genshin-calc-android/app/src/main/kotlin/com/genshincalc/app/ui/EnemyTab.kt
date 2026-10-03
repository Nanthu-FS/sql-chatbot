package com.genshincalc.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.core.calc.Formulas
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.EnemyConfig
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Team
import com.genshincalc.core.text.Format

@Composable
fun EnemyTab(data: GameDataSet, team: Team, result: TeamResult?, vm: CalcViewModel, nav: Nav) {
    val enemy = team.enemy
    LazyColumn(
        Modifier.fillMaxSize().testTag("enemy_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = "Enemy level", subtitle = "Spiral Abyss floor 12 enemies are Lv 95-103") {
                Stepper("Level", enemy.level, 1..200, { v -> vm.setEnemy { it.copy(level = v) } })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (lvl in listOf(90, 95, 100, 103, 110, 120)) {
                        FilterChip(selected = enemy.level == lvl, onClick = { vm.setEnemy { it.copy(level = lvl) } }, label = { Text("$lvl") })
                    }
                }
                result?.member(team.activeIndex)?.let { m ->
                    val def = Formulas.defMultiplier(m.build.level, enemy.level, result.defReduction)
                    Text("DEF multiplier for a Lv ${m.build.level} character: ${Format.pct(def)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            val selected = data.enemyOrNull(enemy.enemyId)
            SectionCard(
                title = selected?.name ?: "Custom enemy",
                subtitle = selected?.category ?: "Pick an enemy to load its resistances",
                action = {
                    TextButton(onClick = { nav.push(Route.PickEnemy) }, modifier = Modifier.testTag("pick_enemy")) { Text("Choose") }
                },
            ) {
                Element.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { e ->
                            NumberField(
                                label = "${e.display} RES",
                                value = enemy.baseRes(e),
                                onValue = { v -> vm.setEnemy { it.copy(res = it.res + (e to v), enemyId = null) } },
                                modifier = Modifier.weight(1f),
                                percent = true,
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { vm.setEnemy { EnemyConfig(level = it.level) } }) { Text("Reset all to 10%") }
            }
        }
        if (result != null) {
            item {
                SectionCard(title = "Effective RES", subtitle = "After shred from party buffs (VV, Zhongli, ...)") {
                    Element.entries.forEach { e ->
                        val base = result.enemyRes.getValue(e)
                        val shred = result.resShred[e] ?: 0.0
                        val final = base - shred
                        StatLine(
                            e.display, "${Format.pct(final)}  (×${Format.trim(Formulas.resMultiplier(final))})",
                            sub = if (shred != 0.0) "−${Format.pct(shred)}" else null, color = e.color,
                        )
                    }
                    if (result.defReduction > 0) StatLine("DEF reduction", Format.pct(result.defReduction))
                }
            }
        }
    }
}
