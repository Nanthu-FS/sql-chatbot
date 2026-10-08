package app.monoworkspace

import app.monoworkspace.core.FractionalIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FractionalIndexTest {

    @Test
    fun firstKeyAndAppends() {
        val a = FractionalIndex.first()
        val b = FractionalIndex.after(a)
        val c = FractionalIndex.after(b)
        assertTrue(a < b && b < c)
        assertTrue(listOf(a, b, c).all { FractionalIndex.isValid(it) })
    }

    @Test
    fun betweenAdjacentKeys() {
        val k = FractionalIndex.between("V", "W")
        assertTrue("V" < k && k < "W")
        val k2 = FractionalIndex.between("V", k)
        assertTrue("V" < k2 && k2 < k)
    }

    @Test
    fun prependRepeatedly() {
        var first = FractionalIndex.first()
        repeat(200) {
            val k = FractionalIndex.before(first)
            assertTrue("$k < $first", k < first)
            assertTrue(FractionalIndex.isValid(k))
            first = k
        }
    }

    @Test
    fun randomInsertionsStayOrdered() {
        val rnd = Random(42)
        val keys = mutableListOf(FractionalIndex.first())
        repeat(2000) {
            val i = rnd.nextInt(keys.size + 1)
            val k = FractionalIndex.between(keys.getOrNull(i - 1), keys.getOrNull(i))
            keys.add(i, k)
        }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(keys.all { FractionalIndex.isValid(it) })
    }

    @Test
    fun nBetweenIsSortedAndBounded() {
        val keys = FractionalIndex.nBetween("A", "B", 50)
        assertEquals(50, keys.size)
        assertEquals(keys.sorted(), keys)
        assertTrue(keys.all { it > "A" && it < "B" })
        val open = FractionalIndex.nBetween(null, null, 10)
        assertEquals(open.sorted(), open)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfOrder() {
        FractionalIndex.between("b", "a")
    }
}
