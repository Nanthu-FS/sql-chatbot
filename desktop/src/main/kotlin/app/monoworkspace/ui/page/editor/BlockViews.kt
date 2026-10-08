package app.monoworkspace.ui.page.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyListState
import app.monoworkspace.engine.FlatBlock
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.CalloutGlyphs
import app.monoworkspace.model.CodeLanguages
import app.monoworkspace.model.RichText
import app.monoworkspace.ui.common.Formats
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MediaImage
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoCheckbox
import app.monoworkspace.ui.components.MonoChip
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.page.EditorState
import app.monoworkspace.ui.page.PageViewModel
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween
import app.monoworkspace.ui.theme.tnum

/** Callbacks the screen provides for things that need Activity APIs or dialogs. */
data class EditorUi(
    val pickImage: (String) -> Unit,
    val pickFile: (String) -> Unit,
    val editLink: (String) -> Unit,
    val openFile: (Block) -> Unit,
    val requestInlineLink: (blockId: String, start: Int, end: Int) -> Unit,
    val navigate: (String) -> Unit,
    val scrollTo: (String) -> Unit,
)

/** Drag-to-reorder state for the editor's lazy list. */
@Stable
class BlockDragState(private val list: LazyListState, private val blockIds: () -> Set<String>, private val onDrop: (String, String?) -> Unit) {
    var dragging by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var target by mutableStateOf<String?>(null)
        private set
    var targetEnd by mutableStateOf(false)
        private set

    fun start(id: String) {
        dragging = id
        offset = 0f
        compute()
    }

    fun dragBy(dy: Float) {
        offset += dy
        compute()
    }

    fun compensateScroll(dy: Float) {
        offset += dy
        compute()
    }

    /** Pointer position inside the viewport, or null while idle. */
    fun pointerY(): Float? {
        val id = dragging ?: return null
        val me = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return null
        return me.offset + me.size / 2f + offset
    }

    private fun compute() {
        val center = pointerY() ?: return
        val ids = blockIds()
        val candidates = list.layoutInfo.visibleItemsInfo.filter { it.key in ids && it.key != dragging }
        val next = candidates.firstOrNull { it.offset + it.size / 2f > center }
        target = next?.key as? String
        targetEnd = next == null && candidates.isNotEmpty()
    }

    fun end() {
        val id = dragging ?: return
        val t = target
        val atEnd = targetEnd
        reset()
        if (t != null || atEnd) onDrop(id, t)
    }

    fun reset() {
        dragging = null
        offset = 0f
        target = null
        targetEnd = false
    }
}

@Composable
fun styleForBlock(b: Block): TextStyle = when (b.type) {
    BlockType.H1 -> MonoType.h1
    BlockType.H2 -> MonoType.h2
    BlockType.H3 -> MonoType.h3
    BlockType.CODE -> MonoType.code
    BlockType.TODO -> if (b.props.checked) MonoType.body.copy(color = MonoColors.Secondary, textDecoration = TextDecoration.LineThrough) else MonoType.body
    else -> MonoType.body
}

private fun topPadding(type: BlockType): Dp = when (type) {
    BlockType.H1 -> 20.dp
    BlockType.H2 -> 16.dp
    BlockType.H3 -> 12.dp
    else -> 2.dp
}

