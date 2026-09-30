package com.spendlens.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.spendlens.app.domain.SampleData
import com.spendlens.app.ui.components.MotionSettings
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Starts the real app (database, settings, view models, navigation) with sample data and walks
 * through the main screens, failing on any crash and saving what each screen looked like.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class AppSmokeTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun noLoops() {
            MotionSettings.loops = false
        }
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/smoke_$name.png")
    }

    @Test
    fun walkThroughTheApp() {
        val app = ApplicationProvider.getApplicationContext<SpendLensApplication>()
        runBlocking { app.container.repository.addSamples(SampleData.generate(LocalDate.now())) }

        compose.waitUntil(15_000) { compose.onAllNodes(hasText("SPENT THIS MONTH", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("1_home")

        compose.onNodeWithText("WEEK").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("SPENT THIS WEEK", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("2_home_week")

        compose.onNodeWithText("ACTIVITY").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("TODAY").fetchSemanticsNodes().isNotEmpty() }
        shot("3_activity")

        compose.onAllNodesWithText("Blue Tokai Coffee")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("BLUE TOKAI COFFEE").fetchSemanticsNodes().isNotEmpty() }
        shot("4_detail")

        compose.onNodeWithText("[ BACK ]").performClick()
        compose.onNodeWithText("SETTINGS").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("SAMPLE DATA").fetchSemanticsNodes().isNotEmpty() }
        shot("5_settings")

        compose.onNodeWithText("HOME").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("[ COMPARE ]").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("[ COMPARE ]").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("PACE").fetchSemanticsNodes().isNotEmpty() }
        shot("6_compare")
    }
}
