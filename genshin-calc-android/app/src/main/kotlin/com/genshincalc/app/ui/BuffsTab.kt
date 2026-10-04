package com.genshincalc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.core.calc.AppliedEffect
import com.genshincalc.core.calc.EffectControl
import com.genshincalc.core.calc.EffectSource
import com.genshincalc.core.calc.EffectTarget
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.model.CustomBuffs
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Team

@Composable
fun BuffsTab(data: GameDataSet, team: Team, result: TeamResult?, index: Int, vm: CalcViewModel) {
    val member = result?.member(index)?.takeIf { it.character.id == team.members.getOrNull(index)?.characterId }
    if (member == null || result == null) {
        EmptyNote("Calculating…")
        return
    }
    val name = member.character.name.substringBefore(" (")
    LazyColumn(
        Modifier.fillMaxSize().testTag("buffs_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val own = member.ownEffects.filter { it.effect.source != EffectSource.CUSTOM }
        for (source in listOf(EffectSource.CHARACTER, EffectSource.WEAPON, EffectSource.ARTIFACT)) {
            val list = own.filter { it.effect.source == source }
            if (list.isEmpty()) continue
            item(key = "own_$source") {
                SectionCard(title = "$name · ${source.display}") {
                    list.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        EffectRow(e) { v -> vm.setEffect(index, e.effect.id, v) }
                    }
                }
            }
        }
        if (own.isEmpty()) {
            item { SectionCard(title = "$name's effects") { EmptyNote("No passives, weapon or set effects are modelled for this build yet. Use Custom buffs below.") } }
        }
        // Buffs teammates give to the party.
        for (other in result.members) {
            if (other.index == index) continue
            val partyEffects = other.ownEffects.filter { it.effect.target != EffectTarget.SELF && it.effect.source != EffectSource.CUSTOM }
            if (partyEffects.isEmpty()) continue
            item(key = "mate_${other.index}") {
                SectionCard(title = "From ${other.character.name.substringBefore(" (")}", subtitle = "Party buffs and enemy debuffs") {
                    partyEffects.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        EffectRow(e) { v -> vm.setEffect(other.index, e.effect.id, v) }
                    }
                }
            }
        }
        val resonances = result.teamEffects.filter { it.unlocked }
        if (resonances.isNotEmpty()) {
            item {
                SectionCard(title = "Party effects") {
                    resonances.forEach { e -> EffectRow(e) { v -> vm.setTeamEffect(e.effect.id, v) } }
                }
            }
        }
        item {
            CustomBuffsCard(team.members[index].customBuffs) { cb -> vm.updateMember(index) { it.copy(customBuffs = cb) } }
        }
    }
}

@Composable
private fun EffectRow(applied: AppliedEffect, onValue: (Int) -> Unit) {
    val e = applied.effect
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .alpha(if (applied.unlocked) 1f else 0.45f)
            .testTag("effect_${e.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.name, style = MaterialTheme.typography.bodyMedium)
                    e.requirementLabel?.let {
                        Spacer(Modifier.width(6.dp))
                        Pill(it)
                    }
                    if (e.target != EffectTarget.SELF) {
                        Spacer(Modifier.width(6.dp))
                        Pill(e.target.display, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!applied.unlocked) {
                    Text("Not unlocked for this build", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            when (val c = e.control) {
                is EffectControl.Toggle -> Switch(checked = applied.value > 0, onCheckedChange = { onValue(if (it) 1 else 0) },
                    modifier = Modifier.testTag("toggle_${e.id}"))
                EffectControl.Always -> Pill("Always")
                else -> Unit
            }
        }
        when (val c = e.control) {
            is EffectControl.Stacks -> Stepper(c.label, applied.value, 0..c.max, onValue, Modifier.padding(top = 4.dp))
            is EffectControl.Choice -> Dropdown("Option", c.options.indices.toList(), applied.value, { c.options[it] }, onValue,
                Modifier.fillMaxWidth().padding(top = 4.dp))
            else -> Unit
        }
    }
}

@Composable
private fun CustomBuffsCard(cb: CustomBuffs, onChange: (CustomBuffs) -> Unit) {
    SectionCard(title = "Custom buffs", subtitle = "Anything else (food, buffs not modelled yet). Applied to this character.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("ATK%", cb.atkPct, { onChange(cb.copy(atkPct = it)) }, Modifier.weight(1f), percent = true)
            NumberField("Flat ATK", cb.atk, { onChange(cb.copy(atk = it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("HP%", cb.hpPct, { onChange(cb.copy(hpPct = it)) }, Modifier.weight(1f), percent = true)
            NumberField("DEF%", cb.defPct, { onChange(cb.copy(defPct = it)) }, Modifier.weight(1f), percent = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("CRIT Rate", cb.critRate, { onChange(cb.copy(critRate = it)) }, Modifier.weight(1f), percent = true)
            NumberField("CRIT DMG", cb.critDmg, { onChange(cb.copy(critDmg = it)) }, Modifier.weight(1f), percent = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("EM", cb.em, { onChange(cb.copy(em = it)) }, Modifier.weight(1f))
            NumberField("DMG Bonus", cb.dmgBonus, { onChange(cb.copy(dmgBonus = it)) }, Modifier.weight(1f), percent = true, tag = "custom_dmg")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Flat DMG", cb.flatDmg, { onChange(cb.copy(flatDmg = it)) }, Modifier.weight(1f))
            NumberField("RES shred", cb.resShred, { onChange(cb.copy(resShred = it)) }, Modifier.weight(1f), percent = true)
        }
        NumberField("DEF reduction", cb.defReduction, { onChange(cb.copy(defReduction = it)) }, Modifier.fillMaxWidth(), percent = true)
    }
}
