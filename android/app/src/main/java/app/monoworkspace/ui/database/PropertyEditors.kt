package app.monoworkspace.ui.database

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monoworkspace.engine.CellValue
import app.monoworkspace.engine.Dates
import app.monoworkspace.engine.ValueFormat
import app.monoworkspace.engine.Validation
import app.monoworkspace.engine.formula.Evaluator
import app.monoworkspace.model.FileRef
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SelectOption
import app.monoworkspace.model.StatusGroup
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.components.ChipText
import app.monoworkspace.ui.components.DatePickerSheet
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoCheckbox
import app.monoworkspace.ui.components.MonoDialog
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.MonoTextField
import app.monoworkspace.ui.components.PickedDate
import app.monoworkspace.ui.components.WarningChip
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.launch
import java.time.ZoneId

fun PropertyType.icon(): ImageVector = when (this) {
    PropertyType.TITLE -> MonoIcons.Text
    PropertyType.TEXT -> MonoIcons.Text
    PropertyType.NUMBER -> MonoIcons.Hash
    PropertyType.SELECT -> MonoIcons.Select
    PropertyType.MULTI_SELECT -> MonoIcons.Tag
    PropertyType.STATUS -> MonoIcons.Status
    PropertyType.DATE -> MonoIcons.Calendar
    PropertyType.CHECKBOX -> MonoIcons.Todo
    PropertyType.URL -> MonoIcons.Link
    PropertyType.EMAIL -> MonoIcons.Mail
    PropertyType.PHONE -> MonoIcons.Phone
    PropertyType.FILES -> MonoIcons.File
    PropertyType.RELATION -> MonoIcons.Relation
    PropertyType.ROLLUP -> MonoIcons.Sigma
    PropertyType.FORMULA -> MonoIcons.Formula
    PropertyType.CREATED_TIME, PropertyType.EDITED_TIME -> MonoIcons.Clock
    PropertyType.CREATED_BY -> MonoIcons.Person
}

/** Geometric glyph for a status group: ○ to do, ◐ in progress, ● complete. */
fun statusGlyph(group: StatusGroup?): String = when (group) {
    StatusGroup.TODO, null -> "○"
    StatusGroup.IN_PROGRESS -> "◐"
    StatusGroup.COMPLETE -> "●"
}

