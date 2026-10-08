package app.monoworkspace.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.monoworkspace.ui.components.MonoSegmented
import app.monoworkspace.ui.components.hoverFocus
import app.monoworkspace.ui.components.rememberHoverFocusState
import app.monoworkspace.ui.theme.CardSurface
import app.monoworkspace.ui.theme.LocalTheme
import app.monoworkspace.ui.theme.ProvideSurface
import app.monoworkspace.ui.theme.SideSurface
import app.monoworkspace.ui.theme.ThemeSpec
import app.monoworkspace.ui.theme.Themes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.common.SavedStateHandle
import app.monoworkspace.ui.common.ViewModel
import androidx.compose.runtime.collectAsState
import app.monoworkspace.AppContainer
import app.monoworkspace.desktop.AppInfo
import app.monoworkspace.ui.common.LocalComposeWindow
import app.monoworkspace.ui.common.NativeFiles
import java.io.File
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.repo.AppSettings
import app.monoworkspace.ui.common.Formats
import app.monoworkspace.ui.common.LocalNavigator
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.monoViewModel
import app.monoworkspace.ui.components.FormRow
import app.monoworkspace.ui.components.InkRow
import app.monoworkspace.ui.components.LabelText
import app.monoworkspace.ui.components.LocalMessenger
import app.monoworkspace.ui.components.MonoDialog
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.MonoIconButton
import app.monoworkspace.ui.components.MonoSwitch
import app.monoworkspace.ui.components.MonoTextField
import app.monoworkspace.ui.components.MonoTopBar
import app.monoworkspace.ui.components.SectionHeader
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween
import app.monoworkspace.ui.theme.tnum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StorageUsage(val databaseBytes: Long, val mediaBytes: Long) {
    val total: Long get() = databaseBytes + mediaBytes
}

class SettingsViewModel(private val c: AppContainer, @Suppress("unused") handle: SavedStateHandle) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setName(name: String) = viewModelScope.launch { c.settings.setWorkspaceName(name) }
    fun enableLock(pin: String) = viewModelScope.launch { c.settings.enableLock(pin) }
    fun disableLock() = viewModelScope.launch { c.settings.disableLock() }
    fun setReduceMotion(on: Boolean) = viewModelScope.launch { c.settings.setReduceMotion(on) }
    fun setThemeMode(mode: String) = viewModelScope.launch { c.settings.setThemeMode(mode) }
    fun setAmbient(on: Boolean) = viewModelScope.launch { c.settings.setAmbientEffects(on) }
    fun chooseTheme(t: ThemeSpec) = viewModelScope.launch { c.settings.chooseTheme(t.id, t.isDark) }
    val dataDir: File get() = c.dataDir

    fun storage(): StorageUsage = StorageUsage(c.database.sizeBytes(), c.export.mediaBytes())

    fun exportBackup(file: File, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { file.outputStream().use { c.export.exportBackup(it) } }.isSuccess
        onDone(ok)
    }

    fun exportJson(file: File, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { file.outputStream().use { c.export.exportJson(it) } }.isSuccess
        onDone(ok)
    }

    fun restoreBackup(file: File, onDone: (String?) -> Unit) = viewModelScope.launch {
        val r = runCatching { file.inputStream().use { c.export.restoreBackup(it, c.cacheDir) } }
        c.database.save()
        onDone(r.exceptionOrNull()?.let { (it.message ?: "").ifBlank { "Restore failed" } })
    }
}

