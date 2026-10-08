package app.monoworkspace.ui.database

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.monoworkspace.data.repo.DatabaseSnapshot
import app.monoworkspace.data.repo.ViewResult
import app.monoworkspace.engine.CellValue
import app.monoworkspace.engine.Grouping
import app.monoworkspace.engine.RowGroup
import app.monoworkspace.engine.RowInput
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SortDirection
import app.monoworkspace.model.ViewType
import app.monoworkspace.ui.common.LayoutMode
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.components.BackToTopButton
import app.monoworkspace.ui.components.BackToTopFor
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.HoverFocusState
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MediaImage
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoCheckbox
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.hoverFocus
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.components.rememberHoverFocusState
import app.monoworkspace.ui.components.rememberShowBackToTop
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** What every view can ask the screen to do. */
data class ViewActions(
    val openRow: (String) -> Unit,
    val editCell: (RowInput, PropertyDef) -> Unit,
    val toggleSelect: (String) -> Unit,
    val addRow: (Map<String, PropertyValue>) -> Unit,
    val sortBy: (String, SortDirection) -> Unit,
    val hideProperty: (String) -> Unit,
    val editProperty: (String) -> Unit,
    val setColumnWidth: (String, Int) -> Unit,
    val moveToGroup: (String, PropertyDef, String?) -> Unit,
    val reschedule: (String, PropertyDef, LocalDate) -> Unit,
    val addProperty: () -> Unit,
    val showError: (String) -> Unit,
)

@Composable
fun ViewBody(
    snapshot: DatabaseSnapshot,
    view: DatabaseView,
    result: ViewResult,
    selection: Set<String>,
    actions: ViewActions,
    modifier: Modifier = Modifier,
) {
    if (result.rows.isEmpty() && view.type != ViewType.BOARD && view.type != ViewType.CALENDAR) {
        val filtered = result.total > 0
        EmptyState(
            if (filtered) "No rows match this view's filters." else "This database has no rows yet.",
            "New row", { actions.addRow(emptyMap()) },
            modifier.padding(horizontal = LocalWindowLayout.current.margin),
            icon = MonoIcons.Table,
        )
        return
    }
    when (view.type) {
        ViewType.TABLE -> TableView(snapshot, view, result, selection, actions, modifier)
        ViewType.BOARD -> BoardView(snapshot, view, result, selection, actions, modifier)
        ViewType.LIST -> ListView(view, result, selection, actions, modifier)
        ViewType.GALLERY -> GalleryView(result, selection, actions, modifier)
        ViewType.CALENDAR -> CalendarView(snapshot, view, result, actions, modifier)
    }
}


// ---------------------------------------------------------------- Table

@Composable
private fun TableView(snapshot: DatabaseSnapshot, view: DatabaseView, result: ViewResult, selection: Set<String>, actions: ViewActions, modifier: Modifier) {
    val layout = LocalWindowLayout.current
    val titleWidth = if (layout.mode == LayoutMode.Phone) 168 else 240
    val props = result.visibleProperties.filter { !it.isTitle }
    val titleProp = snapshot.database.schema.title
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val widths = remember(view.config.columnWidths, props) { mutableStateMapOf<String, Float>().apply { props.forEach { put(it.id, (view.config.columnWidths[it.id] ?: 150).toFloat()) } } }
    val selectionMode = selection.isNotEmpty()

    Box(modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            stickyHeader("header") {
                Column(Modifier.fillMaxWidth().background(MonoColors.Background)) {
                Row(Modifier.fillMaxWidth().background(MonoColors.Background).height(40.dp)) {
                    HeaderCell(titleProp, titleWidth.toFloat(), actions, pinned = true, onResize = null)
                    Row(Modifier.horizontalScroll(hScroll)) {
                        props.forEach { p ->
                            HeaderCell(p, widths[p.id] ?: 150f, actions, pinned = false, onResize = { w -> widths[p.id] = w }, onResizeDone = { actions.setColumnWidth(p.id, (widths[p.id] ?: 150f).toInt()) })
                        }
                    }
                }
                SectionRule()
                }
            }
            val groups = result.groups
            if (groups == null) {
                tableRows(result.rows, titleWidth, props, widths, hScroll, result, selection, selectionMode, actions)
            } else {
                groups.filter { it.rows.isNotEmpty() || it.key != null }.forEach { g ->
                    item("g-" + (g.key ?: Grouping.NONE_KEY)) { GroupHeader(g, result.resolver.schema.property(view.config.groupBy)) }
                    tableRows(g.rows, titleWidth, props, widths, hScroll, result, selection, selectionMode, actions, keyPrefix = (g.key ?: "none") + "-")
                }
            }
            item("footer") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { actions.addRow(emptyMap()) }).padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+ New", Modifier.weight(1f), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                    LabelText("Count ${result.rows.size}" + if (result.rows.size != result.total) " of ${result.total}" else "", color = MonoColors.Secondary)
                }
            }
        }
        BackToTopFor(listState, Modifier.align(Alignment.BottomEnd).padding(Space.l))
    }
}

