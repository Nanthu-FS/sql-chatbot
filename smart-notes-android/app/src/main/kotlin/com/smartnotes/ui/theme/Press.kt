package com.smartnotes.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Press state for skin controls. We draw our own feedback instead of the Material ripple,
 * which flashed grey over the flat, high-contrast themes.
 */
class Press(val source: MutableInteractionSource, val pressed: Boolean)

@Composable
fun rememberPress(): Press {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return Press(source, pressed)
}

/**
 * Shadowed controls sink into their shadow while pressed; flat ones dim slightly.
 * Apply before the shadow/background so the whole control moves together.
 */
fun Modifier.pressFeedback(press: Press, shadow: Dp): Modifier = graphicsLayer {
    if (press.pressed) {
        if (shadow > 0.dp) {
            translationX = shadow.toPx()
            translationY = shadow.toPx()
        } else {
            alpha = 0.6f
        }
    }
}

fun Modifier.pressClick(
    press: Press,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    label: String? = null,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = press.source,
    indication = null,
    enabled = enabled,
    onClickLabel = label,
    role = role,
    onClick = onClick,
)
