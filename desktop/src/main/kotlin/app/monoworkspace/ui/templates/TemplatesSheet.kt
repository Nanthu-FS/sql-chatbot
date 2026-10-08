package app.monoworkspace.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monoworkspace.model.TemplatePayload
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.components.DeleteButton
import app.monoworkspace.ui.components.HoverFocusState
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.hoverFocus
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.components.rememberHoverFocusState
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import kotlinx.coroutines.launch

/** What the bottom bar's + offers: page, database, or a template. */
@Composable
fun NewSheet(
    onDismiss: () -> Unit,
    onNewPage: () -> Unit,
    onNewDatabase: () -> Unit,
    onFromTemplate: () -> Unit,
) {
    MonoBottomSheet(onDismiss = onDismiss, title = "Create") {
        NewRow(MonoIcons.Page, "New page", "Blank page with blocks", onNewPage)
        NewRow(MonoIcons.Table, "New database", "Typed properties and views", onNewDatabase)
        NewRow(MonoIcons.Template, "From template", "Meeting notes, task tracker and more", onFromTemplate)
    }
}

@Composable
private fun NewRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, caption: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).inkClickable(onClick = onClick).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoIcon(icon, null)
            Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f)) {
                Text(title, style = MonoType.body)
                Text(caption, style = MonoType.caption)
            }
            MonoIcon(MonoIcons.ChevronRight, null, tint = MonoColors.Tertiary, size = 20.dp)
        }
        app.monoworkspace.ui.components.Hairline()
    }
}

/** Built-in and user templates as hover-focus cards. */
@Composable
fun TemplatesSheet(onDismiss: () -> Unit, onPick: (TemplatePayload) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val builtins by produceState(emptyList<TemplatePayload>()) { value = container.templates.builtins() }
    val user by remember { container.templates.observeUserTemplates() }.collectAsState(initial = emptyList())
    val focus = rememberHoverFocusState()
    val columns = if (LocalWindowLayout.current.isWide) 3 else 2

    MonoBottomSheet(onDismiss = onDismiss, title = "Templates") {
        LabelText("Built in", Modifier.padding(start = Space.l, top = Space.l, bottom = Space.s), color = MonoColors.Secondary)
        CardGrid(builtins.map { it to null }, columns, focus, onPick, onDelete = null)
        if (user.isNotEmpty()) {
            LabelText("Yours", Modifier.padding(start = Space.l, top = Space.xl, bottom = Space.s), color = MonoColors.Secondary)
            CardGrid(user.map { it.payload to it.id }, columns, focus, onPick) { id -> scope.launch { container.templates.deleteUserTemplate(id) } }
        } else {
            Text(
                "Save any page as a template from its ⋯ menu.",
                Modifier.padding(horizontal = Space.l, vertical = Space.xl),
                style = MonoType.bodySmall.copy(color = MonoColors.Secondary),
            )
        }
    }
}

@Composable
private fun CardGrid(
    items: List<Pair<TemplatePayload, String?>>,
    columns: Int,
    focus: HoverFocusState,
    onPick: (TemplatePayload) -> Unit,
    onDelete: ((String) -> Unit)?,
) {
    Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        items.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                rowItems.forEach { (payload, id) ->
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .hoverable(interaction)
                            .hoverFocus(focus, id ?: payload.name, interaction)
                            .background(MonoColors.Background)
                            .border(1.dp, MonoColors.Ink),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 148.dp)
                                .inkClickable(onClick = { onPick(payload) }, showBar = false)
                                .padding(Space.l),
                        ) {
                            Text(payload.glyph, style = MonoType.display.copy(fontSize = 36.sp, lineHeight = 40.sp))
                            Spacer(Modifier.height(Space.m))
                            Text(payload.name, style = MonoType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(payload.description, style = MonoType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (id != null && onDelete != null) {
                            DeleteButton({ onDelete(id) }, Modifier.align(Alignment.TopEnd).padding(Space.s), size = 36.dp, expandedWidth = 112.dp)
                        }
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
