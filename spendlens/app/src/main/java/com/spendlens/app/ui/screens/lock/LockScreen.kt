package com.spendlens.app.ui.screens.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendlens.app.lock.AppLock
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.theme.ControlStyle
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Four dots and a number pad. [onComplete] gets the full PIN and says whether it was accepted;
 * a wrong one shakes the dots and clears them.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    onComplete: (String) -> Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
    onBiometric: (() -> Unit)? = null,
    resetKey: Any = Unit,
) {
    val colors = Spend.ink
    val look = Spend.look
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var entered by remember(resetKey) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }

    fun press(d: String) {
        if (entered.length >= AppLock.PIN_LENGTH) return
        haptics.tick()
        entered += d
        if (entered.length == AppLock.PIN_LENGTH) {
            val pin = entered
            scope.launch {
                delay(120)
                if (onComplete(pin)) {
                    haptics.confirm()
                } else {
                    haptics.reject()
                    shake.animateTo(0f, keyframes {
                        durationMillis = 360
                        -14f at 50
                        14f at 110
                        -10f at 170
                        8f at 230
                        -4f at 290
                    })
                }
                entered = ""
            }
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LensMark(Modifier.size(44.dp))
        Spacer(Modifier.height(20.dp))
        Text(caps(title), style = MaterialTheme.typography.headlineMedium, color = colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Label(subtitle, color = colors.muted, style = MaterialTheme.typography.labelLarge)
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Label(error, color = colors.alert)
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier
                .offset(x = shake.value.dp)
                .semantics { contentDescription = "${entered.length} of ${AppLock.PIN_LENGTH} digits entered" },
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(AppLock.PIN_LENGTH) { i ->
                val filled = i < entered.length
                val scale by animateFloatAsState(if (filled) 1f else 0.72f, label = "pinDot")
                Box(
                    Modifier
                        .size((14 * scale).dp)
                        .then(if (filled) Modifier.background(colors.accent, CircleShape) else Modifier.border(1.5.dp, colors.lineStrong, CircleShape)),
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { k ->
                        val keyShape = CircleShape
                        Box(
                            Modifier
                                .size(72.dp)
                                .then(
                                    when (k) {
                                        "bio", "del" -> Modifier
                                        else -> if (look.control == ControlStyle.PILL) Modifier.background(colors.ghost, keyShape) else Modifier.border(1.dp, colors.line, keyShape)
                                    },
                                )
                                .then(
                                    when {
                                        k == "bio" && onBiometric == null -> Modifier
                                        k == "bio" -> Modifier.pressable(pressedScale = 0.9f) { onBiometric?.invoke() }
                                        k == "del" -> Modifier.pressable(pressedScale = 0.9f, haptic = false) {
                                            if (entered.isNotEmpty()) {
                                                haptics.tick()
                                                entered = entered.dropLast(1)
                                            }
                                        }
                                        else -> Modifier.pressable(pressedScale = 0.9f, haptic = false) { press(k) }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (k) {
                                "bio" -> if (onBiometric != null) Icon(Icons.Rounded.Fingerprint, "Unlock with fingerprint", tint = colors.text, modifier = Modifier.size(30.dp))
                                "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete digit", tint = colors.muted, modifier = Modifier.size(24.dp))
                                else -> Text(k, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A lens: circle plus handle — the app mark, drawn in the ink colour. */
@Composable
fun LensMark(modifier: Modifier = Modifier, progress: Float = 1f) {
    val colors = Spend.ink
    Canvas(modifier) {
        val stroke = size.minDimension * 0.09f
        val r = size.minDimension * 0.32f
        val c = Offset(size.width * 0.42f, size.height * 0.42f)
        drawArc(
            colors.text, -90f, 360f * progress.coerceIn(0f, 1f), false,
            Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val h = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (h > 0f) {
            val start = Offset(c.x + r * 0.72f, c.y + r * 0.72f)
            val end = Offset(start.x + size.width * 0.26f * h, start.y + size.height * 0.26f * h)
            drawLine(colors.accent, start, end, stroke * 1.2f, StrokeCap.Round)
        }
    }
}

/** Full-screen lock shown over the app. */
@Composable
fun LockScreen(lock: AppLock, onBiometric: (() -> Unit)?) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    BackHandler { (context as? android.app.Activity)?.moveTaskToBack(true) }
    LaunchedEffect(Unit) { onBiometric?.invoke() }
    LockContent(
        onPin = { pin ->
            val ok = lock.tryPin(pin)
            if (!ok) {
                attempt++
                val wait = lock.waitSeconds()
                error = if (wait > 0) "Too many tries — wait $wait seconds" else "Wrong PIN"
            }
            ok
        },
        error = error,
        onBiometric = onBiometric,
    )
}

@Composable
fun LockContent(onPin: (String) -> Boolean, error: String?, onBiometric: (() -> Unit)?) {
    Screen {
        PinPad(
            title = "SpendLens is locked",
            subtitle = "Enter your PIN",
            onComplete = onPin,
            error = error,
            onBiometric = onBiometric,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 72.dp),
        )
    }
}

/** Choose a PIN (twice), or confirm the current one. Full-screen dialog over Settings. */
@Composable
fun PinDialog(mode: PinMode, check: (String) -> Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Screen {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    BracketButton("Cancel", onClick = onDismiss, color = Spend.ink.muted)
                }
                val (title, subtitle) = when {
                    mode == PinMode.CONFIRM -> "Enter your PIN" to "To make this change"
                    first == null -> "Choose a PIN" to "Four digits you'll remember"
                    else -> "Enter it again" to "To be sure"
                }
                PinPad(
                    title = title,
                    subtitle = subtitle,
                    error = error,
                    resetKey = first ?: "",
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                    onComplete = { pin ->
                        when {
                            mode == PinMode.CONFIRM -> check(pin).also { ok -> if (ok) onDone(pin) else error = "Wrong PIN" }
                            first == null -> {
                                first = pin
                                error = null
                                true
                            }
                            pin == first -> {
                                onDone(pin)
                                true
                            }
                            else -> {
                                first = null
                                error = "Didn't match — start again"
                                false
                            }
                        }
                    },
                )
            }
        }
    }
}

enum class PinMode { CHOOSE, CONFIRM }
