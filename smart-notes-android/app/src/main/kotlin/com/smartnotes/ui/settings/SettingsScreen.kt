package com.smartnotes.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartnotes.R
import com.smartnotes.features.capture.QuickCaptureTileService
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.Skin
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinChip
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinTheme
import com.smartnotes.ui.theme.Skins
import com.smartnotes.ui.theme.skinBackdrop

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavController) {
    val context = LocalContext.current
    val current by vm.skin.collectAsState()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshContext() }

    SkinScaffold(title = "Settings", onBack = { nav.popBackStack() }) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkinLabel("Theme")
            Skin.entries.forEach { skin ->
                ThemePreview(skin, selected = skin == current) { vm.prefs.setSkin(skin) }
            }

            SkinLabel("Permissions", Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkinChip("Microphone", { permission.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) })
                SkinChip("Calendar", { permission.launch(arrayOf(Manifest.permission.READ_CALENDAR)) })
                SkinChip("Location", { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (Build.VERSION.SDK_INT >= 33) SkinChip("Notifications", { permission.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) })
                SkinChip("Location all the time", {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }

            SkinLabel("Lock screen capture", Modifier.padding(top = 12.dp))
            Text("Add the “Quick note” tile to Quick Settings. It opens over the lock screen and only lets you add notes.", fontSize = 13.sp, color = LocalSkin.current.muted)
            if (Build.VERSION.SDK_INT >= 33) {
                SkinButton("Add Quick Settings tile", {
                    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                        ComponentName(context, QuickCaptureTileService::class.java),
                        context.getString(R.string.quick_note),
                        Icon.createWithResource(context, R.drawable.ic_tile),
                        context.mainExecutor,
                    ) { }
                }, primary = false)
            }
        }
    }
}

/** Each option is drawn in its own skin, so you see exactly what you'll get. */
@Composable
private fun ThemePreview(skin: Skin, selected: Boolean, onSelect: () -> Unit) {
    val outer = LocalSkin.current
    Box(
        Modifier.fillMaxWidth()
            .border(if (selected) 3.dp else 1.dp, if (selected) outer.accent else outer.muted.copy(alpha = 0.5f))
            .clickable(role = Role.RadioButton, onClick = onSelect),
    ) {
        SkinTheme(skin) {
            val t = Skins.tokens(skin)
            Column(Modifier.fillMaxWidth().skinBackdrop(t).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (t.upperCaseTitles) skin.label.uppercase() else skin.label,
                        Modifier.weight(1f), fontFamily = t.display, fontWeight = FontWeight.Black, fontSize = 20.sp, color = t.onBackground,
                    )
                    if (selected) Text("✓ IN USE", fontFamily = t.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.onBackground)
                }
                SkinCard(title = "API redesign") {
                    Text(skin.blurb, fontSize = 13.sp)
                }
            }
        }
    }
}
