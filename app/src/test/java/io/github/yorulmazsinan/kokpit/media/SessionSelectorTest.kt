package io.github.yorulmazsinan.kokpit.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSelectorTest {
    private fun s(pkg: String, title: String?, playing: Boolean, active: Long) =
        SessionSnapshot(pkg, title, "Artist", playing, active)

    @Test
    fun prefersPlayingOverPaused() {
        val sessions = listOf(s("a", "Paused", false, 300), s("b", "Playing", true, 100))
        assertEquals("b", selectSession(sessions)?.packageName)
    }

    @Test
    fun fallsBackToMostRecent() {
        val sessions = listOf(s("a", "Old", false, 100), s("b", "New", false, 200))
        assertEquals("b", selectSession(sessions)?.packageName)
    }

    @Test
    fun ignoresBlankTitles() {
        val sessions = listOf(s("a", " ", true, 300), s("b", null, true, 400), s("c", "Song", false, 100))
        assertEquals("c", selectSession(sessions)?.packageName)
    }

    @Test
    fun nullOnEmpty() {
        assertNull(selectSession(emptyList()))
    }
}
