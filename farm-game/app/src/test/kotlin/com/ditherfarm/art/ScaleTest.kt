package com.ditherfarm.art

import com.ditherfarm.shots.ScreenshotTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * World scale check (Docs/DECISIONS.md, "World scale"): every world sprite must stay inside the
 * height band of its kind, measured against a cottage (about 44px). Catches art that comes out
 * too big or too small before anyone has to spot it in the game. Heights include the 2px pad
 * (body height from the manifest, without the cast shadow).
 */
class ScaleTest {
    private class Band(pattern: String, val min: Int, val max: Int, val why: String) {
        val regex = Regex(pattern)
    }

    private val bands = listOf(
        Band("people/", 22, 27, "a farmhand is a little over half a cottage"),
        Band("buildings/cottage_", 38, 48, "the reference house"),
        Band("buildings/(house_|shop_)", 34, 52, "village houses and shops: cottage-sized, two storeys a little taller"),
        Band("buildings/barn", 60, 72, "the barn is the biggest building"),
        Band("buildings/(silo|windmill)", 50, 66, "tall farm buildings"),
        Band("buildings/drill_rig", 36, 52, "a derrick, taller than a cottage"),
        Band("buildings/(market|sawmill|logging_hut|tree_nursery|bakery_oven|furnace|charcoal_kiln)$", 20, 40, "sheds and big machines stay under the cottage"),
        Band("buildings/(well|orders|market_cart|merchant_cart|preserves_jar|fruit_press|seed_maker|oil_press|wood_chipper)$", 16, 34, "people-sized machines"),
        Band("buildings/(workbench|compost_bin|beehive|geode_anvil|syrup_kettle)$", 12, 26, "waist-high things"),
        Band("vehicles/(truck|harvester)_[ns]e_", 14, 26, "vehicles: about a farmhand tall"),
        Band("deco/(crate_|hay_bale|trough)", 8, 22, "knee-high props"),
        Band("deco/scarecrow", 22, 32, "a scarecrow on its pole, a bit taller than a farmhand"),
        Band("crops/(?!giant_)", 4, 34, "crops fill their plot but stay under a farmhand and a half"),
        Band("crops/giant_", 30, 60, "a giant crop fills a 3x3 patch"),
    )

    /** Height from the PNG header (IHDR, bytes 20-23), so the check needs no image library. */
    private fun pngHeight(f: File): Int {
        val b = ByteArray(24).also { buf -> java.io.DataInputStream(f.inputStream()).use { it.readFully(buf) } }
        return ((b[20].toInt() and 0xFF) shl 24) or ((b[21].toInt() and 0xFF) shl 16) or ((b[22].toInt() and 0xFF) shl 8) or (b[23].toInt() and 0xFF)
    }

    @Test fun worldSpritesStayInScale() {
        val art = File(ScreenshotTest.assetRoot(), "art")
        // Polished sprites record their body height ("bh"): the cast shadow makes the canvas taller.
        val manifest = org.json.JSONObject(File(art, "manifest.json").readText())
        val bad = ArrayList<String>()
        var checked = 0
        for (dir in listOf("people", "buildings", "vehicles", "deco", "crops")) {
            for (f in File(art, dir).listFiles { f -> f.name.endsWith(".png") }.orEmpty()) {
                val name = "$dir/" + f.name.removeSuffix(".png").removeSuffix("_snow")
                val band = bands.firstOrNull { it.regex.containsMatchIn(name) } ?: continue
                val h = manifest.optJSONObject("$dir/" + f.name.removeSuffix(".png"))?.optInt("bh", 0)?.takeIf { it > 0 } ?: pngHeight(f)
                checked++
                if (h !in band.min..band.max) bad += "$name is ${h}px tall, wants ${band.min}-${band.max} (${band.why})"
            }
        }
        assertTrue("nothing checked", checked > 100)
        assertTrue("Out of scale:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