/** One block with its gutter (grip / +), selection and drop indicator. */
@Composable
fun BlockRow(
    item: FlatBlock,
    state: EditorState,
    vm: PageViewModel,
    ui: EditorUi,
    drag: BlockDragState?,
    modifier: Modifier = Modifier,
) {
    val b = item.block
    val layout = LocalWindowLayout.current
    val wide = layout.usesSidebar
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val selected = b.id in state.selection
    val focused = state.focusedBlockId == b.id || state.activeBlockId == b.id
    val isDragging = drag?.dragging == b.id
    val highlighted = state.highlightBlockId == b.id
    val tint by animateColorAsState(
        when {
            selected -> MonoColors.Tint
            isDragging -> MonoColors.Background
            else -> MonoColors.Background.copy(alpha = 0f)
        },
        monoTween(Motion.FAST), label = "blockTint",
    )
    val bar by animateFloatAsState(if (selected) 1f else 0f, monoTween(Motion.MEDIUM), label = "selBar")
    val highlight by animateFloatAsState(if (highlighted) 1f else 0f, monoTween(Motion.SLOW), label = "hl")
    val dropHere = drag != null && drag.dragging != null && drag.target == b.id
    val dropAfter = drag != null && drag.dragging != null && drag.targetEnd && state.flat.lastOrNull()?.block?.id == b.id
    val showGrip = hovered || isDragging || state.selectionMode
    val gutter = if (wide) 48.dp else 24.dp
    val onDragState by rememberUpdatedState(drag)

    val inkLine = MonoColors.Ink
    Box(
        modifier
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = drag?.offset ?: 0f
                    alpha = 0.92f
                }
            }
            .hoverable(interaction)
            .drawBehind {
                drawRect(tint)
                if (bar > 0f) drawRect(inkLine, Offset.Zero, Size(2.dp.toPx(), size.height * bar))
                val rule = 2.dp.toPx()
                if (dropHere) drawRect(inkLine, Offset(0f, 0f), Size(size.width, rule))
                if (dropAfter) drawRect(inkLine, Offset(0f, size.height - rule), Size(size.width, rule))
                if (highlight > 0f) {
                    val w = 1.dp.toPx()
                    val c = inkLine.copy(alpha = highlight)
                    drawRect(c, Offset.Zero, Size(size.width, w))
                    drawRect(c, Offset(0f, size.height - w), Size(size.width, w))
                    drawRect(c, Offset.Zero, Size(w, size.height))
                    drawRect(c, Offset(size.width - w, 0f), Size(w, size.height))
                }
            }
            .then(if (isDragging) Modifier.border(1.dp, MonoColors.Ink) else Modifier),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = topPadding(b.type)), verticalAlignment = Alignment.Top) {
            // Gutter: drag handle and insert button.
            Row(
                Modifier
                    .width(gutter)
                    .heightIn(min = 30.dp)
                    .pointerInput(b.id) {
                        detectDragGestures(
                            onDragStart = { onDragState?.start(b.id) },
                            onDrag = { change, amount ->
                                change.consume()
                                onDragState?.dragBy(amount.y)
                            },
                            onDragEnd = { onDragState?.end() },
                            onDragCancel = { onDragState?.reset() },
                        )
                    },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (wide && (focused || hovered)) {
                    Icon(
                        MonoIcons.Plus, "Insert block below",
                        Modifier.size(20.dp).clickable { vm.insertBelow(b.id) },
                        tint = MonoColors.Secondary,
                    )
                }
                val icon = when {
                    state.selectionMode && selected -> MonoIcons.Check
                    showGrip || (wide && focused) -> MonoIcons.Grip
                    focused -> MonoIcons.Plus
                    else -> null
                }
                Box(
                    Modifier
                        .size(width = 24.dp, height = 30.dp)
                        .combinedClickable(
                            onClick = {
                                when {
                                    state.selectionMode -> vm.toggleSelected(b.id)
                                    icon == MonoIcons.Plus -> vm.insertBelow(b.id)
                                    else -> vm.startSelection(b.id)
                                }
                            },
                            onLongClick = { vm.startSelection(b.id) },
                        )
                        .semantics { contentDescription = if (icon == MonoIcons.Plus) "Insert block below" else "Drag to reorder, tap to select" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (icon == MonoIcons.Grip) MonoColors.Secondary else MonoColors.Ink)
                }
            }
            Box(Modifier.weight(1f).padding(start = (24 * item.depth).dp)) {
                BlockContent(b, item, state, vm, ui)
            }
        }
        // In selection mode a tap anywhere on the block toggles it.
        if (state.selectionMode) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(start = gutter)
                    .clickable(remember { MutableInteractionSource() }, null) { vm.toggleSelected(b.id) },
            )
        }
    }
}

