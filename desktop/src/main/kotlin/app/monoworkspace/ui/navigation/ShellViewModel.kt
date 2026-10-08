package app.monoworkspace.ui.navigation

import app.monoworkspace.ui.common.SavedStateHandle
import app.monoworkspace.ui.common.ViewModel
import app.monoworkspace.AppContainer
import app.monoworkspace.data.repo.CircularNestingException
import app.monoworkspace.model.Page
import app.monoworkspace.model.TemplatePayload
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShellState(
    val workspaceName: String = "My Workspace",
    val tree: List<Page> = emptyList(),
    val favorites: List<Page> = emptyList(),
    val trashCount: Int = 0,
    val sidebarCollapsed: Boolean = false,
    val sidebarWidthDp: Int = 280,
    val loaded: Boolean = false,
)

class ShellViewModel(private val c: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    val state: StateFlow<ShellState> = combine(
        c.settings.settings,
        c.pages.observeTree(),
        c.pages.observeFavorites(),
        c.pages.observeTrash().map { it.size },
    ) { settings, tree, favorites, trash ->
        ShellState(settings.workspaceName, tree, favorites, trash, settings.sidebarCollapsed, settings.sidebarWidthDp, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellState())

    val expanded: StateFlow<List<String>> = handle.getStateFlow(KEY_EXPANDED, emptyList())

    fun toggleExpanded(id: String) {
        val cur = expanded.value
        handle[KEY_EXPANDED] = if (id in cur) cur - id else cur + id
    }

    fun expand(id: String) {
        val cur = expanded.value
        if (id !in cur) handle[KEY_EXPANDED] = cur + id
    }

    fun createPage(parentId: String?, onCreated: (Page) -> Unit) = viewModelScope.launch {
        val page = c.pages.createPage(parentId)
        parentId?.let { expand(it) }
        onCreated(page)
    }

    fun createDatabase(parentId: String?, onCreated: (Page) -> Unit) = viewModelScope.launch {
        val page = c.databases.createDatabase(parentId)
        parentId?.let { expand(it) }
        onCreated(page)
    }

    fun fromTemplate(payload: TemplatePayload, parentId: String?, onCreated: (Page) -> Unit) = viewModelScope.launch {
        val page = c.templates.instantiate(payload, parentId)
        parentId?.let { expand(it) }
        onCreated(page)
    }

    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch { c.pages.setFavorite(id, favorite) }

    fun moveFavorite(id: String, up: Boolean) = viewModelScope.launch { c.pages.moveFavorite(state.value.favorites, id, up) }

    fun trash(id: String, onDone: () -> Unit) = viewModelScope.launch {
        c.pages.trash(id)
        onDone()
    }

    fun restore(id: String) = viewModelScope.launch { c.pages.restore(id) }

    fun move(id: String, parentId: String?, onResult: (String?) -> Unit) = viewModelScope.launch {
        try {
            c.pages.move(id, parentId)
            parentId?.let { expand(it) }
            onResult(null)
        } catch (e: CircularNestingException) {
            onResult("A page can't be moved inside itself")
        } catch (e: IllegalArgumentException) {
            onResult(e.message ?: "Can't move this page")
        }
    }

    fun setSidebarCollapsed(collapsed: Boolean) = viewModelScope.launch { c.settings.setSidebarCollapsed(collapsed) }
    fun setSidebarWidth(dp: Int) = viewModelScope.launch { c.settings.setSidebarWidth(dp) }

    companion object {
        private const val KEY_EXPANDED = "expanded"
    }
}
