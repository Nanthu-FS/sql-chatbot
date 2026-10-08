package app.monoworkspace.ui.database

import app.monoworkspace.ui.common.NativeFiles

import app.monoworkspace.ui.common.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.FilterGroup
import app.monoworkspace.model.FilterRule
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.ViewType
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.Breadcrumb
import app.monoworkspace.ui.components.DeleteButton
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.ExpandingActionButton
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoChip
import app.monoworkspace.ui.components.MonoDialog
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.MonoTopBar
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.page.GlyphHeader
import app.monoworkspace.ui.page.IconPickerSheet
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.launch

private sealed interface DbSheet {
    data object Filter : DbSheet
    data object Sort : DbSheet
    data object Group : DbSheet
    data object Properties : DbSheet
    data object AddProperty : DbSheet
    data object Icon : DbSheet
    data object ConfirmTrash : DbSheet
    data object BulkPick : DbSheet
    data class ViewSettings(val view: DatabaseView) : DbSheet
    data class EditProperty(val propertyId: String) : DbSheet
    data class EditValue(val request: ValueEditRequest) : DbSheet
    data class BulkValue(val prop: PropertyDef) : DbSheet
    data class Csv(val data: CsvImport) : DbSheet
    data class Error(val message: String) : DbSheet
}

fun ViewType.icon(): ImageVector = when (this) {
    ViewType.TABLE -> MonoIcons.Table
    ViewType.BOARD -> MonoIcons.Board
    ViewType.LIST -> MonoIcons.ListIcon
    ViewType.GALLERY -> MonoIcons.Gallery
    ViewType.CALENDAR -> MonoIcons.Calendar
}

