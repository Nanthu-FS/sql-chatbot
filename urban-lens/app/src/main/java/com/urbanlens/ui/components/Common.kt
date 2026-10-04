package com.urbanlens.ui.components

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbanlens.core.air.AqiCategory
import com.urbanlens.core.crowd.CrowdLevel
import com.urbanlens.data.ReportRepository
import com.urbanlens.ui.theme.UrbanColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

fun aqiColor(aqi: Int): Color = UrbanColors.hex(AqiCategory.of(aqi).color)

fun crowdColor(level: Int): Color = UrbanColors.hex(CrowdLevel.of(level).color)

fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

fun formatDistance(meters: Double): String =
    if (meters < 1000) "${(meters / 10).roundToInt() * 10} m" else String.format(Locale.getDefault(), "%.1f km", meters / 1000)

fun hourLabel(hour: Int): String = when {
    hour == 0 -> "12 am"
    hour < 12 -> "$hour am"
    hour == 12 -> "12 pm"
    else -> "${hour - 12} pm"
}

fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Rounded dark card used for every overlay on the map. */
@Composable
fun MapCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = UrbanColors.Surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, UrbanColors.Outline),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = UrbanColors.Muted,
        modifier = modifier,
    )
}

/** A small colored dot with a label, e.g. "● Busy". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        } else {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = UrbanColors.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A labelled number, e.g. "AQI / 64 / Moderate". */
@Composable
fun Metric(label: String, value: String, caption: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        SectionLabel(label)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Loads a downscaled photo from disk off the main thread. */
@Composable
fun rememberPhoto(path: String?, maxPx: Int = 900): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = ReportRepository.sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
                    }
                    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}