@Composable
fun BlockContent(b: Block, item: FlatBlock?, state: EditorState, vm: PageViewModel, ui: EditorUi) {
    val version = state.versions[b.id] ?: 0
    val slashOpen = state.slash?.blockId == b.id
    val field: @Composable (Modifier) -> Unit = { m ->
        BlockTextField(
            b, version, state.focus, vm, styleForBlock(b), m, slashOpen,
            onLinkRequest = { s, e -> ui.requestInlineLink(b.id, s, e) },
        )
    }
    Column {
        when (b.type) {
            BlockType.TEXT, BlockType.H1, BlockType.H2, BlockType.H3 -> field(Modifier.padding(vertical = 3.dp))
            BlockType.BULLET -> Row {
                val glyph = when ((item?.depth ?: 0) % 3) { 0 -> "•"; 1 -> "◦"; else -> "▪" }
                Text(glyph, Modifier.width(22.dp).padding(top = 3.dp), style = MonoType.body)
                field(Modifier.weight(1f).padding(vertical = 3.dp))
            }
            BlockType.NUMBERED -> Row {
                Text("${item?.number ?: 1}.", Modifier.widthIn(min = 26.dp).padding(top = 3.dp, end = 4.dp), style = MonoType.body.tnum())
                field(Modifier.weight(1f).padding(vertical = 3.dp))
            }
            BlockType.TODO -> Row(verticalAlignment = Alignment.Top) {
                MonoCheckbox(b.props.checked, { vm.setChecked(b.id, it) }, Modifier.padding(end = 2.dp), label = "Done")
                field(Modifier.weight(1f).padding(top = 13.dp, bottom = 3.dp))
            }
            BlockType.TOGGLE -> Row(verticalAlignment = Alignment.Top) {
                val rotation by animateFloatAsState(if (b.props.collapsed) 0f else 90f, monoTween(Motion.FAST), label = "toggle")
                Box(
                    Modifier.size(28.dp).clickable { vm.toggleCollapsed(b.id) }.semantics { contentDescription = if (b.props.collapsed) "Expand toggle" else "Collapse toggle" },
                    contentAlignment = Alignment.Center,
                ) { Icon(MonoIcons.ChevronRight, null, Modifier.size(18.dp).rotate(rotation)) }
                field(Modifier.weight(1f).padding(vertical = 3.dp))
            }
            BlockType.QUOTE -> Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(MonoColors.Ink))
                Spacer(Modifier.width(14.dp))
                field(Modifier.weight(1f))
            }
            BlockType.CALLOUT -> CalloutBlock(b, vm, field)
            BlockType.DIVIDER -> Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.setActive(b.id) }
                    .padding(vertical = Space.l)
                    .semantics { contentDescription = "Divider" },
            ) { SectionRule(color = if (state.activeBlockId == b.id) MonoColors.Ink else MonoColors.Rule, thickness = if (state.activeBlockId == b.id) 2.dp else 1.dp) }
            BlockType.CODE -> CodeBlock(b, vm, field)
            BlockType.IMAGE -> ImageBlock(b, state.activeBlockId == b.id, vm, ui)
            BlockType.FILE -> FileBlock(b, vm, ui)
            BlockType.LINK_PREVIEW -> LinkPreviewBlock(b, ui)
            BlockType.TABLE -> TableBlock(b.props.table ?: app.monoworkspace.model.TableData(), { vm.updateTable(b.id, it) }, Modifier.padding(vertical = Space.s))
            BlockType.COLUMNS -> ColumnsBlock(b, state, vm, ui)
            BlockType.COLUMN -> Unit
            BlockType.CHILD_PAGE -> ChildPageBlock(b, state, ui)
            BlockType.CHILD_DATABASE -> EmbedBlock(b, state, ui)
            BlockType.TOC -> TocBlock(state, ui)
        }
        if (slashOpen) SlashMenu(state.slash!!, vm)
    }
}

@Composable
private fun CalloutBlock(b: Block, vm: PageViewModel, field: @Composable (Modifier) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.s).border(1.dp, MonoColors.Ink).padding(Space.l),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            Text(
                b.props.glyph ?: "◆",
                Modifier.clickable { menu = true }.padding(end = Space.m).semantics { contentDescription = "Callout glyph, tap to change" },
                style = MonoType.body.copy(fontWeight = FontWeight.Bold),
            )
            MonoMenu(menu, { menu = false }, CalloutGlyphs.all.map { g -> MenuItem(g, checked = g == b.props.glyph) { vm.setGlyph(b.id, g) } }, width = 120.dp)
        }
        field(Modifier.weight(1f))
    }
}

@Composable
private fun CodeBlock(b: Block, vm: PageViewModel, field: @Composable (Modifier) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s).border(1.dp, MonoColors.Hairline)) {
        Row(Modifier.fillMaxWidth().padding(start = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LabelText(b.props.language ?: "plain", Modifier.clickable { menu = true }.padding(vertical = Space.s), color = MonoColors.Secondary)
                MonoMenu(menu, { menu = false }, CodeLanguages.all.map { l -> MenuItem(l.uppercase(), checked = l == b.props.language) { vm.setLanguage(b.id, l) } }, width = 180.dp)
            }
            MonoIconButton(MonoIcons.Copy, "Copy code", { clipboard.setText(AnnotatedString(b.text)) }, iconSize = 20.dp)
        }
        Hairline()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val min = maxWidth
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                field(Modifier.widthIn(min = min).padding(Space.m))
            }
        }
    }
}

