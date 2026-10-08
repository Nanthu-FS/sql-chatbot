package app.monoworkspace.ui.database

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monoworkspace.core.Ids
import app.monoworkspace.data.repo.CsvTarget
import app.monoworkspace.data.repo.DatabaseQuery
import app.monoworkspace.data.repo.DatabaseSnapshot
import app.monoworkspace.model.Conjunction
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.FilterGroup
import app.monoworkspace.model.FilterOperator
import app.monoworkspace.model.FilterOperators
import app.monoworkspace.model.FilterRule
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SortDirection
import app.monoworkspace.model.SortRule
import app.monoworkspace.model.ViewConfig
import app.monoworkspace.model.ViewType
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.components.DatePickerSheet
import app.monoworkspace.ui.components.DeleteButton
import app.monoworkspace.ui.components.FormRow
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoChip
import app.monoworkspace.ui.components.MonoDropdown
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoSegmented
import app.monoworkspace.ui.components.MonoSwitch
import app.monoworkspace.ui.components.MonoTextField
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space

// ---------------------------------------------------------------- Filter

/** Top-level group with AND/OR, rules, and nested groups with their own AND/OR. */
@Composable
fun FilterSheet(schema: DatabaseSchema, databaseId: String, config: ViewConfig, onChange: (FilterGroup) -> Unit, onDismiss: () -> Unit) {
    var root by remember { mutableStateOf(config.filter) }
    fun update(g: FilterGroup) {
        root = g
        onChange(g)
    }
    MonoBottomSheet(onDismiss = onDismiss, title = "Filter", trailing = {
        MonoButton("Clear", { update(FilterGroup()) }, style = MonoButtonStyle.Text, height = 36.dp)
    }) {
        Column(Modifier.padding(horizontal = Space.l)) {
            GroupEditor(root, schema, databaseId, nested = false, onChange = { update(it) }, onRemove = null)
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MonoButton("Add rule", { update(root.copy(rules = root.rules + newRule(schema))) }, icon = MonoIcons.Plus)
                MonoButton("Add group", {
                    update(root.copy(groups = root.groups + FilterGroup(Ids.new(), Conjunction.OR, listOf(newRule(schema)))))
                }, icon = MonoIcons.Plus)
            }
        }
    }
}

private fun newRule(schema: DatabaseSchema): FilterRule {
    val prop = schema.properties.firstOrNull { !it.isTitle } ?: schema.title
    return FilterRule(Ids.new(), prop.id, FilterOperators.forType(prop.type).first())
}

@Composable
private fun GroupEditor(
    group: FilterGroup,
    schema: DatabaseSchema,
    databaseId: String,
    nested: Boolean,
    onChange: (FilterGroup) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Column(if (nested) Modifier.fillMaxWidth().border(1.dp, MonoColors.Hairline).padding(Space.m) else Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (nested) "Group: match" else "Match", style = MonoType.caption)
            Spacer(Modifier.width(Space.s))
            MonoSegmented(Conjunction.entries, group.conjunction, { if (it == Conjunction.AND) "ALL" else "ANY" }, { onChange(group.copy(conjunction = it)) })
            Spacer(Modifier.weight(1f))
            if (onRemove != null) MonoIconButton(MonoIcons.X, "Remove group", onRemove, iconSize = 18.dp)
        }
        if (group.rules.isEmpty() && group.groups.isEmpty()) {
            Text("No rules yet: every row is shown.", Modifier.padding(vertical = Space.m), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
        }
        group.rules.forEachIndexed { i, rule ->
            if (i > 0) LabelText(if (group.conjunction == Conjunction.AND) "and" else "or", Modifier.padding(vertical = Space.xs), color = MonoColors.Tertiary)
            RuleEditor(rule, schema, databaseId,
                onChange = { r -> onChange(group.copy(rules = group.rules.map { if (it.id == rule.id) r else it })) },
                onRemove = { onChange(group.copy(rules = group.rules.filter { it.id != rule.id })) },
            )
        }
        group.groups.forEach { g ->
            Spacer(Modifier.height(Space.m))
            GroupEditor(g, schema, databaseId, nested = true,
                onChange = { ng -> onChange(group.copy(groups = group.groups.map { if (it.id == g.id) ng else it })) },
                onRemove = { onChange(group.copy(groups = group.groups.filter { it.id != g.id })) },
            )
        }
        if (nested) {
            MonoButton("Add rule to group", { onChange(group.copy(rules = group.rules + newRule(schema))) }, style = MonoButtonStyle.Text, icon = MonoIcons.Plus, height = 36.dp)
        }
    }
}