private fun LazyListScope.tableRows(
    rows: List<RowInput>,
    titleWidth: Int,
    props: List<PropertyDef>,
    widths: Map<String, Float>,
    hScroll: ScrollState,
    result: ViewResult,
    selection: Set<String>,
    selectionMode: Boolean,
    actions: ViewActions,
    keyPrefix: String = "",
) {
    items(rows, key = { keyPrefix + it.id }) { row ->
        val selected = row.id in selection
        Column(Modifier.animateItem()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(if (selected) MonoColors.Tint else MonoColors.Background)) {
                Row(
                    Modifier
                        .width(titleWidth.dp)
                        .heightIn(min = 48.dp)
                        .inkClickable(
                            onClick = { if (selectionMode) actions.toggleSelect(row.id) else actions.openRow(row.id) },
                            onLongClick = { actions.toggleSelect(row.id) },
                            selected = selected,
                        )
                        .padding(horizontal = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) MonoCheckbox(selected, { actions.toggleSelect(row.id) }, size = 18.dp)
                    if (row.icon != null) Text(row.icon + " ", style = MonoType.bodySmall)
                    Text(
                        row.title.ifBlank { "Untitled" },
                        style = MonoType.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = if (row.title.isBlank()) MonoColors.Tertiary else MonoColors.Ink),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.width(1.dp).heightIn(min = 48.dp).background(MonoColors.Ink))
                Row(Modifier.horizontalScroll(hScroll)) {
                    props.forEach { p ->
                        val cell = result.resolver.cell(row, p)
                        Box(
                            Modifier
                                .width((widths[p.id] ?: 150f).dp)
                                .heightIn(min = 48.dp)
                                .inkClickable(
                                    onClick = {
                                        if (selectionMode) actions.toggleSelect(row.id)
                                        else if (cell is CellValue.Error) actions.showError(cell.message)
                                        else actions.editCell(row, p)
                                    },
                                    onLongClick = { actions.toggleSelect(row.id) },
                                    showBar = false,
                                )
                                .padding(horizontal = Space.m, vertical = Space.s),
                            contentAlignment = Alignment.CenterStart,
                        ) { CellDisplay(cell, p, maxLines = 2) }
                    }
                }
            }
            Hairline()
        }
    }
}

@Composable
private fun HeaderCell(prop: PropertyDef, width: Float, actions: ViewActions, pinned: Boolean, onResize: ((Float) -> Unit)?, onResizeDone: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(Modifier.width(width.dp).height(40.dp)) {
        Row(
            Modifier.fillMaxSize().inkClickable(onClick = { menu = true }, showBar = false).padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(prop.type.icon(), null, Modifier.size(14.dp), tint = MonoColors.Secondary)
            Spacer(Modifier.width(6.dp))
            LabelText(prop.name)
        }
        if (onResize != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(12.dp)
                    .fillMaxHeight()
                    .draggable(
                        rememberDraggableState { d -> onResize((width + with(density) { d.toDp().value }).coerceIn(72f, 480f)) },
                        Orientation.Horizontal,
                        onDragStopped = { onResizeDone() },
                    )
                    .semantics { contentDescription = "Resize ${prop.name} column" },
            ) { Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(MonoColors.Hairline)) }
        }
        if (pinned) Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(MonoColors.Ink))
        MonoMenu(
            menu, { menu = false },
            buildList {
                add(MenuItem("Sort ascending", MonoIcons.ArrowUp) { actions.sortBy(prop.id, SortDirection.ASC) })
                add(MenuItem("Sort descending", MonoIcons.ArrowDown) { actions.sortBy(prop.id, SortDirection.DESC) })
                if (!prop.isTitle) add(MenuItem("Hide in view", MonoIcons.EyeOff) { actions.hideProperty(prop.id) })
                add(MenuItem("Edit property", MonoIcons.Sliders) { actions.editProperty(prop.id) })
            },
        )
    }
}

