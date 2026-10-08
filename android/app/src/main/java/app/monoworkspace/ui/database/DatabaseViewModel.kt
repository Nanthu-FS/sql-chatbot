package app.monoworkspace.ui.database

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.monoworkspace.AppContainer
import app.monoworkspace.data.repo.CsvTarget
import app.monoworkspace.data.repo.DatabaseQuery
import app.monoworkspace.data.repo.DatabaseSnapshot
import app.monoworkspace.data.repo.ViewResult
import app.monoworkspace.engine.Csv
import app.monoworkspace.engine.Dates
import app.monoworkspace.engine.FilterEngine
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SortDirection
import app.monoworkspace.model.SortRule
import app.monoworkspace.model.ViewConfig
import app.monoworkspace.model.ViewType
import app.monoworkspace.ui.navigation.DatabaseRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class DbUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val snapshot: DatabaseSnapshot? = null,
    val view: DatabaseView? = null,
    val result: ViewResult? = null,
    val selection: Set<String> = emptySet(),
    val title: String = "",
)

data class CsvImport(val headers: List<String>, val rows: List<List<String>>)

class DatabaseViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {
    val pageId: String = handle.toRoute<DatabaseRoute>().pageId
    private val activeViewId: StateFlow<String?> = handle.getStateFlow(KEY_VIEW, null)
    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val localTitle = MutableStateFlow<String?>(null)
    private var titleJob: Job? = null

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private data class Computed(val snapshot: DatabaseSnapshot?, val view: DatabaseView?, val result: ViewResult?, val loaded: Boolean)

    private val computed: Flow<Computed> = combine(c.databases.observeSnapshot(pageId), activeViewId) { snap, viewId ->
        if (snap == null) return@combine Computed(null, null, null, true)
        val view = snap.views.firstOrNull { it.id == viewId } ?: snap.views.firstOrNull()
        Computed(snap, view, view?.let { DatabaseQuery.run(snap, it) }, true)
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<DbUiState> = combine(computed, selection, localTitle) { comp, sel, title ->
        val rowIds = comp.snapshot?.rows?.map { it.id }?.toSet().orEmpty()
        DbUiState(
            loading = !comp.loaded,
            missing = comp.loaded && (comp.snapshot == null || comp.snapshot.page.isTrashed),
            snapshot = comp.snapshot,
            view = comp.view,
            result = comp.result,
            selection = sel.filter { it in rowIds }.toSet(),
            title = title ?: comp.snapshot?.page?.title.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DbUiState())

    init {
        viewModelScope.launch {
            c.pages.visit(pageId)
            c.settings.setLastOpened(pageId)
        }
    }

    private val databaseId: String? get() = state.value.snapshot?.database?.id

    // ---------- Header ----------

    fun setTitle(t: String) {
        localTitle.value = t.replace("\n", "")
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(300)
            c.pages.rename(pageId, localTitle.value.orEmpty())
            localTitle.value = null
        }
    }

    fun setIcon(icon: String?) = viewModelScope.launch { c.pages.setIcon(pageId, icon) }
    fun setFavorite(fav: Boolean) = viewModelScope.launch { c.pages.setFavorite(pageId, fav) }

    fun trash(onDone: () -> Unit) = viewModelScope.launch {
        c.pages.trash(pageId)
        onDone()
    }

    // ---------- Views ----------

    fun selectView(id: String) {
        handle[KEY_VIEW] = id
        selection.value = emptySet()
    }

    fun addView(type: ViewType) = viewModelScope.launch {
        val id = databaseId ?: return@launch
        val v = c.databases.addView(id, type)
        selectView(v.id)
    }

    fun renameView(view: DatabaseView, name: String) = viewModelScope.launch {
        c.databases.updateView(view.copy(name = name.trim().ifEmpty { view.type.label }.take(60)))
    }

    fun deleteView(view: DatabaseView) = viewModelScope.launch {
        if (!c.databases.deleteView(view)) _messages.send("A database needs at least one view")
        else if (activeViewId.value == view.id) handle[KEY_VIEW] = null
    }

    fun updateConfig(transform: (ViewConfig) -> ViewConfig) {
        val v = state.value.view ?: return
        viewModelScope.launch { c.databases.updateView(v.copy(config = transform(v.config))) }
    }

    fun setColumnWidth(propertyId: String, width: Int) = updateConfig { it.copy(columnWidths = it.columnWidths + (propertyId to width.coerceIn(72, 480))) }

    fun sortBy(propertyId: String, direction: SortDirection) = updateConfig { cfg ->
        cfg.copy(sorts = (listOf(SortRule(propertyId, direction)) + cfg.sorts.filter { it.propertyId != propertyId }).take(3))
    }

    fun hideProperty(propertyId: String) = updateConfig { cfg ->
        val all = state.value.snapshot?.database?.schema?.properties?.map { it.id }.orEmpty()
        val visible = (cfg.visibleProperties ?: all).filter { it != propertyId }
        cfg.copy(visibleProperties = visible)
    }

    fun setVisible(propertyId: String, visible: Boolean) = updateConfig { cfg ->
        val all = state.value.snapshot?.database?.schema?.properties?.map { it.id }.orEmpty()
        val current = cfg.visibleProperties ?: all
        cfg.copy(visibleProperties = if (visible) (current + propertyId).distinct() else current - propertyId)
    }

    fun moveProperty(propertyId: String, up: Boolean) = updateConfig { cfg ->
        val snap = state.value.snapshot ?: return@updateConfig cfg
        val view = state.value.view ?: return@updateConfig cfg
        val order = DatabaseQuery.orderedProperties(snap, view).map { it.id }.toMutableList()
        val i = order.indexOf(propertyId)
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in order.indices) return@updateConfig cfg
        order.removeAt(i)
        order.add(j, propertyId)
        cfg.copy(propertyOrder = order)
    }

