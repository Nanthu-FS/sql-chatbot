package com.genshincalc.core.text

import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.model.ArtifactPiece
import com.genshincalc.core.model.ArtifactSlot
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Stat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One line of text recognized in a screenshot, with its bounding box in pixels (all 0 when unknown). */
data class OcrLine(val text: String, val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    val hasBox: Boolean get() = right > left && bottom > top
    val height: Int get() = bottom - top
}

/** What was read from one artifact screenshot. Fields are null when they could not be found. */
data class ArtifactScan(
    val setId: String? = null,
    val pieceName: String? = null,
    val slot: ArtifactSlot? = null,
    val mainStat: Stat? = null,
    val mainValue: Double? = null,
    val level: Int? = null,
    val rarity: Int? = null,
    val substats: Map<Stat, Double> = emptyMap(),
    val warnings: List<String> = emptyList(),
) {
    /** True if the screenshot looked like an artifact card at all. */
    val recognized: Boolean get() = slot != null || setId != null || mainStat != null

    /** The artifact, with anything that wasn't recognized filled with defaults (5★ +20, the slot's first main stat). */
    fun toPiece(id: String): ArtifactPiece? {
        val s = slot ?: return null
        return ArtifactPiece(
            id = id,
            setId = setId,
            slot = s,
            mainStat = mainStat?.takeIf { it in s.mainStats } ?: s.mainStats.first(),
            rarity = rarity ?: 5,
            level = level ?: 20,
            substats = substats,
        ).normalized()
    }
}

/**
 * Reads an artifact from the text of a game screenshot (the artifact detail card in the inventory or on
 * the character's artifact page, English UI). Works on OCR output, so names are matched fuzzily.
 */
object ArtifactScanParser {

    private val SUB_LABELS: List<Pair<String, Stat>> = listOf(
        "crit rate" to Stat.CRIT_RATE, "crit dmg" to Stat.CRIT_DMG, "atk" to Stat.ATK, "hp" to Stat.HP, "def" to Stat.DEF,
        "elemental mastery" to Stat.EM, "energy recharge" to Stat.ER,
    )

    private val MAIN_LABELS: List<Pair<String, Stat>> = SUB_LABELS + listOf("healing bonus" to Stat.HEALING_BONUS) +
        Element.entries.map { "${it.display.lowercase()} dmg bonus" to it.dmgBonusStat }

    private val NUMBER = Regex("""^[0-9OoIl][0-9OoIl,.]*%?$""")
    private val LEVEL = Regex("""^\+\s*([0-9OoIl]{1,2})$""")