@Composable
private fun RuleEditor(rule: FilterRule, schema: DatabaseSchema, databaseId: String, onChange: (FilterRule) -> Unit, onRemove: () -> Unit) {
    val prop = schema.property(rule.propertyId) ?: schema.title
    val ops = FilterOperators.forType(prop.type)
    var pickDate by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(verticalAlignment = Alignment.Bottom) {
            MonoDropdown(prop, schema.properties, { it.name }, { p ->
                onChange(FilterRule(rule.id, p.id, FilterOperators.forType(p.type).first()))
            }, Modifier.weight(1f).padding(end = Space.s))
            MonoDropdown(rule.operator, ops, { it.label }, { onChange(rule.copy(operator = it)) }, Modifier.weight(1f))
            MonoIconButton(MonoIcons.X, "Remove rule", onRemove, iconSize = 18.dp)
        }
        if (FilterOperators.needsValue(rule.operator)) {
            Spacer(Modifier.height(Space.xs))
            val v = rule.value
            when {
                prop.type.hasOptions -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val single = rule.operator == FilterOperator.IS || rule.operator == FilterOperator.IS_NOT
                    prop.sortedOptions.forEach { o ->
                        val on = o.id in v.optionIds
                        MonoChip((if (prop.type == PropertyType.STATUS) statusGlyph(o.group) + " " else "") + o.name, on, {
                            val ids = if (single) listOf(o.id) else if (on) v.optionIds - o.id else v.optionIds + o.id
                            onChange(rule.copy(value = v.copy(optionIds = ids)))
                        })
                    }
                }
                prop.type == PropertyType.NUMBER || prop.type == PropertyType.ROLLUP || (prop.type == PropertyType.FORMULA && rule.operator in setOf(FilterOperator.EQ, FilterOperator.LT, FilterOperator.GT)) -> Row {
                    NumberField(v.number, { onChange(rule.copy(value = v.copy(number = it))) }, Modifier.weight(1f))
                    if (rule.operator == FilterOperator.BETWEEN) {
                        Text(" and ", Modifier.align(Alignment.CenterVertically), style = MonoType.caption)
                        NumberField(v.number2, { onChange(rule.copy(value = v.copy(number2 = it))) }, Modifier.weight(1f))
                    }
                }
                prop.type == PropertyType.DATE || prop.type == PropertyType.CREATED_TIME || prop.type == PropertyType.EDITED_TIME -> Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    MonoChip(v.date ?: "Pick date", v.date != null, { pickDate = 1 }, leading = MonoIcons.Calendar)
                    if (rule.operator == FilterOperator.DATE_BETWEEN) MonoChip(v.date2 ?: "End date", v.date2 != null, { pickDate = 2 }, leading = MonoIcons.Calendar)
                }
                prop.type == PropertyType.RELATION -> RelationRowDropdown(prop, v.rowId) { onChange(rule.copy(value = v.copy(rowId = it))) }
                else -> MonoTextField(v.text.orEmpty(), { onChange(rule.copy(value = v.copy(text = it))) }, placeholder = "Value")
            }
        }
    }
    pickDate?.let { which ->
        DatePickerSheet(null, { pickDate = null }, onSave = { picked ->
            pickDate = null
            val d = picked?.start?.toString()
            onChange(rule.copy(value = if (which == 1) rule.value.copy(date = d) else rule.value.copy(date2 = d)))
        }, allowRange = false, allowTime = false, title = "Filter date")
    }
}

@Composable
private fun NumberField(value: Double?, onChange: (Double?) -> Unit, modifier: Modifier) {
    var text by remember(value) { mutableStateOf(value?.let { app.monoworkspace.engine.formula.Evaluator.formatNumber(it) }.orEmpty()) }
    MonoTextField(text, { t -> text = t; onChange(t.replace(",", "").toDoubleOrNull()) }, modifier, placeholder = "Number", keyboardType = KeyboardType.Decimal)
}

@Composable
private fun RelationRowDropdown(prop: PropertyDef, rowId: String?, onSelect: (String) -> Unit) {
    val container = LocalAppContainer.current
    val target = prop.config.relationDatabaseId
    val rows by produceState(emptyList<Page>(), target) { value = target?.let { container.databases.rowsOf(it) }.orEmpty() }
    MonoDropdown(rows.firstOrNull { it.id == rowId }, rows, { it.displayTitle }, { onSelect(it.id) }, placeholder = "Pick a row")
}

// ---------------------------------------------------------------- Sort

