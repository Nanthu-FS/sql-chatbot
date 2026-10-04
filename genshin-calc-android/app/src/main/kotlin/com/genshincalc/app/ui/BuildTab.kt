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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genshincalc.app.CalcViewModel
import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.calc.MemberResult
import com.genshincalc.core.calc.TeamResult
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactBuild
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.ManualStats
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.StatMode
import com.genshincalc.core.model.TalentType
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.text.Format

/** Level milestones offered in the level pickers: (level, ascended). */
internal fun levelOptions(maxLevel: Int): List<Pair<Int, Boolean>> {
    val caps = listOf(20, 40, 50, 60, 70, 80, 90).filter { it <= maxLevel }
    val out = mutableListOf(1 to false)
    for (cap in caps) {
        out += cap to false
        if (cap < maxLevel) out += cap to true
    }
    if (maxLevel > 90) {
        out += 95 to true
        out += 100 to true
    }
    return out.distinct()
}

internal fun levelLabel(level: Int, ascended: Boolean, maxLevel: Int): String {
    if (level > 90) return "Lv $level"
    val asc = BaseStats.ascensionFor(level, ascended)
    val cap = listOf(20, 40, 50, 60, 70, 80, 90).getOrElse(asc) { maxLevel }.coerceAtMost(maxLevel)
    return "Lv $level/$cap"
}