/** Read-only rendering of a cell value: chips for options, glyphs for status. */
@Composable
fun CellDisplay(cell: CellValue, prop: PropertyDef, modifier: Modifier = Modifier, maxLines: Int = 1, onError: ((String) -> Unit)? = null) {
    when {
        cell is CellValue.Error -> Text(
            "#ERR",
            modifier.then(if (onError != null) Modifier.inkClickable(onClick = { onError(cell.message) }, showBar = false) else Modifier),
            style = MonoType.caption.copy(color = MonoColors.Tertiary),
        )
        cell is CellValue.Options && prop.type == PropertyType.STATUS -> {
            val o = cell.options.first()
            Text("${statusGlyph(o.group)} ${o.name}", modifier, style = MonoType.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        cell is CellValue.Options -> FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxLines = maxLines) {
            cell.options.forEach { o -> ChipText(o.name, inverted = prop.type == PropertyType.SELECT && o.order == 0) }
        }
        cell is CellValue.Bool && prop.type == PropertyType.CHECKBOX -> MonoCheckbox(cell.value, null, modifier, size = 18.dp)
        cell is CellValue.Rows -> FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), maxLines = maxLines) {
            cell.rows.forEach { r ->
                if (r.deleted) ChipText("Deleted row", color = MonoColors.Tertiary)
                else Text("↗ " + r.title, style = MonoType.bodySmall.copy(textDecoration = TextDecoration.Underline), maxLines = 1)
            }
        }
        else -> {
            val text = ValueFormat.display(cell, prop)
            val numeric = cell is CellValue.Num || cell is CellValue.DateTime
            Text(
                text,
                modifier,
                style = (if (numeric) MonoType.bodySmall.tnum() else MonoType.bodySmall).copy(
                    textDecoration = if (prop.type == PropertyType.URL || prop.type == PropertyType.EMAIL) TextDecoration.Underline else null,
                ),
                maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Which editor to show for a property value. */
data class ValueEditRequest(val rowId: String, val prop: PropertyDef, val stored: PropertyValue?, val cell: CellValue)

/**
 * Hosts the right editor for a property: inline dialog for text-like and
 * numbers, sheets for options, dates, relations and files.
 */
@Composable
fun ValueEditorHost(
    request: ValueEditRequest?,
    databaseId: String,
    onDismiss: () -> Unit,
    onSave: (rowId: String, propertyId: String, value: PropertyValue?) -> Unit,
) {
    val r = request ?: return
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    when (r.prop.type) {
        PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE -> {
            var text by remember(r) { mutableStateOf((r.stored as? PropertyValue.Text)?.value.orEmpty()) }
            MonoDialog(
                onDismiss = onDismiss, title = r.prop.name, confirmLabel = "Save",
                onConfirm = { onSave(r.rowId, r.prop.id, PropertyValue.Text(text.trim())); onDismiss() },
            ) {
                MonoTextField(
                    text, { text = it.take(2000) },
                    singleLine = r.prop.type != PropertyType.TEXT,
                    minLines = if (r.prop.type == PropertyType.TEXT) 3 else 1,
                    keyboardType = when (r.prop.type) {
                        PropertyType.URL -> KeyboardType.Uri
                        PropertyType.EMAIL -> KeyboardType.Email
                        PropertyType.PHONE -> KeyboardType.Phone
                        else -> KeyboardType.Text
                    },
                    warning = Validation.warning(r.prop.type, text),
                )
                if (r.prop.type == PropertyType.TEXT) Text("${text.length} / 2000", style = MonoType.caption.tnum())
            }
        }
        PropertyType.NUMBER -> {
            var text by remember(r) { mutableStateOf((r.stored as? PropertyValue.Number)?.value?.let { Evaluator.formatNumber(it) }.orEmpty()) }
            val parsed = text.replace(",", "").trim().toDoubleOrNull()
            MonoDialog(
                onDismiss = onDismiss, title = r.prop.name, confirmLabel = "Save",
                onConfirm = {
                    if (text.isBlank()) onSave(r.rowId, r.prop.id, null)
                    else if (parsed != null) onSave(r.rowId, r.prop.id, PropertyValue.Number(parsed))
                    onDismiss()
                },
            ) {
                MonoTextField(text, { text = it }, keyboardType = KeyboardType.Decimal, error = if (text.isNotBlank() && parsed == null) "Not a number" else null)
            }
        }
        PropertyType.SELECT, PropertyType.STATUS, PropertyType.MULTI_SELECT -> OptionPickerSheet(
            prop = r.prop,
            selected = when (val v = r.stored) {
                is PropertyValue.Select -> listOf(v.optionId)
                is PropertyValue.Multi -> v.optionIds
                else -> emptyList()
            },
            onDismiss = onDismiss,
            onChange = { ids ->
                val value = when {
                    r.prop.type == PropertyType.MULTI_SELECT -> PropertyValue.Multi(ids)
                    ids.isEmpty() -> null
                    else -> PropertyValue.Select(ids.first())
                }
                onSave(r.rowId, r.prop.id, value)
                if (r.prop.type != PropertyType.MULTI_SELECT) onDismiss()
            },
            onCreate = { name, group ->
                scope.launch {
                    val opt = container.databases.addOption(databaseId, r.prop.id, name, group)
                    val value = if (r.prop.type == PropertyType.MULTI_SELECT) {
                        PropertyValue.Multi(((r.stored as? PropertyValue.Multi)?.optionIds.orEmpty() + opt.id).distinct())
                    } else PropertyValue.Select(opt.id)
                    onSave(r.rowId, r.prop.id, value)
                    if (r.prop.type != PropertyType.MULTI_SELECT) onDismiss()
                }
            },
        )
        PropertyType.DATE -> {
            val stored = r.stored as? PropertyValue.DateValue
            val zone = ZoneId.systemDefault()
            val initial = stored?.let { Dates.parse(it, zone) }?.let {
                PickedDate(it.start.toLocalDate(), it.end?.toLocalDate(), it.includeTime, if (it.includeTime) it.start.toLocalTime() else null)
            }
            DatePickerSheet(initial, onDismiss, onSave = { picked ->
                val value = picked?.let { p ->
                    val start = if (p.includeTime && p.time != null) p.start.atTime(p.time).atZone(zone) else p.start.atStartOfDay(zone)
                    val end = p.end?.let { e -> if (p.includeTime && p.time != null) e.atTime(p.time).atZone(zone) else e.atStartOfDay(zone) }
                    PropertyValue.DateValue(Dates.store(start, p.includeTime), end?.let { Dates.store(it, p.includeTime) }, p.includeTime)
                }
                onSave(r.rowId, r.prop.id, value)
                onDismiss()
            }, title = r.prop.name)
        }
        PropertyType.CHECKBOX -> androidx.compose.runtime.LaunchedEffect(r) {
            onSave(r.rowId, r.prop.id, PropertyValue.Checkbox(!((r.stored as? PropertyValue.Checkbox)?.value ?: false)))
            onDismiss()
        }
        PropertyType.RELATION -> RelationPickerSheet(
            prop = r.prop,
            selected = (r.stored as? PropertyValue.Relation)?.rowIds.orEmpty(),
            excludeId = r.rowId,
            onDismiss = onDismiss,
            onChange = { ids -> onSave(r.rowId, r.prop.id, PropertyValue.Relation(ids)) },
        )
        PropertyType.FILES -> FilesSheet(
            prop = r.prop,
            files = (r.stored as? PropertyValue.Files)?.files.orEmpty(),
            onDismiss = onDismiss,
            onChange = { files -> onSave(r.rowId, r.prop.id, PropertyValue.Files(files)) },
        )
        PropertyType.FORMULA -> {
            val err = (r.cell as? CellValue.Error)?.message
            MonoDialog(
                onDismiss = onDismiss, title = r.prop.name,
                body = if (err != null) "This formula has an error: $err" else "Formulas are computed: ${ValueFormat.display(r.cell, r.prop)}",
                confirmLabel = "OK", onConfirm = onDismiss,
            )
        }
        else -> MonoDialog(
            onDismiss = onDismiss, title = r.prop.name,
            body = "${r.prop.type.label} is computed and can't be edited. Value: ${ValueFormat.display(r.cell, r.prop).ifEmpty { "empty" }}",
            confirmLabel = "OK", onConfirm = onDismiss,
        )
    }
}

/** Option list with search-to-create; status options are grouped. */
@Composable
fun OptionPickerSheet(
    prop: PropertyDef,
    selected: List<String>,
    onDismiss: () -> Unit,
    onChange: (List<String>) -> Unit,
    onCreate: (String, StatusGroup?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var current by remember(selected) { mutableStateOf(selected) }
    val multi = prop.type == PropertyType.MULTI_SELECT
    val options = prop.sortedOptions.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val exact = prop.options.any { it.name.equals(query.trim(), ignoreCase = true) }
    MonoBottomSheet(onDismiss = onDismiss, title = prop.name) {
        Column(Modifier.padding(horizontal = Space.l)) {
            MonoTextField(query, { query = it }, placeholder = if (multi) "Search or create options" else "Search or create an option")
        }
        Spacer(Modifier.size(Space.s))
        val groups: List<Pair<String?, List<SelectOption>>> = if (prop.type == PropertyType.STATUS) {
            StatusGroup.entries.map { g -> g.label to options.filter { (it.group ?: StatusGroup.TODO) == g } }
        } else listOf(null to options)
        groups.forEach { (label, opts) ->
            if (label != null) LabelText(label, Modifier.padding(start = Space.l, top = Space.m, bottom = Space.xs), color = MonoColors.Secondary)
            opts.forEach { o ->
                val on = o.id in current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .inkClickable(onClick = {
                            current = if (multi) (if (on) current - o.id else current + o.id) else (if (on) emptyList() else listOf(o.id))
                            onChange(current)
                        }, selected = on)
                        .padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (prop.type == PropertyType.STATUS) Text(statusGlyph(o.group) + "  ", style = MonoType.body)
                    Text(o.name, Modifier.weight(1f), style = MonoType.body)
                    if (on) Icon(MonoIcons.Check, "Selected", Modifier.size(20.dp))
                }
                Hairline()
            }
        }
        if (query.isNotBlank() && !exact) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { onCreate(query.trim(), null); query = "" }).padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoIcon(MonoIcons.Plus, null, size = 20.dp)
                Spacer(Modifier.width(Space.m))
                Text("Create “${query.trim()}”", style = MonoType.body)
            }
        }
        if (multi || prop.type != PropertyType.STATUS) {
            Row(Modifier.fillMaxWidth().padding(Space.l), horizontalArrangement = Arrangement.End) {
                MonoButton("Clear", { current = emptyList(); onChange(emptyList()) }, style = MonoButtonStyle.Text)
                Spacer(Modifier.width(Space.s))
                MonoButton("Done", onDismiss, style = MonoButtonStyle.Filled)
            }
        }
    }
}

