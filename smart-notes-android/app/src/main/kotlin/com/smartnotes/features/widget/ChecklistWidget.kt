package com.smartnotes.features.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.smartnotes.app
import com.smartnotes.core.Checklist
import com.smartnotes.ui.MainActivity

/** Home-screen checklist that stays in sync with the note pinned to it. */
class ChecklistWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val note = context.app.repo.widgetNote()
        val items = note?.let { Checklist.items(it.body) }.orEmpty()
        val ink = ColorProvider(day = Color(0xFF111111), night = Color(0xFFF5F5F5))
        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(ColorProvider(day = Color.White, night = Color(0xFF1A1A1A))).padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text(
                    note?.title ?: "Pin a note's checklist from the note's menu",
                    style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                )
                LazyColumn {
                    items(items, itemId = { it.lineIndex.toLong() }) { item ->
                        CheckBox(
                            checked = item.done,
                            onCheckedChange = actionRunCallback<ToggleItemAction>(
                                actionParametersOf(ToggleItemAction.NOTE to note!!.id, ToggleItemAction.LINE to item.lineIndex),
                            ),
                            text = item.text,
                            style = TextStyle(color = ink, fontSize = 14.sp),
                        )
                    }
                }
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = ChecklistWidget().updateAll(context)
    }
}

class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NOTE] ?: return
        val line = parameters[LINE] ?: return
        val repo = context.app.repo
        val note = repo.get(noteId) ?: return
        repo.save(note.copy(body = Checklist.toggle(note.body, line)))
        ChecklistWidget().update(context, glanceId)
    }

    companion object {
        val NOTE = ActionParameters.Key<Long>("note")
        val LINE = ActionParameters.Key<Int>("line")
    }
}

class ChecklistWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()
}
