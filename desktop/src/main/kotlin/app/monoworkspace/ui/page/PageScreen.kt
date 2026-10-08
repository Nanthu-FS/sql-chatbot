package app.monoworkspace.ui.page

import app.monoworkspace.ui.common.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalWindowInfo
import app.monoworkspace.ui.common.LocalComposeWindow
import app.monoworkspace.ui.common.NativeFiles
import androidx.compose.runtime.collectAsState
import app.monoworkspace.data.repo.ExportRepository
import app.monoworkspace.model.Block
import app.monoworkspace.model.Mark
import app.monoworkspace.model.RichText
import app.monoworkspace.ui.common.Formats
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.BackToTopFor
import app.monoworkspace.ui.components.Breadcrumb
import app.monoworkspace.ui.components.CoverArt
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.InkRow
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoBottomSheet
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoDialog
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.MonoTopBar
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionHeader
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.database.AddPropertySheet
import app.monoworkspace.ui.database.PropertyRowLine
import app.monoworkspace.ui.database.PropertySheet
import app.monoworkspace.ui.database.ValueEditRequest
import app.monoworkspace.ui.database.ValueEditorHost
import app.monoworkspace.engine.Validation
import app.monoworkspace.engine.CellValue
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.ui.navigation.PagePickerList
import app.monoworkspace.ui.page.editor.BlockDragState
import app.monoworkspace.ui.page.editor.BlockRow
import app.monoworkspace.ui.page.editor.EditorUi
import app.monoworkspace.ui.page.editor.FormatToolbar
import app.monoworkspace.ui.page.editor.SelectionBar
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface PageSheet {
    data object Icon : PageSheet
    data object Cover : PageSheet
    data object History : PageSheet
    data object Move : PageSheet
    data object SaveTemplate : PageSheet
    data object ConfirmTrash : PageSheet
    data class TurnInto(val ids: Set<String>) : PageSheet
    data class Mention(val blockId: String, val caret: Int) : PageSheet
    data class InlineLink(val blockId: String, val start: Int, val end: Int, val current: String) : PageSheet
    data class LinkPreview(val blockId: String) : PageSheet
    data class EditValue(val request: ValueEditRequest) : PageSheet
    data class EditProperty(val propertyId: String) : PageSheet
    data object AddProperty : PageSheet
    data class FormulaError(val message: String) : PageSheet
}

