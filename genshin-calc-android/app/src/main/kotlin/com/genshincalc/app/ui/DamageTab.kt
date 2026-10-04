package com.genshincalc.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.genshincalc.core.calc.HitResult
import com.genshincalc.core.calc.MemberResult
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.HitKind
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentType
import com.genshincalc.core.model.Team
import com.genshincalc.core.text.Format

@Composable
fun DamageTab(data: GameDataSet, team: Team, result: TeamResult?, index: Int, nav: Nav) {
    val member = result?.member(index)
    if (member == null || member.character.id != team.members.getOrNull(index)?.characterId) {
        EmptyNote("Calculating…")
        return
    }
    var reaction by remember(member.character.id) { mutableStateOf<Reaction?>(null) }
    var detail by remember { mutableStateOf<HitResult?>(null) }
    val reactions = member.hits.flatMap { h -> h.reactions.map { it.reaction } }.distinct()

    LazyColumn(
        Modifier.fillMaxSize().testTag("damage_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatsSummary(member) }
        if (reactions.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = reaction == null, onClick = { reaction = null }, label = { Text("No reaction") })
                    reactions.forEach { r ->
                        FilterChip(
                            selected = reaction == r,
                            onClick = { reaction = if (reaction == r) null else r },
                            label = { Text(r.display) },
                            modifier = Modifier.testTag("reaction_${r.name}"),
                        )
                    }
                }
            }
        }
        for (type in listOf(TalentType.NORMAL, TalentType.SKILL, TalentType.BURST)) {
            val hits = member.hits.filter { it.talent == type }
            if (hits.isEmpty()) continue
            val talent = member.character.talent(type)
            item(key = "talent_$type") {
                SectionCard(
                    title = talent?.name ?: type.display,
                    subtitle = "${type.display} · Lv ${member.talentLevels[type] ?: 1}",
                ) {
                    hits.forEachIndexed { i, hit ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HitRow(hit, reaction) { detail = hit }
                    }
                }
            }
        }
        if (member.transformative.isNotEmpty()) {
            item { TransformativeCard(member) }
        }
        item { BuffSummary(member, result, team, data) }
    }

    detail?.let { hit -> HitDetailDialog(hit, member, reaction) { detail = null } }
}

