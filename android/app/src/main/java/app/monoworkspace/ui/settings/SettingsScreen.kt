package app.monoworkspace.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.monoworkspace.AppContainer
import app.monoworkspace.BuildConfig
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
    fun setLock(on: Boolean) = viewModelScope.launch { c.settings.setAppLock(on) }

    fun storage(): StorageUsage {
        val dbFile = c.appContext.getDatabasePath(MonoDatabase.NAME)
        return StorageUsage(c.export.databaseBytes(dbFile), c.export.mediaBytes())
    }

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { c.appContext.contentResolver.openOutputStream(uri)?.use { c.export.exportBackup(it) } ?: error("No stream") }.isSuccess
        onDone(ok)
    }

    fun exportJson(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { c.appContext.contentResolver.openOutputStream(uri)?.use { c.export.exportJson(it) } ?: error("No stream") }.isSuccess
        onDone(ok)
    }

    fun restoreBackup(uri: Uri, onDone: (String?) -> Unit) = viewModelScope.launch {
        val r = runCatching {
            c.appContext.contentResolver.openInputStream(uri)?.use { c.export.restoreBackup(it, c.appContext.cacheDir) } ?: error("Can't open the file")
        }
        onDone(r.exceptionOrNull()?.message?.let { it.ifBlank { "Restore failed" } })
    }
}

@Composable
fun SettingsScreen() {
    val vm = monoViewModel { c, h -> SettingsViewModel(c, h) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val messenger = LocalMessenger.current
    val layout = LocalWindowLayout.current
    val context = LocalContext.current
    var name by remember(settings.workspaceName) { mutableStateOf(settings.workspaceName) }
    var usage by remember { mutableStateOf(vm.storage()) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            busy = true
            vm.exportBackup(uri) { ok -> busy = false; messenger.show(if (ok) "Backup saved" else "Backup failed") }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportJson(uri) { ok -> messenger.show(if (ok) "JSON exported" else "Export failed") }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    LaunchedEffect(busy) { if (!busy) usage = vm.storage() }

    Column(Modifier.fillMaxSize()) {
        MonoTopBar(
            height = layout.topBarHeight,
            navigation = {
                if (layout.usesSidebar) MonoIconButton(MonoIcons.Back, "Back", nav::back)
                else MonoIconButton(MonoIcons.Menu, "Open navigation", nav::openDrawer)
            },
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
            FormRow("Theme", caption = "Light only. Mono is black on white by design.") { LabelText("Fixed", color = MonoColors.Tertiary) }

            SectionHeader("Privacy", Modifier.padding(top = Space.xl))
            FormRow("App lock", caption = "Fingerprint, face or device PIN on start and after 60 s away") {
                MonoSwitch(settings.appLock, { on ->
                    if (on) {
                        val can = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        if (can == BiometricManager.BIOMETRIC_SUCCESS) vm.setLock(true)
                        else messenger.show("Set up a screen lock on this device first")
                    } else vm.setLock(false)
                }, label = "App lock")
            }
            FormRow("Network", caption = "None. This app has no internet permission; nothing leaves the device.") {
                MonoIcon(MonoIcons.Lock, null, tint = MonoColors.Secondary)
            }

            SectionHeader("Data", Modifier.padding(top = Space.xl))
            InkRow(
                "Export backup", { backupLauncher.launch("MonoWorkspace-${LocalDate.now()}.monobackup") },
                caption = "One .monobackup file with every page and all media",
                leading = { MonoIcon(MonoIcons.Export, null) },
            )
            InkRow(
                "Restore backup", { restoreLauncher.launch(arrayOf("*/*")) },
                caption = "Replaces all data in this app",
                leading = { MonoIcon(MonoIcons.Import, null) },
            )
            InkRow(
                "Export as JSON", { jsonLauncher.launch("MonoWorkspace-${LocalDate.now()}.json") },
                caption = "Structured dump of pages, databases and rows",
                leading = { MonoIcon(MonoIcons.File, null) },
            )

            SectionHeader("Storage", Modifier.padding(top = Space.xl))
            StorageBar(usage)

            SectionHeader("About", Modifier.padding(top = Space.xl))
            FormRow("Version") { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MonoType.bodySmall.tnum().copy(color = MonoColors.Secondary)) }
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