@Composable
fun SettingsScreen() {
    val vm = monoViewModel { c, h -> SettingsViewModel(c, h) }
    val settings by vm.settings.collectAsState()
    val nav = LocalNavigator.current
    val messenger = LocalMessenger.current
    val layout = LocalWindowLayout.current
    val window = LocalComposeWindow.current
    var name by remember(settings.workspaceName) { mutableStateOf(settings.workspaceName) }
    var usage by remember { mutableStateOf(vm.storage()) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pinSetup by remember { mutableStateOf(false) }

    fun exportBackup() {
        val f = NativeFiles.save(window, "Export backup", "MonoWorkspace-${LocalDate.now()}.monobackup") ?: return
        busy = true
        vm.exportBackup(f) { ok -> busy = false; messenger.show(if (ok) "Backup saved to ${f.name}" else "Backup failed") }
    }
    fun exportJson() {
        val f = NativeFiles.save(window, "Export as JSON", "MonoWorkspace-${LocalDate.now()}.json") ?: return
        vm.exportJson(f) { ok -> messenger.show(if (ok) "JSON exported" else "Export failed") }
    }
    fun restore() {
        pendingRestore = NativeFiles.open(window, "Restore backup", listOf("monobackup", "zip"))
    }

    LaunchedEffect(busy) { if (!busy) usage = vm.storage() }

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = { MonoIconButton(MonoIcons.Back, "Back (Alt+Left)", nav::back) },
            title = { LabelText("Settings") },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.margin)
                .widthIn(max = 720.dp)
                .padding(bottom = Space.x4),
        ) {
            Text("Settings", Modifier.padding(top = Space.xl, bottom = Space.l), style = MonoType.display)

            SectionHeader("Workspace")
            MonoTextField(
                name, { name = it.take(80) },
                Modifier.padding(vertical = Space.m),
                label = "Name",
                imeDone = { vm.setName(name); messenger.show("Workspace renamed") },
            )
            if (name != settings.workspaceName && name.isNotBlank()) {
                app.monoworkspace.ui.components.MonoButton("Save name", { vm.setName(name); messenger.show("Workspace renamed") }, height = 40.dp)
            }

            SectionHeader("Appearance", Modifier.padding(top = Space.xl))
            AppearanceSection(settings, vm)

            SectionHeader("Privacy", Modifier.padding(top = Space.xl))
            FormRow("App lock", caption = "PIN on start, after 5 minutes in the background, or with Ctrl+L") {
                MonoSwitch(settings.appLock, { on ->
                    if (on) pinSetup = true else { vm.disableLock(); messenger.show("App lock off") }
                }, label = "App lock")
            }
            FormRow("Network", caption = "None. The app never opens a network connection; nothing leaves this PC.") {
                MonoIcon(MonoIcons.Lock, null, tint = MonoColors.Secondary)
            }

            SectionHeader("Data", Modifier.padding(top = Space.xl))
            InkRow(
                "Export backup", ::exportBackup,
                caption = "One .monobackup file with every page and all media. Opens on Android too.",
                leading = { MonoIcon(MonoIcons.Export, null) },
            )
            InkRow(
                "Restore backup", ::restore,
                caption = "Replaces all data in this app. Accepts backups from the Android app.",
                leading = { MonoIcon(MonoIcons.Import, null) },
            )
            InkRow(
                "Export as JSON", ::exportJson,
                caption = "Structured dump of pages, databases and rows",
                leading = { MonoIcon(MonoIcons.File, null) },
            )
            InkRow(
                "Open data folder", { if (!NativeFiles.reveal(vm.dataDir)) messenger.show(vm.dataDir.absolutePath) },
                caption = vm.dataDir.absolutePath,
                leading = { MonoIcon(MonoIcons.Storage, null) },
            )

            SectionHeader("Storage", Modifier.padding(top = Space.xl))
            StorageBar(usage)

            SectionHeader("About", Modifier.padding(top = Space.xl))
            FormRow("Version") { Text("${AppInfo.VERSION} · Windows", style = MonoType.bodySmall.tnum().copy(color = MonoColors.Secondary)) }
            FormRow("Shortcuts", caption = AppInfo.SHORTCUTS) { MonoIcon(MonoIcons.Info, null, tint = MonoColors.Secondary) }
        }
    }

    if (pinSetup) {
        PinSetupDialog(onDismiss = { pinSetup = false }) { pin ->
            pinSetup = false
            vm.enableLock(pin)
            messenger.show("App lock on")
        }
    }

    pendingRestore?.let { uri ->
        MonoDialog(
            onDismiss = { pendingRestore = null },
            title = "Replace all data?",
            body = "Everything in this app is replaced by the backup. This can't be undone. Consider exporting a backup first.",
            confirmLabel = "Replace",
            destructive = true,
            onConfirm = {
                pendingRestore = null
                busy = true
                vm.restoreBackup(uri) { err ->
                    busy = false
                    messenger.show(err ?: "Backup restored")
                    if (err == null) nav.openHome()
                }
            },
        )
    }
}

@Composable
private fun StorageBar(usage: StorageUsage) {
    val total = usage.total.coerceAtLeast(1)
    val dbFraction by animateFloatAsState(usage.databaseBytes.toFloat() / total, monoTween(Motion.SLOW), label = "db")
    Column(Modifier.padding(vertical = Space.m)) {
        Row {
            Text("Used", Modifier.weight(1f), style = MonoType.body)
            Text(Formats.bytes(usage.total), style = MonoType.body.tnum())
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth().height(8.dp).background(MonoColors.Hairline)) {
            Box(Modifier.fillMaxHeight().weight(dbFraction.coerceAtLeast(0.001f)).background(MonoColors.Ink))
            Box(Modifier.fillMaxHeight().weight((1f - dbFraction).coerceAtLeast(0.001f)).background(MonoColors.Secondary))
        }
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(10.dp).height(10.dp).background(MonoColors.Ink))
            Text("  Database ${Formats.bytes(usage.databaseBytes)}", style = MonoType.caption.tnum())
            Spacer(Modifier.width(Space.l))
            Box(Modifier.width(10.dp).height(10.dp).background(MonoColors.Secondary))
            Text("  Media ${Formats.bytes(usage.mediaBytes)}", style = MonoType.caption.tnum())
        }
    }
}

