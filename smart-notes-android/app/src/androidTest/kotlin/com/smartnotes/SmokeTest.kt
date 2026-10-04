package com.smartnotes

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartnotes.data.NoteSource
import com.smartnotes.ui.MainActivity
import com.smartnotes.ui.theme.Skin
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks every screen in every theme. A crash fails the test; screenshots land in
 * the app's files/screens directory for review.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SmartNotesApp

    @Before
    fun seed() = runBlocking {
        if (app.repo.all().isNotEmpty()) return@runBlocking
        app.repo.create(
            "API redesign",
            "We decided to move the REST API to GraphQL.\n\n## To do\n- [ ] Draft schema\n- [x] Spike GraphQL\n\nSee [[Sprint plan]]\n\n{{timer 25}}",
        )
        app.repo.create("Sprint plan", "Ship auth refresh and rate limiting on endpoints for the API.\n\n- [ ] Token refresh\n- [ ] Rate limiting")
        app.repo.create("Walk thoughts", "Onboarding should start with a sample note", NoteSource.VOICE)
        app.repo.create("Login flow", "Whiteboard\n\n```mermaid\ngraph TD\n  A[Login] --> B[Token]\n```\n\n{{weather London}}", NoteSource.PHOTO)
        app.repo.captureClip("graphql.org/learn/schema")
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(app.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun setSkin(skin: Skin) {
        rule.runOnUiThread { app.prefs.setSkin(skin) }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun desc(d: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription(d, substring = true)

    @Test
    fun homeInEveryTheme() {
        Skin.entries.forEach { skin ->
            setSkin(skin)
            shot("home_${skin.name}")
        }
    }

    @Test
    fun editorModesInEveryTheme() {
        Skin.entries.forEach { skin ->
            setSkin(skin)
            val label = if (skin == Skin.TERMINAL) "api_redesign" else "API redesign"
            rule.onAllNodesWithText(label, substring = true, ignoreCase = true).onFirst().performClick()
            rule.waitForIdle()
            shot("editor_read_${skin.name}")
            rule.onNodeWithText("WRITE").performClick()
            shot("editor_write_${skin.name}")
            rule.onNodeWithText("DRAW").performClick()
            shot("editor_draw_${skin.name}")
            rule.onNodeWithText("READ").performClick()
            back()
        }
    }

    @Test
    fun newNoteBoardAndHistory() {
        setSkin(Skin.BRUTAL)
        desc("New note").performClick()
        rule.waitForIdle()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Test note")
        fields[1].performTextInput("First paragraph about the API\n\nSecond paragraph\n- [ ] a todo")
        Thread.sleep(1500) // autosave debounce
        shot("new_note_write")
        desc("Board view").performClick()
        shot("board")
        back()
        desc("Time travel").performClick()
        shot("history")
        back()
        back()
        shot("home_after_new_note")
    }

    /** Mirrors the user's recording: chips in Write, switch modes, draw, back out. */
    @Test
    fun widgetsDiagramAndQuickEditing() {
        setSkin(Skin.BRUTAL)
        rule.onAllNodesWithText("Login flow", substring = true, ignoreCase = true).onFirst().performClick()
        Thread.sleep(3000) // weather + mermaid load
        shot("diagram_weather_read")
        back()

        desc("New note").performClick()
        rule.waitForIdle()
        listOf("+ Todo", "+ Heading", "+ Timer", "+ Weather", "+ Diagram").forEach {
            rule.onNodeWithText(it).performScrollTo().performClick()
        }
        shot("chips_write")
        rule.onNodeWithText("READ").performClick()
        Thread.sleep(2000)
        shot("chips_read")
        rule.onNodeWithText("DRAW").performClick()
        shot("chips_draw")
        back()
        Thread.sleep(1500) // tidy-up runs after the editor closes
        shot("home_after_quick_note")
    }

    @Test
    fun searchSettingsClipsVoiceMeeting() {
        setSkin(Skin.TERMINAL)
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("API")
        rule.onAllNodes(hasSetTextAction())[0].performImeAction()
        shot("search")
        back()

        setSkin(Skin.RETRO)
        desc("Settings").performClick()
        shot("settings")
        back()

        desc("Clipboard inbox").performClick()
        shot("clips")
        back()

        setSkin(Skin.ROLODEX)
        desc("Voice note").performClick()
        shot("voice")
        back()

        desc("Meeting mode").performClick()
        shot("meeting")
        back()
    }
}