@Composable
private fun ImageBlock(b: Block, active: Boolean, vm: PageViewModel, ui: EditorUi) {
    val path = b.props.mediaPath
    if (path == null) {
        Placeholder(MonoIcons.Image, "Add an image", "Pick from your device · up to 20 MB") { ui.pickImage(b.id) }
        return
    }
    val ratio = if ((b.props.imageWidth ?: 0) > 0 && (b.props.imageHeight ?: 0) > 0) b.props.imageWidth!!.toFloat() / b.props.imageHeight!! else 1.5f
    var caption by remember(b.id, b.props.caption) { mutableStateOf(RichText.plain(b.props.caption)) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        MediaImage(
            path, RichText.plain(b.props.caption).ifBlank { "Image" },
            Modifier
                .fillMaxWidth(b.props.widthFraction.coerceIn(0.25f, 1f))
                .aspectRatio(ratio.coerceIn(0.3f, 4f))
                .clickable { vm.setActive(if (active) null else b.id) }
                .then(if (active) Modifier.border(2.dp, MonoColors.Ink) else Modifier),
            contentScale = ContentScale.Fit,
        )
        BasicTextField(
            value = caption,
            onValueChange = { caption = it.replace("\n", " "); vm.setCaption(b.id, caption) },
            modifier = Modifier.fillMaxWidth().padding(top = Space.xs),
            textStyle = MonoType.caption,
            cursorBrush = SolidColor(MonoColors.Ink),
            decorationBox = { inner ->
                Box {
                    if (caption.isEmpty()) Text("Add a caption", style = MonoType.caption.copy(color = MonoColors.Tertiary))
                    inner()
                }
            },
        )
        if (active) {
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf(0.5f to "50%", 0.75f to "75%", 1f to "100%").forEach { (f, l) ->
                    MonoChip(l, b.props.widthFraction == f, { vm.setImageWidth(b.id, f) })
                }
                MonoChip("Replace", false, { ui.pickImage(b.id) }, leading = MonoIcons.Import)
            }
        }
    }
}

@Composable
private fun FileBlock(b: Block, vm: PageViewModel, ui: EditorUi) {
    if (b.props.mediaPath == null) {
        Placeholder(MonoIcons.File, "Add a file", "Copied into the app · up to 20 MB") { ui.pickFile(b.id) }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s)
            .border(1.dp, MonoColors.Ink)
            .inkClickable(onClick = { ui.openFile(b) }, onLongClick = { vm.startSelection(b.id) })
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoIcon(MonoIcons.File, null)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(b.props.fileName ?: "File", style = MonoType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Formats.bytes(b.props.fileSize ?: 0) + (b.props.mimeType?.let { " · $it" } ?: ""), style = MonoType.caption.tnum())
        }
        LabelText("Open", color = MonoColors.Ink)
    }
}

@Composable
private fun LinkPreviewBlock(b: Block, ui: EditorUi) {
    val url = b.props.url
    if (url.isNullOrBlank()) {
        Placeholder(MonoIcons.Link, "Add a link", "Type a URL, title and description") { ui.editLink(b.id) }
        return
    }
    val uri = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s)
            .border(1.dp, MonoColors.Ink)
            .inkClickable(onClick = { runCatching { uri.openUri(if (url.contains("://")) url else "https://$url") } }, onLongClick = { ui.editLink(b.id) })
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(b.props.title ?: url, style = MonoType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            b.props.description?.let { Text(it, style = MonoType.bodySmall.copy(color = MonoColors.Secondary), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Text(url, style = MonoType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.m))
        MonoIconButton(MonoIcons.Pen, "Edit link", { ui.editLink(b.id) }, tint = MonoColors.Secondary, iconSize = 20.dp)
    }
}

@Composable
private fun Placeholder(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, caption: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s)
            .background(MonoColors.Tint)
            .inkClickable(onClick = onClick)
            .padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoIcon(icon, null, tint = MonoColors.Secondary)
        Spacer(Modifier.width(Space.m))
        Column {
            Text(title, style = MonoType.body)
            Text(caption, style = MonoType.caption)
        }
    }
}

