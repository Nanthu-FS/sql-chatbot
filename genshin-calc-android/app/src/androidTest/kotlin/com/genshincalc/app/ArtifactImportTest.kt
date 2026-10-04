package com.genshincalc.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.ArtifactPiece
import com.genshincalc.core.model.ArtifactSlot
import com.genshincalc.core.model.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Renders artifact screenshots, runs them through the real on-device text recognition and checks the
 * recognized artifacts, then walks the artifact screens. Screenshots are saved to files/screens.
 */
@RunWith(AndroidJUnit4::class)
class ArtifactImportTest {

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

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private class Line(val text: String, val y: Float, val size: Float, val color: Int)

    /**
     * A landscape screenshot like the game's inventory: an item grid with level labels on the left and the
     * artifact's detail card on the right.
     */
    private fun screenshot(name: String, header: List<Line>, body: List<Line>): Uri {
        val bmp = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(30, 32, 44))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        val levels = listOf("+20", "+16", "+4", "+0", "+12")
        for (row in 0 until 3) for (col in 0 until 5) {
            val x = 120f + col * 210
            val y = 180f + row * 270
            paint.color = Color.rgb(150, 100, 60)
            c.drawRect(x, y, x + 180, y + 220, paint)
            paint.color = Color.WHITE
            paint.textSize = 34f
            c.drawText(levels[(row + col) % levels.size], x + 20, y + 205, paint)
        }
        paint.color = Color.rgb(185, 105, 50)
        c.drawRect(1500f, 70f, 2300f, 430f, paint)
        paint.color = Color.rgb(236, 229, 216)
        c.drawRect(1500f, 430f, 2300f, 1040f, paint)
        for (line in header + body) {
            paint.color = line.color
            paint.textSize = line.size
            c.drawText(line.text, 1540f, line.y, paint)
        }
        val file = File(context.cacheDir, "$name.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private val white = Color.WHITE
    private val dark = Color.rgb(73, 83, 102)
    private val green = Color.rgb(70, 140, 50)

    private fun card(name: String, slot: String, main: String, value: String, level: String, subs: List<String>, set: String) =
        Pair(
            listOf(Line(name, 150f, 52f, white), Line(slot, 220f, 36f, white), Line(main, 310f, 34f, Color.rgb(230, 225, 215)), Line(value, 395f, 64f, white)),
            listOf(Line(level, 490f, 34f, dark)) +
                subs.mapIndexed { i, s -> Line("· $s", 570f + i * 62, 38f, dark) } +
                listOf(Line(set, 830f, 38f, green), Line("2-Piece Set: ATK +18%.", 890f, 32f, dark)),
        )

    /** [scan] (what was read) is printed when something doesn't match. */
    private fun assertPiece(p: ArtifactPiece?, scan: Any?, set: String, slot: ArtifactSlot, main: Stat, subs: Map<Stat, Double>) {
        val info = "piece=$p scan=$scan"
        assertTrue(info, p != null)
        p!!
        assertEquals(info, set, p.setId)
        assertEquals(info, slot, p.slot)
        assertEquals(info, main, p.mainStat)
        assertEquals(info, 20, p.level)
        assertEquals(info, 5, p.rarity)
        assertEquals(info, subs.keys, p.substats.keys)
        subs.forEach { (s, v) -> assertTrue("$s: $info", abs((p.substats[s] ?: 0.0) - v) < 1e-6) }
    }

    @Test
    fun scanScreenshotsAndEditArtifacts() {
        waitForTag("damage_list")
        tag("reset_team").performClick()
        rule.onNodeWithText("Reset").performClick()
        waitForTag("damage_list")
        val vm = rule.runOnUiThread { ViewModelProvider(rule.activity)[CalcViewModel::class.java] }

        // 1. A screenshot shared to the app ("Share" → Teyvat DMG Calc).
        val (h1, b1) = card(
            "Gladiator's Nostalgia", "Flower of Life", "HP", "4,780", "+20",
            listOf("CRIT Rate+10.5%", "CRIT DMG+14.0%", "ATK+5.8%", "Elemental Mastery+23"), "Gladiator's Finale:(4)",
        )
        val flowerUri = screenshot("flower", h1, b1)
        val share = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, flowerUri)
        rule.runOnUiThread { rule.activity.importSharedImages(share) }
        waitForTag("scan_results")
        rule.waitUntil(120_000) { vm.scan.value?.running == false }
        rule.waitForIdle()
        val flowerId = vm.scan.value!!.items.single().pieceId
        val flower = vm.inventory.value.firstOrNull { it.id == flowerId }
        assertPiece(
            flower, vm.scan.value, "gladiatorsfinale", ArtifactSlot.FLOWER, Stat.HP,
            mapOf(Stat.CRIT_RATE to 0.105, Stat.CRIT_DMG to 0.14, Stat.ATK_PCT to 0.058, Stat.EM to 23.0),
        )
        rule.onAllNodesWithText("Gladiator's Nostalgia", substring = true).onFirst().assertExists()
        shot("24_scan_result")
        tag("scan_equip_0").performClick()
        rule.waitForIdle()
        tag("scan_done").performClick()
        waitForTag("damage_list")

        // 2. Scanned from the character's Build tab: equipped right away.
        val (h2, b2) = card(
            "Witch's Heart Flames", "Goblet of Eonothem", "Pyro DMG Bonus", "46.6%", "+20",
            listOf("CRIT DMG+21.0%", "ATK+16", "CRIT Rate+7.0%", "HP+5.3%"), "Crimson Witch of Flames:(4)",
        )
        val gobletUri = screenshot("goblet", h2, b2)
        rule.runOnUiThread { vm.importScreenshots(listOf(gobletUri), equipOn = 0) }
        waitForTag("scan_results")
        rule.waitUntil(120_000) { vm.scan.value?.running == false }
        rule.waitForIdle()
        val member = vm.team.value.members[0]
        assertEquals(ArtifactMode.PIECES, member.artifacts.mode)
        assertEquals(flowerId, member.artifacts.pieces[ArtifactSlot.FLOWER]?.id)
        assertPiece(
            member.artifacts.pieces[ArtifactSlot.GOBLET], vm.scan.value, "crimsonwitchofflames", ArtifactSlot.GOBLET, Stat.PYRO_DMG,
            mapOf(Stat.CRIT_DMG to 0.21, Stat.ATK to 16.0, Stat.CRIT_RATE to 0.07, Stat.HP_PCT to 0.053),
        )
        shot("25_scan_equipped")
        tag("scan_done").performClick()
        waitForTag("damage_list")

        // 3. The pieces on the Build tab, the slot picker, the editor and My artifacts.
        tag("tab_build").performClick()
        waitForTag("build_list")
        tag("build_list").performScrollToNode(hasTestTag("art_pieces"))
        shot("26_build_pieces")
        tag("build_list").performScrollToNode(hasTestTag("slot_flower"))
        tag("slot_flower").performClick()
        waitForTag("piece_list")
        shot("27_pick_flower")
        rule.onAllNodesWithContentDescription("Edit artifact").onFirst().performClick()
        waitForTag("piece_editor")
        shot("28_piece_editor")
        rule.onNodeWithTag("piece_slot_sands").assertExists()
        tag("piece_editor").performScrollToNode(hasTestTag("piece_save"))
        tag("piece_save").performClick()
        waitForTag("piece_list")
        back()
        waitForTag("build_list")
        tag("build_list").performScrollToNode(hasTestTag("my_artifacts"))
        tag("my_artifacts").performClick()
        waitForTag("my_artifacts_list")
        shot("29_my_artifacts")
        back()

        // 4. Back to quick totals: the summary build is used again.
        waitForTag("build_list")
        tag("build_list").performScrollToNode(hasTestTag("art_quick"))
        tag("art_quick").performClick()
        rule.waitForIdle()
        assertEquals(ArtifactMode.SUMMARY, vm.team.value.members[0].artifacts.mode)
        tag("tab_damage").performClick()
        waitForTag("damage_list")
    }
}
