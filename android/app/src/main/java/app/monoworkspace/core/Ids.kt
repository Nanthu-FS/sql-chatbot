package app.monoworkspace.core

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

/** Abstracted clock so engine code stays testable. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
