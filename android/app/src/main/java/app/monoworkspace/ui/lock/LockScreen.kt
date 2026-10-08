package app.monoworkspace.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space

/** Shows only the app name and a lock glyph until the user authenticates. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val margin = LocalWindowLayout.current.margin
    BackHandler { }
    Column(
        Modifier
            .fillMaxSize()
            .background(MonoColors.Background)
            // Swallow touches so nothing underneath can be used.
            .clickable(remember { MutableInteractionSource() }, null) { }
            .padding(horizontal = margin),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            MonoIcon(MonoIcons.Lock, "Locked", size = 48.dp)
            Spacer(Modifier.height(Space.xl))
            Text("Mono\nWorkspace", style = MonoType.display)
        }
        SectionRule()
        Box(Modifier.fillMaxWidth().padding(vertical = Space.l)) {
            MonoButton("Unlock", onUnlock, Modifier.fillMaxWidth(), style = MonoButtonStyle.Filled, icon = MonoIcons.Lock, height = 56.dp)
        }
    }
}