@Composable
private fun GroupHeader(g: RowGroup, prop: PropertyDef?) {
    Column(Modifier.fillMaxWidth().background(MonoColors.Background).padding(top = Space.l)) {
        Row(Modifier.padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            val glyph = if (prop?.type == PropertyType.STATUS) statusGlyph(g.option?.group) + " " else ""
            LabelText(glyph + g.label, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(Space.s))
            Text("${g.rows.size}", style = MonoType.caption.tnum())
        }
        SectionRule()
    }
}

// ---------------------------------------------------------------- Board

@Composable
private fun BoardView(snapshot: DatabaseSnapshot, view: DatabaseView, result: ViewResult, selection: Set<String>, actions: ViewActions, modifier: Modifier) {
    val schema = snapshot.database.schema
    val groupProp = schema.property(view.config.groupBy)?.takeIf { it.type.hasOptions || it.type == PropertyType.CHECKBOX }
        ?: schema.properties.firstOrNull { it.type == PropertyType.STATUS || it.type == PropertyType.SELECT }
    val margin = LocalWindowLayout.current.margin
    if (groupProp == null) {
        EmptyState("Board views group cards by a Select or Status property.", "Add a property", actions.addProperty, modifier.padding(horizontal = margin), icon = MonoIcons.Board)
        return
    }
    val groups = remember(result, groupProp) { Grouping.group(result.rows, groupProp, result.resolver) }
    val cardProps = result.visibleProperties.filter { !it.isTitle && it.id != groupProp.id }.take(3)
    val rowState = rememberLazyListState()
    val focus = rememberHoverFocusState()
    val scope = rememberCoroutineScope()

    var dragRow by remember { mutableStateOf<RowInput?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var cardWidthPx by remember { mutableStateOf(0) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    val hoverColumn = columnBounds.entries.firstOrNull { dragRow != null && it.value.contains(pointer) }?.key

    LaunchedEffect(dragRow) {
        while (dragRow != null) {
            val width = rowState.layoutInfo.viewportEndOffset
            val x = pointer.x - rootPos.x
            val delta = when {
                x < 72f -> -20f
                x > width - 72f -> 20f
                else -> 0f
            }
            if (delta != 0f) rowState.scrollBy(delta)
            delay(16)
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionInRoot() }) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = margin, vertical = Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.l),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(groups, key = { it.key ?: Grouping.NONE_KEY }) { g ->
                val key = g.key ?: Grouping.NONE_KEY
                val highlighted = hoverColumn == key
                val bg by animateColorAsState(if (highlighted) MonoColors.Tint else MonoColors.Background, monoTween(Motion.FAST), label = "col")
                Column(
                    Modifier
                        .width(272.dp)
                        .fillMaxHeight()
                        .background(bg)
                        .then(if (highlighted) Modifier.border(2.dp, MonoColors.Ink) else Modifier)
                        .onGloballyPositioned { columnBounds[key] = it.boundsInRoot() }
                        .semantics { contentDescription = "${g.label}, ${g.rows.size} cards" },
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.s, horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                        val glyph = if (groupProp.type == PropertyType.STATUS) statusGlyph(g.option?.group) + " " else ""
                        LabelText(glyph + if (g.key == null) "No ${groupProp.name.lowercase()}" else g.label, Modifier.weight(1f))
                        Text("${g.rows.size}", style = MonoType.caption.tnum())
                    }
                    SectionRule()
                    LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        items(g.rows, key = { key + "/" + it.id }) { row ->
                            var bounds by remember { mutableStateOf(Rect.Zero) }
                            val interaction = remember { MutableInteractionSource() }
                            val dragging = dragRow?.id == row.id
                            Box(
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .onGloballyPositioned { bounds = it.boundsInRoot(); cardWidthPx = it.size.width }
                                    .hoverable(interaction)
                                    .hoverFocus(focus, row.id, interaction)
                                    .pointerInput(row.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { off ->
                                                grab = off
                                                pointer = bounds.topLeft + off
                                                dragRow = row
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                pointer += amount
                                            },
                                            onDragEnd = {
                                                val target = columnBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                                val moving = dragRow
                                                dragRow = null
                                                if (moving != null && target != null && target != key) {
                                                    actions.moveToGroup(moving.id, groupProp, if (target == Grouping.NONE_KEY) null else target)
                                                }
                                            },
                                            onDragCancel = { dragRow = null },
                                        )
                                    },
                            ) {
                                BoardCard(row, cardProps, result, row.id in selection, Modifier.then(if (dragging) Modifier.graphicsLayer { alpha = 0.3f } else Modifier), actions)
                            }
                        }
                    }
                    Text(
                        "+ New",
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .inkClickable(onClick = {
                                val k = g.key
                                val v: Map<String, PropertyValue> = if (k == null) emptyMap() else when (groupProp.type) {
                                    PropertyType.MULTI_SELECT -> mapOf(groupProp.id to PropertyValue.Multi(listOf(k)))
                                    PropertyType.CHECKBOX -> mapOf(groupProp.id to PropertyValue.Checkbox(k == "true"))
                                    else -> mapOf(groupProp.id to PropertyValue.Select(k))
                                }
                                actions.addRow(v)
                            })
                            .padding(Space.m),
                        style = MonoType.bodySmall.copy(color = MonoColors.Secondary),
                    )
                }
            }
        }
        // The card being dragged follows the pen or finger.
        dragRow?.let { row ->
            val density = LocalDensity.current
            Box(
                Modifier
                    .zIndex(10f)
                    .offset { IntOffset((pointer.x - rootPos.x - grab.x).toInt(), (pointer.y - rootPos.y - grab.y).toInt()) }
                    .width(with(density) { cardWidthPx.toDp() }),
            ) {
                BoardCard(row, cardProps, result, false, Modifier.border(1.dp, MonoColors.Ink), actions, lifted = true)
            }
        }
    }
}

