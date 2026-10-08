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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.monoworkspace.core.Ids
import app.monoworkspace.engine.formula.CompiledFormula
import app.monoworkspace.model.Database
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.NumberFormat
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.RollupFunction
import app.monoworkspace.model.SelectOption
import app.monoworkspace.model.StatusGroup
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.components.DeleteButton
import app.monoworkspace.ui.components.FormRow
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoChip
import app.monoworkspace.ui.components.MonoDialog
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
import kotlinx.coroutines.launch

/** Picks a name and type for a new property. */
@Composable
fun AddPropertySheet(databaseId: String, onDismiss: () -> Unit, onAdded: (PropertyDef) -> Unit = {}) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    MonoBottomSheet(onDismiss = onDismiss, title = "New property") {
        Column(Modifier.padding(horizontal = Space.l)) {
            MonoTextField(name, { name = it.take(80) }, label = "Name", placeholder = "Property name")
        }
        LabelText("Type", Modifier.padding(start = Space.l, top = Space.l, bottom = Space.s), color = MonoColors.Secondary)
        PropertyType.pickable.forEach { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .inkClickable(onClick = {
                        scope.launch {
                            val def = container.databases.addProperty(databaseId, name.ifBlank { t.label }, t)
                            onAdded(def)
                            onDismiss()
                        }
                    })
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon(), null, Modifier.size(20.dp))
                Spacer(Modifier.width(Space.m))
                Text(t.label, style = MonoType.body)
            }
            Hairline()
        }
    }
}

/**
 * Edits one property: name, type, options, number format, formula, relation
 * and rollup settings. Type changes convert values and report what was lost.
 */
@Composable
fun PropertySheet(database: Database, propertyId: String, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val original = database.schema.property(propertyId) ?: return
    var draft by remember(propertyId, original.type) { mutableStateOf(original) }
    var confirmType by remember { mutableStateOf<PropertyType?>(null) }
    val databases by remember { container.databases.observeDatabases() }.collectAsState(initial = emptyList())

    fun save() {
        scope.launch {
            container.databases.updateProperty(database.id, draft)
            onDismiss()
        }
    }

    MonoBottomSheet(onDismiss = { save() }, title = "Edit property", trailing = {
        MonoButton("Done", { save() }, style = MonoButtonStyle.Filled, height = 36.dp)
    }) {
        Column(Modifier.padding(horizontal = Space.l)) {
            MonoTextField(draft.name, { draft = draft.copy(name = it.take(80)) }, label = "Name")
            Spacer(Modifier.height(Space.l))
            if (original.isTitle) {
                FormRow("Type", caption = "Every database has one title property") { Text("Title", style = MonoType.bodySmall) }
            } else {
                MonoDropdown(
                    value = draft.type,
                    options = PropertyType.pickable,
                    label = { it.label },
                    onSelect = { t -> if (t != draft.type) confirmType = t },
                    caption = "Type",
                )
            }
            Spacer(Modifier.height(Space.l))
            when (draft.type) {
                PropertyType.SELECT, PropertyType.MULTI_SELECT, PropertyType.STATUS -> OptionsEditor(draft) { draft = it }
                PropertyType.NUMBER -> NumberEditor(draft) { draft = it }
                PropertyType.FORMULA -> FormulaEditor(draft, database.schema) { draft = it }
                PropertyType.RELATION -> RelationEditor(draft, databases.map { it.database to it.page.displayTitle }) { draft = it }
                PropertyType.ROLLUP -> RollupEditor(draft, database) { draft = it }
                else -> Text(describe(draft.type), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
            }
            if (!original.isTitle) {
                Spacer(Modifier.height(Space.xl))
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete this property and its values", Modifier.weight(1f), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                    DeleteButton({
                        scope.launch {
                            container.databases.deleteProperty(database.id, propertyId)
                            onDismiss()
                        }
                    })
                }
            }
        }
    }

    confirmType?.let { t ->
        MonoDialog(
            onDismiss = { confirmType = null },
            title = "Change type to ${t.label}?",
            body = "Values are converted where possible. Values that can't be converted are cleared.",
            confirmLabel = "Change",
            onConfirm = {
                confirmType = null
                scope.launch {
                    container.databases.updateProperty(database.id, draft.copy(type = original.type))
                    val cleared = container.databases.changeType(database.id, propertyId, t)
                    val fresh = container.databases.get(database.id)?.schema?.property(propertyId)
                    if (fresh != null) draft = fresh
                    if (cleared > 0) messenger.show("$cleared value${if (cleared == 1) "" else "s"} couldn't be converted and ${if (cleared == 1) "was" else "were"} cleared")
                }
            },
        )
    }
}