@Composable
fun PageScreen() {
    val vm = monoViewModel { c, h -> PageViewModel(c, h) }
    val state by vm.state.collectAsState()
    val nav = LocalNavigator.current
    val layout = LocalWindowLayout.current
    val messenger = LocalMessenger.current
    val container = LocalAppContainer.current
    val window = LocalComposeWindow.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var sheet by remember { mutableStateOf<PageSheet?>(null) }
    var menu by remember { mutableStateOf(false) }
    val margin = layout.margin

    // Save when the window loses focus or the page leaves; refresh backlinks on return.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) vm.refreshBacklinks() else vm.flushAsync() }
    DisposableEffect(vm) { onDispose { vm.flushAsync() } }

    fun pickImage(id: String) {
        NativeFiles.open(window, "Add image", NativeFiles.IMAGES)?.let { vm.attachMedia(id, it, image = true) }
    }
    fun pickFile(id: String) {
        NativeFiles.open(window, "Add file")?.let { vm.attachMedia(id, it, image = false) }
    }
    fun pickCover() {
        NativeFiles.open(window, "Cover image", NativeFiles.IMAGES)?.let(vm::setCoverImage)
    }
    fun export(ex: ExportRepository.MarkdownExport) {
        val target = NativeFiles.save(window, "Export page", ex.fileName + if (ex.needsZip) ".zip" else ".md") ?: return
        scope.launch {
            runCatching { target.outputStream().use { container.export.writeMarkdown(ex, it) } }
                .onSuccess { messenger.show("Exported to ${target.name}") }
                .onFailure { messenger.show("Export failed") }
        }
    }

    val hasCover = state.page?.cover != null
    val isRow = state.row != null
    val blockOffset = (if (hasCover) 1 else 0) + 1 + (if (isRow) 1 else 0)

    fun scrollToBlock(id: String) {
        val s = vm.state.value
        var target: String? = id
        // Blocks inside columns or collapsed toggles scroll to their visible ancestor.
        while (target != null && s.flat.none { it.block.id == target }) target = s.blocks[target]?.parentBlockId
        val idx = s.flat.indexOfFirst { it.block.id == target }
        if (idx >= 0) scope.launch { listState.animateScrollToItem(blockOffset + idx) }
    }

    val ui = EditorUi(
        pickImage = ::pickImage,
        pickFile = ::pickFile,
        editLink = { id -> sheet = PageSheet.LinkPreview(id) },
        openFile = { b -> openFile(container, b) { messenger.show(it) } },
        requestInlineLink = { id, s, e ->
            if (e > s) {
                val b = vm.state.value.blocks[id]
                val url = b?.let { RichText.slice(it.content, s, e).firstOrNull { sp -> Mark.LINK in sp.marks }?.data?.url }.orEmpty()
                sheet = PageSheet.InlineLink(id, s, e, url)
            } else messenger.show("Select some text first")
        },
        navigate = { nav.openPage(it) },
        scrollTo = ::scrollToBlock,
    )

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is EditorEvent.Navigate -> nav.openPage(e.pageId)
                is EditorEvent.PickImage -> ui.pickImage(e.blockId)
                is EditorEvent.PickFile -> ui.pickFile(e.blockId)
                is EditorEvent.EditLink -> sheet = PageSheet.LinkPreview(e.blockId)
                is EditorEvent.Message -> messenger.show(e.text)
                is EditorEvent.ScrollTo -> {
                    delay(120)
                    scrollToBlock(e.blockId)
                }
                is EditorEvent.OpenMention -> sheet = PageSheet.Mention(e.blockId, vm.state.value.blocks[e.blockId]?.text?.length ?: 0)
                EditorEvent.Closed -> nav.back()
            }
        }
    }

    BackHandler(enabled = state.selectionMode || state.slash != null) {
        if (state.slash != null) vm.slashClose() else vm.clearSelection()
    }

    val drag = remember(listState) {
        BlockDragState(listState, { vm.state.value.flat.map { it.block.id }.toSet() }) { id, target -> vm.moveBefore(id, target) }
    }
    // Auto-scroll while dragging near the viewport edges.
    LaunchedEffect(drag.dragging) {
        while (drag.dragging != null) {
            val y = drag.pointerY()
            val info = listState.layoutInfo
            if (y != null) {
                val edge = 96f
                val delta = when {
                    y < info.viewportStartOffset + edge -> -18f
                    y > info.viewportEndOffset - edge -> 18f
                    else -> 0f
                }
                if (delta != 0f) {
                    val consumed = listState.scrollBy(delta)
                    drag.compensateScroll(consumed)
                }
            }
            delay(16)
        }
    }

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = {
                if (layout.usesSidebar) MonoIconButton(MonoIcons.Back, "Back", nav::back)
                else MonoIconButton(MonoIcons.Menu, "Open navigation", nav::openDrawer)
            },
            title = {
                Breadcrumb(
                    state.breadcrumb.map { it.displayTitle },
                    onClick = { i -> state.breadcrumb.getOrNull(i)?.let { nav.openPage(it.id) } },
                )
            },
            actions = {
                val page = state.page
                if (page != null) {
                    MonoIconButton(
                        if (page.isFavorite) MonoIcons.StarFilled else MonoIcons.Star,
                        if (page.isFavorite) "Remove from favorites" else "Add to favorites",
                        {
                            if (!page.isFavorite) app.monoworkspace.ui.theme.fx.FxBus.fire(app.monoworkspace.ui.theme.fx.BurstKind.Favorite)
                            vm.setFavorite(!page.isFavorite)
                        },
                    )
                    Box {
                        MonoIconButton(MonoIcons.More, "Page options", { menu = true })
                        MonoMenu(
                            menu, { menu = false },
                            buildList {
                                add(MenuItem("Icon", MonoIcons.Glyph) { sheet = PageSheet.Icon })
                                add(MenuItem("Cover", MonoIcons.Cover) { sheet = PageSheet.Cover })
                                if (!page.isRow) add(MenuItem("Move to…", MonoIcons.Swap) { sheet = PageSheet.Move })
                                add(MenuItem("Version history", MonoIcons.History) { scope.launch { vm.flush() }; sheet = PageSheet.History })
                                add(MenuItem("Save as template", MonoIcons.Template) { sheet = PageSheet.SaveTemplate })
                                add(MenuItem("Export Markdown", MonoIcons.Export) {
                                    scope.launch {
                                        vm.flush()
                                        val ex = container.export.pageMarkdown(vm.pageId)
                                        export(ex)
                                    }
                                })
                                state.row?.let { r -> add(MenuItem("Open database", MonoIcons.Table) { nav.openPage(r.snapshot.page.id) }) }
                                add(MenuItem("Move to trash", MonoIcons.Trash, destructive = true) { sheet = PageSheet.ConfirmTrash })
                            },
                        )
                    }
                }
            },
        )

        if (state.missing && !state.loading) {
            EmptyState(
                "This page was deleted or moved to the trash.",
                "Go home", nav::openHome,
                Modifier.padding(horizontal = margin),
                icon = MonoIcons.Trash,
            )
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                val page = state.page
                if (page?.cover != null) {
                    item("cover") {
                        CoverArt(
                            page.cover,
                            Modifier
                                .fillMaxWidth()
                                .height(if (layout.mode == app.monoworkspace.ui.common.LayoutMode.Landscape) 120.dp else 168.dp)
                                .graphicsLayer {
                                    // Parallax: the cover drifts at half speed.
                                    if (listState.firstVisibleItemIndex == 0) translationY = listState.firstVisibleItemScrollOffset * 0.5f
                                }
                                .clickable { sheet = PageSheet.Cover },
                        )
                    }
                }
                item("header") {
                    Column(Modifier.widthIn(max = 880.dp).padding(horizontal = margin)) {
                        if (page?.cover != null) {
                            GlyphHeader(page.icon, page.isDatabase, { sheet = PageSheet.Icon }, Modifier.offset(y = (-36).dp))
                        } else {
                            Spacer(Modifier.height(Space.xl))
                            if (page?.icon != null) GlyphHeader(page.icon, false, { sheet = PageSheet.Icon })
                        }
                        Row {
                            if (page?.icon == null) MonoButton("Add icon", { sheet = PageSheet.Icon }, style = MonoButtonStyle.Text, icon = MonoIcons.Glyph, height = 36.dp)
                            if (page?.cover == null) MonoButton("Add cover", { sheet = PageSheet.Cover }, style = MonoButtonStyle.Text, icon = MonoIcons.Cover, height = 36.dp)
                        }
                        TitleField(state, vm, if (layout.usesSidebar) MonoType.display else MonoType.h1)
                        Text(
                            (if (page?.isRow == true) "Row · " else "") + "Edited " + (page?.editedAt?.let { Formats.relative(it) } ?: ""),
                            Modifier.padding(top = Space.xs, bottom = Space.l),
                            style = MonoType.caption.tnum(),
                        )
                    }
                }
                state.row?.let { row ->
                    item("properties") {
                        Column(Modifier.widthIn(max = 880.dp).padding(horizontal = margin).padding(bottom = Space.l)) {
                            SectionRule()
                            row.properties.forEach { prop ->
                                val cell = row.cells[prop.id] ?: CellValue.Empty
                                val stored = row.values[prop.id]
                                PropertyRowLine(
                                    prop, cell,
                                    onClick = {
                                        if (cell is CellValue.Error) sheet = PageSheet.FormulaError(cell.message)
                                        else if (prop.type == PropertyType.CHECKBOX) vm.setRowValue(prop.id, PropertyValue.Checkbox(!((stored as? PropertyValue.Checkbox)?.value ?: false)))
                                        else sheet = PageSheet.EditValue(ValueEditRequest(vm.pageId, prop, stored, cell))
                                    },
                                    onLabelClick = { sheet = PageSheet.EditProperty(prop.id) },
                                    warning = (stored as? PropertyValue.Text)?.let { Validation.warning(prop.type, it.value) },
                                )
                            }
                            MonoButton("Add property", { sheet = PageSheet.AddProperty }, style = MonoButtonStyle.Text, icon = MonoIcons.Plus, height = 40.dp)
                            SectionRule()
                        }
                    }
                }
                items(state.flat, key = { it.block.id }) { item ->
                    BlockRow(
                        item, state, vm, ui, drag,
                        Modifier.widthIn(max = 880.dp).padding(start = (margin - if (layout.usesSidebar) 48.dp else 24.dp).coerceAtLeast(0.dp), end = margin),
                    )
                }
                item("tail") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { vm.tapBelowContent() },
                    )
                }
                if (state.subpages.isNotEmpty()) {
                    item("subpages-h") { SectionHeader("Subpages", Modifier.widthIn(max = 880.dp).padding(horizontal = margin).padding(top = Space.l)) }
                    items(state.subpages, key = { "sub-" + it.id }) { p ->
                        InkRow(p.displayTitle, { nav.openPage(p.id) }, Modifier.widthIn(max = 880.dp).padding(horizontal = margin), leading = { PageGlyph(p.icon, p.isDatabase) }, minHeight = 44.dp, titleStyle = MonoType.bodySmall)
                    }
                }
                if (!state.backlinks.isEmpty) {
                    item("backlinks-h") { SectionHeader("Backlinks", Modifier.widthIn(max = 880.dp).padding(horizontal = margin).padding(top = Space.xl)) }
                    if (state.backlinks.mentionedIn.isNotEmpty()) {
                        item("bl-m") { Text("Mentioned in", Modifier.padding(horizontal = margin, vertical = Space.s), style = MonoType.caption) }
                        items(state.backlinks.mentionedIn, key = { "blm-" + it.id }) { p ->
                            InkRow(p.displayTitle, { nav.openPage(p.id) }, Modifier.widthIn(max = 880.dp).padding(horizontal = margin), leading = { PageGlyph(p.icon, p.isDatabase) }, minHeight = 44.dp, titleStyle = MonoType.bodySmall)
                        }
                    }
                    if (state.backlinks.relatedRows.isNotEmpty()) {
                        item("bl-r") { Text("Related rows", Modifier.padding(horizontal = margin, vertical = Space.s), style = MonoType.caption) }
                        items(state.backlinks.relatedRows, key = { "blr-" + it.id }) { p ->
                            InkRow(p.displayTitle, { nav.openPage(p.id) }, Modifier.widthIn(max = 880.dp).padding(horizontal = margin), leading = { PageGlyph(p.icon, p.isDatabase) }, minHeight = 44.dp, titleStyle = MonoType.bodySmall)
                        }
                    }
                }
            }
            BackToTopFor(listState, Modifier.align(Alignment.BottomEnd).padding(Space.l))
        }

        when {
            state.selectionMode -> SelectionBar(state, vm) { sheet = PageSheet.TurnInto(state.selection) }
            state.focusedBlockId != null -> FormatToolbar(
                state, vm,
                onTurnInto = { state.focusedBlockId?.let { sheet = PageSheet.TurnInto(setOf(it)) } },
                onLink = { state.focusedBlockId?.let { id -> ui.requestInlineLink(id, state.textSelection.first, state.textSelection.second) } },
                onMention = { state.focusedBlockId?.let { id -> sheet = PageSheet.Mention(id, state.textSelection.first) } },
                onMore = { state.focusedBlockId?.let { vm.startSelection(it) } },
            )
        }
    }

    when (val s = sheet) {
        PageSheet.Icon -> IconPickerSheet(state.page?.icon, { sheet = null }) { vm.setIcon(it); sheet = null }
        PageSheet.Cover -> CoverPickerSheet(state.page?.cover, { sheet = null }, { vm.setCover(it); sheet = null }, { sheet = null; pickCover() })
        PageSheet.History -> VersionHistorySheet(vm.versionsFlow, { sheet = null }) { vm.restoreVersion(it.id) }
        PageSheet.Move -> MonoBottomSheet(onDismiss = { sheet = null }, title = "Move to") {
            PagePickerList(state.pages.values.filter { it.databaseId == null && !it.isTrashed }, excludeSubtreeOf = vm.pageId, onPick = { parent ->
                sheet = null
                scope.launch {
                    try {
                        container.pages.move(vm.pageId, parent)
                        messenger.show("Moved")
                    } catch (e: IllegalArgumentException) {
                        messenger.show(e.message ?: "Can't move this page")
                    }
                }
            })
        }
        PageSheet.SaveTemplate -> SaveTemplateDialog(state.title.ifBlank { "Untitled" }, { sheet = null }) { vm.saveAsTemplate(it) }
        PageSheet.ConfirmTrash -> MonoDialog(
            onDismiss = { sheet = null },
            title = "Move to trash?",
            body = "Subpages go with it. You can restore it from the trash for 30 days.",
            confirmLabel = "Move to trash",
            destructive = true,
            onConfirm = {
                sheet = null
                val id = vm.pageId
                val title = state.title.ifBlank { "Untitled" }
                app.monoworkspace.ui.theme.fx.FxBus.fire(app.monoworkspace.ui.theme.fx.BurstKind.Delete)
                vm.trashPage {
                    nav.back()
                    messenger.show("Moved “$title” to trash", "Undo") { scope.launch { container.pages.restore(id) } }
                }
            },
        )
        is PageSheet.TurnInto -> TurnIntoSheet({ sheet = null }) { t -> vm.turnInto(s.ids, t); sheet = null; vm.clearSelection() }
        is PageSheet.Mention -> MentionSheet(
            state.pages.values.filter { !it.isTrashed }, vm.pageId, { sheet = null },
            onPage = { p -> vm.insertPageMention(s.blockId, s.caret, p); sheet = null },
            onDate = { ms, withTime -> vm.insertDateMention(s.blockId, s.caret, ms, withTime); sheet = null },
        )
        is PageSheet.InlineLink -> InlineLinkDialog(s.current, { sheet = null }) { url -> vm.setLink(s.blockId, s.start, s.end, url) }
        is PageSheet.LinkPreview -> {
            val b = state.blocks[s.blockId]
            LinkPreviewDialog(b?.props?.url.orEmpty(), b?.props?.title.orEmpty(), b?.props?.description.orEmpty(), { sheet = null }) { u, t, d ->
                vm.setLinkPreview(s.blockId, u, t, d)
            }
        }
        is PageSheet.EditValue -> state.row?.let { row ->
            ValueEditorHost(s.request, row.snapshot.database.id, { sheet = null }) { _, propId, value -> vm.setRowValue(propId, value) }
        }
        is PageSheet.EditProperty -> state.row?.let { row -> PropertySheet(row.snapshot.database, s.propertyId) { sheet = null } }
        PageSheet.AddProperty -> state.row?.let { row -> AddPropertySheet(row.snapshot.database.id, { sheet = null }) }
        is PageSheet.FormulaError -> MonoDialog({ sheet = null }, "Formula error", s.message, "OK", { sheet = null })
        null -> Unit
    }
}