@Composable
private fun BoardCard(row: RowInput, props: List<PropertyDef>, result: ViewResult, selected: Boolean, modifier: Modifier, actions: ViewActions, lifted: Boolean = false) {
    Column(
        modifier
            .fillMaxWidth()
            .background(if (selected) MonoColors.Tint else MonoColors.Background)
            .border(1.dp, if (selected || lifted) MonoColors.Ink else MonoColors.Hairline)
            .inkClickable(onClick = { actions.openRow(row.id) }, onLongClick = null, selected = selected, showBar = false)
            .padding(Space.m),
    ) {
        Text(
            (row.icon?.let { "$it " } ?: "") + row.title.ifBlank { "Untitled" },
            style = MonoType.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = if (row.title.isBlank()) MonoColors.Tertiary else MonoColors.Ink),
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        props.forEach { p ->
            val cell = result.resolver.cell(row, p)
            if (!cell.isEmpty || cell is CellValue.Bool) {
                Spacer(Modifier.height(6.dp))
                CellDisplay(cell, p, maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------- List

@Composable
private fun ListView(view: DatabaseView, result: ViewResult, selection: Set<String>, actions: ViewActions, modifier: Modifier) {
    val props = result.visibleProperties.filter { !it.isTitle }.take(2)
    val listState = rememberLazyListState()
    val margin = LocalWindowLayout.current.margin
    val selectionMode = selection.isNotEmpty()
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            val groups = result.groups
            val sections: List<Pair<RowGroup?, List<RowInput>>> = groups?.filter { it.rows.isNotEmpty() }?.map { it to it.rows } ?: listOf(null to result.rows)
            sections.forEach { (g, rows) ->
                if (g != null) item("lg-" + (g.key ?: "none")) { GroupHeader(g, result.resolver.schema.property(view.config.groupBy)) }
                items(rows, key = { (g?.key ?: "") + "/" + it.id }) { row ->
                    val selected = row.id in selection
                    Column(Modifier.animateItem().padding(horizontal = margin)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .inkClickable(
                                    onClick = { if (selectionMode) actions.toggleSelect(row.id) else actions.openRow(row.id) },
                                    onLongClick = { actions.toggleSelect(row.id) },
                                    selected = selected,
                                )
                                .padding(horizontal = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectionMode) MonoCheckbox(selected, { actions.toggleSelect(row.id) }, size = 18.dp)
                            PageGlyph(row.icon, false, size = 18.dp)
                            Spacer(Modifier.width(Space.m))
                            Text(row.title.ifBlank { "Untitled" }, Modifier.weight(1f), style = MonoType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            props.forEach { p ->
                                val cell = result.resolver.cell(row, p)
                                if (!cell.isEmpty) {
                                    Spacer(Modifier.width(Space.m))
                                    CellDisplay(cell, p, Modifier.width(112.dp))
                                }
                            }
                        }
                        Hairline()
                    }
                }
            }
            item("list-new") {
                Text(
                    "+ New",
                    Modifier.fillMaxWidth().padding(horizontal = margin).heightIn(min = 48.dp).inkClickable(onClick = { actions.addRow(emptyMap()) }).padding(Space.m),
                    style = MonoType.bodySmall.copy(color = MonoColors.Secondary),
                )
            }
        }
        BackToTopFor(listState, Modifier.align(Alignment.BottomEnd).padding(Space.l))
    }
}

// ---------------------------------------------------------------- Gallery

@Composable
private fun GalleryView(result: ViewResult, selection: Set<String>, actions: ViewActions, modifier: Modifier) {
    val layout = LocalWindowLayout.current
    val focus = rememberHoverFocusState()
    val gridState = remember { LazyGridState() }
    val scope = rememberCoroutineScope()
    val props = result.visibleProperties.filter { !it.isTitle }.take(2)
    val selectionMode = selection.isNotEmpty()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = when {
            layout.mode == LayoutMode.Phone && maxWidth < 360.dp -> 1
            layout.mode == LayoutMode.Phone -> 2
            layout.mode == LayoutMode.Landscape -> 4
            else -> 3
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(start = layout.margin, end = layout.margin, top = Space.l, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(result.rows, key = { it.id }) { row ->
                GalleryCard(row, props, result, row.id in selection, focus, selectionMode, actions)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "+ New",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { actions.addRow(emptyMap()) }).padding(Space.m),
                    style = MonoType.bodySmall.copy(color = MonoColors.Secondary),
                )
            }
        }
        BackToTopButton(rememberShowBackToTop(gridState), { scope.launch { gridState.animateScrollToItem(0) } }, Modifier.align(Alignment.BottomEnd).padding(Space.l))
    }
}

@Composable
private fun GalleryCard(row: RowInput, props: List<PropertyDef>, result: ViewResult, selected: Boolean, focus: HoverFocusState, selectionMode: Boolean, actions: ViewActions) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .hoverable(interaction)
            .hoverFocus(focus, row.id, interaction)
            .background(MonoColors.Background)
            .border(if (selected) 2.dp else 1.dp, MonoColors.Ink)
            .inkClickable(
                onClick = { if (selectionMode) actions.toggleSelect(row.id) else actions.openRow(row.id) },
                onLongClick = { actions.toggleSelect(row.id) },
                showBar = false,
            ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(MonoColors.Tint), contentAlignment = Alignment.Center) {
            val img = row.firstImagePath
            if (img != null) MediaImage(img, null, Modifier.fillMaxSize(), maxEdge = 720)
            else Text(row.icon ?: "◆", style = MonoType.display.copy(fontSize = 44.sp, lineHeight = 48.sp, color = MonoColors.Ink))
            if (selected) MonoCheckbox(true, null, Modifier.align(Alignment.TopStart).padding(Space.s), size = 18.dp)
        }
        Hairline()
        Column(Modifier.padding(Space.m)) {
            Text(row.title.ifBlank { "Untitled" }, style = MonoType.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            props.forEach { p ->
                val cell = result.resolver.cell(row, p)
                if (!cell.isEmpty) {
                    Spacer(Modifier.height(4.dp))
                    CellDisplay(cell, p)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Calendar

private fun daysOf(cell: CellValue): List<LocalDate> {
    val d = cell as? CellValue.DateTime ?: return emptyList()
    val start = d.start.toLocalDate()
    val end = d.end?.toLocalDate()?.takeIf { !it.isBefore(start) } ?: start
    val n = java.time.temporal.ChronoUnit.DAYS.between(start, end).coerceAtMost(62)
    return (0..n).map { start.plusDays(it) }
}

@Composable
private fun CalendarView(snapshot: DatabaseSnapshot, view: DatabaseView, result: ViewResult, actions: ViewActions, modifier: Modifier) {
    val schema = snapshot.database.schema
    val dateProp = schema.property(view.config.calendarPropertyId)?.takeIf { it.type == PropertyType.DATE }
        ?: schema.properties.firstOrNull { it.type == PropertyType.DATE }
    val layout = LocalWindowLayout.current
    if (dateProp == null) {
        EmptyState("Calendar views place rows on a Date property.", "Add a property", actions.addProperty, modifier.padding(horizontal = layout.margin), icon = MonoIcons.Calendar)
        return
    }
    val byDay = remember(result, dateProp) {
        val map = HashMap<LocalDate, MutableList<RowInput>>()
        for (r in result.rows) daysOf(result.resolver.cell(r, dateProp)).forEach { map.getOrPut(it) { ArrayList() }.add(r) }
        map
    }
    val undated = remember(result, dateProp) { result.rows.filter { result.resolver.cell(it, dateProp) !is CellValue.DateTime } }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var showUndated by remember { mutableStateOf(false) }
    val wide = layout.mode != LayoutMode.Phone

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item("cal") {
            if (wide) MonthGrid(selected, { selected = it }, byDay, dateProp, actions)
            else WeekStrip(selected, { selected = it }, byDay)
        }
        item("day-h") {
            Column(Modifier.padding(horizontal = layout.margin).padding(top = Space.l)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    LabelText(selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), Modifier.weight(1f).padding(bottom = Space.s))
                    Text("${byDay[selected]?.size ?: 0} rows", Modifier.padding(bottom = Space.s), style = MonoType.caption.tnum())
                }
                SectionRule()
            }
        }
        val dayRows = byDay[selected].orEmpty()
        if (dayRows.isEmpty()) {
            item("day-empty") {
                EmptyState(
                    "Nothing on this day.", "New row on this day",
                    { actions.addRow(mapOf(dateProp.id to PropertyValue.DateValue(selected.toString()))) },
                    Modifier.padding(horizontal = layout.margin),
                )
            }
        }
        items(dayRows, key = { "d-" + it.id }) { row ->
            val cell = result.resolver.cell(row, dateProp) as? CellValue.DateTime
            Column(Modifier.padding(horizontal = layout.margin)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).inkClickable(onClick = { actions.openRow(row.id) }).padding(horizontal = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (cell != null && cell.includeTime) cell.start.format(DateTimeFormatter.ofPattern("HH:mm")) else "All day",
                        Modifier.width(64.dp), style = MonoType.caption.tnum(),
                    )
                    Text(row.title.ifBlank { "Untitled" }, Modifier.weight(1f), style = MonoType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Hairline()
            }
        }
        item("undated") {
            Column(Modifier.padding(horizontal = layout.margin).padding(top = Space.xl)) {
                Text("${undated.size} row${if (undated.size == 1) "" else "s"} without a ${dateProp.name.lowercase()}.", style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                if (undated.isNotEmpty()) {
                    app.monoworkspace.ui.components.MonoButton(if (showUndated) "Hide undated" else "Show undated", { showUndated = !showUndated }, Modifier.padding(top = Space.s), height = 40.dp)
                }
            }
        }
        if (showUndated) {
            items(undated, key = { "u-" + it.id }) { row ->
                Column(Modifier.padding(horizontal = layout.margin)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { actions.openRow(row.id) }).padding(horizontal = Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(row.title.ifBlank { "Untitled" }, style = MonoType.body) }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit, byDay: Map<LocalDate, List<RowInput>>) {
    val margin = LocalWindowLayout.current.margin
    val weekStart = selected.with(DayOfWeek.MONDAY)
    Column {
        Row(Modifier.padding(start = margin, end = Space.xs, top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(YearMonth.from(selected).format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MonoType.h2)
            MonoIconButton(MonoIcons.ChevronLeft, "Previous week", { onSelect(selected.minusWeeks(1)) })
            MonoIconButton(MonoIcons.Clock, "Today", { onSelect(LocalDate.now()) })
            MonoIconButton(MonoIcons.ChevronRight, "Next week", { onSelect(selected.plusWeeks(1)) })
        }
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            (0L..6L).forEach { i ->
                val day = weekStart.plusDays(i)
                val on = day == selected
                val bg by animateColorAsState(if (on) MonoColors.Ink else MonoColors.Background, monoTween(Motion.FAST), label = "day")
                Column(
                    Modifier.weight(1f).height(76.dp).background(bg).inkClickable(onClick = { onSelect(day) }, showBar = false),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val fg = if (on) MonoColors.White else if (i >= 5) MonoColors.Secondary else MonoColors.Ink
                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), style = MonoType.label.copy(color = fg))
                    Text("${day.dayOfMonth}", style = MonoType.body.tnum().copy(color = fg, fontWeight = if (day == LocalDate.now()) FontWeight.Bold else FontWeight.Normal))
                    Box(Modifier.padding(top = 4.dp).size(4.dp).background(if (byDay[day].isNullOrEmpty()) bg else fg))
                }
            }
        }
        SectionRule()
    }
}

@Composable
private fun MonthGrid(selected: LocalDate, onSelect: (LocalDate) -> Unit, byDay: Map<LocalDate, List<RowInput>>, dateProp: PropertyDef, actions: ViewActions) {
    val margin = LocalWindowLayout.current.margin
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val cellBounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    var drag by remember { mutableStateOf<RowInput?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val hoverDay = if (drag != null) cellBounds.entries.firstOrNull { it.value.contains(pointer) }?.key else null
    Column(Modifier.padding(horizontal = margin)) {
        Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MonoType.h2)
            MonoIconButton(MonoIcons.ChevronLeft, "Previous month", { month = month.minusMonths(1) })
            MonoIconButton(MonoIcons.Clock, "Today", { month = YearMonth.now(); onSelect(LocalDate.now()) })
            MonoIconButton(MonoIcons.ChevronRight, "Next month", { month = month.plusMonths(1) })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            DayOfWeek.entries.forEach { d ->
                LabelText(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f), color = MonoColors.Secondary)
            }
        }
        SectionRule()
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val weeks = (lead + days + 6) / 7
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().height(112.dp)) {
                for (dow in 0 until 7) {
                    val n = w * 7 + dow - lead + 1
                    val date = if (n in 1..days) month.atDay(n) else null
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, MonoColors.Hairline)
                            .background(
                                when {
                                    date != null && date == hoverDay -> MonoColors.Tint
                                    date == selected -> MonoColors.Tint
                                    else -> MonoColors.Background
                                },
                            )
                            .then(if (date != null) Modifier.onGloballyPositioned { cellBounds[date] = it.boundsInRoot() } else Modifier)
                            .then(if (date != null) Modifier.inkClickable(onClick = { onSelect(date) }, showBar = false) else Modifier)
                            .padding(4.dp),
                    ) {
                        if (date != null) {
                            Column {
                                Text(
                                    "$n",
                                    style = MonoType.caption.tnum().copy(
                                        color = if (date == LocalDate.now()) MonoColors.White else MonoColors.Ink,
                                        fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    modifier = Modifier.background(if (date == LocalDate.now()) MonoColors.Ink else MonoColors.Background.copy(alpha = 0f)).padding(horizontal = 4.dp),
                                )
                                val rows = byDay[date].orEmpty()
                                rows.take(3).forEach { r ->
                                    var bounds by remember(r.id, date) { mutableStateOf(Rect.Zero) }
                                    Text(
                                        r.title.ifBlank { "Untitled" },
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp)
                                            .border(1.dp, MonoColors.Ink)
                                            .background(if (drag?.id == r.id) MonoColors.Tint else MonoColors.Background)
                                            .onGloballyPositioned { bounds = it.boundsInRoot() }
                                            .inkClickable(onClick = { actions.openRow(r.id) }, showBar = false)
                                            .pointerInput(r.id, date) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { off -> pointer = bounds.topLeft + off; drag = r },
                                                    onDrag = { ch, amt -> ch.consume(); pointer += amt },
                                                    onDragEnd = {
                                                        val target = cellBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                                        val moving = drag
                                                        drag = null
                                                        if (moving != null && target != null && target != date) actions.reschedule(moving.id, dateProp, target)
                                                    },
                                                    onDragCancel = { drag = null },
                                                )
                                            }
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MonoType.caption.copy(color = MonoColors.Ink),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (rows.size > 3) Text("+${rows.size - 3}", style = MonoType.caption.tnum())
                            }
                        }
                    }
                }
            }
        }
        if (drag != null) Text("Drop on a day to reschedule", Modifier.padding(top = Space.s), style = MonoType.caption)
    }
}
