package com.smartnotes.core

/** Markdown checklist helpers shared by gestures, widgets and voice notes. */
object Checklist {
    private val ITEM = Regex("^(\\s*)- \\[( |x|X)] (.*)$")

    data class Item(val lineIndex: Int, val text: String, val done: Boolean)

    fun items(body: String): List<Item> = body.lines().mapIndexedNotNull { i, line ->
        ITEM.matchEntire(line)?.let { Item(i, it.groupValues[3], it.groupValues[2].isNotBlank()) }
    }

    /** Turns a plain line into a todo (checkmark gesture). Existing todos are left alone. */
    fun makeTodo(body: String, lineIndex: Int): String = editLine(body, lineIndex) { line ->
        if (ITEM.matches(line) || line.isBlank()) line
        else "- [ ] " + line.trimStart().removePrefix("- ").removePrefix("* ")
    }

    fun toggle(body: String, lineIndex: Int): String = editLine(body, lineIndex) { line ->
        val m = ITEM.matchEntire(line) ?: return@editLine line
        val mark = if (m.groupValues[2].isBlank()) "x" else " "
        "${m.groupValues[1]}- [$mark] ${m.groupValues[3]}"
    }

    private fun editLine(body: String, lineIndex: Int, edit: (String) -> String): String {
        val lines = body.lines().toMutableList()
        if (lineIndex !in lines.indices) return body
        lines[lineIndex] = edit(lines[lineIndex])
        return lines.joinToString("\n")
    }
}
