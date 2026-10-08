package app.monoworkspace.core

/**
 * Lexicographic fractional indexing. Keys are strings over a base-62 alphabet
 * ordered by code point, so plain string comparison gives list order. A key
 * between any two keys always exists, so a reorder rewrites exactly one row.
 * Keys never end in the zero digit, which keeps every gap open.
 */
object FractionalIndex {
    const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    private fun digit(c: Char): Int {
        val i = DIGITS.indexOf(c)
        require(i >= 0) { "Invalid key character '$c'" }
        return i
    }

    fun isValid(key: String): Boolean =
        key.isNotEmpty() && key.all { DIGITS.indexOf(it) >= 0 } && key.last() != DIGITS[0]

    /** Returns a key strictly between [a] and [b]; null means open-ended. */
    fun between(a: String?, b: String?): String {
        if (a != null) require(isValid(a)) { "Invalid key '$a'" }
        if (b != null) require(isValid(b)) { "Invalid key '$b'" }
        if (a != null && b != null) require(a < b) { "Keys out of order: '$a' >= '$b'" }
        return midpoint(a ?: "", b)
    }

    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            // Share the common prefix, treating a missing digit in a as zero.
            var n = 0
            while (n < b.length && (if (n < a.length) a[n] else DIGITS[0]) == b[n]) n++
            if (n > 0) {
                return b.substring(0, n) + midpoint(if (n < a.length) a.substring(n) else "", b.substring(n))
            }
        }
        val digitA = if (a.isNotEmpty()) digit(a[0]) else 0
        val digitB = if (b != null) digit(b[0]) else BASE
        return if (digitB - digitA > 1) {
            val mid = (digitA + digitB + 1) / 2
            DIGITS[mid].toString()
        } else if (b != null && b.length > 1) {
            b.substring(0, 1)
        } else {
            DIGITS[digitA] + midpoint(if (a.isNotEmpty()) a.substring(1) else "", null)
        }
    }

    /** [count] increasing keys strictly between [a] and [b]. */
    fun nBetween(a: String?, b: String?, count: Int): List<String> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(between(a, b))
        if (b == null) {
            val out = ArrayList<String>(count)
            var prev = a
            repeat(count) {
                val k = between(prev, null)
                out.add(k)
                prev = k
            }
            return out
        }
        val mid = count / 2
        val c = between(a, b)
        return nBetween(a, c, mid) + c + nBetween(c, b, count - mid - 1)
    }

    fun first(): String = between(null, null)
    fun after(a: String?): String = between(a, null)
    fun before(b: String?): String = between(null, b)
}