@Composable
fun DatabaseScreen() {
    val vm = monoViewModel { c, h -> DatabaseViewModel(c, h) }
    val state by vm.state.collectAsState()
    val nav = LocalNavigator.current
    val layout = LocalWindowLayout.current
    val messenger = LocalMessenger.current
    val window = app.monoworkspace.ui.common.LocalComposeWindow.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<DbSheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    val margin = layout.margin

    LaunchedEffect(vm) { vm.messages.collect { messenger.show(it) } }
    BackHandler(enabled = state.selection.isNotEmpty()) { vm.clearSelection() }

    fun exportCsv(name: String) {
        NativeFiles.save(window, "Export view as CSV", name)?.let(vm::exportCsv)
    }
    fun exportZip(name: String) {
        NativeFiles.save(window, "Export database", name)?.let(vm::exportZip)
    }
    fun importCsv() {
        val file = NativeFiles.open(window, "Import CSV", listOf("csv", "txt")) ?: return
        scope.launch {
            val data = vm.readCsv(file)
            if (data == null) messenger.show("That file isn't a readable CSV") else sheet = DbSheet.Csv(data)
        }
    }

    val snapshot = state.snapshot
    val view = state.view
    val result = state.result

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = {
                if (layout.usesSidebar) MonoIconButton(MonoIcons.Back, "Back", nav::back)
                else MonoIconButton(MonoIcons.Menu, "Open navigation", nav::openDrawer)
            },
            title = { Breadcrumb(listOf(snapshot?.page?.displayTitle ?: "Database")) },
            actions = {
                val page = snapshot?.page
                if (page != null) {
                    MonoIconButton(if (page.isFavorite) MonoIcons.StarFilled else MonoIcons.Star, if (page.isFavorite) "Remove from favorites" else "Add to favorites", { vm.setFavorite(!page.isFavorite) })
                    Box {
                        MonoIconButton(MonoIcons.More, "Database options", { menu = true })
                        MonoMenu(
                            menu, { menu = false },
                            listOf(
                                MenuItem("Icon", MonoIcons.Glyph) { sheet = DbSheet.Icon },
                                MenuItem("Export view as CSV", MonoIcons.Export) { exportCsv(page.displayTitle + ".csv") },
                                MenuItem("Export database (CSV + Markdown)", MonoIcons.Export) { exportZip(page.displayTitle + ".zip") },
                                MenuItem("Import CSV", MonoIcons.Import) { importCsv() },
                                MenuItem("Move to trash", MonoIcons.Trash, destructive = true) { sheet = DbSheet.ConfirmTrash },
                            ),
                        )
                    }
                }
            },
        )

        if (state.missing) {
            EmptyState("This database was deleted or moved to the trash.", "Go home", nav::openHome, Modifier.padding(horizontal = margin), icon = MonoIcons.Trash)
            return@Column
        }
        if (snapshot == null || view == null || result == null) return@Column

        // Header: icon and title.
        Row(Modifier.fillMaxWidth().padding(horizontal = margin).padding(top = Space.l), verticalAlignment = Alignment.CenterVertically) {
            GlyphHeader(snapshot.page.icon, true, { sheet = DbSheet.Icon }, Modifier.size(56.dp))
            Spacer(Modifier.width(Space.l))
            BasicTextField(
                value = state.title,
                onValueChange = vm::setTitle,
                modifier = Modifier.weight(1f).semantics { heading() },
                textStyle = if (layout.usesSidebar) MonoType.h1 else MonoType.h2,
                cursorBrush = SolidColor(MonoColors.Ink),
                decorationBox = { inner ->
                    Box {
                        if (state.title.isEmpty()) Text("Untitled database", style = (if (layout.usesSidebar) MonoType.h1 else MonoType.h2).copy(color = MonoColors.Tertiary))
                        inner()
                    }
                },
            )
        }

        // View tabs with an underline that grows into the selected tab.
        Row(Modifier.fillMaxWidth().padding(top = Space.m).horizontalScroll(rememberScrollState()).padding(horizontal = margin - Space.s)) {
            snapshot.views.forEach { v -> ViewTab(v, v.id == view.id, { vm.selectView(v.id) }, { sheet = DbSheet.ViewSettings(v) }) }
            var addMenu by remember { mutableStateOf(false) }
            Box {
                MonoIconButton(MonoIcons.Plus, "Add view", { addMenu = true }, iconSize = 20.dp)
                MonoMenu(addMenu, { addMenu = false }, ViewType.entries.map { t -> MenuItem(t.label, t.icon()) { vm.addView(t) } })
            }
        }
        Hairline()

        // Toolbar.
        if (state.selection.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = margin - Space.s, end = margin),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ToolButton(MonoIcons.Filter, "Filter", view.config.filter.ruleCount) { sheet = DbSheet.Filter }
                    ToolButton(MonoIcons.Sort, "Sort", view.config.sorts.size) { sheet = DbSheet.Sort }
                    if (view.type != ViewType.GALLERY && view.type != ViewType.CALENDAR) {
                        ToolButton(MonoIcons.Group, if (view.type == ViewType.BOARD) "Columns" else "Group", if (view.config.groupBy != null) 1 else 0) { sheet = DbSheet.Group }
                    }
                    ToolButton(MonoIcons.Sliders, "Properties", 0) { sheet = DbSheet.Properties }
                }
                ExpandingActionButton(
                    MonoIcons.Plus, "New row",
                    { vm.addRow { nav.openPage(it.id) } },
                    size = 40.dp, expandedWidth = 124.dp,
                )
            }
        } else {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).background(MonoColors.Tint).padding(start = margin, end = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.selection.size} selected", Modifier.weight(1f), style = MonoType.label.tnum())
                MonoIconButton(MonoIcons.Check, "Select all", vm::selectAll, iconSize = 20.dp)
                MonoIconButton(MonoIcons.Sliders, "Set a property", { sheet = DbSheet.BulkPick }, iconSize = 20.dp)
                if (state.selection.size == 1) MonoIconButton(MonoIcons.Copy, "Duplicate", { vm.duplicate(state.selection.first()); vm.clearSelection() }, iconSize = 20.dp)
                MonoIconButton(MonoIcons.X, "Cancel", vm::clearSelection, iconSize = 20.dp)
                DeleteButton({
                    val ids = state.selection.toList()
                    vm.trashRows(ids) { n -> messenger.show("Moved $n row${if (n == 1) "" else "s"} to trash", "Undo") { vm.restoreRows(ids) } }
                }, size = 40.dp, expandedWidth = 120.dp)
            }
        }
        SectionRule()

        // Active filter rules as removable chips.
        val rules = flatRules(view.config.filter)
        if (rules.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = margin, vertical = Space.s),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s),
            ) {
                rules.forEach { r ->
                    val p = snapshot.database.schema.property(r.propertyId)
                    MonoChip(
                        ruleLabel(r, p), false, { sheet = DbSheet.Filter },
                        trailing = MonoIcons.X,
                        onTrailingClick = { vm.updateConfig { it.copy(filter = removeRule(it.filter, r.id)) } },
                    )
                }
            }
            Hairline()
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            ViewBody(
                snapshot, view, result, state.selection,
                ViewActions(
                    openRow = { nav.openPage(it) },
                    editCell = { row, prop ->
                        if (prop.isTitle) nav.openPage(row.id)
                        else if (prop.type == app.monoworkspace.model.PropertyType.CHECKBOX) {
                            val cur = (row.values[prop.id] as? app.monoworkspace.model.PropertyValue.Checkbox)?.value ?: false
                            vm.setValue(row.id, prop.id, app.monoworkspace.model.PropertyValue.Checkbox(!cur))
                        } else sheet = DbSheet.EditValue(ValueEditRequest(row.id, prop, row.values[prop.id], result.resolver.cell(row, prop)))
                    },
                    toggleSelect = vm::toggleSelected,
                    addRow = { extra -> vm.addRow(extra) { nav.openPage(it.id) } },
                    sortBy = vm::sortBy,
                    hideProperty = vm::hideProperty,
                    editProperty = { sheet = DbSheet.EditProperty(it) },
                    setColumnWidth = vm::setColumnWidth,
                    moveToGroup = vm::moveToGroup,
                    reschedule = vm::reschedule,
                    addProperty = { sheet = DbSheet.AddProperty },
                    showError = { sheet = DbSheet.Error(it) },
                ),
            )
        }
    }

    val schema = snapshot?.database?.schema
    when (val s = sheet) {
        DbSheet.Filter -> if (snapshot != null && view != null) FilterSheet(snapshot.database.schema, snapshot.database.id, view.config, { g -> vm.updateConfig { it.copy(filter = g) } }) { sheet = null }
        DbSheet.Sort -> if (snapshot != null && view != null) SortSheet(snapshot.database.schema, view.config, { s2 -> vm.updateConfig { it.copy(sorts = s2) } }) { sheet = null }
        DbSheet.Group -> if (snapshot != null && view != null) GroupSheet(snapshot.database.schema, view, { id -> vm.updateConfig { it.copy(groupBy = id) } }) { sheet = null }
        DbSheet.Properties -> if (snapshot != null && view != null) PropertiesSheet(
            snapshot, view, vm::setVisible, vm::moveProperty,
            onEdit = { sheet = DbSheet.EditProperty(it) },
            onAdd = { sheet = DbSheet.AddProperty },
            onDismiss = { sheet = null },
        )
        DbSheet.AddProperty -> snapshot?.let { AddPropertySheet(it.database.id, { sheet = null }) { def -> sheet = DbSheet.EditProperty(def.id) } }
        is DbSheet.EditProperty -> snapshot?.let { PropertySheet(it.database, s.propertyId) { sheet = null } }
        is DbSheet.EditValue -> snapshot?.let {
            ValueEditorHost(s.request, it.database.id, { sheet = null }) { rowId, propId, value -> vm.setValue(rowId, propId, value) }
        }
        DbSheet.BulkPick -> if (schema != null) BulkSetSheet(schema, state.selection.size, { p -> sheet = DbSheet.BulkValue(p) }) { sheet = null }
        is DbSheet.BulkValue -> snapshot?.let {
            ValueEditorHost(blankRequest(s.prop), it.database.id, { sheet = null }) { _, propId, value -> vm.bulkSet(propId, value) }
        }
        is DbSheet.ViewSettings -> if (snapshot != null) ViewSettingsSheet(
            snapshot, s.view,
            onRename = { vm.renameView(s.view, it) },
            onConfig = { t -> vm.updateConfig(t) },
            onDelete = { vm.deleteView(s.view) },
            onDismiss = { sheet = null },
        )
        is DbSheet.Csv -> if (schema != null) CsvImportSheet(schema, s.data, { mapping -> vm.importCsv(s.data, mapping) }) { sheet = null }
        DbSheet.Icon -> IconPickerSheet(snapshot?.page?.icon, { sheet = null }) { vm.setIcon(it); sheet = null }
        DbSheet.ConfirmTrash -> MonoDialog(
            onDismiss = { sheet = null },
            title = "Move database to trash?",
            body = "Its rows go with it. You can restore everything from the trash for 30 days.",
            confirmLabel = "Move to trash",
            destructive = true,
            onConfirm = {
                sheet = null
                val title = snapshot?.page?.displayTitle ?: "Database"
                vm.trash {
                    nav.back()
                    messenger.show("Moved “$title” to trash")
                }
            },
        )
        is DbSheet.Error -> MonoDialog({ sheet = null }, "Formula error", s.message, "OK", { sheet = null })
        null -> Unit
    }
}

