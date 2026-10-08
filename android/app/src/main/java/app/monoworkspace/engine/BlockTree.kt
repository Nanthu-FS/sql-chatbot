package app.monoworkspace.engine

import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockType

/** A block as it appears in the editor's flattened list. */
data class FlatBlock(
    val block: Block,
    val depth: Int,
    /** Position within a numbered run, 1-based; null for other types. */
    val number: Int?,
    val hasChildren: Boolean,
)

/** Pure helpers over a page's block tree. */
object BlockTree {

    fun children(blocks: List<Block>, parentId: String?): List<Block> =
        blocks.filter { it.parentBlockId == parentId }.sortedBy { it.orderKey }

    fun childrenMap(blocks: List<Block>): Map<String?, List<Block>> =
        blocks.groupBy { it.parentBlockId }.mapValues { (_, v) -> v.sortedBy { it.orderKey } }

    /**
     * Depth-first flattening. Children of collapsed toggles are hidden, and the
     * contents of column layouts are rendered by the layout block itself.
     */
    fun flatten(blocks: List<Block>): List<FlatBlock> {
        val map = childrenMap(blocks)
        val out = ArrayList<FlatBlock>(blocks.size)
        fun visit(parentId: String?, depth: Int) {
            var run = 0
            for (b in map[parentId].orEmpty()) {
                run = if (b.type == BlockType.NUMBERED) run + 1 else 0
                val kids = map[b.id].orEmpty()
                out.add(FlatBlock(b, depth, if (b.type == BlockType.NUMBERED) run else null, kids.isNotEmpty()))
                val showKids = b.type != BlockType.COLUMNS && !(b.type == BlockType.TOGGLE && b.props.collapsed)
                if (showKids) visit(b.id, depth + 1)
            }
        }
        visit(null, 0)
        return out
    }

    /** Every descendant id of [rootId], not including it. */
    fun descendants(blocks: List<Block>, rootId: String): Set<String> {
        val map = childrenMap(blocks)
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        stack.add(rootId)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            for (c in map[id].orEmpty()) if (out.add(c.id)) stack.add(c.id)
        }
        return out
    }

    /** Key that places a block right after [after] among [parentId]'s children. */
    fun keyAfter(blocks: List<Block>, parentId: String?, after: Block?): String {
        val siblings = children(blocks, parentId)
        if (after == null) return FractionalIndex.between(null, siblings.firstOrNull()?.orderKey)
        val idx = siblings.indexOfFirst { it.id == after.id }
        val next = if (idx >= 0) siblings.getOrNull(idx + 1) else null
        return FractionalIndex.between(after.orderKey, next?.orderKey)
    }

    fun keyBefore(blocks: List<Block>, parentId: String?, before: Block): String {
        val siblings = children(blocks, parentId)
        val idx = siblings.indexOfFirst { it.id == before.id }
        val prev = if (idx > 0) siblings[idx - 1] else null
        return FractionalIndex.between(prev?.orderKey, before.orderKey)
    }

    fun keyAtEnd(blocks: List<Block>, parentId: String?): String =
        FractionalIndex.between(children(blocks, parentId).lastOrNull()?.orderKey, null)

    /**
     * Computes where a dragged block lands when dropped before [target] in the
     * flattened list (or at the very end when [target] is null). The block takes
     * the target's parent so the visual position is preserved. Returns null when
     * the move is a no-op or would put a block inside its own subtree.
     */
    fun moveBefore(blocks: List<Block>, movingId: String, target: Block?): Pair<String?, String>? {
        val moving = blocks.firstOrNull { it.id == movingId } ?: return null
        if (target != null) {
            if (target.id == movingId) return null
            if (target.parentBlockId != null && target.parentBlockId in descendants(blocks, movingId) + movingId) return null
            val others = blocks.filter { it.id != movingId }
            val key = keyBefore(others, target.parentBlockId, target)
            if (moving.parentBlockId == target.parentBlockId) {
                val siblings = children(blocks, target.parentBlockId)
                val ti = siblings.indexOfFirst { it.id == target.id }
                if (ti > 0 && siblings[ti - 1].id == movingId) return null
            }
            return target.parentBlockId to key
        }
        val others = blocks.filter { it.id != movingId }
        val roots = children(others, null)
        if (moving.parentBlockId == null && children(blocks, null).lastOrNull()?.id == movingId) return null
        return null to FractionalIndex.between(roots.lastOrNull()?.orderKey, null)
    }

    /** Moves a block one step among its siblings; returns the new key or null at the edge. */
    fun stepKey(blocks: List<Block>, block: Block, up: Boolean): String? {
        val siblings = children(blocks, block.parentBlockId)
        val i = siblings.indexOfFirst { it.id == block.id }
        return if (up) {
            if (i <= 0) null else FractionalIndex.between(siblings.getOrNull(i - 2)?.orderKey, siblings[i - 1].orderKey)
        } else {
            if (i < 0 || i >= siblings.lastIndex) null else FractionalIndex.between(siblings[i + 1].orderKey, siblings.getOrNull(i + 2)?.orderKey)
        }
    }

    /** Headings for a table of contents, in document order including column content. */
    fun headings(blocks: List<Block>): List<Block> {
        val map = childrenMap(blocks)
        val out = ArrayList<Block>()
        fun visit(parentId: String?) {
            for (b in map[parentId].orEmpty()) {
                if (b.type.isHeading) out.add(b)
                visit(b.id)
            }
        }
        visit(null)
        return out
    }
}
