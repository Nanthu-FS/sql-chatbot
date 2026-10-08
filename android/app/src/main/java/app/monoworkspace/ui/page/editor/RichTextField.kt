package app.monoworkspace.ui.page.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Mark
import app.monoworkspace.model.RichText
import app.monoworkspace.model.Span
import app.monoworkspace.model.SpanKind
import app.monoworkspace.ui.page.FocusTarget
import app.monoworkspace.ui.page.PageViewModel
import app.monoworkspace.ui.theme.MonoColors

/** Visual style for one span of inline content. */
fun styleFor(span: Span): SpanStyle {
    val decorations = buildList {
        if (Mark.UNDERLINE in span.marks || Mark.LINK in span.marks || span.kind != SpanKind.TEXT) add(TextDecoration.Underline)
        if (Mark.STRIKE in span.marks) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = when {
            Mark.BOLD in span.marks -> FontWeight.Bold
            span.kind == SpanKind.DATE_MENTION -> FontWeight.SemiBold
            else -> null
        },
        fontStyle = if (Mark.ITALIC in span.marks) FontStyle.Italic else null,
        fontFamily = if (Mark.CODE in span.marks) FontFamily.Monospace else null,
        fontSize = if (Mark.CODE in span.marks) 0.9.em else androidx.compose.ui.unit.TextUnit.Unspecified,
        background = if (Mark.CODE in span.marks) MonoColors.Tint else androidx.compose.ui.graphics.Color.Unspecified,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
    )
}

fun styledText(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    for (s in spans) withStyle(styleFor(s)) { append(s.text) }
}

/** Renders marks over the plain text in the field. Offsets map 1:1. */
class SpanTransformation(private val spans: List<Span>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (RichText.plain(spans) != text.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(styledText(spans), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is SpanTransformation && other.spans == spans
    override fun hashCode(): Int = spans.hashCode()
}

fun placeholderFor(type: BlockType, focused: Boolean): String? = when (type) {
    BlockType.H1 -> "Heading 1"
    BlockType.H2 -> "Heading 2"
    BlockType.H3 -> "Heading 3"
    BlockType.TODO -> if (focused) "To-do" else null
    BlockType.BULLET, BlockType.NUMBERED -> if (focused) "List" else null
    BlockType.TOGGLE -> "Toggle"
    BlockType.QUOTE -> if (focused) "Quote" else null
    BlockType.CALLOUT -> if (focused) "Callout" else null
    BlockType.CODE -> if (focused) "Code" else null
    BlockType.TEXT -> if (focused) "Type '/' for commands" else null
    else -> null
}

/**
 * The editable text of one block. Owns its caret; text edits go to the VM,
 * which either accepts them (field keeps its value) or rewrites the block
 * (version bump re-creates the field state from the VM's content).
 */
@Composable
fun BlockTextField(
    block: Block,
    version: Int,
    focus: FocusTarget?,
    vm: PageViewModel,
    style: TextStyle,
    modifier: Modifier = Modifier,
    slashOpen: Boolean = false,
    onLinkRequest: (start: Int, end: Int) -> Unit = { _, _ -> },
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var tfv by rememberSaveable(block.id, version, stateSaver = TextFieldValue.Saver) {
        val caret = if (focus?.blockId == block.id) focus.caret.coerceIn(0, block.text.length) else block.text.length
        mutableStateOf(TextFieldValue(block.text, TextRange(caret)))
    }
    // Keep the field in step if the VM's text changed without a version bump.
    if (!focused && tfv.text != block.text) tfv = TextFieldValue(block.text, TextRange(block.text.length))

    LaunchedEffect(focus?.nonce) {
        if (focus != null && focus.blockId == block.id) {
            tfv = tfv.copy(selection = TextRange(focus.caret.coerceIn(0, tfv.text.length)))
            runCatching { requester.requestFocus() }
        }
    }

    BasicTextField(
        value = tfv,
        onValueChange = { new ->
            val handled = vm.onTextChange(block.id, new.text, new.selection.start, new.selection.end)
            if (!handled) tfv = new
        },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                vm.onFocusChanged(block.id, it.isFocused)
                if (it.isFocused) vm.onSelection(tfv.selection.start, tfv.selection.end)
            }
            .semantics { stateDescription = block.type.label }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val sel = tfv.selection
                val mod = e.isCtrlPressed || e.isMetaPressed
                when {
                    slashOpen && e.key == Key.DirectionDown -> { vm.slashMove(1); true }
                    slashOpen && e.key == Key.DirectionUp -> { vm.slashMove(-1); true }
                    slashOpen && (e.key == Key.Enter || e.key == Key.NumPadEnter) -> vm.slashConfirm()
                    slashOpen && e.key == Key.Escape -> { vm.slashClose(); true }
                    e.key == Key.Backspace && sel.collapsed && sel.start == 0 -> vm.backspaceAtStart(block.id)
                    e.key == Key.Tab -> {
                        if (block.type == BlockType.CODE && !e.isShiftPressed) {
                            val t = tfv.text.replaceRange(sel.min, sel.max, "    ")
                            val nv = TextFieldValue(t, TextRange(sel.min + 4))
                            if (!vm.onTextChange(block.id, nv.text, nv.selection.start, nv.selection.end)) tfv = nv
                        } else if (e.isShiftPressed) vm.outdent(block.id) else vm.indent(block.id)
                        true
                    }
                    mod && e.key == Key.B -> { vm.toggleMark(block.id, sel.min, sel.max, Mark.BOLD); true }
                    mod && e.key == Key.I -> { vm.toggleMark(block.id, sel.min, sel.max, Mark.ITALIC); true }
                    mod && e.key == Key.U -> { vm.toggleMark(block.id, sel.min, sel.max, Mark.UNDERLINE); true }
                    mod && e.isShiftPressed && e.key == Key.S -> { vm.toggleMark(block.id, sel.min, sel.max, Mark.STRIKE); true }
                    mod && e.key == Key.E -> { vm.toggleMark(block.id, sel.min, sel.max, Mark.CODE); true }
                    mod && e.key == Key.K -> { onLinkRequest(sel.min, sel.max); true }
                    mod && e.key == Key.Slash -> { vm.slashOpen(block.id); true }
                    mod && (e.key == Key.Enter || e.key == Key.NumPadEnter) && block.type == BlockType.TODO -> {
                        vm.setChecked(block.id, !block.props.checked); true
                    }
                    else -> false
                }
            },
        textStyle = style,
        cursorBrush = SolidColor(MonoColors.Ink),
        keyboardOptions = KeyboardOptions(
            capitalization = if (block.type == BlockType.CODE) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
        ),
        visualTransformation = if (block.type == BlockType.CODE) VisualTransformation.None else SpanTransformation(block.content),
        decorationBox = { inner ->
            Box {
                if (tfv.text.isEmpty()) {
                    placeholderFor(block.type, focused)?.let { Text(it, style = style.copy(color = MonoColors.Tertiary)) }
                }
                inner()
            }
        },
    )
}