@Composable
private fun ViewTab(view: DatabaseView, selected: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    val underline by animateFloatAsState(if (selected) 1f else 0f, monoTween(Motion.MEDIUM), label = "tab")
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .drawBehind {
                if (underline > 0f) {
                    val w = size.width * underline
                    drawRect(MonoColors.Ink, Offset((size.width - w) / 2f, size.height - 2.dp.toPx()), Size(w, 2.dp.toPx()))
                }
            }
            .inkClickable(onClick = onClick, onLongClick = onLongPress, showBar = false, role = Role.Tab)
            .semantics { this.selected = selected }
            .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(view.type.icon(), null, Modifier.size(18.dp), tint = if (selected) MonoColors.Ink else MonoColors.Secondary)
        Spacer(Modifier.width(6.dp))
        Text(
            view.name,
            style = MonoType.bodySmall.copy(color = if (selected) MonoColors.Ink else MonoColors.Secondary, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 44.dp).inkClickable(onClick = onClick, showBar = false).padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (count > 0) MonoColors.Ink else MonoColors.Secondary)
        Spacer(Modifier.width(4.dp))
        Text(label.uppercase() + if (count > 0) " $count" else "", style = MonoType.label.tnum().copy(color = if (count > 0) MonoColors.Ink else MonoColors.Secondary))
    }
}

private fun flatRules(g: FilterGroup): List<FilterRule> = g.rules + g.groups.flatMap { flatRules(it) }

private fun removeRule(g: FilterGroup, id: String): FilterGroup =
    g.copy(rules = g.rules.filter { it.id != id }, groups = g.groups.map { removeRule(it, id) }.filter { !it.isEmpty })

private fun ruleLabel(r: FilterRule, p: PropertyDef?): String {
    val name = p?.name ?: "Property"
    val v = r.value
    val value = when {
        v.optionIds.isNotEmpty() -> v.optionIds.mapNotNull { id -> p?.option(id)?.name }.joinToString(", ")
        v.text != null -> v.text
        v.number != null -> app.monoworkspace.engine.formula.Evaluator.formatNumber(v.number) + (v.number2?.let { " – " + app.monoworkspace.engine.formula.Evaluator.formatNumber(it) } ?: "")
        v.date != null -> v.date + (v.date2?.let { " – $it" } ?: "")
        else -> ""
    }
    return "$name ${r.operator.label} $value".trim()
}
