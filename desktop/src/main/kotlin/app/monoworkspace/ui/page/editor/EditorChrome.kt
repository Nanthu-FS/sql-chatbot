package app.monoworkspace.ui.page.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.monoworkspace.model.Mark
import app.monoworkspace.model.RichText
import app.monoworkspace.ui.components.DeleteButton
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.ToolbarDivider
import app.monoworkspace.ui.page.EditorState
import app.monoworkspace.ui.page.PageViewModel
import app.monoworkspace.ui.page.SlashAction
import app.monoworkspace.ui.page.SlashState
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween
import app.monoworkspace.ui.theme.tnum

private object BelowBlock : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height) below else (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** Searchable block menu anchored under the block where "/" was typed. */
@Composable
fun SlashMenu(slash: SlashState, vm: PageViewModel) {
    val items = remember(slash.query) { SlashAction.filter(slash.query) }
    val listState = rememberLazyListState()
    LaunchedEffect(slash.selected) { if (items.isNotEmpty()) listState.animateScrollToItem((slash.selected - 2).coerceAtLeast(0)) }
    Popup(popupPositionProvider = BelowBlock, onDismissRequest = vm::slashClose, properties = PopupProperties(focusable = false)) {
        Column(Modifier.width(300.dp).background(MonoColors.Background).border(1.dp, MonoColors.Ink)) {
            if (items.isEmpty()) {
                Text("No blocks match “${slash.query}”", Modifier.padding(Space.m), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp), state = listState) {
                    itemsIndexed(items, key = { _, a -> a.name }) { i, a ->
                        if (i == 0 || items[i - 1].group != a.group) {
                            Column {
                                if (i > 0) SectionRule(color = MonoColors.Hairline)
                                LabelText(a.group.label, Modifier.padding(horizontal = Space.m, vertical = Space.s), color = MonoColors.Secondary)
                            }
                        }
                        SlashRow(a, i == slash.selected) { vm.applySlash(a) }
                    }
                }
            }
            SectionRule(color = MonoColors.Hairline)
            Text("↑↓ navigate · ↵ insert · esc close", Modifier.padding(horizontal = Space.m, vertical = Space.s), style = MonoType.caption)
        }
    }
}

@Composable
private fun SlashRow(a: SlashAction, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(if (selected) MonoColors.Ink else if (hovered) MonoColors.Tint else MonoColors.Background, monoTween(Motion.FAST), label = "slashBg")
    val fg = if (selected) MonoColors.OnInk else MonoColors.Ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(bg)
            .clickable(interaction, null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).border(1.dp, fg), contentAlignment = Alignment.Center) {
            Icon(a.icon, null, Modifier.size(20.dp), tint = fg)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(a.label, style = MonoType.bodySmall.copy(color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
            Text(a.description, style = MonoType.caption.copy(color = if (selected) MonoColors.Tertiary else MonoColors.Secondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Formatting bar above the keyboard while a text block is focused. */
@Composable
fun FormatToolbar(
    state: EditorState,
    vm: PageViewModel,
    onTurnInto: () -> Unit,
    onLink: () -> Unit,
    onMention: () -> Unit,
    onMore: () -> Unit,
) {
    val id = state.focusedBlockId ?: return
    val block = state.blocks[id] ?: return
    val (start, end) = state.textSelection
    val hasRange = end > start
    fun active(mark: Mark) = if (hasRange) RichText.hasMark(block.content, start, end, mark) else mark in RichText.marksAt(block.content, start)
    Column(Modifier.fillMaxWidth().background(MonoColors.Background)) {
        SectionRule()
        Row(
            Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Aa",
                Modifier
                    .clickable(role = Role.Button, onClick = onTurnInto)
                    .padding(horizontal = Space.m, vertical = Space.m)
                    .semantics { contentDescription = "Turn into" },
                style = MonoType.label,
            )
            ToolbarDivider()
            MarkButton("B", "Bold", active(Mark.BOLD), hasRange, MonoType.body.copy(fontWeight = FontWeight.Bold)) { vm.toggleMark(id, start, end, Mark.BOLD) }
            MarkButton("I", "Italic", active(Mark.ITALIC), hasRange, MonoType.body.copy(fontStyle = FontStyle.Italic)) { vm.toggleMark(id, start, end, Mark.ITALIC) }
            MarkButton("U", "Underline", active(Mark.UNDERLINE), hasRange, MonoType.body.copy(textDecoration = TextDecoration.Underline)) { vm.toggleMark(id, start, end, Mark.UNDERLINE) }
            MarkButton("S", "Strikethrough", active(Mark.STRIKE), hasRange, MonoType.body.copy(textDecoration = TextDecoration.LineThrough)) { vm.toggleMark(id, start, end, Mark.STRIKE) }
            MarkButton("</>", "Inline code", active(Mark.CODE), hasRange, MonoType.bodySmall.copy(fontFamily = FontFamily.Monospace)) { vm.toggleMark(id, start, end, Mark.CODE) }
            MonoIconButton(MonoIcons.Link, "Link", onLink, enabled = hasRange, selected = active(Mark.LINK), iconSize = 20.dp)
            MonoIconButton(MonoIcons.At, "Mention", onMention, iconSize = 20.dp)
            ToolbarDivider()
            MonoIconButton(MonoIcons.Indent, "Indent", { vm.indent(id) }, iconSize = 20.dp)
            MonoIconButton(MonoIcons.Outdent, "Outdent", { vm.outdent(id) }, enabled = block.parentBlockId != null, iconSize = 20.dp)
            MonoIconButton(MonoIcons.Plus, "Insert block below", { vm.insertBelow(id) }, iconSize = 20.dp)
            MonoIconButton(MonoIcons.More, "Block actions", onMore, iconSize = 20.dp)
        }
    }
}

@Composable
private fun MarkButton(label: String, description: String, active: Boolean, enabled: Boolean, style: androidx.compose.ui.text.TextStyle, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) MonoColors.Ink else MonoColors.Background, monoTween(Motion.FAST), label = "mark")
    Box(
        Modifier
            .size(44.dp)
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style.copy(color = if (active) MonoColors.OnInk else if (enabled) MonoColors.Ink else MonoColors.Tertiary))
    }
}

/** Actions for the blocks picked in selection mode. */
@Composable
fun SelectionBar(state: EditorState, vm: PageViewModel, onTurnInto: () -> Unit) {
    val ids = state.selection
    Column(Modifier.fillMaxWidth().background(MonoColors.Background)) {
        SectionRule()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${ids.size} selected", Modifier.padding(horizontal = Space.s), style = MonoType.label.tnum())
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                MonoIconButton(MonoIcons.Swap, "Turn into", onTurnInto, iconSize = 20.dp)
                MonoIconButton(MonoIcons.Copy, "Duplicate", { vm.duplicateBlocks(ids) }, iconSize = 20.dp)
                MonoIconButton(MonoIcons.ChevronUp, "Move up", { vm.moveStep(ids, true) }, iconSize = 20.dp)
                MonoIconButton(MonoIcons.ChevronDown, "Move down", { vm.moveStep(ids, false) }, iconSize = 20.dp)
                MonoIconButton(MonoIcons.X, "Cancel selection", vm::clearSelection, iconSize = 20.dp)
            }
            DeleteButton({ vm.deleteBlocks(ids) }, size = 44.dp, expandedWidth = 132.dp)
        }
    }
}
