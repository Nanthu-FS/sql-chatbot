package app.monoworkspace.ui.page.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.monoworkspace.model.TableColumn
import app.monoworkspace.model.TableData
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space

/** Block-level grid. Tab moves between cells; header cells name the columns. */
@Composable
fun TableBlock(table: TableData, onChange: (TableData) -> Unit, modifier: Modifier = Modifier) {
    val t = table.normalized()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var widths by remember(t.columns.size) { mutableStateOf(t.columns.map { it.width.toFloat() }) }
    Column(modifier.semantics { contentDescription = "Table, ${t.columns.size} columns, ${t.rows.size} rows" }) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column(Modifier.border(1.dp, MonoColors.Ink)) {
                // Header
                Row(Modifier.height(IntrinsicSize.Min).background(MonoColors.Tint)) {
                    t.columns.forEachIndexed { ci, col ->
                        var menu by remember { mutableStateOf(false) }
                        Box(Modifier.width(widths.getOrElse(ci) { 140f }.dp).fillMaxHeight()) {
                            Cell(
                                value = col.name,
                                bold = true,
                                onValue = { v -> onChange(t.copy(columns = t.columns.mapIndexed { i, c -> if (i == ci) c.copy(name = v) else c })) },
                                onTab = { back -> focusManager.moveFocus(if (back) FocusDirection.Previous else FocusDirection.Next) },
                                onLongPress = { menu = true },
                            )
                            // Resize handle on the right edge.
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(10.dp)
                                    .fillMaxHeight()
                                    .draggable(
                                        rememberDraggableState { d ->
                                            val dp = with(density) { d.toDp().value }
                                            widths = widths.mapIndexed { i, w -> if (i == ci) (w + dp).coerceIn(72f, 480f) else w }
                                        },
                                        Orientation.Horizontal,
                                        onDragStopped = {
                                            onChange(t.copy(columns = t.columns.mapIndexed { i, c -> c.copy(width = widths.getOrElse(i) { c.width.toFloat() }.toInt()) }))
                                        },
                                    ),
                            ) { Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(MonoColors.Hairline)) }
                            MonoMenu(
                                menu, { menu = false },
                                listOf(
                                    MenuItem("Insert column left", MonoIcons.ChevronLeft, enabled = t.columns.size < TableData.MAX_COLUMNS) {
                                        onChange(insertColumn(t, ci))
                                    },
                                    MenuItem("Insert column right", MonoIcons.ChevronRight, enabled = t.columns.size < TableData.MAX_COLUMNS) {
                                        onChange(insertColumn(t, ci + 1))
                                    },
                                    MenuItem("Delete column", MonoIcons.Trash, destructive = true, enabled = t.columns.size > 1) {
                                        onChange(TableData(t.columns.filterIndexed { i, _ -> i != ci }, t.rows.map { r -> r.filterIndexed { i, _ -> i != ci } }))
                                    },
                                ),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MonoColors.Ink))
                t.rows.forEachIndexed { ri, row ->
                    var menu by remember(ri) { mutableStateOf(false) }
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        row.forEachIndexed { ci, cell ->
                            Box(Modifier.width(widths.getOrElse(ci) { 140f }.dp).fillMaxHeight()) {
                                Cell(
                                    value = cell,
                                    bold = false,
                                    onValue = { v ->
                                        onChange(t.copy(rows = t.rows.mapIndexed { r, cells -> if (r == ri) cells.mapIndexed { c, x -> if (c == ci) v else x } else cells }))
                                    },
                                    onTab = { back ->
                                        val lastCell = ri == t.rows.lastIndex && ci == row.lastIndex
                                        if (!back && lastCell && t.rows.size < TableData.MAX_ROWS) {
                                            onChange(t.copy(rows = t.rows + listOf(List(t.columns.size) { "" })))
                                        }
                                        focusManager.moveFocus(if (back) FocusDirection.Previous else FocusDirection.Next)
                                    },
                                    onLongPress = { menu = true },
                                )
                                if (ci < row.lastIndex) Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(MonoColors.Hairline))
                            }
                        }
                        Box {
                            MonoMenu(
                                menu, { menu = false },
                                listOf(
                                    MenuItem("Insert row above", MonoIcons.ChevronUp, enabled = t.rows.size < TableData.MAX_ROWS) { onChange(insertRow(t, ri)) },
                                    MenuItem("Insert row below", MonoIcons.ChevronDown, enabled = t.rows.size < TableData.MAX_ROWS) { onChange(insertRow(t, ri + 1)) },
                                    MenuItem("Delete row", MonoIcons.Trash, destructive = true, enabled = t.rows.size > 1) {
                                        onChange(t.copy(rows = t.rows.filterIndexed { i, _ -> i != ri }))
                                    },
                                ),
                            )
                        }
                    }
                    if (ri < t.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(MonoColors.Hairline))
                }
            }
        }
        Row(Modifier.padding(top = Space.xs)) {
            MonoButton(
                "Row", { onChange(insertRow(t, t.rows.size)) }, style = MonoButtonStyle.Text, icon = MonoIcons.Plus, height = 36.dp,
                enabled = t.rows.size < TableData.MAX_ROWS,
            )
            MonoButton(
                "Column", { onChange(insertColumn(t, t.columns.size)) }, style = MonoButtonStyle.Text, icon = MonoIcons.Plus, height = 36.dp,
                enabled = t.columns.size < TableData.MAX_COLUMNS,
            )
        }
    }
}

private fun insertRow(t: TableData, at: Int): TableData =
    t.copy(rows = t.rows.toMutableList().apply { add(at.coerceIn(0, size), List(t.columns.size) { "" }) })

private fun insertColumn(t: TableData, at: Int): TableData {
    val i = at.coerceIn(0, t.columns.size)
    return TableData(
        t.columns.toMutableList().apply { add(i, TableColumn("Column ${t.columns.size + 1}")) },
        t.rows.map { r -> r.toMutableList().apply { add(i, "") } },
    )
}

@Composable
private fun Cell(value: String, bold: Boolean, onValue: (String) -> Unit, onTab: (Boolean) -> Unit, onLongPress: () -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).inkClickable(onClick = {}, onLongClick = onLongPress, showBar = false)) {
        BasicTextField(
            value = text,
            onValueChange = {
                val v = it.replace("\n", " ")
                text = v
                onValue(v)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.s, vertical = 10.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Tab) {
                        onTab(e.isShiftPressed); true
                    } else false
                },
            textStyle = MonoType.bodySmall.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
            cursorBrush = SolidColor(MonoColors.Ink),
        )
    }
}