@Composable
private fun StatsSummary(m: MemberResult) {
    val c = m.character
    val s = m.finalStats
    SectionCard(
        title = c.name,
        subtitle = "Lv ${m.build.level} · C${m.build.constellation} · NA ${m.talentLevels[TalentType.NORMAL]} / E ${m.talentLevels[TalentType.SKILL]} / Q ${m.talentLevels[TalentType.BURST]}",
        action = { ElementTag(c.element) },
    ) {
        val element = c.element
        val rows = listOf(
            "Max HP" to Format.int(s.hp),
            "ATK" to Format.int(s.atk),
            "DEF" to Format.int(s.def),
            "Elemental Mastery" to Format.int(s.em),
            "CRIT Rate" to Format.pct(s.critRate),
            "CRIT DMG" to Format.pct(s.critDmg),
            "Energy Recharge" to Format.pct(s.er),
            "${element.display} DMG Bonus" to Format.pct(s.dmgBonus(element) + s[Stat.ALL_DMG]),
        )
        rows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (label, value) -> StatLine(label, value, Modifier.weight(1f)) }
            }
        }
        m.infusion?.let {
            Spacer(Modifier.padding(2.dp))
            Text("Normal Attacks infused: ${it.display}", style = MaterialTheme.typography.bodySmall, color = it.color)
        }
        Text(
            "Final stats with all active buffs. Damage below is vs. the enemy set in the Enemy tab.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun HitRow(hit: HitResult, reaction: Reaction?, onClick: () -> Unit) {
    val variant = reaction?.let { r -> hit.reactions.firstOrNull { it.reaction == r } }
    val numbers = variant?.numbers ?: hit.numbers
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("hit_${hit.hit.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hit.kind == HitKind.DMG) ElementDot(hit.element) else Spacer(Modifier.width(10.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.hit.name, style = MaterialTheme.typography.bodyMedium)
            Text(hit.scaling, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (variant != null) Pill(variant.reaction.display)
            if (hit.special != null) Pill(hit.special!!.display)
        }
        Column(horizontalAlignment = Alignment.End) {
            when (hit.kind) {
                HitKind.DMG -> {
                    Text(Format.damage(numbers.average), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (variant != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text("Crit ${Format.damage(numbers.crit)}", style = MaterialTheme.typography.bodySmall)
                    Text("Non-crit ${Format.damage(numbers.nonCrit)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HitKind.HEAL -> {
                    Text(Format.int(numbers.nonCrit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = Element.DENDRO.color)
                    Text("Healing", style = MaterialTheme.typography.bodySmall)
                }
                HitKind.SHIELD -> {
                    Text(Format.int(numbers.nonCrit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = Element.GEO.color)
                    Text("Shield HP", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TransformativeCard(m: MemberResult) {
    SectionCard(
        title = "Reactions triggered by ${m.character.name.substringBefore(" (")}",
        subtitle = "Transformative and Lunar reactions use level ${m.build.level} and ${Format.int(m.finalStats.em)} EM",
    ) {
        m.transformative.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                t.element?.let { ElementDot(it) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.reaction.display + (if (t.reaction == Reaction.SWIRL) " (${t.element?.display})" else ""), style = MaterialTheme.typography.bodyMedium)
                    t.note?.takeIf { t.reaction != Reaction.SWIRL }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.damage(if (t.canCrit) t.numbers.average else t.numbers.nonCrit), fontWeight = FontWeight.Bold)
                    if (t.canCrit) Text("Crit ${Format.damage(t.numbers.crit)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BuffSummary(m: MemberResult, result: TeamResult, team: Team, data: GameDataSet) {
    SectionCard(title = "Active buffs", subtitle = "Toggle them in the Buffs tab") {
        val received = m.receivedEffects.filter { it.effect.source != com.genshincalc.core.calc.EffectSource.CUSTOM }
        if (received.isEmpty()) EmptyNote("No buffs active.")
        received.forEach { e ->
            val owner = team.members.getOrNull(e.ownerIndex)?.let { data.character(it.characterId).name.substringBefore(" (") } ?: ""
            Text("• ${e.effect.name}" + if (e.ownerIndex != m.index && e.effect.source !in PARTY_SOURCES) "  ($owner)" else "",
                style = MaterialTheme.typography.bodySmall)
        }
        val shred = result.resShred.filterValues { it != 0.0 }
        if (shred.isNotEmpty() || result.defReduction > 0) {
            Spacer(Modifier.padding(4.dp))
            Text("Enemy debuffs", style = MaterialTheme.typography.labelLarge)
            shred.forEach { (e, v) ->
                Text("• ${e.display} RES −${Format.pct(v)} → ${Format.pct(result.enemyRes.getValue(e) - v)}", style = MaterialTheme.typography.bodySmall)
            }
            if (result.defReduction > 0) Text("• DEF −${Format.pct(result.defReduction)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HitDetailDialog(hit: HitResult, m: MemberResult, reaction: Reaction?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(hit.hit.name) },
        text = {
            Column {
                StatLine("Talent", "${hit.talent.display} Lv ${hit.talentLevel}")
                StatLine("Counts as", hit.category.display)
                StatLine("Element", hit.element.display, color = hit.element.color)
                StatLine("Scaling", hit.scaling)
                val b = hit.breakdown
                if (hit.kind == HitKind.DMG && b != null) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    StatLine("Base DMG", Format.int(b.baseDamage))
                    if (b.flatDamage != 0.0) StatLine("Flat DMG bonus", "+" + Format.int(b.flatDamage))
                    StatLine("DMG Bonus", Format.pct(b.dmgBonus))
                    StatLine("CRIT Rate / DMG", "${Format.pct(b.critRate)} / ${Format.pct(b.critDmg)}")
                    StatLine("DEF multiplier", Format.pct(b.defMultiplier))
                    StatLine("Enemy RES", "${Format.pct(b.enemyRes)} → ×${Format.trim(b.resMultiplier)}")
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    StatLine("Non-crit", Format.int(hit.numbers.nonCrit))
                    StatLine("Crit", Format.int(hit.numbers.crit))
                    StatLine("Average", Format.int(hit.numbers.average))
                    hit.reactions.forEach { r ->
                        StatLine(r.reaction.display + " (avg)", Format.int(r.numbers.average),
                            color = if (r.reaction == reaction) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified)
                    }
                    Text(
                        "DMG = (Base + Flat) × (1 + DMG Bonus) × CRIT × DEF × RES × Reaction",
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (hit.special != null) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("${hit.special!!.display} DMG ignores DEF and DMG Bonus and uses the Lunar/Stellar formula.",
                        style = MaterialTheme.typography.bodySmall)
                    StatLine("Average", Format.int(hit.numbers.average))
                }
            }
        },
    )
}

/** Effects that belong to the whole party rather than to one member. */
private val PARTY_SOURCES = setOf(com.genshincalc.core.calc.EffectSource.RESONANCE, com.genshincalc.core.calc.EffectSource.PARTY)