@Composable
private fun TitleField(state: EditorState, vm: PageViewModel, style: androidx.compose.ui.text.TextStyle) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.focusTitleNonce) {
        if (state.focusTitleNonce > 0) runCatching { requester.requestFocus() }
    }
    BasicTextField(
        value = state.title,
        onValueChange = { v ->
            if (v.contains('\n')) vm.titleEnter() else vm.setTitle(v)
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .semantics { heading() },
        textStyle = style,
        cursorBrush = SolidColor(MonoColors.Ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { vm.titleEnter() }),
        decorationBox = { inner ->
            Box {
                if (state.title.isEmpty()) Text("Untitled", style = style.copy(color = MonoColors.Tertiary))
                inner()
            }
        },
    )
}

private fun openFile(container: app.monoworkspace.AppContainer, b: Block, onError: (String) -> Unit) {
    val name = b.props.mediaPath ?: return
    val file = container.media.file(name)
    if (!file.exists()) {
        onError("The file is missing")
        return
    }
    // Hand Windows a copy with the original name so the right app opens it.
    val display = (b.props.fileName ?: name).replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val shown = java.io.File(container.cacheDir, "open").apply { mkdirs() }.resolve(display)
    val ok = runCatching { file.copyTo(shown, overwrite = true) }.isSuccess && NativeFiles.launch(shown)
    if (!ok) onError("No app can open this file")
}