private fun describe(t: PropertyType): String = when (t) {
    PropertyType.TEXT -> "Plain text, up to 2,000 characters."
    PropertyType.DATE -> "A date or range, with optional time. Stored in your device's time zone."
    PropertyType.CHECKBOX -> "A simple yes or no."
    PropertyType.URL -> "A web address. You'll see a warning if it doesn't look valid."
    PropertyType.EMAIL -> "An email address. You'll see a warning if it doesn't look valid."
    PropertyType.PHONE -> "A phone number. You'll see a warning if it doesn't look valid."
    PropertyType.FILES -> "Files copied into the app from your device."
    PropertyType.CREATED_TIME -> "Set automatically when a row is created."
    PropertyType.EDITED_TIME -> "Updated automatically on every edit."
    PropertyType.CREATED_BY -> "Always “You”: this workspace has a single local user."
    else -> ""
}

@Composable
private fun OptionsEditor(def: PropertyDef, onChange: (PropertyDef) -> Unit) {
    val sorted = def.sortedOptions
    LabelText("Options", color = MonoColors.Secondary)
    Spacer(Modifier.height(Space.s))
    sorted.forEachIndexed { i, o ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (def.type == PropertyType.STATUS) {
                MonoDropdown(
                    value = o.group ?: StatusGroup.TODO,
                    options = StatusGroup.entries,
                    label = { statusGlyph(it) + " " + it.label },
                    onSelect = { g -> onChange(def.copy(options = def.options.map { if (it.id == o.id) it.copy(group = g) else it })) },
                    modifier = Modifier.width(132.dp).padding(end = Space.s),
                    menuWidth = 200.dp,
                )
            }
            MonoTextField(
                o.name,
                { v -> onChange(def.copy(options = def.options.map { if (it.id == o.id) it.copy(name = v.take(100)) else it })) },
                Modifier.weight(1f),
                textStyle = MonoType.bodySmall,
            )
            MonoIconButton(MonoIcons.ChevronUp, "Move ${o.name} up", { onChange(def.copy(options = reorder(sorted, i, i - 1))) }, enabled = i > 0, iconSize = 18.dp)
            MonoIconButton(MonoIcons.ChevronDown, "Move ${o.name} down", { onChange(def.copy(options = reorder(sorted, i, i + 1))) }, enabled = i < sorted.lastIndex, iconSize = 18.dp)
            MonoIconButton(MonoIcons.X, "Remove ${o.name}", { onChange(def.copy(options = def.options.filter { it.id != o.id })) }, iconSize = 18.dp)
        }
    }
    MonoButton("Add option", {
        val next = (def.options.maxOfOrNull { it.order } ?: -1) + 1
        onChange(def.copy(options = def.options + SelectOption(Ids.new(), "Option ${def.options.size + 1}", next, if (def.type == PropertyType.STATUS) StatusGroup.TODO else null)))
    }, style = MonoButtonStyle.Text, icon = MonoIcons.Plus)
}

private fun reorder(list: List<SelectOption>, from: Int, to: Int): List<SelectOption> {
    if (to !in list.indices) return list
    val m = list.toMutableList()
    val item = m.removeAt(from)
    m.add(to, item)
    return m.mapIndexed { i, o -> o.copy(order = i) }
}

@Composable
private fun NumberEditor(def: PropertyDef, onChange: (PropertyDef) -> Unit) {
    MonoDropdown(
        value = def.config.numberFormat,
        options = NumberFormat.entries,
        label = { it.label },
        onSelect = { onChange(def.copy(config = def.config.copy(numberFormat = it))) },
        caption = "Format",
    )
    Spacer(Modifier.height(Space.l))
    Text("Decimal places", style = MonoType.caption)
    Spacer(Modifier.height(Space.xs))
    MonoSegmented((0..4).toList(), def.config.decimals, { it.toString() }, { onChange(def.copy(config = def.config.copy(decimals = it))) })
    if (def.config.numberFormat == NumberFormat.CURRENCY) {
        Spacer(Modifier.height(Space.l))
        MonoTextField(def.config.currencySymbol, { onChange(def.copy(config = def.config.copy(currencySymbol = it.take(4)))) }, label = "Currency symbol")
        Text("Display only: no currency conversion.", style = MonoType.caption)
    }
}

