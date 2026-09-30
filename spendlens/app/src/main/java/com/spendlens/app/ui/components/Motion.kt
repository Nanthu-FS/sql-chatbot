package com.spendlens.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Looping animations (ticker ribbon, scan line) are skipped when the system "Remove animations"
 * setting is on — and in tests, which need the UI to go idle.
 */
object MotionSettings {
    @Volatile var loops: Boolean = true
}

/** Material "emphasized" curves — quick start, long soft landing. */
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Springs with a visible overshoot — used for the "bounce back" on release. */
fun <T> bouncy() = spring<T>(dampingRatio = 0.38f, stiffness = 480f)
fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1400f)

/** Thin wrapper over the platform haptics so every control feels the same. */
class Haptics(private val view: View) {
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK,
    )
    fun click() = perform(HapticFeedbackConstants.VIRTUAL_KEY)
    fun press() = perform(HapticFeedbackConstants.KEYBOARD_TAP)
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun edge() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.LONG_PRESS)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/**
 * Press → shrink quickly with a light tap; release → spring back past 1.0 and settle.
 * No ripple: the motion is the feedback.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    haptic: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val haptics = rememberHaptics()
    LaunchedEffect(interaction) {
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    if (haptic) haptics.press()
                    launch { scale.animateTo(pressedScale, snappy()) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel ->
                    launch { scale.animateTo(1f, bouncy()) }
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role) {
        if (haptic) haptics.click()
        onClick()
    }
}

/**
 * Staggered entrance: fade up from 18dp. Plays once per item (state survives scrolling in lazy lists).
 */
fun Modifier.reveal(order: Int = 0): Modifier = composed {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    val distance = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(40L * order.coerceAtMost(10))
            progress.animateTo(1f, tween(560, easing = EmphasizedDecelerate))
            played = true
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * distance
    }
}
