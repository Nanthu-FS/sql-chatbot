package com.genshincalc.app

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.ArtifactSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Imports a Character Showcase (the core tests' fixture) through the UID screen, then tries the real
 * service once; that part only reports, so an unreachable service doesn't fail the test.
 */
@RunWith(AndroidJUnit4::class)
class ShowcaseImportTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        val dir = File(context.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun tag(t: String) = rule.onNodeWithTag(t)

    private fun waitForTag(t: String, timeout: Long = 30_000) {
        rule.waitUntil(timeout) { rule.onAllNodesWithTag(t).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun checkbox(i: Int) = tag("showcase_check_$i")

    @Test
    fun importShowcaseByUid() {
        waitForTag("damage_list")
        val vm = rule.runOnUiThread { ViewModelProvider(rule.activity)[CalcViewModel::class.java] }
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("showcase/sample.json")
            .bufferedReader().use { it.readText() }

        tag("import_uid").performClick()
        waitForTag("showcase_uid")
        tag("showcase_uid").performTextClearance()
        tag("showcase_uid").performTextInput("800000001")
        shot("30_showcase_uid")
        rule.runOnUiThread { vm.loadShowcaseJson("800000001", fixture) }
        rule.waitUntil(30_000) { vm.showcase.value.showcase != null || vm.showcase.value.error != null }
        assertEquals(null, vm.showcase.value.error)
        waitForTag("showcase_char_0")
        rule.onNodeWithText("Paimon's Friend").assertExists()
        shot("31_showcase_loaded")

        // The first four known characters are selected; the fifth (Bennett) is not.
        assertEquals(listOf(0, 1, 2, 3), vm.showcase.value.selected)
        checkbox(0).assertIsOn()
        tag("showcase_list").performScrollToNode(hasTestTag("showcase_char_4"))
        checkbox(4).assertIsOff()
        shot("32_showcase_characters")
        tag("showcase_list").performScrollToNode(hasTestTag("showcase_apply"))
        tag("showcase_apply").performClick()
        waitForTag("damage_list")

        val team = vm.team.value
        assertEquals(listOf("hutao", "traveleranemo", "raidenshogun", "kaedeharakazuha"), team.members.map { it.characterId })
        val hutao = team.members[0]
        assertEquals(ArtifactMode.PIECES, hutao.artifacts.mode)
        assertEquals(ArtifactSlot.entries.toSet(), hutao.artifacts.pieces.keys)
        assertEquals("staffofhoma", hutao.weapon?.weaponId)
        val ids = vm.inventory.value.map { it.id }.toSet()
        assertTrue(hutao.artifacts.pieces.values.all { it.id in ids })
        shot("33_showcase_party_damage")
        tag("tab_build").performClick()
        waitForTag("build_list")
        tag("build_list").performScrollToNode(hasTestTag("art_pieces"))
        shot("34_showcase_party_build")
        tag("tab_damage").performClick()
        waitForTag("damage_list")

        // Importing the same showcase again reuses the artifacts already saved.
        val before = vm.inventory.value.size
        rule.runOnUiThread {
            vm.loadShowcaseJson("800000001", fixture)
        }
        rule.waitUntil(30_000) { vm.showcase.value.showcase != null }
        rule.runOnUiThread { vm.applyShowcase() }
        rule.waitForIdle()
        assertEquals(before, vm.inventory.value.size)

        // The real service with the API documentation's example UID; reported only.
        tag("import_uid").performClick()
        waitForTag("showcase_uid")
        tag("showcase_uid").performTextClearance()
        tag("showcase_uid").performTextInput("618285856")
        tag("showcase_load").performClick()
        rule.waitUntil(60_000) { !vm.showcase.value.loading }
        rule.waitForIdle()
        val live = vm.showcase.value
        Log.i(
            "ShowcaseImportTest",
            "LIVE showcase: error=${live.error} characters=${live.showcase?.characters?.map { it.build.characterId }} " +
                "differences=${live.differences.map { d -> d.map { "${it.label} ${it.game}/${it.calculated}" } }}",
        )
        shot("35_showcase_live")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForTag("damage_list")
    }
}
