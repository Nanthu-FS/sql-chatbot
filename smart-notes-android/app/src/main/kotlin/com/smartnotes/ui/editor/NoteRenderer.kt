package com.smartnotes.ui.editor

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.smartnotes.features.Weather
import com.smartnotes.features.WeatherNow
import com.smartnotes.ui.linkify
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinLabel
import kotlinx.coroutines.delay

private val TIMER = Regex("^\\{\\{\\s*timer\\s+(\\d+)\\s*}}$", RegexOption.IGNORE_CASE)
private val WEATHER = Regex("^\\{\\{\\s*weather\\s+(.+?)\\s*}}$", RegexOption.IGNORE_CASE)
private val TODO = Regex("^\\s*- \\[( |x|X)] (.*)$")

/**
 * Renders a note body: headings, checklists, [[links]], Mermaid diagrams and live widgets.
 * Reports each line's on-screen bounds so the gesture layer can target lines.
 */
@Composable
fun NoteRenderer(
    body: String,
    onToggleLine: (Int) -> Unit,
    onOpenLink: (String) -> Unit,
    onLineBounds: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalSkin.current
    val lines = body.lines()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val index = i
            val line = lines[i]
            val track = Modifier.onGloballyPositioned { onLineBounds(index, it.boundsInRoot()) }
            when {
                line.trimStart().startsWith("```mermaid") -> {
                    val end = (i + 1 until lines.size).firstOrNull { lines[it].trim() == "```" } ?: lines.size
                    MermaidBlock(lines.subList(i + 1, end).joinToString("\n"))
                    i = end
                }
                line.trimStart().startsWith("```") -> {
                    val end = (i + 1 until lines.size).firstOrNull { lines[it].trim() == "```" } ?: lines.size
                    Text(
                        lines.subList(i + 1, end).joinToString("\n"),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(t.highlight).padding(10.dp),
                    )
                    i = end
                }
                TIMER.matches(line.trim()) -> TimerWidget(TIMER.find(line.trim())!!.groupValues[1].toInt())
                WEATHER.matches(line.trim()) -> WeatherWidget(WEATHER.find(line.trim())!!.groupValues[1])
                line.startsWith("# ") -> Text(line.removePrefix("# "), track, fontFamily = t.display, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                line.startsWith("## ") -> Text(line.removePrefix("## "), track.padding(top = 6.dp), fontFamily = t.display, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                line.startsWith("### ") -> Text(line.removePrefix("### "), track, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                TODO.matches(line) -> {
                    val m = TODO.find(line)!!
                    val done = m.groupValues[1].isNotBlank()
                    Row(track.fillMaxWidth().clickable { onToggleLine(index) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = done, onCheckedChange = { onToggleLine(index) },
                            colors = CheckboxDefaults.colors(checkedColor = t.accent, checkmarkColor = t.onAccent, uncheckedColor = t.muted),
                        )
                        Text(
                            linkify(m.groupValues[2], t.accent, onOpenLink),
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            color = if (done) t.muted else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                    }
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ->
                    Text(linkify("•  " + line.trimStart().drop(2), t.accent, onOpenLink), track.padding(start = 8.dp))
                line.isBlank() -> Spacer(track.height(4.dp))
                else -> Text(linkify(line, t.accent, onOpenLink), track, lineHeight = 22.sp)
            }
            i++
        }
    }
}

@Composable
private fun TimerWidget(minutes: Int) {
    var remaining by remember(minutes) { mutableIntStateOf(minutes * 60) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && remaining > 0) { delay(1_000); remaining-- }
        if (remaining == 0) running = false
    }
    SkinCard(title = "Timer") {
        Text("%02d:%02d".format(remaining / 60, remaining % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkinButton(if (running) "Pause" else "Start", { running = !running })
            SkinButton("Reset", { running = false; remaining = minutes * 60 }, primary = false)
        }
    }
}

@Composable
private fun WeatherWidget(city: String) {
    val state by produceState<Result<WeatherNow>?>(null, city) {
        value = runCatching { Weather.now(city) }
    }
    SkinCard(title = "Weather · $city") {
        when (val s = state) {
            null -> SkinLabel("Loading…")
            else -> s.fold(
                onSuccess = { Text("${"%.0f".format(it.tempC)}° ${it.summary}", fontSize = 26.sp, fontWeight = FontWeight.Bold) },
                onFailure = { SkinLabel("Couldn't load weather: ${it.message}") },
            )
        }
    }
}

/** Renders Mermaid with mermaid.js in a WebView. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidBlock(code: String) {
    val escaped = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val html = """
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script></head>
        <body style="margin:0;background:#fff"><pre class="mermaid">$escaped</pre>
        <script>mermaid.initialize({ startOnLoad: true, theme: 'neutral' });</script></body></html>
    """.trimIndent()
    SkinCard(title = "Diagram") {
        AndroidView(
            factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true } },
            update = { it.loadDataWithBaseURL("https://smartnotes.local/", html, "text/html", "utf-8", null) },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
    }
}