/** Searchable list of rows in the relation's target database. */
@Composable
fun RelationPickerSheet(prop: PropertyDef, selected: List<String>, excludeId: String?, onDismiss: () -> Unit, onChange: (List<String>) -> Unit) {
    val container = LocalAppContainer.current
    val target = prop.config.relationDatabaseId
    val rows by produceState(emptyList<Page>(), target) { value = target?.let { container.databases.rowsOf(it) }.orEmpty() }
    var query by remember { mutableStateOf("") }
    var current by remember(selected) { mutableStateOf(selected) }
    MonoBottomSheet(onDismiss = onDismiss, title = prop.name) {
        Column(Modifier.padding(horizontal = Space.l)) { MonoTextField(query, { query = it }, placeholder = "Search rows") }
        Spacer(Modifier.size(Space.s))
        if (target == null) Text("Pick a target database in the property settings first.", Modifier.padding(Space.l), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
        // Selected ids whose rows are gone show as deleted and drop on next edit.
        current.filter { id -> rows.none { it.id == id } }.forEach { id ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).inkClickable(onClick = { current = current - id; onChange(current) }).padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipText("Deleted row", color = MonoColors.Tertiary)
                Spacer(Modifier.weight(1f))
                Text("Remove", style = MonoType.caption)
            }
        }
        rows.filter { it.id != excludeId && it.displayTitle.contains(query.trim(), ignoreCase = true) }.forEach { p ->
            val on = p.id in current
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .inkClickable(onClick = {
                        // Editing drops references to rows that no longer exist.
                        current = (if (on) current - p.id else current + p.id).filter { id -> rows.any { it.id == id } }
                        onChange(current)
                    }, selected = on)
                    .padding(horizontal = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.icon ?: "↗", Modifier.width(28.dp), style = MonoType.body)
                Text(p.displayTitle, Modifier.weight(1f), style = MonoType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (on) Icon(MonoIcons.Check, "Linked", Modifier.size(20.dp))
            }
            Hairline()
        }
        Row(Modifier.fillMaxWidth().padding(Space.l), horizontalArrangement = Arrangement.End) {
            MonoButton("Done", onDismiss, style = MonoButtonStyle.Filled)
        }
    }
}

