package app.monoworkspace

import app.monoworkspace.engine.formula.CompiledFormula
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.TemplateBlock
import app.monoworkspace.model.TemplatePayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TemplateAssetsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val dir = File(System.getProperty("assetsDir") ?: "src/main/assets")

    private fun load(path: String) = json.decodeFromString(TemplatePayload.serializer(), File(dir, path).readText())

    private fun walk(blocks: List<TemplateBlock>): List<TemplateBlock> = blocks.flatMap { listOf(it) + walk(it.children) + walk(it.page?.blocks.orEmpty()) }

    @Test
    fun builtInTemplatesParse() {
        val names = File(dir, "templates").listFiles()!!.filter { it.name.endsWith(".json") }.map { it.name }.sorted()
        assertEquals(4, names.size)
        val payloads = names.map { load("templates/$it") }
        assertEquals(listOf("Meeting notes", "Task tracker", "Reading log", "Project brief"), payloads.map { it.name })
        val tracker = payloads[1].page.database!!
        assertEquals(listOf(PropertyType.TITLE, PropertyType.STATUS, PropertyType.DATE, PropertyType.SELECT), tracker.properties.map { it.type })
    }

    @Test
    fun gettingStartedHasEveryBlockType() {
        val seed = load("seed/getting_started.json")
        val types = walk(seed.page.blocks).map { it.type }.toSet()
        assertEquals(BlockType.entries.toSet(), types)
        val db = walk(seed.page.blocks).first { it.type == BlockType.CHILD_DATABASE }.page!!.database!!
        assertTrue(db.rows.size >= 5)
        db.properties.filter { it.type == PropertyType.FORMULA }.forEach {
            assertNull(it.config.expression, CompiledFormula(it.config.expression).parseError)
        }
    }
}
