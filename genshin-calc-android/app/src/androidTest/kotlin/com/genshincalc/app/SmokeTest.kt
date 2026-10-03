package com.genshincalc.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genshincalc.core.Defaults
import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.text.Format
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks every screen. A crash fails the test; screenshots are saved to files/screens.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File(context.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun tag(t: String): SemanticsNodeInteraction = rule.onNodeWithTag(t, useUnmergedTree = false)

    private fun waitForTag(t: String, timeout: Long = 30_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String, timeout: Long = 15_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun resetTeam() {
        waitForTag("reset_team")
        tag("reset_team").performClick()
        rule.onNodeWithText("Reset").performClick()
        waitForTag("damage_list")
    }

    @Test
    fun walkThroughEveryScreen() {
        waitForTag("damage_list")
        resetTeam()

        // The number on screen must be what the engine computes for the sample team.
        val data = GameDataSet.load()
        val expected = TeamCalculator(data, GameEffects).calculate(Defaults.sampleTeam(data)).members[0]
            .hits.first { it.hit.id == "normal/charged-attack" }.numbers.average
        tag("damage_list").performScrollToNode(hasTestTag("hit_normal/charged-attack"))
        rule.onAllNodesWithText(Format.damage(expected), substring = true).onFirst().assertExists()
        tag("damage_list").performScrollToNode(hasTestTag("talent_NORMAL").or(hasText("Secret Spear of Wangsheng")))
        shot("01_damage_top")

        tag("hit_normal/charged-attack").performClick()
        waitForText("DEF multiplier")
        shot("02_hit_detail")
        rule.onNodeWithText("Close").performClick()

        tag("damage_list").performScrollToNode(hasTestTag("reaction_VAPORIZE"))
        tag("reaction_VAPORIZE").performClick()
        shot("03_vaporize")
        tag("damage_list").performScrollToNode(hasText("Active buffs"))
        shot("04_damage_bottom")

        tag("tab_build").performClick()
        waitForTag("build_list")
        shot("05_build_top")
        tag("build_list").performScrollToNode(hasText("Artifacts"))
        shot("06_build_artifacts")
        tag("build_list").performScrollToNode(hasText("Character screen"))
        shot("07_build_screen_stats")

        tag("tab_buffs").performClick()
        waitForTag("buffs_list")
        shot("08_buffs_top")
        tag("buffs_list").performScrollToNode(hasText("Custom buffs"))
        shot("09_buffs_custom")

        tag("tab_enemy").performClick()
        waitForTag("enemy_list")
        shot("10_enemy")
        tag("pick_enemy").performClick()
        tag("search").performTextInput("Ruin Guard")
        tag("enemy_ruinguard").performClick()
        waitForTag("enemy_list")
        shot("11_enemy_ruin_guard")

        // Other party members.
        tag("tab_damage").performClick()
        tag("member_1").performClick()
        waitForTag("damage_list")
        shot("12_member2_damage")
        tag("member_3").performClick()
        waitForTag("damage_list")
        shot("13_member4_damage")

        // Swap a character.
        tag("tab_build").performClick()
        waitForTag("build_list")
        rule.onAllNodesWithText("Change").onFirst().performClick()
        tag("search").performTextInput("Raiden")
        tag("char_raidenshogun").performClick()
        waitForTag("build_list")
        tag("tab_damage").performClick()
        waitForTag("damage_list")
        waitForText("Raiden Shogun")
        shot("14_raiden_damage")

        // Manual stats mode.
        tag("tab_build").performClick()
        tag("mode_manual").performClick()
        tag("build_list").performScrollToNode(hasText("Character screen stats"))
        shot("15_manual_stats")

        // Library.
        tag("nav_library").performClick()
        waitForTag("character_list")
        shot("16_library_characters")
        tag("search").performTextInput("Furina")
        tag("char_furina").performClick()
        waitForTag("character_detail")
        shot("17_character_detail")
        tag("character_detail").performScrollToNode(hasText("Constellations"))
        shot("18_character_constellations")
        back()

        tag("lib_weapons").performClick()
        tag("search").performTextInput("Homa")
        tag("weapon_staffofhoma").performClick()
        waitForTag("weapon_detail")
        shot("19_weapon_detail")
        back()

        tag("lib_artifacts").performClick()
        shot("20_artifacts")
        tag("search").performTextInput("Crimson")
        tag("set_crimsonwitchofflames").performClick()
        waitForTag("set_detail")
        shot("21_set_detail")
        back()

        tag("lib_reactions").performClick()
        shot("22_reactions")
        tag("reaction_row_VAPORIZE").performClick()
        waitForTag("reaction_detail")
        shot("23_reaction_detail")
        back()

        tag("nav_calculator").performClick()
        waitForTag("damage_list")
        tag("damage_list").assertIsDisplayed()
    }
}