@Composable
fun SortSheet(schema: DatabaseSchema, config: ViewConfig, onChange: (List<SortRule>) -> Unit, onDismiss: () -> Unit) {
    var sorts by remember { mutableStateOf(config.sorts) }
    fun update(s: List<SortRule>) {
        sorts = s
        onChange(s)
    }
    MonoBottomSheet(onDismiss = onDismiss, title = "Sort") {
        Column(Modifier.padding(horizontal = Space.l)) {
            Text("Up to three levels, applied in order. Empty values always go last.", style = MonoType.caption)
            Spacer(Modifier.height(Space.m))
            sorts.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${i + 1}.", Modifier.width(24.dp).padding(bottom = Space.m), style = MonoType.bodySmall)
                    MonoDropdown(schema.property(s.propertyId), schema.properties.filter { p -> p.id == s.propertyId || sorts.none { it.propertyId == p.id } }, { it.name }, { p ->
                        update(sorts.mapIndexed { j, r -> if (j == i) r.copy(propertyId = p.id) else r })
                    }, Modifier.weight(1f).padding(end = Space.s))
                    MonoSegmented(SortDirection.entries, s.direction, { if (it == SortDirection.ASC) "↑ ASC" else "↓ DESC" }, { d ->
                        update(sorts.mapIndexed { j, r -> if (j == i) r.copy(direction = d) else r })
                    }, Modifier.padding(bottom = Space.s))
                    MonoIconButton(MonoIcons.X, "Remove sort", { update(sorts.filterIndexed { j, _ -> j != i }) }, iconSize = 18.dp)
                }
            }
            Spacer(Modifier.height(Space.m))
            MonoButton("Add sort", {
                val next = schema.properties.firstOrNull { p -> sorts.none { it.propertyId == p.id } }
                if (next != null) update(sorts + SortRule(next.id))
            }, icon = MonoIcons.Plus, enabled = sorts.size < 3 && sorts.size < schema.properties.size)
        }
    }
}

// ---------------------------------------------------------------- Group

@Composable
fun GroupSheet(schema: DatabaseSchema, view: DatabaseView, onChange: (String?) -> Unit, onDismiss: () -> Unit) {
    val candidates = schema.properties.filter {
        it.type !in setOf(PropertyType.FILES, PropertyType.RELATION, PropertyType.CREATED_TIME, PropertyType.EDITED_TIME, PropertyType.TITLE)
    }
    MonoBottomSheet(onDismiss = onDismiss, title = if (view.type == ViewType.BOARD) "Board columns" else "Group by") {
        if (view.type != ViewType.BOARD) {
            GroupRow("None", MonoIcons.X, view.config.groupBy == null) { onChange(null); onDismiss() }
        }
        candidates
            .filter { view.type != ViewType.BOARD || it.type.hasOptions || it.type == PropertyType.CHECKBOX }
            .forEach { p -> GroupRow(p.name, p.type.icon(), view.config.groupBy == p.id) { onChange(p.id); onDismiss() } }
    }
}

@Composable
private fun GroupRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = onClick, selected = selected).padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(Space.m))
        Text(label, Modifier.weight(1f), style = MonoType.body)
        if (selected) Icon(MonoIcons.Check, "Selected", Modifier.size(20.dp))
    }
    Hairline()
}

// ---------------------------------------------------------------- Properties

@Composable
fun PropertiesSheet(
    snapshot: DatabaseSnapshot,
    view: DatabaseView,
    onVisible: (String, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = DatabaseQuery.orderedProperties(snapshot, view)
    val visible = view.config.visibleProperties
    MonoBottomSheet(onDismiss = onDismiss, title = "Properties", trailing = {
        MonoButton("New", onAdd, style = MonoButtonStyle.Filled, icon = MonoIcons.Plus, height = 36.dp)
    }) {
        ordered.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = Space.l, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(p.type.icon(), null, Modifier.size(18.dp), tint = MonoColors.Secondary)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f).inkClickable(onClick = { onEdit(p.id) }, showBar = false).padding(vertical = Space.s)) {
                    Text(p.name, style = MonoType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.type.label, style = MonoType.caption)
                }
                if (!p.isTitle) {
                    MonoIconButton(MonoIcons.ChevronUp, "Move ${p.name} up", { onMove(p.id, true) }, enabled = i > 1, iconSize = 18.dp)
                    MonoIconButton(MonoIcons.ChevronDown, "Move ${p.name} down", { onMove(p.id, false) }, enabled = i < ordered.lastIndex, iconSize = 18.dp)
                    MonoSwitch(visible == null || p.id in visible, { onVisible(p.id, it) }, label = "Show ${p.name}")
                } else {
                    Text("Always shown", Modifier.padding(end = Space.m), style = MonoType.caption)
                }
            }
            Hairline()
        }
    }
}

// ---------------------------------------------------------------- View settings

