package app.monoworkspace.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monoworkspace.model.Page
import app.monoworkspace.ui.components.Hairline
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.MenuItem
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoMenu
import app.monoworkspace.ui.components.PageGlyph
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.components.inkClickable
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween
import app.monoworkspace.ui.theme.tnum

/** Callbacks for everything the sidebar can do. */
data class SidebarActions(
    val onSearch: () -> Unit,
    val onHome: () -> Unit,
    val onNew: () -> Unit,
    val onTemplates: () -> Unit,
    val onTrash: () -> Unit,
    val onSettings: () -> Unit,
    val onOpen: (Page) -> Unit,
    val onToggle: (String) -> Unit,
    val onAddChild: (Page) -> Unit,
    val onFavorite: (Page, Boolean) -> Unit,
    val onMoveFavorite: (Page, Boolean) -> Unit,
    val onMove: (Page) -> Unit,
    val onTrashPage: (Page) -> Unit,
    val onCollapse: (() -> Unit)? = null,
)

private data class TreeRow(val page: Page, val depth: Int, val hasChildren: Boolean, val expanded: Boolean)

private fun flattenTree(pages: List<Page>, expanded: Set<String>): List<TreeRow> {
    val ids = pages.map { it.id }.toSet()
    val byParent = pages.groupBy { p -> p.parentId?.takeIf { it in ids } }
    val out = ArrayList<TreeRow>()
    fun visit(parent: String?, depth: Int) {
        for (p in byParent[parent].orEmpty().sortedBy { it.orderKey }) {
            val kids = byParent[p.id].orEmpty()
            val open = p.id in expanded
            out.add(TreeRow(p, depth, kids.isNotEmpty(), open))
            if (open) visit(p.id, depth + 1)
        }
    }
    visit(null, 0)
    return out
}

/** Workspace navigation shared by the phone drawer and the tablet/landscape sidebar. */
@Composable
fun WorkspaceSidebar(
    state: ShellState,
    expanded: Set<String>,
    currentPageId: String?,
    currentSection: String?,
    actions: SidebarActions,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.tree, expanded) { flattenTree(state.tree, expanded) }
    LazyColumn(modifier.fillMaxHeight().background(MonoColors.Background)) {
        item("header") {
            Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs, top = Space.xl, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    LabelText("Workspace", color = MonoColors.Secondary)
                    Text(state.workspaceName, style = MonoType.h3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                actions.onCollapse?.let { MonoIconButton(MonoIcons.Sidebar, "Collapse sidebar", it) }
            }
            SectionRule()
        }
        item("primary") {
            Column(Modifier.padding(vertical = Space.s)) {
                NavItem(MonoIcons.Search, "Search", currentSection == "search", actions.onSearch)
                NavItem(MonoIcons.Home, "Home", currentSection == "home", actions.onHome)
                NavItem(MonoIcons.Plus, "New", false, actions.onNew)
                NavItem(MonoIcons.Template, "Templates", false, actions.onTemplates)
            }
            Hairline()
        }
        if (state.favorites.isNotEmpty()) {
            item("fav-h") { SidebarLabel("Favorites") }
            items(state.favorites, key = { "fav-" + it.id }) { p ->
                TreeItem(
                    TreeRow(p, 0, false, false), p.id == currentPageId, actions,
                    leadingIcon = MonoIcons.StarFilled,
                    extraMenu = listOf(
                        MenuItem("Move up", MonoIcons.ChevronUp) { actions.onMoveFavorite(p, true) },
                        MenuItem("Move down", MonoIcons.ChevronDown) { actions.onMoveFavorite(p, false) },
                    ),
                )
            }
            item("fav-rule") { Hairline(Modifier.padding(top = Space.s)) }
        }
        item("pages-h") {
            Row(Modifier.fillMaxWidth().padding(end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SidebarLabel("Pages") }
                MonoIconButton(MonoIcons.Plus, "New page", actions.onNew, iconSize = 20.dp)
            }
        }
        if (rows.isEmpty()) {
            item("empty") { Text("No pages yet.", Modifier.padding(horizontal = Space.l, vertical = Space.s), style = MonoType.bodySmall.copy(color = MonoColors.Secondary)) }
        }
        items(rows, key = { "tree-" + it.page.id }) { row -> TreeItem(row, row.page.id == currentPageId, actions) }
        item("footer") {
            Spacer(Modifier.height(Space.l))
            SectionRule()
            Column(Modifier.padding(vertical = Space.s)) {
                NavItem(MonoIcons.Trash, "Trash", currentSection == "trash", actions.onTrash, badge = state.trashCount.takeIf { it > 0 }?.toString())
                NavItem(MonoIcons.Settings, "Settings", currentSection == "settings", actions.onSettings)
            }
        }
    }
}