/** Asks for a new PIN twice. */
@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..12 && pin.all { it.isDigit() }
    val error = when {
        pin.isNotEmpty() && !pin.all { it.isDigit() } -> "Digits only"
        confirm.isNotEmpty() && confirm != pin -> "PINs don't match"
        else -> null
    }
    MonoDialog(
        onDismiss = onDismiss,
        title = "Set a PIN",
        body = "4 to 12 digits. You'll need it to open the app. There is no recovery: if you forget it, restore a backup.",
        confirmLabel = "Turn on",
        onConfirm = { if (valid && confirm == pin) onSet(pin) },
    ) {
        app.monoworkspace.ui.components.MonoTextField(pin, { pin = it.take(12) }, label = "PIN", password = true)
        Spacer(Modifier.height(Space.m))
        app.monoworkspace.ui.components.MonoTextField(confirm, { confirm = it.take(12) }, label = "Repeat PIN", password = true, imeDone = { if (valid && confirm == pin) onSet(pin) })
        if (error != null) Text(error, Modifier.padding(top = Space.s), style = MonoType.caption.copy(color = MonoColors.Destructive))
    }
}

private val modes = listOf("system", "light", "dark")

@Composable
private fun AppearanceSection(settings: AppSettings, vm: SettingsViewModel) {
    val current = LocalTheme.current
    FormRow("Mode", caption = "Follow Windows switches between your light and dark theme with the system.") {
        MonoSegmented(modes, settings.themeMode, { when (it) { "system" -> "Follow Windows"; "light" -> "Light"; else -> "Dark" } }, vm::setThemeMode)
    }
    Text(
        "Pick a light and a dark theme. Each brings its own motion: backdrops, transitions, selection and celebrations.",
        Modifier.padding(top = Space.m, bottom = Space.m),
        style = MonoType.caption,
    )
    val focus = rememberHoverFocusState()
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Themes.all.forEach { t ->
            val inUse = if (t.isDark) settings.darkTheme == t.id else settings.lightTheme == t.id
            ThemeCard(t, inUse = inUse, showing = current.id == t.id, focus = focus) { vm.chooseTheme(t) }
        }
    }
    FormRow("Ambient effects", caption = "Looping backdrops and the pointer trail. They pause when the window is in the background.", modifier = Modifier.padding(top = Space.l)) {
        MonoSwitch(settings.ambientEffects, vm::setAmbient, label = "Ambient effects")
    }
    FormRow("Reduce motion", caption = "Turns off transitions, blur, springs and all theme effects. Also follows Windows' animation setting.") {
        MonoSwitch(settings.reduceMotion, vm::setReduceMotion, label = "Reduce motion")
    }
}

/** A live miniature of [theme], drawn in the theme's own colors and fonts. */
@Composable
private fun ThemeCard(theme: ThemeSpec, inUse: Boolean, showing: Boolean, focus: app.monoworkspace.ui.components.HoverFocusState, onPick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val outline = MonoColors.Ink
    val ring by animateFloatAsState(if (showing) 1f else 0f, monoTween(Motion.MEDIUM), label = "ring")
    Column(
        Modifier
            .width(212.dp)
            .hoverable(interaction)
            .hoverFocus(focus, theme.id, interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interaction, null, onClick = onPick)
            .drawBehind {
                if (ring > 0f) {
                    val inset = -5.dp.toPx()
                    drawRect(outline.copy(alpha = ring), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(2.dp.toPx()))
                }
            },
    ) {
        CompositionLocalProvider(LocalTheme provides theme) {
            ProvideSurface(theme.main) {
                val shape = RoundedCornerShape(theme.radius)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .clip(shape)
                        .background(MonoColors.Background)
                        .border(1.dp, MonoColors.Hairline, shape),
                ) {
                    // Sidebar strip with a selected row.
                    SideSurface {
                        Column(Modifier.width(52.dp).fillMaxHeight().background(theme.sideBrush).padding(top = 14.dp)) {
                            repeat(4) { i ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .background(if (i == 1) MonoColors.Tint else androidx.compose.ui.graphics.Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                ) { Box(Modifier.fillMaxWidth(if (i == 1) 0.9f else 0.6f).fillMaxHeight().background(MonoColors.Secondary)) }
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(10.dp)) {
                        Text("Good evening.", style = MonoType.display.copy(fontSize = 19.sp, lineHeight = 21.sp), maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.size(34.dp, 22.dp).clip(RoundedCornerShape(theme.radius * 0.6f)).background(theme.hotBrush))
                            CardSurface {
                                repeat(2) {
                                    Box(Modifier.size(34.dp, 22.dp).clip(RoundedCornerShape(theme.radius * 0.6f)).background(MonoColors.Background).border(1.dp, MonoColors.Hairline, RoundedCornerShape(theme.radius * 0.6f)))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(2) {
                                Box(Modifier.size(48.dp, 34.dp).clip(RoundedCornerShape(theme.radius * 0.6f)).drawBehind { drawRect(theme.cover(size)) })
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Text(theme.name, Modifier.weight(1f), style = MonoType.bodySmall.copy(fontWeight = FontWeight.SemiBold))
            Text(if (theme.isDark) "DARK" else "LIGHT", style = MonoType.label.copy(color = MonoColors.Tertiary, fontSize = 10.sp))
        }
        Text(theme.tagline, style = MonoType.caption, maxLines = 2)
        if (inUse) Text(if (showing) "SHOWING NOW" else "IN USE FOR ${if (theme.isDark) "DARK" else "LIGHT"} MODE", Modifier.padding(top = 2.dp), style = MonoType.label.copy(fontSize = 10.sp))
    }
}