@Composable
fun ViewSettingsSheet(
    snapshot: DatabaseSnapshot,
    view: DatabaseView,
    onRename: (String) -> Unit,
    onConfig: ((ViewConfig) -> ViewConfig) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(view.id) { mutableStateOf(view.name) }
    val schema = snapshot.database.schema
    MonoBottomSheet(onDismiss = { onRename(name); onDismiss() }, title = "View settings") {
        Column(Modifier.padding(horizontal = Space.l)) {
            MonoTextField(name, { name = it.take(60) }, label = "Name", imeDone = { onRename(name) })
            Spacer(Modifier.height(Space.l))
            FormRow("Layout", caption = "Set when the view is created") { Text(view.type.label, style = MonoType.bodySmall) }
            when (view.type) {
                ViewType.CALENDAR -> {
                    val dates = schema.properties.filter { it.type == PropertyType.DATE }
                    MonoDropdown(schema.property(view.config.calendarPropertyId) ?: dates.firstOrNull(), dates, { it.name }, { p ->
                        onConfig { it.copy(calendarPropertyId = p.id) }
                    }, caption = "Show rows by", placeholder = "Add a Date property first")
                }
                ViewType.BOARD -> {
                    val groupable = schema.properties.filter { it.type.hasOptions || it.type == PropertyType.CHECKBOX }
                    MonoDropdown(schema.property(view.config.groupBy) ?: groupable.firstOrNull(), groupable, { it.name }, { p ->
                        onConfig { it.copy(groupBy = p.id) }
                    }, caption = "Columns by", placeholder = "Add a Select or Status property first")
                }
                else -> Unit
            }
            Spacer(Modifier.height(Space.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delete this view. Rows are not affected.", Modifier.weight(1f), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                DeleteButton({ onDelete(); onDismiss() }, label = "Delete view", expandedWidth = 156.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- CSV import

@Composable
fun CsvImportSheet(schema: DatabaseSchema, data: CsvImport, onImport: (List<CsvTarget>) -> Unit, onDismiss: () -> Unit) {
    val initial = remember(data) {
        data.headers.mapIndexed { i, h ->
            val match = schema.byName(h.trim())
            when {
                match?.isTitle == true -> CsvTarget.Title
                match != null && !match.type.computed -> CsvTarget.Existing(match.id)
                i == 0 && data.headers.none { schema.byName(it.trim())?.isTitle == true } -> CsvTarget.Title
                else -> CsvTarget.NewProperty(h.trim().ifEmpty { "Column ${i + 1}" })
            }
        }
    }
    var mapping by remember(data) { mutableStateOf(initial) }
    val editable = schema.properties.filter { !it.isTitle && !it.type.computed && it.type != PropertyType.RELATION && it.type != PropertyType.FILES }
    MonoBottomSheet(onDismiss = onDismiss, title = "Import CSV") {
        Column(Modifier.padding(horizontal = Space.l)) {
            Text("${data.rows.size} rows found. Map each column to a property.", style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
            Spacer(Modifier.height(Space.m))
            data.headers.forEachIndexed { i, h ->
                val options: List<CsvTarget> = listOf(CsvTarget.Skip, CsvTarget.Title, CsvTarget.NewProperty(h.trim().ifEmpty { "Column ${i + 1}" })) +
                    editable.map { CsvTarget.Existing(it.id) }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f).padding(end = Space.s)) {
                        Text(h.ifBlank { "Column ${i + 1}" }, style = MonoType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(data.rows.firstOrNull()?.getOrNull(i).orEmpty().ifEmpty { "—" }, style = MonoType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MonoDropdown(mapping[i], options, { t ->
                        when (t) {
                            CsvTarget.Skip -> "Skip"
                            CsvTarget.Title -> "Title"
                            is CsvTarget.NewProperty -> "New text property"
                            is CsvTarget.Existing -> schema.property(t.propertyId)?.name ?: "Property"
                        }
                    }, { t ->
                        mapping = mapping.mapIndexed { j, cur ->
                            when {
                                j == i -> t
                                t == CsvTarget.Title && cur == CsvTarget.Title -> CsvTarget.Skip
                                else -> cur
                            }
                        }
                    }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.s))
            }
            Spacer(Modifier.height(Space.l))
            Row {
                MonoButton("Cancel", onDismiss, style = MonoButtonStyle.Text)
                Spacer(Modifier.weight(1f))
                MonoButton("Import ${data.rows.size} rows", { onImport(mapping); onDismiss() }, style = MonoButtonStyle.Filled)
            }
        }
    }
}

// ---------------------------------------------------------------- Bulk edit

@Composable
fun BulkSetSheet(schema: DatabaseSchema, count: Int, onPick: (PropertyDef) -> Unit, onDismiss: () -> Unit) {
    MonoBottomSheet(onDismiss = onDismiss, title = "Set a property on $count rows") {
        schema.properties.filter { !it.isTitle && !it.type.computed }.forEach { p ->
            GroupRow(p.name, p.type.icon(), false) { onPick(p) }
        }
    }
}

/** Empty value placeholder used when bulk-editing several rows. */
fun blankRequest(prop: PropertyDef): ValueEditRequest =
    ValueEditRequest("__bulk__", prop, if (prop.type == PropertyType.CHECKBOX) PropertyValue.Checkbox(false) else null, app.monoworkspace.engine.CellValue.Empty)
