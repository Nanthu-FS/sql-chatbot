package app.monoworkspace.engine

/** RFC 4180 CSV reading and writing. */
object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("﻿")
        var fieldStarted = false
        while (i < src.length) {
            val c = src[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') {
                        field.append('"'); i++
                    } else inQuotes = false
                } else field.append(c)
            } else {
                when (c) {
                    '"' -> { inQuotes = true; fieldStarted = true }
                    ',' -> { row.add(field.toString()); field.clear(); fieldStarted = false }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString()); field.clear(); fieldStarted = false
                        rows.add(row); row = ArrayList()
                    }
                    else -> { field.append(c); fieldStarted = true }
                }
            }
            i++
        }
        if (fieldStarted || field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> !(r.size == 1 && r[0].isEmpty()) }
    }

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value.startsWith(" ") || value.endsWith(" ")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    fun write(rows: List<List<String>>): String = buildString {
        for (r in rows) {
            append(r.joinToString(",") { escape(it) })
            append("\r\n")
        }
    }
}