@Composable
private fun ColumnsBlock(b: Block, state: EditorState, vm: PageViewModel, ui: EditorUi) {
    val columns = state.childMap[b.id].orEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        val stacked = maxWidth < 600.dp
        val content: @Composable (Block, Modifier) -> Unit = { col, m ->
            Column(m) {
                val kids = state.childMap[col.id].orEmpty()
                kids.forEach { child -> NestedBlock(child, state, vm, ui) }
                Text(
                    "+ Add text",
                    Modifier.fillMaxWidth().clickable { vm.appendChild(col.id) }.padding(vertical = Space.s),
                    style = MonoType.caption.copy(color = MonoColors.Tertiary),
                )
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                columns.forEachIndexed { i, col ->
                    content(col, Modifier.fillMaxWidth())
                    if (i < columns.lastIndex) Hairline()
                }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                columns.forEachIndexed { i, col ->
                    content(col, Modifier.weight(1f))
                    if (i < columns.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(MonoColors.Hairline))
                }
            }
        }
    }
}

/** Blocks inside a column: same renderers, no drag handle, children nested inline. */
@Composable
private fun NestedBlock(b: Block, state: EditorState, vm: PageViewModel, ui: EditorUi, depth: Int = 0) {
    Column(Modifier.padding(start = (20 * depth).dp)) {
        val numbered = if (b.type == BlockType.NUMBERED) {
            val sibs = state.childMap[b.parentBlockId].orEmpty()
            var n = 0
            for (s in sibs) {
                n = if (s.type == BlockType.NUMBERED) n + 1 else 0
                if (s.id == b.id) break
            }
            n
        } else null
        BlockContent(b, FlatBlock(b, depth, numbered, false), state, vm, ui)
        if (!(b.type == BlockType.TOGGLE && b.props.collapsed)) {
            state.childMap[b.id].orEmpty().forEach { NestedBlock(it, state, vm, ui, depth + 1) }
        }
    }
}

@Composable
private fun ChildPageBlock(b: Block, state: EditorState, ui: EditorUi) {
    val page = b.props.pageId?.let { state.pages[it] }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .inkClickable(onClick = { b.props.pageId?.let(ui.navigate) })
                .padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageGlyph(page?.icon, page?.isDatabase ?: false)
            Spacer(Modifier.width(Space.m))
            Text(
                page?.displayTitle ?: "Deleted page",
                Modifier.weight(1f),
                style = MonoType.body.copy(
                    textDecoration = TextDecoration.Underline,
                    color = if (page == null) MonoColors.Tertiary else MonoColors.Ink,
                ),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            MonoIcon(MonoIcons.ChevronRight, null, tint = MonoColors.Tertiary, size = 20.dp)
        }
    }
}

@Composable
private fun EmbedBlock(b: Block, state: EditorState, ui: EditorUi) {
    val id = b.props.pageId
    val e = id?.let { state.embeds[it] }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s).border(1.dp, MonoColors.Ink)) {
        Row(Modifier.fillMaxWidth().padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
            PageGlyph(e?.icon, true)
            Spacer(Modifier.width(Space.s))
            Text(e?.title ?: "Database", Modifier.weight(1f), style = MonoType.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e?.let { "${it.viewName} · ${it.total} rows" } ?: "", style = MonoType.caption.tnum())
        }
        SectionRule()
        if (e != null) {
            if (e.rows.isEmpty()) Text("No rows yet.", Modifier.padding(Space.m), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
            e.rows.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).inkClickable(onClick = { ui.navigate(r.id) }).padding(horizontal = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.title, Modifier.weight(1f), style = MonoType.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.meta.isNotEmpty()) Text(r.meta, style = MonoType.caption, maxLines = 1)
                }
                Hairline()
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { id?.let(ui.navigate) }).padding(horizontal = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelText("Open database", Modifier.weight(1f))
            MonoIcon(MonoIcons.ChevronRight, null, size = 20.dp)
        }
    }
}

@Composable
private fun TocBlock(state: EditorState, ui: EditorUi) {
    val headings = remember(state.blocks) { app.monoworkspace.engine.BlockTree.headings(state.blocks.values.toList()) }
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s).semantics { contentDescription = "Table of contents" }) {
        if (headings.isEmpty()) Text("Add headings to build a table of contents.", style = MonoType.bodySmall.copy(color = MonoColors.Tertiary))
        headings.forEach { h ->
            val level = when (h.type) { BlockType.H1 -> 0; BlockType.H2 -> 1; else -> 2 }
            Text(
                h.text.ifBlank { "Untitled heading" },
                Modifier
                    .fillMaxWidth()
                    .clickable { ui.scrollTo(h.id) }
                    .padding(start = (16 * level).dp, top = 6.dp, bottom = 6.dp),
                style = MonoType.bodySmall.copy(
                    color = if (level == 0) MonoColors.Ink else MonoColors.Secondary,
                    textDecoration = TextDecoration.Underline,
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
