package app.monoworkspace

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.repo.BlockRepository
import app.monoworkspace.data.repo.MediaRepository
import app.monoworkspace.data.repo.PageRepository
import app.monoworkspace.data.repo.SearchRepository
import app.monoworkspace.engine.BlockTree
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockProps
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.RichText
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Reordering writes exactly one row and survives a reload from Room. */
@RunWith(AndroidJUnit4::class)
class BlockReorderTest {
    private lateinit var db: MonoDatabase
    private lateinit var blocks: BlockRepository
    private lateinit var pages: PageRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MonoDatabase::class.java).build()
        val search = SearchRepository(db)
        pages = PageRepository(db, search, MediaRepository(context))
        blocks = BlockRepository(db, search)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun moveLastBlockToTop() = runTest {
        val page = pages.createPage(null, "Reorder")
        val keys = FractionalIndex.nBetween(null, null, 4)
        val list = listOf("a", "b", "c", "d").mapIndexed { i, t ->
            Block("blk-$t", page.id, null, keys[i], BlockType.TEXT, RichText.of(t), BlockProps(), 0, 0)
        }
        blocks.insert(list)

        val loaded = blocks.load(page.id)
        val target = loaded.first { it.id == "blk-a" }
        val (parent, key) = BlockTree.moveBefore(loaded, "blk-d", target)!!
        val before = db.blocks().byPage(page.id).associate { it.id to it.orderKey }
        blocks.move("blk-d", parent, key, page.id)
        val after = db.blocks().byPage(page.id).associate { it.id to it.orderKey }

        val changed = after.filter { (id, k) -> before[id] != k }.keys
        assertEquals(setOf("blk-d"), changed)
        val order = BlockTree.flatten(blocks.load(page.id)).map { it.block.text }
        assertEquals(listOf("d", "a", "b", "c"), order)
    }

    @Test
    fun nestedMoveKeepsChildren() = runTest {
        val page = pages.createPage(null, "Nested")
        val keys = FractionalIndex.nBetween(null, null, 3)
        val parent = Block("p", page.id, null, keys[0], BlockType.BULLET, RichText.of("parent"), BlockProps(), 0, 0)
        val child = Block("c", page.id, "p", FractionalIndex.first(), BlockType.BULLET, RichText.of("child"), BlockProps(), 0, 0)
        val tail = Block("t", page.id, null, keys[1], BlockType.TEXT, RichText.of("tail"), BlockProps(), 0, 0)
        blocks.insert(listOf(parent, child, tail))

        val loaded = blocks.load(page.id)
        val (newParent, key) = BlockTree.moveBefore(loaded, "t", loaded.first { it.id == "p" })!!
        blocks.move("t", newParent, key, page.id)

        val order = BlockTree.flatten(blocks.load(page.id)).map { it.block.text to it.depth }
        assertEquals(listOf("tail" to 0, "parent" to 0, "child" to 1), order)
    }
}