@Composable
private fun FormulaEditor(def: PropertyDef, schema: DatabaseSchema, onChange: (PropertyDef) -> Unit) {
    val expr = def.config.expression
    val compiled = remember(expr) { CompiledFormula(expr) }
    val missing = compiled.references.filter { schema.byName(it) == null }
    MonoTextField(
        expr,
        { onChange(def.copy(config = def.config.copy(expression = it))) },
        label = "Expression",
        singleLine = false,
        minLines = 3,
        textStyle = MonoType.code,
        error = when {
            expr.isBlank() -> null
            compiled.parseError != null -> compiled.parseError
            missing.isNotEmpty() -> "No property named " + missing.joinToString { "“$it”" }
            else -> null
        },
    )
    if (expr.isNotBlank() && compiled.parseError == null && missing.isEmpty()) {
        Text("Formula is valid", Modifier.padding(top = Space.xs), style = MonoType.caption)
    }
    Spacer(Modifier.height(Space.m))
    LabelText("Properties", color = MonoColors.Secondary)
    FlowRow(Modifier.padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        schema.properties.filter { it.id != def.id }.forEach { p ->
            MonoChip(p.name, false, { onChange(def.copy(config = def.config.copy(expression = expr + "prop(\"${p.name}\")"))) })
        }
    }
    LabelText("Functions", color = MonoColors.Secondary)
    FlowRow(Modifier.padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("if(", "concat(", "length(", "round(", "now()", "dateDiff(", " + ", " - ", " * ", " / ", " == ", " > ").forEach { f ->
            MonoChip(f.trim(), false, { onChange(def.copy(config = def.config.copy(expression = expr + f))) })
        }
    }
    Text(
        "Example: if(prop(\"Done\"), \"✓\", concat(prop(\"Name\"), \" · \", dateDiff(prop(\"Due\"), now(), \"days\")))",
        Modifier.border(1.dp, MonoColors.Hairline).padding(Space.s).fillMaxWidth(),
        style = MonoType.caption.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun RelationEditor(def: PropertyDef, databases: List<Pair<Database, String>>, onChange: (PropertyDef) -> Unit) {
    val current = databases.firstOrNull { it.first.id == def.config.relationDatabaseId }
    MonoDropdown(
        value = current,
        options = databases,
        label = { it.second },
        onSelect = { onChange(def.copy(config = def.config.copy(relationDatabaseId = it.first.id, relationReversePropertyId = null))) },
        caption = "Related database",
        placeholder = "Pick a database",
    )
    Spacer(Modifier.height(Space.m))
    FormRow("Two-way relation", caption = "Also show this link on the related rows") {
        MonoSwitch(def.config.relationTwoWay, { onChange(def.copy(config = def.config.copy(relationTwoWay = it))) }, label = "Two-way relation", enabled = current != null)
    }
}

@Composable
private fun RollupEditor(def: PropertyDef, database: Database, onChange: (PropertyDef) -> Unit) {
    val container = LocalAppContainer.current
    val relations = database.schema.properties.filter { it.type == PropertyType.RELATION }
    val relation = relations.firstOrNull { it.id == def.config.rollupRelationPropertyId }
    val targetSchema by produceState<DatabaseSchema?>(null, relation?.config?.relationDatabaseId) {
        value = relation?.config?.relationDatabaseId?.let { container.databases.get(it)?.schema }
    }
    if (relations.isEmpty()) {
        Text("Add a relation property first; rollups summarise related rows.", style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
        return
    }
    MonoDropdown(relation, relations, { it.name }, { onChange(def.copy(config = def.config.copy(rollupRelationPropertyId = it.id, rollupTargetPropertyId = null))) }, caption = "Relation", placeholder = "Pick a relation")
    Spacer(Modifier.height(Space.m))
    val targets = targetSchema?.properties.orEmpty()
    MonoDropdown(
        targets.firstOrNull { it.id == def.config.rollupTargetPropertyId }, targets, { it.name },
        { onChange(def.copy(config = def.config.copy(rollupTargetPropertyId = it.id))) },
        caption = "Property", placeholder = "Pick a property", enabled = relation != null,
    )
    Spacer(Modifier.height(Space.m))
    MonoDropdown(def.config.rollupFunction, RollupFunction.entries, { it.label }, { onChange(def.copy(config = def.config.copy(rollupFunction = it))) }, caption = "Calculate")
}