@Composable
fun BuildTab(data: GameDataSet, team: Team, result: TeamResult?, index: Int, vm: CalcViewModel, nav: Nav) {
    val build = team.members.getOrNull(index) ?: return
    val c = data.character(build.characterId)
    val member = result?.member(index)?.takeIf { it.character.id == build.characterId }
    fun update(transform: (MemberBuild) -> MemberBuild) = vm.updateMember(index, transform)

    LazyColumn(
        Modifier.fillMaxSize().testTag("build_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(
                title = c.name,
                subtitle = "${c.element.display} · ${c.weapon.display} · ${c.rarity}★" +
                    if (c.id in GameEffects.modelledCharacters) " · passives modelled" else "",
                action = {
                    TextButton(onClick = { nav.push(Route.PickCharacter(index)) }) { Text("Change") }
                    if (team.members.size > 1) TextButton(onClick = { vm.removeMember(index) }) { Text("Remove") }
                },
            ) {
                Dropdown(
                    label = "Level",
                    options = levelOptions(BaseStats.MAX_CHARACTER_LEVEL),
                    selected = build.level to build.ascended,
                    optionLabel = { (l, a) -> levelLabel(l, a, 100) },
                    onSelect = { (l, a) -> update { it.copy(level = l, ascended = a) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(4.dp))
                Text("Constellation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (cons in 0..6) {
                        FilterChip(
                            selected = build.constellation == cons,
                            onClick = { update { it.copy(constellation = cons) } },
                            label = { Text("C$cons") },
                            modifier = Modifier.testTag("cons_$cons"),
                        )
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (type in listOf(TalentType.NORMAL, TalentType.SKILL, TalentType.BURST)) {
                        val base = build.talents.of(type)
                        val effective = member?.talentLevels?.get(type) ?: base
                        Stepper(
                            label = type.short,
                            value = base,
                            range = 1..10,
                            onChange = { v -> update { it.copy(talents = it.talents.with(type, v)) } },
                            valueText = if (effective != base) "$base→$effective" else "$base",
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = "Stats source") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = build.statMode == StatMode.BUILD,
                        onClick = { update { it.copy(statMode = StatMode.BUILD) } },
                        label = { Text("Weapon & artifacts") },
                        modifier = Modifier.testTag("mode_build"),
                    )
                    FilterChip(
                        selected = build.statMode == StatMode.MANUAL,
                        onClick = {
                            update { b ->
                                // Pre-fill manual stats from the computed character screen.
                                val sheet = member?.screenStats
                                val prefill = if (sheet != null && b.manualStats == ManualStats()) ManualStats(
                                    baseHp = sheet[Stat.BASE_HP], baseAtk = sheet[Stat.BASE_ATK], baseDef = sheet[Stat.BASE_DEF],
                                    hp = sheet.hp, atk = sheet.atk, def = sheet.def, em = sheet.em,
                                    critRate = sheet.critRate, critDmg = sheet.critDmg, er = sheet.er,
                                    healingBonus = sheet[Stat.HEALING_BONUS],
                                    dmgBonus = Element.entries.associateWith { sheet.dmgBonus(it) }.filterValues { it != 0.0 },
                                ) else b.manualStats
                                b.copy(statMode = StatMode.MANUAL, manualStats = prefill)
                            }
                        },
                        label = { Text("Character screen") },
                        modifier = Modifier.testTag("mode_manual"),
                    )
                }
                Text(
                    if (build.statMode == StatMode.BUILD) "Stats are computed from level, weapon and artifacts."
                    else "Type the numbers from the in-game attribute screen. Weapon/artifact bonuses that the screen already shows are not added again.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The weapon matters in both modes: its passive effects still apply to manual stats.
        item { WeaponCard(data, build, index, nav, ::update) }
        if (build.statMode == StatMode.BUILD) {
            item { ArtifactCard(data, build, index, vm, nav, ::update) }
            if (member != null) item { ScreenStatsCard(member) }
        } else {
            item { SetOnlyCard(data, build, index, vm, nav, ::update) }
            item { ManualStatsCard(c.element, build.manualStats) { ms -> update { it.copy(manualStats = ms) } } }
        }
    }
}

@Composable
private fun WeaponCard(data: GameDataSet, build: MemberBuild, index: Int, nav: Nav, update: ((MemberBuild) -> MemberBuild) -> Unit) {
    val wb = build.weapon
    val w = wb?.let { data.weaponOrNull(it.weaponId) }
    SectionCard(
        title = w?.name ?: "No weapon",
        subtitle = w?.let {
            "${it.rarity}★ ${it.type.display}" + (it.substat?.let { s -> " · ${s.display}" } ?: "") +
                if (it.id in GameEffects.modelledWeapons) " · passive modelled" else ""
        },
        action = { TextButton(onClick = { nav.push(Route.PickWeapon(index)) }, modifier = Modifier.testTag("change_weapon")) { Text("Change") } },
    ) {
        if (w != null && wb != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dropdown(
                    label = "Level",
                    options = levelOptions(w.maxLevel),
                    selected = wb.level to wb.ascended,
                    optionLabel = { (l, a) -> levelLabel(l, a, w.maxLevel) },
                    onSelect = { (l, a) -> update { it.copy(weapon = wb.copy(level = l, ascended = a)) } },
                    modifier = Modifier.weight(1f),
                )
                val stats = BaseStats.weapon(data, w, wb)
                Column(Modifier.weight(1f)) {
                    Text("Base ATK ${Format.int(stats.atk)}", style = MaterialTheme.typography.bodySmall)
                    stats.substat?.let { Text("${it.display} ${Format.stat(it, stats.substatValue)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 1..5) {
                    FilterChip(selected = wb.refinement == r, onClick = { update { it.copy(weapon = wb.copy(refinement = r)) } }, label = { Text("R$r") })
                }
            }
            w.passiveText(wb.refinement)?.let {
                Text("${w.effectName ?: "Passive"}: $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun SetSelector(label: String, setId: String?, data: GameDataSet, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val set = data.artifactSetOrNull(setId)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(set?.name ?: "None", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SetPickers(data: GameDataSet, build: MemberBuild, index: Int, nav: Nav, update: ((MemberBuild) -> MemberBuild) -> Unit) {
    val a = build.artifacts
    val fourPiece = a.set4 != null || (a.set2a == null && a.set2b == null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = fourPiece, onClick = { if (!fourPiece) update { it.copy(artifacts = a.copy(set4 = a.set2a, set2a = null, set2b = null)) } },
            label = { Text("4-piece") })
        FilterChip(selected = !fourPiece, onClick = { if (fourPiece) update { it.copy(artifacts = a.copy(set4 = null, set2a = a.set4, set2b = null)) } },
            label = { Text("2 + 2") })
    }
    Spacer(Modifier.padding(4.dp))
    if (fourPiece) {
        SetSelector("4-piece set", a.set4, data, { nav.push(Route.PickSet(index, SetSlot.FOUR)) }, Modifier.testTag("pick_set4"))
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetSelector("2-piece", a.set2a, data, { nav.push(Route.PickSet(index, SetSlot.TWO_A)) }, Modifier.weight(1f))
            SetSelector("2-piece", a.set2b, data, { nav.push(Route.PickSet(index, SetSlot.TWO_B)) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArtifactModeChips(build: MemberBuild, index: Int, vm: CalcViewModel) {
    val pieces = build.artifacts.mode == ArtifactMode.PIECES
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !pieces, onClick = { vm.setArtifactMode(index, ArtifactMode.SUMMARY) },
            label = { Text("Quick totals") }, modifier = Modifier.testTag("art_quick"))
        FilterChip(selected = pieces, onClick = { vm.setArtifactMode(index, ArtifactMode.PIECES) },
            label = { Text("Individual pieces") }, modifier = Modifier.testTag("art_pieces"))
    }
    Spacer(Modifier.padding(4.dp))
}

@Composable
private fun SetOnlyCard(data: GameDataSet, build: MemberBuild, index: Int, vm: CalcViewModel, nav: Nav, update: ((MemberBuild) -> MemberBuild) -> Unit) {
    SectionCard(title = "Artifact sets", subtitle = "Set effects that the character screen doesn't show still apply") {
        ArtifactModeChips(build, index, vm)
        if (build.artifacts.mode == ArtifactMode.PIECES) {
            val bonuses = build.artifacts.pieceCounts().filterValues { it >= 2 }
            Text(
                "From equipped pieces: " + if (bonuses.isEmpty()) "no set bonus"
                else bonuses.entries.joinToString(" + ") { (id, n) -> "${data.artifactSetOrNull(id)?.name ?: id} (${if (n >= 4) 4 else 2})" },
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            SetPickers(data, build, index, nav, update)
        }
    }
}

@Composable
private fun ArtifactCard(data: GameDataSet, build: MemberBuild, index: Int, vm: CalcViewModel, nav: Nav, update: ((MemberBuild) -> MemberBuild) -> Unit) {
    if (build.artifacts.mode == ArtifactMode.PIECES) {
        SectionCard(title = "Artifacts", subtitle = "Tap a slot to choose, edit or scan an artifact") {
            ArtifactModeChips(build, index, vm)
            PiecesSection(data, build, index, vm, nav)
        }
        return
    }
    val a = build.artifacts
    fun set(transform: (ArtifactBuild) -> ArtifactBuild) = update { it.copy(artifacts = transform(it.artifacts)) }
    SectionCard(title = "Artifacts", subtitle = "5★ +20 main stats; enter substat totals of all five pieces") {
        ArtifactModeChips(build, index, vm)
        SetPickers(data, build, index, nav, update)
        Spacer(Modifier.padding(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown("Sands", Stat.sandsMain, a.sands, { it.short }, { s -> set { it.copy(sands = s) } }, Modifier.weight(1f))
            Dropdown("Goblet", Stat.gobletMain, a.goblet, { it.short }, { s -> set { it.copy(goblet = s) } }, Modifier.weight(1f))
            Dropdown("Circlet", Stat.circletMain, a.circlet, { it.short }, { s -> set { it.copy(circlet = s) } }, Modifier.weight(1f))
        }
        Spacer(Modifier.padding(4.dp))
        Text("Substats (total)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Stat.substats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { stat ->
                    NumberField(
                        label = stat.display,
                        value = a.substats[stat] ?: 0.0,
                        onValue = { v -> set { it.copy(substats = it.substats + (stat to v)) } },
                        percent = stat.percent,
                        modifier = Modifier.weight(1f),
                        tag = "sub_${stat.name}",
                    )
                }
            }
        }
        val avgRolls = Stat.substats.filter { it != Stat.HP && it != Stat.DEF }.sumOf { s ->
            val roll = BaseStats.maxSubstatRoll(data, s) * 0.85
            if (roll > 0) (a.substats[s] ?: 0.0) / roll else 0.0
        }
        Text("≈ ${Format.trim(avgRolls)} average rolls in useful substats",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScreenStatsCard(m: MemberResult) {
    val s = m.screenStats
    SectionCard(title = "Character screen", subtitle = "What the in-game attribute page should show") {
        StatLine("Max HP", Format.int(s.hp), sub = "${Format.int(s[Stat.BASE_HP])} base")
        StatLine("ATK", Format.int(s.atk), sub = "${Format.int(s[Stat.BASE_ATK])} base")
        StatLine("DEF", Format.int(s.def), sub = "${Format.int(s[Stat.BASE_DEF])} base")
        StatLine("Elemental Mastery", Format.int(s.em))
        StatLine("CRIT Rate", Format.pct(s.critRate))
        StatLine("CRIT DMG", Format.pct(s.critDmg))
        StatLine("Energy Recharge", Format.pct(s.er))
        if (s[Stat.HEALING_BONUS] != 0.0) StatLine("Healing Bonus", Format.pct(s[Stat.HEALING_BONUS]))
        Element.entries.forEach { e ->
            val v = s.dmgBonus(e)
            if (v != 0.0) StatLine("${e.display} DMG Bonus", Format.pct(v), color = e.color)
        }
    }
}

@Composable
private fun ManualStatsCard(element: Element, ms: ManualStats, onChange: (ManualStats) -> Unit) {
    SectionCard(title = "Character screen stats", subtitle = "Copy the values from the in-game attribute page") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Base HP", ms.baseHp, { onChange(ms.copy(baseHp = it)) }, Modifier.weight(1f), tag = "m_basehp")
            NumberField("Max HP", ms.hp, { onChange(ms.copy(hp = it)) }, Modifier.weight(1f), tag = "m_hp")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Base ATK", ms.baseAtk, { onChange(ms.copy(baseAtk = it)) }, Modifier.weight(1f), tag = "m_baseatk")
            NumberField("ATK", ms.atk, { onChange(ms.copy(atk = it)) }, Modifier.weight(1f), tag = "m_atk")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Base DEF", ms.baseDef, { onChange(ms.copy(baseDef = it)) }, Modifier.weight(1f))
            NumberField("DEF", ms.def, { onChange(ms.copy(def = it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Elemental Mastery", ms.em, { onChange(ms.copy(em = it)) }, Modifier.weight(1f))
            NumberField("Energy Recharge", ms.er, { onChange(ms.copy(er = it)) }, Modifier.weight(1f), percent = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("CRIT Rate", ms.critRate, { onChange(ms.copy(critRate = it)) }, Modifier.weight(1f), percent = true, tag = "m_cr")
            NumberField("CRIT DMG", ms.critDmg, { onChange(ms.copy(critDmg = it)) }, Modifier.weight(1f), percent = true, tag = "m_cd")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("${element.display} DMG", ms.dmgBonus[element] ?: 0.0, { onChange(ms.copy(dmgBonus = ms.dmgBonus + (element to it))) },
                Modifier.weight(1f), percent = true)
            if (element != Element.PHYSICAL) {
                NumberField("Physical DMG", ms.dmgBonus[Element.PHYSICAL] ?: 0.0,
                    { onChange(ms.copy(dmgBonus = ms.dmgBonus + (Element.PHYSICAL to it))) }, Modifier.weight(1f), percent = true)
            }
        }
        NumberField("Healing Bonus", ms.healingBonus, { onChange(ms.copy(healingBonus = it)) }, Modifier.fillMaxWidth(), percent = true)
        OutlinedButton(onClick = { onChange(ManualStats()) }, modifier = Modifier.padding(top = 6.dp)) { Text("Clear") }
        Spacer(Modifier.width(1.dp))
    }
}
