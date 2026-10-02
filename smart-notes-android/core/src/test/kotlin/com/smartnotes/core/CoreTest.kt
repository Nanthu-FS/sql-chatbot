package com.smartnotes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TextAndLinkingTest {
    private val notes = listOf(
        NoteDoc(1, "API redesign", "We decided to move the REST API to GraphQL and version the endpoints.", 1_000),
        NoteDoc(2, "Groceries", "Milk, eggs, bread, coffee beans", 2_000),
        NoteDoc(3, "Auth service", "The auth API needs token refresh and rate limiting on endpoints.", 3_000),
    )

    @Test fun searchRanksRelevantNoteFirst() {
        val hits = TextIndex(notes).search("what did I decide about the API")
        assertEquals(1L, hits.first().note.id)
    }

    @Test fun autoLinkerSuggestsRelatedAndSkipsLinked() {
        val current = NoteDoc(9, "Sprint plan", "Endpoints for the API need versioning and rate limiting this sprint.")
        val ids = AutoLinker.suggest(current, notes + current).map { it.note.id }
        assertTrue(1L in ids && 3L in ids)
        assertFalse(2L in ids)

        val linked = current.copy(body = current.body + " [[API redesign]]")
        assertFalse(1L in AutoLinker.suggest(linked, notes + linked).map { it.note.id })
    }

}

class GestureTest {
    private fun line(from: Pt, to: Pt, n: Int = 10) =
        (0..n).map { Pt(from.x + (to.x - from.x) * it / n, from.y + (to.y - from.y) * it / n) }

    @Test fun checkmark() {
        val stroke = line(Pt(0f, 50f), Pt(30f, 100f)) + line(Pt(30f, 100f), Pt(120f, 0f)).drop(1)
        assertEquals(Gesture.CHECKMARK, GestureRecognizer.classify(stroke))
    }

    @Test fun circle() {
        val stroke = (0..36).map {
            val a = Math.toRadians(it * 10.0)
            Pt((100 + 60 * Math.cos(a)).toFloat(), (100 + 50 * Math.sin(a)).toFloat())
        }
        assertEquals(Gesture.CIRCLE, GestureRecognizer.classify(stroke))
    }

    @Test fun strike() {
        assertEquals(Gesture.STRIKE, GestureRecognizer.classify(line(Pt(0f, 10f), Pt(200f, 14f))))
    }

    @Test fun tinyStrokeIsUnknown() {
        assertEquals(Gesture.UNKNOWN, GestureRecognizer.classify(line(Pt(0f, 0f), Pt(5f, 5f))))
    }
}

class ContextFeaturesTest {
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test fun geofence() {
        val store = PlaceReminder(1, "Store", LatLng(12.9716, 77.5946), 150f)
        assertEquals(1, GeoFence.triggered(listOf(store), LatLng(12.9720, 77.5950)).size)
        assertEquals(0, GeoFence.triggered(listOf(store), LatLng(13.0, 77.6)).size)
    }

    @Test fun resurfacesOldNotesForCalendarEvent() {
        val now = 100 * day
        val notes = listOf(
            NoteDoc(1, "Budget review prep", "Questions for the quarterly budget review", now - 40 * day),
            NoteDoc(2, "Budget review today", "fresh note", now - day),
        )
        val out = Resurfacer.suggest(notes, ResurfaceContext(now, upcomingEvents = listOf("Quarterly budget review")))
        assertEquals(listOf(1L), out.map { it.note.id })
        assertTrue(out.first().reason.contains("Quarterly budget review"))
    }

    @Test fun clipboardInbox() {
        val latest = ClipEntry(1, "hello", 0)
        assertFalse(ClipboardInbox.shouldCapture(" hello ", latest))
        assertTrue(ClipboardInbox.shouldCapture("world", latest))
        val clips = listOf(latest, ClipEntry(2, "keep", 0, saved = true))
        assertEquals(listOf(1L), ClipboardInbox.expired(clips, now = 2 * day).map { it.id })
    }
}

class TimeTravelAndChecklistTest {
    @Test fun sliderMapsToVersions() {
        assertEquals(0, TimeTravel.indexAt(0f, 5))
        assertEquals(4, TimeTravel.indexAt(1f, 5))
        assertEquals(2, TimeTravel.indexAt(0.5f, 5))
    }

    @Test fun diffMarksChanges() {
        val d = TimeTravel.diff("a\nb\nc", "a\nc\nd")
        assertEquals(
            listOf(LineChange.SAME, LineChange.REMOVED, LineChange.SAME, LineChange.ADDED),
            d.map { it.change },
        )
    }

    @Test fun snapshotThrottling() {
        val v = Version("hello", 0)
        assertFalse(TimeTravel.shouldSnapshot(v, "hello!", 1_000))
        assertTrue(TimeTravel.shouldSnapshot(v, "hello!", 31_000))
    }

    @Test fun checklistEdits() {
        val body = "Buy milk\n- [ ] Call mom"
        assertEquals("- [ ] Buy milk\n- [ ] Call mom", Checklist.makeTodo(body, 0))
        assertEquals("Buy milk\n- [x] Call mom", Checklist.toggle(body, 1))
        assertEquals(1, Checklist.items(body).size)
    }
}