    // ---------- Rows ----------

    fun addRow(extra: Map<String, PropertyValue> = emptyMap(), title: String = "", onCreated: (Page) -> Unit = {}) = viewModelScope.launch {
        val snap = state.value.snapshot ?: return@launch
        val view = state.value.view
        val defaults = view?.let { FilterEngine.defaultsFor(it.config.filter, snap.database.schema) }.orEmpty()
        val row = c.databases.addRow(snap.database.id, title, defaults + extra)
        onCreated(row)
    }

    fun setValue(rowId: String, propertyId: String, value: PropertyValue?) = viewModelScope.launch { c.databases.setValue(rowId, propertyId, value) }

    fun renameRow(rowId: String, title: String) = viewModelScope.launch { c.pages.rename(rowId, title) }

    fun duplicate(rowId: String) = viewModelScope.launch { c.databases.duplicateRow(rowId) }

    fun trashRows(ids: Collection<String>, onDone: (Int) -> Unit = {}) = viewModelScope.launch {
        c.databases.trashRows(ids)
        selection.value = emptySet()
        onDone(ids.size)
    }

    fun restoreRows(ids: Collection<String>) = viewModelScope.launch { ids.forEach { c.pages.restore(it) } }

    fun bulkSet(propertyId: String, value: PropertyValue?) = viewModelScope.launch {
        c.databases.setValueForRows(state.value.selection, propertyId, value)
        selection.value = emptySet()
    }

    fun toggleSelected(id: String) = selection.update { if (id in it) it - id else it + id }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll() { selection.value = state.value.result?.rows?.map { it.id }?.toSet().orEmpty() }

    /** Board drag: move a card to another group's value. */
    fun moveToGroup(rowId: String, prop: PropertyDef, groupKey: String?) {
        val value: PropertyValue? = when (prop.type) {
            PropertyType.SELECT, PropertyType.STATUS -> groupKey?.let { PropertyValue.Select(it) }
            PropertyType.MULTI_SELECT -> groupKey?.let { PropertyValue.Multi(listOf(it)) }
            PropertyType.CHECKBOX -> PropertyValue.Checkbox(groupKey == "true")
            else -> return
        }
        setValue(rowId, prop.id, value)
    }

    /** Calendar drag: move a row to another day, keeping its time and span. */
    fun reschedule(rowId: String, prop: PropertyDef, day: LocalDate) {
        val snap = state.value.snapshot ?: return
        val row = snap.rows.firstOrNull { it.id == rowId } ?: return
        val stored = row.values[prop.id] as? PropertyValue.DateValue
        val zone = ZoneId.systemDefault()
        val value = if (stored == null) PropertyValue.DateValue(day.toString()) else {
            val parsed = Dates.parse(stored, zone) ?: return
            val shift = java.time.temporal.ChronoUnit.DAYS.between(parsed.start.toLocalDate(), day)
            PropertyValue.DateValue(
                Dates.store(parsed.start.plusDays(shift), stored.includeTime),
                parsed.end?.let { Dates.store(it.plusDays(shift), stored.includeTime) },
                stored.includeTime,
            )
        }
        setValue(rowId, prop.id, value)
    }

    // ---------- Import / export ----------

    fun exportCsv(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val snap = state.value.snapshot ?: return@launch
        val view = state.value.view ?: return@launch
        val ok = runCatching {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(c.export.viewCsv(snap, view)) }
            }
        }.isSuccess
        _messages.send(if (ok) "Exported CSV" else "Export failed")
    }

    fun exportZip(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        val snap = state.value.snapshot ?: return@launch
        val view = state.value.view ?: return@launch
        val ok = runCatching {
            withContext(Dispatchers.IO) { resolver.openOutputStream(uri)?.use { c.export.writeDatabaseExport(snap, view, it) } }
        }.isSuccess
        _messages.send(if (ok) "Exported database" else "Export failed")
    }

    suspend fun readCsv(resolver: ContentResolver, uri: Uri): CsvImport? = withContext(Dispatchers.IO) {
        runCatching {
            val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@runCatching null
            val rows = Csv.parse(text)
            if (rows.isEmpty()) null else CsvImport(rows.first(), rows.drop(1))
        }.getOrNull()
    }

    fun importCsv(data: CsvImport, mapping: List<CsvTarget>) = viewModelScope.launch {
        val id = databaseId ?: return@launch
        val n = c.databases.importCsv(id, data.rows, mapping)
        _messages.send("Imported $n row${if (n == 1) "" else "s"}")
    }

    companion object {
        private const val KEY_VIEW = "view"
    }
}
