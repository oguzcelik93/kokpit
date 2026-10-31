package io.github.yorulmazsinan.kokpit.intro

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MIN = 60_000L

class IntroVideoPolicyTest {
    private val videos = listOf(IntroVideo("a", "a.mp4", 1), IntroVideo("b", "b.mp4", 1), IntroVideo("c", "c.mp4", 1))
    private val on = IntroSettings(mode = IntroMode.SELECTED, selectedId = "b", everyWake = false)

    @Test fun offReturnsNull() = assertNull(pickIntroVideo(IntroSettings(mode = IntroMode.OFF), videos, null, Random(1)))
    @Test fun skipsAfterShortSleep() = assertNull(pickIntroVideo(on, videos, 29 * MIN, Random(1)))
    @Test fun playsAfterLongSleep() = assertEquals("b", pickIntroVideo(on, videos, 31 * MIN, Random(1))?.id)
    @Test fun everyWakeIgnoresSleepLength() = assertEquals("b", pickIntroVideo(on.copy(everyWake = true), videos, 1 * MIN, Random(1))?.id)
    @Test fun playsOnEveryStartByDefault() = assertEquals("b", pickIntroVideo(IntroSettings(mode = IntroMode.SELECTED, selectedId = "b"), videos, 1 * MIN, Random(1))?.id)
    @Test fun coldBootPlays() = assertEquals("b", pickIntroVideo(on, videos, null, Random(1))?.id)

    @Test
    fun sequentialAdvancesAndWraps() {
        val seq = IntroSettings(mode = IntroMode.SEQUENTIAL, lastPlayedIndex = 0)
        assertEquals("b", pickIntroVideo(seq, videos, null, Random(1))?.id)
        assertEquals("a", pickIntroVideo(seq.copy(lastPlayedIndex = 2), videos, null, Random(1))?.id)
        assertEquals("a", pickIntroVideo(seq.copy(lastPlayedIndex = -1), videos, null, Random(1))?.id)
    }

    @Test fun selectedFallsBackToFirstWhenMissing() = assertEquals("a", pickIntroVideo(on.copy(selectedId = "gone"), videos, null, Random(1))?.id)
    @Test fun randomPicksFromLibrary() = assertEquals(true, pickIntroVideo(IntroSettings(mode = IntroMode.RANDOM), videos, null, Random(7)) in videos)
    @Test fun skipsWhenFileMissing() = assertNull(pickIntroVideo(on, emptyList(), null, Random(1)))
}
