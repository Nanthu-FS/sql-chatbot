package com.smartnotes.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartnotes.ui.board.BoardScreen
import com.smartnotes.ui.capture.MeetingScreen
import com.smartnotes.ui.capture.VoiceScreen
import com.smartnotes.ui.clips.ClipsScreen
import com.smartnotes.ui.editor.EditorScreen
import com.smartnotes.ui.history.TimeTravelScreen
import com.smartnotes.ui.search.SearchScreen
import com.smartnotes.ui.home.HomeScreen
import com.smartnotes.ui.settings.SettingsScreen
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.SkinLabel
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val HOME = "home"
    fun note(id: Long) = "note/$id"
    fun board(id: Long) = "board/$id"
    fun history(id: Long) = "history/$id"
    fun search(q: String = "") = "search?q=${Uri.encode(q)}"
    const val CLIPS = "clips"
    const val SETTINGS = "settings"
    const val VOICE = "voice"
    const val MEETING = "meeting"
}

/** Opens a freshly created note in place of the capture screen that made it. */
fun NavController.openNoteReplacing(id: Long) = navigate(Routes.note(id)) {
    popUpTo(Routes.HOME)
}

@Composable
fun AppNav(vm: MainViewModel, openNote: MutableStateFlow<Long?>) {
    val nav = rememberNavController()
    val pending by openNote.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { nav.navigate(Routes.note(it)); openNote.value = null }
    }
    val idArg = listOf(navArgument("id") { type = NavType.LongType })

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable("note/{id}", idArg) { EditorScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("board/{id}", idArg) { BoardScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("history/{id}", idArg) { TimeTravelScreen(vm, nav, it.arguments!!.getLong("id")) }
            composable("search?q={q}", listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) {
                SearchScreen(vm, nav, it.arguments?.getString("q").orEmpty())
            }
            composable(Routes.CLIPS) { ClipsScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.VOICE) { VoiceScreen(vm, nav) }
            composable(Routes.MEETING) { MeetingScreen(vm, nav) }
        }
        StatusOverlay(vm, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatusOverlay(vm: MainViewModel, modifier: Modifier) {
    val t = LocalSkin.current
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    if (busy != null) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Box(Modifier.background(t.surface).padding(20.dp)) { SkinLabel(busy!!, color = t.onSurface) }
        }
    }
    message?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(5_000); vm.clearMessage() }
        Snackbar(
            modifier = modifier.padding(16.dp).padding(bottom = 72.dp),
            containerColor = t.onBackground,
            contentColor = t.background,
            action = { TextButton(onClick = vm::clearMessage) { Text("OK", color = t.background) } },
        ) { Text(msg) }
    }
}