@Composable
private fun SidebarLabel(text: String) {
    LabelText(text, Modifier.padding(start = Space.l, top = Space.l, bottom = Space.s), color = MonoColors.Secondary)
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .inkClickable(onClick = onClick, selected = selected)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MonoColors.Ink)
        Spacer(Modifier.width(Space.m))
        Text(label, Modifier.weight(1f), style = MonoType.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
        if (badge != null) Text(badge, style = MonoType.caption.tnum())
    }
}

@Composable
private fun TreeItem(
    row: TreeRow,
    selected: Boolean,
    actions: SidebarActions,
    leadingIcon: ImageVector? = null,
    extraMenu: List<MenuItem> = emptyList(),
) {
    val p = row.page
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(if (row.expanded) 90f else 0f, monoTween(Motion.FAST), label = "chev")
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .inkClickable(onClick = { actions.onOpen(p) }, onLongClick = { menu = true }, selected = selected)
                .padding(start = Space.s + (16 * row.depth).dp, end = Space.xs)
                .semantics { contentDescription = "${p.displayTitle}, level ${row.depth + 1}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(28.dp).inkClickable(onClick = { actions.onToggle(p.id) }, showBar = false, enabled = row.hasChildren),
                contentAlignment = Alignment.Center,
            ) {
                if (row.hasChildren) {
                    Icon(MonoIcons.ChevronRight, if (row.expanded) "Collapse" else "Expand", Modifier.size(18.dp).rotate(rotation), tint = MonoColors.Secondary)
                } else if (leadingIcon != null) {
                    Icon(leadingIcon, null, Modifier.size(16.dp), tint = MonoColors.Ink)
                }
            }
            PageGlyph(p.icon, p.isDatabase, Modifier.padding(end = Space.s), size = 18.dp)
            Text(
                p.displayTitle,
                Modifier.weight(1f),
                style = MonoType.bodySmall.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MonoIconButton(MonoIcons.More, "Page actions", { menu = true }, tint = if (hovered || selected) MonoColors.Ink else MonoColors.Tertiary, iconSize = 18.dp)
        }
        MonoMenu(
            menu, { menu = false },
            extraMenu + listOf(
                MenuItem("Add subpage", MonoIcons.Plus) { actions.onAddChild(p) },
                if (p.isFavorite) MenuItem("Remove from favorites", MonoIcons.Star) { actions.onFavorite(p, false) }
                else MenuItem("Add to favorites", MonoIcons.Star) { actions.onFavorite(p, true) },
                MenuItem("Move to…", MonoIcons.Swap) { actions.onMove(p) },
                MenuItem("Move to trash", MonoIcons.Trash, destructive = true) { actions.onTrashPage(p) },
            ),
        )
    }
}

/** Collapsed sidebar: icons only. */
@Composable
fun SidebarRail(actions: SidebarActions, onExpand: () -> Unit, currentSection: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().width(64.dp).background(MonoColors.Background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(Space.s))
        MonoIconButton(MonoIcons.Sidebar, "Expand sidebar", onExpand)
        SectionRule(Modifier.padding(vertical = Space.s))
        MonoIconButton(MonoIcons.Search, "Search", actions.onSearch, selected = currentSection == "search")
        MonoIconButton(MonoIcons.Home, "Home", actions.onHome, selected = currentSection == "home")
        MonoIconButton(MonoIcons.Plus, "New", actions.onNew)
        MonoIconButton(MonoIcons.Template, "Templates", actions.onTemplates)
        Spacer(Modifier.weight(1f))
        MonoIconButton(MonoIcons.Trash, "Trash", actions.onTrash, selected = currentSection == "trash")
        MonoIconButton(MonoIcons.Settings, "Settings", actions.onSettings, selected = currentSection == "settings")
        Spacer(Modifier.height(Space.s))
    }
}

/** Sheet listing pages to pick a new parent from. */
@Composable
fun PagePickerList(
    pages: List<Page>,
    excludeSubtreeOf: String?,
    onPick: (String?) -> Unit,
    rootLabel: String = "Workspace (top level)",
) {
    val excluded = remember(pages, excludeSubtreeOf) {
        if (excludeSubtreeOf == null) emptySet() else {
            val byParent = pages.groupBy { it.parentId }
            val out = HashSet<String>()
            val stack = ArrayDeque(listOf(excludeSubtreeOf))
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                if (out.add(id)) byParent[id].orEmpty().forEach { stack.add(it.id) }
            }
            out
        }
    }
    val rows = remember(pages) { flattenTree(pages, pages.map { it.id }.toSet()) }
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).inkClickable(onClick = { onPick(null) }).padding(horizontal = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MonoIcons.Home, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Space.m))
            Text(rootLabel, style = MonoType.body)
        }
        Hairline()
        rows.filter { it.page.id !in excluded && !it.page.isDatabase }.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .inkClickable(onClick = { onPick(r.page.id) })
                    .padding(start = Space.l + (16 * r.depth).dp, end = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageGlyph(r.page.icon, r.page.isDatabase, size = 18.dp)
                Spacer(Modifier.width(Space.m))
                Text(r.page.displayTitle, style = MonoType.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