    fun parse(raw: List<OcrLine>, data: GameDataSet): ArtifactScan {
        val warnings = mutableListOf<String>()
        val all = raw.filter { it.text.isNotBlank() }
        val boxes = all.isNotEmpty() && all.all { it.hasBox }

        // 1. The slot line ("Flower of Life"...) anchors the detail card.
        val slotHit = all.mapNotNull { line ->
            ArtifactSlot.entries.map { it to similarity(letters(line.text), letters(it.gameName), contains = true) }
                .maxByOrNull { it.second }?.takeIf { it.second >= 0.8 }?.let { (slot, score) -> Triple(line, slot, score) }
        }.maxByOrNull { it.third }
        val anchor = slotHit?.first

        // 2. Keep the card's column (inventory screenshots also contain the item grid on the left).
        val card = if (boxes && anchor != null) {
            val h = max(anchor.height, 8)
            all.filter { it.left >= anchor.left - 3 * h && it.top >= anchor.top - 4 * h }
        } else all
        val lines = if (boxes) card.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left }) else card
        val anchorIndex = anchor?.let { lines.indexOf(it) }?.takeIf { it >= 0 } ?: -1

        // 3. Artifact name (gives set and slot) and set name ("Gladiator's Finale:2").
        val pieceHit = bestPieceName(lines, data)
        val setHit = bestSetName(lines, data)
        val setId = when {
            pieceHit != null && setHit != null && pieceHit.first != setHit.first ->
                if (pieceHit.third >= setHit.second) pieceHit.first else setHit.first
            else -> pieceHit?.first ?: setHit?.first
        }
        if (setId == null) warnings += "Artifact set not recognized"

        // 4. Main stat: a label line after the slot, followed by its value.
        var mainStat: Stat? = null
        var mainValue: Double? = null
        var mainIndex = -1
        run {
            for (i in (anchorIndex + 1) until lines.size) {
                val text = lines[i].text.trim()
                if ('+' in text) continue
                val split = Regex("""^(.*?)\s*([0-9][0-9,.]*%?)$""").find(text)
                val labelPart = split?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: text
                val label = matchLabel(labelPart, MAIN_LABELS) ?: continue
                val valueText = if (split != null && split.groupValues[1].isNotBlank()) split.groupValues[2]
                else lines.getOrNull(i + 1)?.text?.trim()?.takeIf { NUMBER.matches(it.replace(" ", "")) } ?: continue
                val percent = valueText.endsWith("%")
                val number = parseNumber(valueText) ?: continue
                mainStat = withPercent(label, percent)
                mainValue = if (mainStat!!.percent) number / 100 else number
                mainIndex = if (split != null && split.groupValues[1].isNotBlank()) i else i + 1
                return@run
            }
        }
        if (mainStat == null) warnings += "Main stat not found"

        // 5. Slot: from the slot line, the artifact name or the main stat.
        var slot = slotHit?.second ?: pieceHit?.second
        if (slot == null) {
            slot = when (mainStat) {
                Stat.HP -> ArtifactSlot.FLOWER
                Stat.ATK -> ArtifactSlot.PLUME
                Stat.ER -> ArtifactSlot.SANDS
                Stat.CRIT_RATE, Stat.CRIT_DMG, Stat.HEALING_BONUS -> ArtifactSlot.CIRCLET
                null -> null
                else -> if (mainStat!!.element != null) ArtifactSlot.GOBLET else null
            }
            if (slot == null) warnings += "Slot not recognized"
        }
        if (slot != null && mainStat != null && mainStat !in slot.mainStats) {
            warnings += "${mainStat!!.display} can't be the main stat of a ${slot.display}"
        }

        // 6. Level: "+20" under the stars.
        val levelStart = max(anchorIndex, 0)
        var level = lines.drop(levelStart).firstNotNullOfOrNull { l ->
            LEVEL.find(l.text.trim())?.groupValues?.get(1)?.let { parseNumber(it)?.toInt() }
        }?.takeIf { it in 0..20 }

        // 7. Rarity (and level if missing) from the main stat value.
        var rarity: Int? = null
        val ms = mainStat
        val mv = mainValue
        if (ms != null && mv != null) {
            val tolerance = if (ms.percent) 0.0015 else 1.01
            fun matches(r: Int, lvl: Int) = abs(BaseStats.artifactMain(data, ms, r, lvl) - mv) <= tolerance &&
                lvl <= BaseStats.artifactMaxLevel(r)
            if (level != null) {
                rarity = (5 downTo 1).firstOrNull { matches(it, level!!) }
            } else {
                for (r in 5 downTo 1) {
                    val lvl = (0..BaseStats.artifactMaxLevel(r)).firstOrNull { matches(r, it) }
                    if (lvl != null) {
                        rarity = r
                        level = lvl
                        break
                    }
                }
            }
            if (rarity == null) warnings += "Main stat value doesn't match a known rarity/level"
        }
        if (level == null) warnings += "Level not found (assumed +20)"

        // 8. Substats: "• CRIT Rate+3.9%" lines after the main stat.
        val substats = linkedMapOf<Stat, Double>()
        for (i in (max(mainIndex, anchorIndex) + 1) until lines.size) {
            if (substats.size >= 4) break
            val text = lines[i].text.trim()
            if (text.contains("unactivated", ignoreCase = true)) continue
            val m = Regex("""^[^A-Za-z]*([A-Za-z][A-Za-z ]*?)\s*[+＋]\s*([0-9OoIl][0-9OoIl,.]*)\s*(%?)""").find(text) ?: continue
            val label = matchLabel(m.groupValues[1], SUB_LABELS) ?: continue
            val percent = m.groupValues[3] == "%"
            val stat = withPercent(label, percent)
            val number = parseNumber(m.groupValues[2]) ?: continue
            val value = if (stat.percent) number / 100 else number
            val maxValue = BaseStats.maxSubstatRoll(data, stat, rarity ?: 5) * 6 * 1.02
            if (maxValue > 0 && value > maxValue) {
                warnings += "Skipped implausible substat ${stat.display} ${Format.stat(stat, value)}"
                continue
            }
            if (stat !in substats && stat != mainStat) substats[stat] = value
        }
        if (substats.isEmpty()) warnings += "No substats found"

        return ArtifactScan(
            setId = setId,
            pieceName = pieceHit?.let { hit -> data.artifactSetOrNull(hit.first)?.pieces?.get(hit.second.key) },
            slot = slot,
            mainStat = mainStat,
            mainValue = mainValue,
            level = level,
            rarity = rarity,
            substats = substats,
            warnings = warnings,
        )
    }

    /** (setId, slot, score) of the best artifact-name match. */
    private fun bestPieceName(lines: List<OcrLine>, data: GameDataSet): Triple<String, ArtifactSlot, Double>? {
        var best: Triple<String, ArtifactSlot, Double>? = null
        for (line in lines) {
            val l = letters(line.text)
            if (l.length < 4) continue
            for (set in data.artifactSets) for ((key, name) in set.pieces) {
                val slot = ArtifactSlot.ofKey(key) ?: continue
                val score = similarity(l, letters(name))
                if (score >= 0.85 && (best == null || score > best.third)) best = Triple(set.id, slot, score)
            }
        }
        return best
    }

    /** (setId, score) of the best set-name match ("Crimson Witch of Flames:4" / "(2)"). */
    private fun bestSetName(lines: List<OcrLine>, data: GameDataSet): Pair<String, Double>? {
        var best: Pair<String, Double>? = null
        for (line in lines) {
            val cleaned = line.text.substringBefore(':').replace(Regex("""\(\s*\d\s*\)|\d+\s*-?\s*piece.*""", RegexOption.IGNORE_CASE), "")
            val l = letters(cleaned)
            if (l.length < 4) continue
            for (set in data.artifactSets) {
                val score = similarity(l, letters(set.name))
                if (score >= 0.85 && (best == null || score > best.second)) best = set.id to score
            }
        }
        return best
    }

    private fun matchLabel(text: String, labels: List<Pair<String, Stat>>): Stat? {
        val l = letters(text)
        if (l.isEmpty() || l.length > 24) return null
        return labels.map { (label, stat) -> stat to similarity(l, letters(label)) }
            .filter { (_, score) -> score >= if (l.length <= 4) 1.0 else 0.8 }
            .maxByOrNull { it.second }?.first
    }

    private fun withPercent(stat: Stat, percent: Boolean): Stat = when {
        !percent -> stat
        stat == Stat.HP -> Stat.HP_PCT
        stat == Stat.ATK -> Stat.ATK_PCT
        stat == Stat.DEF -> Stat.DEF_PCT
        else -> stat
    }

    /** Parses "4,780", "46.6%", "1O.5" (OCR letter/digit mix-ups) into a number. */
    internal fun parseNumber(raw: String): Double? {
        val t = raw.trim().removeSuffix("%").replace(" ", "")
            .map { c -> when (c) { 'O', 'o' -> '0'; 'I', 'l' -> '1'; else -> c } }.joinToString("")
        // A comma is a thousands separator ("4,780"); a single comma among decimals is a decimal point ("3,9").
        val normalized = if (Regex("""^\d{1,3}(,\d{3})+$""").matches(t)) t.replace(",", "") else t.replace(',', '.')
        return normalized.toDoubleOrNull()
    }

    private fun letters(s: String): String = s.lowercase().filter { it in 'a'..'z' }

    /** 1 - normalized edit distance; with [contains], a line that contains the target counts as a full match. */
    internal fun similarity(a: String, b: String, contains: Boolean = false): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (contains && a.contains(b)) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / max(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
