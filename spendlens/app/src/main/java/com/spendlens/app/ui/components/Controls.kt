package com.spendlens.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Txn
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.theme.SpendTheme

/** Pill segmented control with a gradient thumb that springs between options. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpendTheme.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.subtle)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "thumb",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(8.dp, CircleShape, ambientColor = colors.brand[0], spotColor = colors.brand[0])
                .clip(CircleShape)
                .background(colors.brandBrush),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val textColor by animateColorAsState(
                    if (index == selectedIndex) Color.White else colors.textMuted,
                    label = "tabText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = textColor, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/** Floating glass-like nav pill plus a big gradient scan button. */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpendTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(66.dp)
                .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(colors.card)
                .border(1.dp, colors.cardBorder, CircleShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == currentRoute
                val background by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    label = "navBg",
                )
                val tint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else colors.textMuted,
                    label = "navTint",
                )
                Row(
                    Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(24.dp))
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Text(
                            item.label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(66.dp)
                .shadow(20.dp, CircleShape, ambientColor = colors.brand[1], spotColor = colors.brand[1])
                .clip(CircleShape)
                .background(colors.brandBrush)
                .bounceClick(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DocumentScanner, contentDescription = "Scan payments", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun TransactionRow(
    txn: Txn,
    currency: CurrencyOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val colors = SpendTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(txn.category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val timeText = if (showDate) Format.relative(txn.dateTime) else Format.time(txn.dateTime)
            Text(
                "${txn.category.label} · $timeText",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("-" + currency.format(txn.amountMinor), style = MaterialTheme.typography.titleSmall)
            if (txn.paymentApp != null) {
                Text(txn.paymentApp, style = MaterialTheme.typography.labelSmall, color = colors.textFaint, maxLines = 1)
            }
        }
    }
}