@Composable
fun FilesSheet(prop: PropertyDef, files: List<FileRef>, onDismiss: () -> Unit, onChange: (List<FileRef>) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var current by remember(files) { mutableStateOf(files) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val m = container.media.importFile(uri)
                current = current + FileRef(m.name, m.displayName, m.size, m.mimeType)
                onChange(current)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't add that file"
            }
        }
    }
    MonoBottomSheet(onDismiss = onDismiss, title = prop.name) {
        current.forEach { f ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
                MonoIcon(MonoIcons.File, null, size = 20.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MonoType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.monoworkspace.ui.common.Formats.bytes(f.size), style = MonoType.caption.tnum())
                }
                app.monoworkspace.ui.components.MonoIconButton(MonoIcons.X, "Remove ${f.name}", { current = current - f; onChange(current) })
            }
            Hairline()
        }
        error?.let { WarningChip(it, Modifier.padding(Space.l)) }
        Row(Modifier.fillMaxWidth().padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            MonoButton("Add file", { picker.launch(arrayOf("*/*")) }, icon = MonoIcons.Plus)
            Spacer(Modifier.weight(1f))
            MonoButton("Done", onDismiss, style = MonoButtonStyle.Filled)
        }
    }
}

/** Label + value rows for a row page, separated by hairlines. */
@Composable
fun PropertyRowLine(
    prop: PropertyDef,
    cell: CellValue,
    onClick: () -> Unit,
    onLabelClick: () -> Unit,
    warning: String? = null,
) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.width(132.dp).heightIn(min = 44.dp).inkClickable(onClick = onLabelClick, showBar = false).padding(end = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(prop.type.icon(), null, Modifier.size(16.dp), tint = MonoColors.Secondary)
                Spacer(Modifier.width(Space.s))
                Text(prop.name, style = MonoType.bodySmall.copy(color = MonoColors.Secondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).inkClickable(onClick = onClick, showBar = false).padding(horizontal = Space.s),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (cell.isEmpty && cell !is CellValue.Bool) Text("Empty", style = MonoType.bodySmall.copy(color = MonoColors.Tertiary))
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    CellDisplay(cell, prop, Modifier.weight(1f, fill = false), maxLines = 3)
                    if (warning != null) {
                        Spacer(Modifier.width(Space.s))
                        WarningChip(warning)
                    }
                }
            }
        }
        Hairline()
    }
}
