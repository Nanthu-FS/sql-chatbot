package androidx.room

import app.monoworkspace.data.db.MonoDatabase

/**
 * Desktop stand-in for Room's transaction helper used by the shared
 * repositories. The desktop store applies each write atomically to an
 * immutable snapshot, so the block simply runs in place.
 */
suspend fun <R> MonoDatabase.withTransaction(block: suspend () -> R): R = block()
