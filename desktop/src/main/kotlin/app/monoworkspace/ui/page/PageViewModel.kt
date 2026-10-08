package app.monoworkspace.ui.page

import app.monoworkspace.ui.common.SavedStateHandle
import app.monoworkspace.ui.common.ViewModel
import app.monoworkspace.ui.common.toRoute
import app.monoworkspace.AppContainer
import app.monoworkspace.core.Clock
import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.core.Ids
import app.monoworkspace.data.repo.DatabaseQuery
import app.monoworkspace.data.repo.DatabaseSnapshot
import app.monoworkspace.data.repo.MediaTooLargeException
import app.monoworkspace.engine.BlockShortcuts
import app.monoworkspace.engine.BlockTree
import app.monoworkspace.engine.CellValue
import app.monoworkspace.engine.InlineShortcuts
import app.monoworkspace.engine.RowResolver
import app.monoworkspace.engine.ValueFormat
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockProps
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Mark
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.RichText
import app.monoworkspace.model.Span
import app.monoworkspace.model.SpanData
import app.monoworkspace.model.SpanKind
import app.monoworkspace.model.TableData
import app.monoworkspace.ui.navigation.PageRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single writer for one page. Blocks live in memory and every edit is applied
 * there first, then persisted: structural edits immediately (one transaction),
 * typing through a 300ms debounce. The UI owns caret state; the VM bumps a
 * per-block version whenever it rewrites a block's text so the field resyncs.
 */
class PageViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<PageRoute>()
    val pageId: String = route.pageId
    private val clock = Clock.System

    private val _state = MutableStateFlow(EditorState(highlightBlockId = route.blockId))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val blocks = LinkedHashMap<String, Block>()
    private val versions = HashMap<String, Int>()
    private val pendingContent = LinkedHashMap<String, List<Span>>()
    private var saveJob: Job? = null
    private var titleJob: Job? = null
    private var tableJob: Job? = null
    private val pendingTables = HashMap<String, TableData>()
    private var pendingTitle: String? = null
    private val writeLock = Mutex()
    private var dirty = false
    private var idleJob: Job? = null
    private var nonce = 0L
    private val embedIds = MutableStateFlow<Set<String>>(emptySet())

    val versionsFlow = c.blocks.observeVersions(pageId)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            combine(c.pages.observe(pageId), c.pages.observeAll()) { page, all -> page to all }.collect { (page, all) ->
                val map = all.associateBy { it.id }
                _state.update { s ->
                    if (page == null) s.copy(missing = !s.loading || s.page != null, page = null)
                    else s.copy(
                        page = page,
                        title = pendingTitle ?: if (s.page == null) page.title else s.title,
                        pages = map,
                        breadcrumb = breadcrumbOf(page, map),
                        missing = page.isTrashed,
                    )
                }
            }
        }
        viewModelScope.launch { c.pages.observeChildren(pageId).collect { subs -> _state.update { it.copy(subpages = subs) } } }
        viewModelScope.launch { rowFlow().collect { row -> _state.update { it.copy(row = row) } } }
        viewModelScope.launch { embedFlow().collect { e -> _state.update { it.copy(embeds = e) } } }
        viewModelScope.launch {
            c.pages.visit(pageId)
            c.settings.setLastOpened(pageId)
            refreshBacklinks()
        }
        route.blockId?.let { id ->
            viewModelScope.launch {
                delay(1500)
                _state.update { if (it.highlightBlockId == id) it.copy(highlightBlockId = null) else it }
            }
        }
    }

    private fun breadcrumbOf(page: Page, map: Map<String, Page>): List<Page> {
        val out = ArrayList<Page>()
        var cur: Page? = page
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur.id)) {
            out.add(0, cur)
            cur = cur.parentId?.let { map[it] }
        }
        return out
    }

    fun refreshBacklinks() = viewModelScope.launch {
        val b = c.pages.backlinks(pageId)
        _state.update { it.copy(backlinks = b) }
    }

    private fun rowFlow(): Flow<RowState?> =
        c.pages.observe(pageId).map { it?.databaseId }.distinctUntilChanged().flatMapLatest { dbId ->
            if (dbId == null) flowOf(null)
            else flow { emit(c.databases.get(dbId)) }.flatMapLatest { d ->
                if (d == null) flowOf(null) else c.databases.observeSnapshot(d.pageId).map { snap -> snap?.let { rowState(it) } }
            }
        }

    private fun rowState(snap: DatabaseSnapshot): RowState? {
        val row = snap.rows.firstOrNull { it.id == pageId } ?: return null
        val resolver = RowResolver(snap.database.schema, snap.rows, snap.related, ownDatabaseId = snap.database.id)
        val props = snap.database.schema.properties.filter { !it.isTitle }
        val cells = props.associate { it.id to resolver.cell(row, it) }
        return RowState(snap, props, cells, row.values)
    }

    private fun embedFlow(): Flow<Map<String, EmbedSummary>> = embedIds.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyMap())
        else combine(ids.map { id -> c.databases.observeSnapshot(id).map { id to it } }) { arr ->
            arr.mapNotNull { (id, snap) -> snap?.let { id to summarize(it) } }.toMap()
        }
    }

    private fun summarize(snap: DatabaseSnapshot): EmbedSummary {
        val view = snap.views.firstOrNull()
        val metaProp = snap.database.schema.properties.firstOrNull { it.type == PropertyType.STATUS || it.type == PropertyType.SELECT }
        if (view == null) {
            return EmbedSummary(snap.page.id, snap.page.displayTitle, snap.page.icon, "Table", app.monoworkspace.model.ViewType.TABLE, emptyList(), snap.rows.size)
        }
        val result = DatabaseQuery.run(snap, view)
        val rows = result.rows.take(5).map { r ->
            val meta = metaProp?.let { ValueFormat.display(result.resolver.cell(r, it), it) }.orEmpty()
            EmbedRow(r.id, r.title.ifBlank { "Untitled" }, meta)
        }
        return EmbedSummary(snap.page.id, snap.page.displayTitle, snap.page.icon, view.name, view.type, rows, result.rows.size)
    }

    // ---------- Loading & publishing ----------

    private suspend fun load() {
        val list = c.blocks.load(pageId)
        blocks.clear()
        list.forEach { blocks[it.id] = it }
        if (list.isEmpty()) {
            val b = newBlock(BlockType.TEXT, null, FractionalIndex.first())
            blocks[b.id] = b
            persist(listOf(b), markDirty = false)
        }
        val page = c.pages.get(pageId)
        val focusTitle = page != null && page.title.isBlank() && blocks.size == 1 && blocks.values.first().text.isEmpty()
        publish {
            it.copy(
                loading = false,
                missing = page == null || page.isTrashed,
                title = pendingTitle ?: page?.title.orEmpty(),
                focusTitleNonce = if (focusTitle) ++nonce else it.focusTitleNonce,
            )
        }
        route.blockId?.let { _events.trySend(EditorEvent.ScrollTo(it)) }
    }

    private fun publish(extra: (EditorState) -> EditorState = { it }) {
        val list = blocks.values.toList()
        val flat = BlockTree.flatten(list)
        val childMap = BlockTree.childrenMap(list)
        embedIds.value = list.filter { it.type == BlockType.CHILD_DATABASE }.mapNotNull { it.props.pageId }.toSet()
        _state.update { s -> extra(s.copy(flat = flat, blocks = HashMap(blocks), childMap = childMap, versions = HashMap(versions))) }
    }

    private fun newBlock(type: BlockType, parent: String?, key: String, content: List<Span> = emptyList(), props: BlockProps = BlockProps()): Block {
        val now = clock.now()
        return Block(Ids.new(), pageId, parent, key, type, content, props, now, now)
    }

    private fun bump(id: String) {
        versions[id] = (versions[id] ?: 0) + 1
    }

    private fun focusOn(id: String, caret: Int): FocusTarget = FocusTarget(id, caret, ++nonce)

    private fun persist(upserts: List<Block>, deletes: Collection<String> = emptyList(), markDirty: Boolean = true) {
        upserts.forEach { pendingContent.remove(it.id) }
        deletes.forEach { pendingContent.remove(it) }
        if (markDirty) markDirty()
        viewModelScope.launch { writeLock.withLock { c.blocks.apply(pageId, upserts, deletes) } }
    }

    private fun markDirty() {
        dirty = true
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(IDLE_SNAPSHOT_MS)
            snapshotIfDirty()
        }
    }

    private suspend fun snapshotIfDirty() {
        if (!dirty) return
        dirty = false
        val title = pendingTitle ?: _state.value.title
        c.blocks.snapshot(pageId, title, blocks.values.toList())
    }

    private fun siblings(parent: String?): List<Block> = blocks.values.filter { it.parentBlockId == parent }.sortedBy { it.orderKey }

    private fun keyAfter(b: Block): String {
        val sibs = siblings(b.parentBlockId)
        val i = sibs.indexOfFirst { it.id == b.id }
        return FractionalIndex.between(b.orderKey, sibs.getOrNull(i + 1)?.orderKey)
    }

    private fun keyBefore(b: Block): String {
        val sibs = siblings(b.parentBlockId)
        val i = sibs.indexOfFirst { it.id == b.id }
        return FractionalIndex.between(sibs.getOrNull(i - 1)?.orderKey, b.orderKey)
    }

    private fun touch(b: Block): Block = b.copy(updatedAt = clock.now())

    // ---------- Title ----------

    fun setTitle(text: String) {
        val t = text.replace("\n", "")
        pendingTitle = t
        _state.update { it.copy(title = t) }
        markDirty()
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveTitle()
        }
    }

    private suspend fun saveTitle() {
        val t = pendingTitle ?: return
        c.pages.rename(pageId, t)
        if (pendingTitle == t) pendingTitle = null
    }

    /** Enter in the title moves into the first block. */
    fun titleEnter() {
        val first = _state.value.flat.firstOrNull()?.block
        if (first != null && first.type.isText) {
            publish { it.copy(focus = focusOn(first.id, 0)) }
        } else {
            val b = newBlock(BlockType.TEXT, null, FractionalIndex.between(null, siblings(null).firstOrNull()?.orderKey))
            blocks[b.id] = b
            persist(listOf(b))
            publish { it.copy(focus = focusOn(b.id, 0)) }
        }
    }

    // ---------- Typing ----------

    fun onFocusChanged(id: String, focused: Boolean) {
        _state.update { s ->
            when {
                focused -> s.copy(focusedBlockId = id, activeBlockId = null)
                s.focusedBlockId == id -> s.copy(focusedBlockId = null, slash = if (s.slash?.blockId == id) null else s.slash)
                else -> s
            }
        }
    }

    fun onSelection(start: Int, end: Int) {
        _state.update { it.copy(textSelection = start to end) }
    }

    /**
     * Handles a text change from a block field. Returns true when the VM rewrote
     * the block (split, shortcut, conversion); the field then resyncs from state.
     */
    fun onTextChange(id: String, newText: String, selStart: Int, selEnd: Int): Boolean {
        val b = blocks[id] ?: return false
        val old = b.text
        _state.update { it.copy(textSelection = selStart to selEnd) }
        if (newText == old) return false
        val spans = RichText.applyEdit(b.content, newText)

        // Enter while the slash menu is open picks the highlighted item.
        if (newText.contains('\n') && _state.value.slash?.blockId == id) {
            if (slashConfirm()) return true
            slashClose()
        }

        // Enter (or a pasted multi-line text) splits the block.
        if (b.type != BlockType.CODE && newText.contains('\n')) {
            splitOnNewlines(b, spans, newText, selStart)
            return true
        }

        // Slash menu on an empty text block.
        val slash = _state.value.slash
        if (b.type.isText && b.type != BlockType.CODE && newText.startsWith("/") && (old.isEmpty() || slash?.blockId == id) && newText.length <= 40) {
            blocks[id] = b.copy(content = spans)
            scheduleSave(id, spans)
            val query = newText.drop(1)
            _state.update { s ->
                val cur = s.slash
                val selected = if (cur != null && cur.blockId == id && cur.query == query) cur.selected else 0
                s.copy(blocks = HashMap(blocks), slash = SlashState(id, query, selected))
            }
            return false
        } else if (slash?.blockId == id) {
            _state.update { it.copy(slash = null) }
        }

        // Markdown block shortcuts on plain paragraphs.
        if (b.type == BlockType.TEXT && selStart == selEnd) {
            val m = BlockShortcuts.match(newText, selStart)
            if (m != null) {
                val rest = RichText.slice(spans, m.consumed, RichText.length(spans))
                if (m.type == BlockType.DIVIDER) {
                    val divider = touch(b.copy(type = BlockType.DIVIDER, content = emptyList()))
                    val next = newBlock(BlockType.TEXT, b.parentBlockId, keyAfter(b))
                    blocks[divider.id] = divider
                    blocks[next.id] = next
                    bump(divider.id)
                    persist(listOf(divider, next))
                    publish { it.copy(focus = focusOn(next.id, 0)) }
                } else {
                    val nb = touch(b.copy(type = m.type, content = rest, props = b.props.copy(checked = m.checked)))
                    blocks[id] = nb
                    bump(id)
                    persist(listOf(nb))
                    publish { it.copy(focus = focusOn(id, 0)) }
                }
                return true
            }
        }

        // Inline markdown when a closing marker is typed.
        if (selStart == selEnd && b.type != BlockType.CODE && newText.length > old.length) {
            val last = newText.getOrNull(selStart - 1)
            if (last == '*' || last == '~' || last == '`' || last == ')') {
                val r = InlineShortcuts.apply(spans, selStart)
                if (r != null) {
                    val nb = touch(b.copy(content = r.spans))
                    blocks[id] = nb
                    bump(id)
                    persist(listOf(nb))
                    publish { it.copy(focus = focusOn(id, r.caret)) }
                    return true
                }
            }
        }

        blocks[id] = b.copy(content = spans)
        scheduleSave(id, spans)
        _state.update { it.copy(blocks = HashMap(blocks)) }
        return false
    }

    private fun scheduleSave(id: String, spans: List<Span>) {
        pendingContent[id] = spans
        markDirty()
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flushContent()
        }
    }

    private suspend fun flushContent() {
        if (pendingContent.isEmpty()) return
        val batch = LinkedHashMap(pendingContent)
        pendingContent.clear()
        writeLock.withLock { c.blocks.saveContent(batch, pageId) }
    }

    private fun continuingType(t: BlockType): BlockType = when (t) {
        BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO, BlockType.TOGGLE -> t
        else -> BlockType.TEXT
    }

    private fun splitOnNewlines(b: Block, spans: List<Span>, newText: String, caretInNew: Int) {
        val segments = ArrayList<List<Span>>()
        var start = 0
        newText.forEachIndexed { i, ch ->
            if (ch == '\n') {
                segments.add(RichText.slice(spans, start, i))
                start = i + 1
            }
        }
        segments.add(RichText.slice(spans, start, newText.length))
        val first = segments.first()
        val rest = segments.drop(1)
        val after = newText.length - caretInNew

        // Enter on an empty list item: outdent if nested, otherwise become a paragraph.
        if (rest.size == 1 && RichText.length(first) == 0 && RichText.length(rest[0]) == 0 && (b.type.isListLike || b.type == BlockType.TOGGLE)) {
            val parent = b.parentBlockId?.let { blocks[it] }
            if (parent != null && (parent.type.isListLike || parent.type == BlockType.TOGGLE || parent.type == BlockType.TEXT)) {
                blocks[b.id] = b.copy(content = emptyList())
                outdent(b.id)
            } else {
                val nb = touch(b.copy(type = BlockType.TEXT, content = emptyList(), props = b.props.copy(checked = false)))
                blocks[b.id] = nb
                bump(b.id)
                persist(listOf(nb))
                publish { it.copy(focus = focusOn(b.id, 0)) }
            }
            return
        }

        // Enter at the very start of a non-empty block: insert an empty paragraph above.
        if (rest.size == 1 && RichText.length(first) == 0 && RichText.length(rest[0]) > 0 && caretInNew == 1) {
            val above = newBlock(if (b.type.isListLike) b.type else BlockType.TEXT, b.parentBlockId, keyBefore(b))
            val same = b.copy(content = rest[0])
            blocks[above.id] = above
            blocks[b.id] = same
            bump(b.id)
            persist(listOf(above, same))
            publish { it.copy(focus = focusOn(b.id, 0)) }
            return
        }

        val updated = touch(b.copy(content = first))
        blocks[b.id] = updated
        bump(b.id)
        val created = ArrayList<Block>()
        // An expanded toggle with no children takes the new line as its first child.
        val intoToggle = b.type == BlockType.TOGGLE && !b.props.collapsed && rest.size == 1 && siblings(b.id).isEmpty() && RichText.length(rest[0]) == 0
        var prev = updated
        for (seg in rest) {
            val type = if (intoToggle) BlockType.TEXT else continuingType(b.type)
            val parent = if (intoToggle) b.id else b.parentBlockId
            val key = if (intoToggle) FractionalIndex.first() else keyAfter(prev)
            val nb = newBlock(type, parent, key, seg)
            blocks[nb.id] = nb
            created.add(nb)
            prev = nb
        }
        persist(listOf(updated) + created)
        val last = created.lastOrNull() ?: updated
        val caret = (last.text.length - after).coerceIn(0, last.text.length)
        publish { it.copy(focus = focusOn(last.id, caret), slash = null) }
    }

    /** Backspace with the caret at position 0. Returns true when handled. */
    fun backspaceAtStart(id: String): Boolean {
        val b = blocks[id] ?: return false
        if (b.type != BlockType.TEXT && b.type.isText) {
            val nb = touch(b.copy(type = BlockType.TEXT, props = b.props.copy(checked = false)))
            blocks[id] = nb
            bump(id)
            persist(listOf(nb))
            publish { it.copy(focus = focusOn(id, 0)) }
            return true
        }
        val parent = b.parentBlockId?.let { blocks[it] }
        if (parent != null && parent.type != BlockType.COLUMN) {
            outdent(id)
            return true
        }
        val flat = _state.value.flat
        val idx = flat.indexOfFirst { it.block.id == id }
        val prev = if (idx > 0) flat[idx - 1].block else null
        if (prev == null) {
            if (b.text.isEmpty() && siblings(null).size > 1) {
                deleteInternal(listOf(id))
                publish { it.copy(focusTitleNonce = ++nonce) }
            } else {
                publish { it.copy(focusTitleNonce = ++nonce) }
            }
            return true
        }
        when {
            prev.type == BlockType.DIVIDER -> {
                deleteInternal(listOf(prev.id))
                publish { it.copy(focus = focusOn(id, 0)) }
            }
            prev.type.isText && prev.type != BlockType.CODE -> {
                val caret = prev.text.length
                val merged = touch(prev.copy(content = RichText.concat(prev.content, b.content)))
                blocks[prev.id] = merged
                bump(prev.id)
                // Children of the removed block move under the block it merged into.
                val kids = siblings(id)
                val moved = ArrayList<Block>()
                var lastKey = siblings(prev.id).lastOrNull()?.orderKey
                for (k in kids) {
                    val key = FractionalIndex.between(lastKey, null)
                    val mk = k.copy(parentBlockId = prev.id, orderKey = key)
                    blocks[k.id] = mk
                    moved.add(mk)
                    lastKey = key
                }
                blocks.remove(id)
                persist(listOf(merged) + moved, listOf(id))
                publish { it.copy(focus = focusOn(prev.id, caret)) }
            }
            else -> {
                if (b.text.isEmpty()) deleteInternal(listOf(id))
                publish { it.copy(focus = null, activeBlockId = prev.id) }
            }
        }
        return true
    }

    // ---------- Structure ----------

    fun insertBelow(id: String, type: BlockType = BlockType.TEXT): String? {
        val b = blocks[id] ?: return null
        val nb = newBlock(type, b.parentBlockId, keyAfter(b), props = if (type == BlockType.TABLE) BlockProps(table = TableData()) else BlockProps())
        blocks[nb.id] = nb
        persist(listOf(nb))
        publish { it.copy(focus = if (type.isText) focusOn(nb.id, 0) else it.focus, selection = emptySet(), selectionMode = false) }
        return nb.id
    }

    /** Tapping the empty area below the content. */
    fun tapBelowContent() {
        val last = siblings(null).lastOrNull()
        if (last != null && last.type.isText && last.text.isEmpty()) {
            publish { it.copy(focus = focusOn(last.id, 0)) }
            return
        }
        val nb = newBlock(BlockType.TEXT, null, FractionalIndex.between(last?.orderKey, null))
        blocks[nb.id] = nb
        persist(listOf(nb))
        publish { it.copy(focus = focusOn(nb.id, 0)) }
    }

    fun appendChild(parentId: String) {
        val nb = newBlock(BlockType.TEXT, parentId, FractionalIndex.between(siblings(parentId).lastOrNull()?.orderKey, null))
        blocks[nb.id] = nb
        persist(listOf(nb))
        publish { it.copy(focus = focusOn(nb.id, 0)) }
    }

    fun indent(id: String) {
        val b = blocks[id] ?: return
        val sibs = siblings(b.parentBlockId)
        val i = sibs.indexOfFirst { it.id == id }
        if (i <= 0) return
        val newParent = sibs[i - 1]
        if (!newParent.type.isText || newParent.type == BlockType.CODE) return
        val key = FractionalIndex.between(siblings(newParent.id).lastOrNull()?.orderKey, null)
        val moved = touch(b.copy(parentBlockId = newParent.id, orderKey = key))
        blocks[id] = moved
        val ups = mutableListOf(moved)
        if (newParent.type == BlockType.TOGGLE && newParent.props.collapsed) {
            val open = newParent.copy(props = newParent.props.copy(collapsed = false))
            blocks[open.id] = open
            ups.add(open)
        }
        bump(id)
        persist(ups)
        publish { it.copy(focus = focusOn(id, _state.value.textSelection.first.coerceAtMost(moved.text.length))) }
    }

    fun outdent(id: String) {
        val b = blocks[id] ?: return
        val parent = b.parentBlockId?.let { blocks[it] } ?: return
        if (parent.type == BlockType.COLUMN) return
        val key = keyAfter(parent)
        val moved = touch(b.copy(parentBlockId = parent.parentBlockId, orderKey = key))
        blocks[id] = moved
        bump(id)
        persist(listOf(moved))
        publish { it.copy(focus = focusOn(id, _state.value.textSelection.first.coerceAtMost(moved.text.length))) }
    }

    fun turnInto(ids: Collection<String>, type: BlockType) {
        val ups = ids.mapNotNull { id ->
            val b = blocks[id] ?: return@mapNotNull null
            if (!b.type.isText) return@mapNotNull null
            val content = if (type == BlockType.CODE) RichText.of(b.text) else b.content
            val props = when (type) {
                BlockType.CALLOUT -> b.props.copy(glyph = b.props.glyph ?: "◆")
                BlockType.CODE -> b.props.copy(language = b.props.language ?: "plain")
                BlockType.TODO -> b.props
                else -> b.props.copy(checked = false)
            }
            touch(b.copy(type = type, content = content, props = props)).also { blocks[id] = it; bump(id) }
        }
        if (ups.isEmpty()) return
        persist(ups)
        publish()
    }

    private fun subtreeIds(ids: Collection<String>): Set<String> {
        val list = blocks.values.toList()
        return ids.flatMap { listOf(it) + BlockTree.descendants(list, it) }.toSet()
    }

    private fun deleteInternal(ids: Collection<String>) {
        val all = subtreeIds(ids)
        all.forEach { blocks.remove(it) }
        persist(emptyList(), all)
    }

    fun deleteBlocks(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val flat = _state.value.flat
        val firstIdx = flat.indexOfFirst { it.block.id in ids }
        deleteInternal(ids)
        if (blocks.isEmpty()) {
            val b = newBlock(BlockType.TEXT, null, FractionalIndex.first())
            blocks[b.id] = b
            persist(listOf(b))
        }
        val remaining = BlockTree.flatten(blocks.values.toList())
        val target = remaining.getOrNull((firstIdx - 1).coerceAtLeast(0))?.block
        publish {
            it.copy(
                selection = emptySet(), selectionMode = false, activeBlockId = null,
                focus = if (target != null && target.type.isText) focusOn(target.id, target.text.length) else null,
            )
        }
    }

    fun duplicateBlocks(ids: Collection<String>) {
        val list = blocks.values.toList()
        val ordered = _state.value.flat.map { it.block }.filter { it.id in ids }
        val created = ArrayList<Block>()
        for (b in ordered) {
            val subtree = listOf(b) + BlockTree.descendants(list, b.id).mapNotNull { blocks[it] }
            val idMap = subtree.associate { it.id to Ids.new() }
            val now = clock.now()
            for (s in subtree) {
                val copy = s.copy(
                    id = idMap.getValue(s.id),
                    parentBlockId = if (s.id == b.id) b.parentBlockId else s.parentBlockId?.let { idMap[it] ?: it },
                    orderKey = if (s.id == b.id) keyAfter(b) else s.orderKey,
                    createdAt = now, updatedAt = now,
                )
                blocks[copy.id] = copy
                created.add(copy)
            }
        }
        if (created.isEmpty()) return
        persist(created)
        publish { it.copy(selection = emptySet(), selectionMode = false) }
    }

    fun moveStep(ids: Collection<String>, up: Boolean) {
        val ordered = _state.value.flat.map { it.block }.filter { it.id in ids }.let { if (up) it else it.reversed() }
        val ups = ArrayList<Block>()
        for (b0 in ordered) {
            val b = blocks[b0.id] ?: continue
            val key = BlockTree.stepKey(blocks.values.toList(), b, up) ?: continue
            val moved = touch(b.copy(orderKey = key))
            blocks[b.id] = moved
            ups.add(moved)
        }
        if (ups.isEmpty()) return
        persist(ups)
        publish()
    }

    /** Drag and drop: place [id] before [targetId], or at the end when null. */
    fun moveBefore(id: String, targetId: String?) {
        val target = targetId?.let { blocks[it] }
        val r = BlockTree.moveBefore(blocks.values.toList(), id, target) ?: return
        val b = blocks[id] ?: return
        val moved = touch(b.copy(parentBlockId = r.first, orderKey = r.second))
        blocks[id] = moved
        markDirty()
        viewModelScope.launch { writeLock.withLock { c.blocks.move(id, r.first, r.second, pageId) } }
        publish()
    }

    // ---------- Selection mode ----------

    fun startSelection(id: String) {
        _state.update { it.copy(selectionMode = true, selection = setOf(id), slash = null, activeBlockId = null) }
    }

    fun toggleSelected(id: String) {
        _state.update { s ->
            val sel = if (id in s.selection) s.selection - id else s.selection + id
            s.copy(selection = sel, selectionMode = sel.isNotEmpty())
        }
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet(), selectionMode = false) }

    fun setActive(id: String?) = _state.update { it.copy(activeBlockId = id) }

    // ---------- Inline formatting ----------

    fun toggleMark(id: String, start: Int, end: Int, mark: Mark, url: String? = null) {
        val b = blocks[id] ?: return
        if (start >= end) return
        val nb = touch(b.copy(content = RichText.toggleMark(b.content, start, end, mark, url)))
        blocks[id] = nb
        bump(id)
        persist(listOf(nb))
        publish { it.copy(focus = focusOn(id, end)) }
    }

    fun setLink(id: String, start: Int, end: Int, url: String?) {
        val b = blocks[id] ?: return
        if (start >= end) return
        val content = if (url.isNullOrBlank()) {
            if (RichText.hasMark(b.content, start, end, Mark.LINK)) RichText.toggleMark(b.content, start, end, Mark.LINK) else b.content
        } else RichText.setMark(b.content, start, end, Mark.LINK, url.trim())
        val nb = touch(b.copy(content = content))
        blocks[id] = nb
        bump(id)
        persist(listOf(nb))
        publish { it.copy(focus = focusOn(id, end)) }
    }

    fun insertDateMention(id: String, caret: Int, epochMs: Long, includeTime: Boolean = false) {
        val zone = ZoneId.systemDefault()
        val fmt = DateTimeFormatter.ofPattern(if (includeTime) "MMM d, yyyy h:mm a" else "MMM d, yyyy")
        val label = "@" + Instant.ofEpochMilli(epochMs).atZone(zone).format(fmt)
        insertSpan(id, caret, Span(label, kind = SpanKind.DATE_MENTION, data = SpanData(start = epochMs, includeTime = includeTime)))
    }

    fun insertPageMention(id: String, caret: Int, page: Page) {
        insertSpan(id, caret, Span("↗ " + page.displayTitle, setOf(Mark.BOLD), SpanKind.PAGE_MENTION, SpanData(pageId = page.id)))
    }

    private fun insertSpan(id: String, caret: Int, span: Span) {
        val b = blocks[id] ?: return
        val at = caret.coerceIn(0, b.text.length)
        val content = RichText.insert(b.content, at, listOf(span, Span(" ")))
        val nb = touch(b.copy(content = content))
        blocks[id] = nb
        bump(id)
        persist(listOf(nb))
        publish { it.copy(focus = focusOn(id, at + span.text.length + 1)) }
        refreshBacklinks()
    }

    // ---------- Slash menu ----------

    fun slashOpen(id: String) {
        val b = blocks[id] ?: return
        if (!b.type.isText) return
        if (b.text.isNotEmpty()) {
            val nid = insertBelow(id) ?: return
            val nb = blocks[nid] ?: return
            val withSlash = nb.copy(content = RichText.of("/"))
            blocks[nid] = withSlash
            bump(nid)
            publish { it.copy(slash = SlashState(nid, ""), focus = focusOn(nid, 1)) }
        } else {
            val withSlash = b.copy(content = RichText.of("/"))
            blocks[id] = withSlash
            bump(id)
            publish { it.copy(slash = SlashState(id, ""), focus = focusOn(id, 1)) }
        }
    }

    fun slashMove(delta: Int) {
        _state.update { s ->
            val sl = s.slash ?: return@update s
            val n = SlashAction.filter(sl.query).size
            if (n == 0) s else s.copy(slash = sl.copy(selected = (sl.selected + delta).mod(n)))
        }
    }

    fun slashClose() = _state.update { it.copy(slash = null) }

    fun slashConfirm(): Boolean {
        val sl = _state.value.slash ?: return false
        val items = SlashAction.filter(sl.query)
        val action = items.getOrNull(sl.selected) ?: return false
        applySlash(action)
        return true
    }

    fun applySlash(action: SlashAction) {
        val sl = _state.value.slash ?: return
        val b = blocks[sl.blockId] ?: return
        // Remove the "/query" text that opened the menu.
        val slashIdx = b.text.lastIndexOf('/')
        val cleaned = if (slashIdx >= 0) RichText.replace(b.content, slashIdx, b.text.length, emptyList()) else b.content
        val base = b.copy(content = cleaned)
        blocks[b.id] = base
        _state.update { it.copy(slash = null) }
        when (action) {
            SlashAction.DATE -> {
                bump(b.id)
                persist(listOf(base))
                insertDateMention(b.id, cleaned.sumOf { it.text.length }, clock.now())
            }
            SlashAction.MENTION -> {
                bump(b.id)
                persist(listOf(base))
                publish { it.copy(focus = focusOn(b.id, RichText.length(cleaned))) }
                _events.trySend(EditorEvent.OpenMention(b.id))
            }
            SlashAction.DIVIDER -> {
                val div = touch(base.copy(type = BlockType.DIVIDER, content = emptyList()))
                val next = newBlock(BlockType.TEXT, b.parentBlockId, keyAfter(b))
                blocks[div.id] = div
                blocks[next.id] = next
                bump(div.id)
                persist(listOf(div, next))
                publish { it.copy(focus = focusOn(next.id, 0)) }
            }
            SlashAction.IMAGE, SlashAction.FILE, SlashAction.LINK, SlashAction.TABLE, SlashAction.TOC -> {
                val type = action.blockType!!
                val props = when (action) {
                    SlashAction.TABLE -> base.props.copy(table = TableData())
                    else -> base.props
                }
                val nb = touch(base.copy(type = type, content = emptyList(), props = props))
                blocks[nb.id] = nb
                bump(nb.id)
                val next = newBlock(BlockType.TEXT, b.parentBlockId, keyAfter(nb))
                blocks[next.id] = next
                persist(listOf(nb, next))
                publish { it.copy(focus = null, activeBlockId = nb.id) }
                when (action) {
                    SlashAction.IMAGE -> _events.trySend(EditorEvent.PickImage(nb.id))
                    SlashAction.FILE -> _events.trySend(EditorEvent.PickFile(nb.id))
                    SlashAction.LINK -> _events.trySend(EditorEvent.EditLink(nb.id))
                    else -> Unit
                }
            }
            SlashAction.COLUMNS2, SlashAction.COLUMNS3 -> {
                val n = if (action == SlashAction.COLUMNS2) 2 else 3
                val container = touch(base.copy(type = BlockType.COLUMNS, content = emptyList()))
                blocks[container.id] = container
                val keys = FractionalIndex.nBetween(null, null, n)
                val created = ArrayList<Block>()
                var firstText: Block? = null
                for (k in keys) {
                    val col = newBlock(BlockType.COLUMN, container.id, k)
                    val txt = newBlock(BlockType.TEXT, col.id, FractionalIndex.first())
                    blocks[col.id] = col
                    blocks[txt.id] = txt
                    created.add(col)
                    created.add(txt)
                    if (firstText == null) firstText = txt
                }
                bump(container.id)
                persist(listOf(container) + created)
                publish { it.copy(focus = firstText?.let { t -> focusOn(t.id, 0) }) }
            }
            SlashAction.PAGE -> viewModelScope.launch {
                val child = c.pages.createPage(pageId)
                val nb = touch(base.copy(type = BlockType.CHILD_PAGE, content = emptyList(), props = base.props.copy(pageId = child.id)))
                blocks[nb.id] = nb
                bump(nb.id)
                persist(listOf(nb))
                publish { it.copy(focus = null) }
                _events.send(EditorEvent.Navigate(child.id))
            }
            SlashAction.DATABASE -> viewModelScope.launch {
                val dbPage = c.databases.createDatabase(pageId)
                val d = c.databases.byPage(dbPage.id)
                val nb = touch(base.copy(type = BlockType.CHILD_DATABASE, content = emptyList(), props = base.props.copy(pageId = dbPage.id, databaseId = d?.id)))
                blocks[nb.id] = nb
                bump(nb.id)
                val next = newBlock(BlockType.TEXT, b.parentBlockId, keyAfter(nb))
                blocks[next.id] = next
                persist(listOf(nb, next))
                publish { it.copy(focus = focusOn(next.id, 0)) }
            }
            else -> {
                val type = action.blockType ?: BlockType.TEXT
                val props = when (type) {
                    BlockType.CALLOUT -> base.props.copy(glyph = base.props.glyph ?: "◆")
                    BlockType.CODE -> base.props.copy(language = base.props.language ?: "plain")
                    else -> base.props
                }
                val nb = touch(base.copy(type = type, props = props))
                blocks[nb.id] = nb
                bump(nb.id)
                persist(listOf(nb))
                publish { it.copy(focus = focusOn(nb.id, nb.text.length)) }
            }
        }
    }

    // ---------- Block props ----------

    private fun updateProps(id: String, transform: (BlockProps) -> BlockProps) {
        val b = blocks[id] ?: return
        val nb = touch(b.copy(props = transform(b.props)))
        blocks[id] = nb
        persist(listOf(nb))
        publish()
    }

    fun setChecked(id: String, checked: Boolean) = updateProps(id) { it.copy(checked = checked) }
    fun toggleCollapsed(id: String) = updateProps(id) { it.copy(collapsed = !it.collapsed) }
    fun setGlyph(id: String, glyph: String) = updateProps(id) { it.copy(glyph = glyph) }
    fun setLanguage(id: String, language: String) = updateProps(id) { it.copy(language = language) }
    fun setImageWidth(id: String, fraction: Float) = updateProps(id) { it.copy(widthFraction = fraction) }
    fun setCaption(id: String, text: String) = updateProps(id) { it.copy(caption = RichText.of(text)) }
    fun setLinkPreview(id: String, url: String, title: String, description: String) =
        updateProps(id) { it.copy(url = url.trim(), title = title.trim().ifEmpty { null }, description = description.trim().ifEmpty { null }) }

    fun updateTable(id: String, table: TableData) {
        val b = blocks[id] ?: return
        val normalized = table.normalized()
        blocks[id] = b.copy(props = b.props.copy(table = normalized))
        pendingTables[id] = normalized
        markDirty()
        _state.update { it.copy(blocks = HashMap(blocks)) }
        tableJob?.cancel()
        tableJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flushTables()
        }
    }

    private suspend fun flushTables() {
        if (pendingTables.isEmpty()) return
        val ids = pendingTables.keys.toList()
        pendingTables.clear()
        val ups = ids.mapNotNull { blocks[it] }.map { touch(it) }
        writeLock.withLock { c.blocks.apply(pageId, ups, emptyList()) }
    }

    fun attachMedia(id: String, uri: java.io.File, image: Boolean) = viewModelScope.launch {
        try {
            val m = if (image) c.media.importImage(uri) else c.media.importFile(uri)
            updateProps(id) {
                if (image) it.copy(mediaPath = m.name, imageWidth = m.width, imageHeight = m.height, mimeType = m.mimeType)
                else it.copy(mediaPath = m.name, fileName = m.displayName, fileSize = m.size, mimeType = m.mimeType)
            }
        } catch (e: MediaTooLargeException) {
            _events.send(EditorEvent.Message("Files over 20 MB can't be added"))
        } catch (e: Exception) {
            _events.send(EditorEvent.Message("Couldn't add that file"))
        }
    }

    // ---------- Page actions ----------

    fun setIcon(icon: String?) = viewModelScope.launch { c.pages.setIcon(pageId, icon?.takeIf { it.isNotBlank() }) }
    fun setCover(cover: String?) = viewModelScope.launch { c.pages.setCover(pageId, cover) }

    fun setCoverImage(uri: java.io.File) = viewModelScope.launch {
        try {
            val m = c.media.importImage(uri)
            c.pages.setCover(pageId, app.monoworkspace.model.Covers.image(m.name))
        } catch (e: Exception) {
            _events.send(EditorEvent.Message("Couldn't use that image"))
        }
    }

    fun setFavorite(fav: Boolean) = viewModelScope.launch { c.pages.setFavorite(pageId, fav) }

    fun trashPage(onDone: () -> Unit) = viewModelScope.launch {
        flush()
        c.pages.trash(pageId)
        onDone()
    }

    fun saveAsTemplate(name: String) = viewModelScope.launch {
        flush()
        val ok = c.templates.saveAsTemplate(pageId, name)
        _events.send(EditorEvent.Message(if (ok) "Saved as template" else "Couldn't save template"))
    }

    fun restoreVersion(versionId: String) = viewModelScope.launch {
        flush()
        c.blocks.restoreVersion(versionId)
        versions.keys.toList().forEach { bump(it) }
        val list = c.blocks.load(pageId)
        blocks.clear()
        list.forEach { blocks[it.id] = it; bump(it.id) }
        pendingTitle = null
        val page = c.pages.get(pageId)
        publish { it.copy(title = page?.title.orEmpty(), focus = null, selection = emptySet(), selectionMode = false) }
        _events.send(EditorEvent.Message("Version restored"))
    }

    // ---------- Row properties ----------

    fun setRowValue(propertyId: String, value: PropertyValue?) = viewModelScope.launch { c.databases.setValue(pageId, propertyId, value) }

    // ---------- Lifecycle ----------

    /** Writes everything pending now. Called on pause and before leaving. */
    suspend fun flush() {
        saveJob?.cancel()
        titleJob?.cancel()
        tableJob?.cancel()
        flushContent()
        flushTables()
        saveTitle()
        snapshotIfDirty()
    }

    fun flushAsync() {
        c.appScope.launch { flush() }
    }

    override fun onCleared() {
        // viewModelScope is gone; finish the writes on the app scope.
        c.appScope.launch { flush() }
        super.onCleared()
    }

    /** Text of the current row cell for display (helper for previews). */
    fun cellText(cell: CellValue?): String = cell?.let { ValueFormat.display(it, null) }.orEmpty()

    companion object {
        const val SAVE_DEBOUNCE_MS = 300L
        const val IDLE_SNAPSHOT_MS = 30_000L
    }
}
