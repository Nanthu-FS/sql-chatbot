package com.spendlens.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.spendlens.app.MainActivity
import com.spendlens.app.SpendLensApplication
import com.spendlens.app.notify.Notifier
import com.spendlens.app.notify.SpendSummary

/** Home-screen (and, where supported, lock-screen) widget: today, this month, last seven days. */
class SpendWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = runCatching { (context.applicationContext as SpendLensApplication).container.surfaces.summary() }.getOrNull()
        val scan = Intent(context, MainActivity::class.java).putExtra(Notifier.EXTRA_ACTION, Notifier.ACTION_SCAN)
        provideContent { WidgetBody(summary, scan) }
    }

    companion object {
        val SMALL = DpSize(110.dp, 48.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
        val WIDE = DpSize(260.dp, 110.dp)
    }
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}

private val Paper = ColorProvider(Color(0xFFEDEDEA))
private val Grey = ColorProvider(Color(0xFF8A8A87))
private val Faint = ColorProvider(Color(0xFF55554F))

@Composable
private fun WidgetBody(s: SpendSummary?, scan: Intent) {
    val size = LocalSize.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(18.dp)
            .background(Color(0xF00A0A0A))
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (s == null || !s.hasData) {
            Column {
                Text("SPENDLENS", style = label(Grey))
                Spacer(GlanceModifier.height(4.dp))
                Text("Add a payment to start", style = TextStyle(color = Paper, fontSize = 13.sp))
            }
            return@Box
        }
        val c = s.currency
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("TODAY", style = label(Grey))
                    Text(c.format(s.today), style = TextStyle(color = Paper, fontSize = if (size.height >= MEDIUM_H) 26.sp else 20.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                if (size.width >= SpendWidget.MEDIUM.width) {
                    Box(
                        GlanceModifier
                            .size(32.dp)
                            .cornerRadius(4.dp)
                            .background(Color(0xFFEDEDEA))
                            .clickable(actionStartActivity(scan)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+", style = TextStyle(color = ColorProvider(Color(0xFF0A0A0A)), fontSize = 18.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
            if (size.height >= MEDIUM_H) {
                Spacer(GlanceModifier.defaultWeight())
                Bars(s.last7)
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(s.monthName.uppercase(), style = label(Faint))
                        Text(c.format(s.month), style = TextStyle(color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    }
                    if (size.width >= SpendWidget.WIDE.width) {
                        val left = s.budgetLeft
                        Column(GlanceModifier.defaultWeight()) {
                            Text(if (left == null) "PACE" else if (left >= 0) "LEFT" else "OVER", style = label(Faint))
                            Text(
                                when {
                                    left != null -> c.format(kotlin.math.abs(left))
                                    s.forecast != null -> "≈ " + c.compact(s.forecast)
                                    else -> "—"
                                },
                                style = TextStyle(color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else if (size.width >= SpendWidget.MEDIUM.width) {
                Text("${c.compact(s.month)} in ${s.monthName}", style = label(Grey), maxLines = 1)
            }
        }
    }
}

private val MEDIUM_H = SpendWidget.MEDIUM.height

@Composable
private fun Bars(values: List<Long>) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(GlanceModifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.Vertical.Bottom) {
        values.forEachIndexed { i, v ->
            val h = if (v <= 0) 2 else (4 + 30 * v / max).toInt()
            Box(GlanceModifier.defaultWeight().height(34.dp), contentAlignment = Alignment.BottomCenter) {
                Box(
                    GlanceModifier
                        .width(10.dp)
                        .height(h.dp)
                        .background(if (i == values.lastIndex) Color(0xFFEDEDEA) else Color(0xFF55554F)),
                ) {}
            }
        }
    }
}

private fun label(color: ColorProvider) = TextStyle(color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
