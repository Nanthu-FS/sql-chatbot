package app.monoworkspace.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.common.BackHandler
import app.monoworkspace.ui.components.MonoButton
import app.monoworkspace.ui.components.MonoButtonStyle
import app.monoworkspace.ui.components.MonoIcon
import app.monoworkspace.ui.components.SectionRule
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Covers the whole window until the PIN is entered. Each digit fills a
 * square; a wrong PIN shakes the row and clears it. Repeated failures add a
 * growing wait.
 */
@Composable
fun LockScreen(check: (String) -> Boolean, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var failures by remember { mutableIntStateOf(0) }
    var waitUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val reduce = LocalReduceMotion.current
    BackHandler { }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    val waiting = now < waitUntil

    fun submit() {
        if (waiting || pin.length < 4) return
        if (check(pin)) {
            onUnlocked()
            return
        }
        failures++
        pin = ""
        if (failures >= 5) waitUntil = System.currentTimeMillis() + 30_000L * (failures - 4)
        scope.launch {
            if (reduce) return@launch
            for (x in listOf(18f, -16f, 12f, -8f, 4f, 0f)) shake.animateTo(x, tween(45))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MonoColors.Background)
            .clickable(remember { MutableInteractionSource() }, null) { focus.requestFocus() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = Space.xl)) {
            MonoIcon(MonoIcons.Lock, "Locked", size = 40.dp)
            Spacer(Modifier.height(Space.xl))
            Text("Mono\nWorkspace", style = MonoType.display)
            Spacer(Modifier.height(Space.xl))
            SectionRule()
            Spacer(Modifier.height(Space.xl))
            Text(
                if (waiting) "Too many tries. Wait ${((waitUntil - now) / 1000) + 1} s." else if (failures > 0) "Wrong PIN. Try again." else "Enter your PIN",
                style = MonoType.label.copy(color = if (failures > 0) MonoColors.Destructive else MonoColors.Secondary),
            )
            Spacer(Modifier.height(Space.m))
            Box {
                Row(Modifier.graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    repeat(maxOf(6, pin.length)) { i -> PinCell(filled = i < pin.length, active = i == pin.length) }
                }
                // Invisible field that owns the keyboard input.
                BasicTextField(
                    pin,
                    { v -> if (!waiting) pin = v.filter { it.isDigit() }.take(12) },
                    Modifier
                        .size(1.dp)
                        .graphicsLayer { alpha = 0f }
                        .focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                                submit()
                                true
                            } else false
                        },
                    singleLine = true,
                    textStyle = MonoType.body.copy(color = Color.Transparent),
                )
            }
            Spacer(Modifier.height(Space.xl))
            MonoButton("Unlock", ::submit, Modifier.fillMaxWidth(), style = MonoButtonStyle.Filled, icon = MonoIcons.Lock, height = 52.dp, enabled = !waiting && pin.length >= 4)
        }
    }
}

@Composable
private fun PinCell(filled: Boolean, active: Boolean) {
    val fill by animateFloatAsState(if (filled) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 900f), label = "pin")
    Box(
        Modifier
            .size(40.dp)
            .border(if (active) 2.dp else 1.dp, if (active) MonoColors.Ink else MonoColors.Hairline),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp).graphicsLayer { scaleX = fill; scaleY = fill }.background(MonoColors.Ink))
    }
}
