package app.monoworkspace.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.common.SavedStateHandle
import app.monoworkspace.ui.common.ViewModel
import androidx.compose.runtime.collectAsState
import app.monoworkspace.AppContainer
import app.monoworkspace.data.repo.PageRepository
import app.monoworkspace.model.Page
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.BackToTopFor
import app.monoworkspace.ui.components.DeleteButton
import app.monoworkspace.ui.components.EmptyState
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoTopBar
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(private val c: AppContainer, @Suppress("unused") handle: SavedStateHandle) : ViewModel() {
    val items: StateFlow<List<Page>?> = c.pages.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(p: Page, onDone: () -> Unit) = viewModelScope.launch {
        c.pages.restore(p.id)
        onDone()
    }

    fun deleteForever(p: Page) = viewModelScope.launch { c.pages.deleteForever(p.id) }
}

@Composable
fun TrashScreen() {
    val vm = monoViewModel { c, h -> TrashViewModel(c, h) }
    val items by vm.items.collectAsState()
    val nav = LocalNavigator.current
    val messenger = LocalMessenger.current
    val layout = LocalWindowLayout.current
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = {
                if (layout.usesSidebar) MonoIconButton(MonoIcons.Back, "Back", nav::back)
                else MonoIconButton(MonoIcons.Menu, "Open navigation", nav::openDrawer)
            },
            title = { LabelText("Trash") },
        )
        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                item("intro") {
                    Column(Modifier.padding(horizontal = layout.margin).padding(top = Space.xl)) {
                        Text("Trash", style = MonoType.display)
                        Text("Items are deleted forever 30 days after they're trashed.", Modifier.padding(top = Space.xs, bottom = Space.l), style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                        SectionRule()
                    }
                }
                val list = items
                if (list != null && list.isEmpty()) {
                    item("empty") { EmptyState("The trash is empty. Deleted pages, databases and rows wait here.", "Go home", nav::openHome, Modifier.padding(horizontal = layout.margin), icon = MonoIcons.Trash) }
                }
                items(list.orEmpty(), key = { it.id }) { p ->
                    TrashRow(
                        p,
                        onRestore = { vm.restore(p) { messenger.show("Restored “${p.displayTitle}”", "Open") { nav.openPage(p.id) } } },
                        onDelete = { vm.deleteForever(p); messenger.show("Deleted “${p.displayTitle}” forever") },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            BackToTopFor(listState, Modifier.align(Alignment.BottomEnd).padding(Space.l))
        }
    }
}

@Composable
private fun TrashRow(p: Page, onRestore: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    val margin = LocalWindowLayout.current.margin
    val trashedAt = p.trashedAt ?: System.currentTimeMillis()
    val daysLeft = ((trashedAt + PageRepository.TRASH_RETENTION_MS - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    val kind = when {
        p.isDatabase -> "Database"
        p.isRow -> "Row"
        else -> "Page"
    }
    Column(modifier.padding(horizontal = margin)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.m), verticalAlignment = Alignment.Top) {
            PageGlyph(p.icon, p.isDatabase, Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(p.displayTitle, style = MonoType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$kind · $daysLeft day${if (daysLeft == 1L) "" else "s"} left", style = MonoType.caption.tnum())
                Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                    MonoButton("Restore", onRestore, icon = MonoIcons.Restore, height = 44.dp)
                    DeleteButton(onDelete, label = "Delete forever", size = 44.dp, expandedWidth = 168.dp)
                }
            }
        }
        Hairline()
    }
}
