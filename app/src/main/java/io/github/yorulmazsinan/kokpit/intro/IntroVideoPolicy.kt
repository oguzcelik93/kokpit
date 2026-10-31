package io.github.yorulmazsinan.kokpit.intro

import kotlin.random.Random

data class IntroVideo(val id: String, val displayName: String, val sizeBytes: Long)

private const val SHORT_SLEEP_MS = 30 * 60_000L

/**
 * Which video to play on wake, or null. [sleptMs] is null on a cold boot. A short stop
 * (under 30 minutes) plays nothing unless the user asked for every wake-up.
 */
fun pickIntroVideo(settings: IntroSettings, videos: List<IntroVideo>, sleptMs: Long?, random: Random, season: Season = Season.SUMMER): IntroVideo? {
    if (settings.mode == IntroMode.OFF || videos.isEmpty()) return null
    if (!settings.everyWake && sleptMs != null && sleptMs < SHORT_SLEEP_MS) return null
    return when (settings.mode) {
        IntroMode.OFF -> null
        IntroMode.SELECTED -> videos.firstOrNull { it.id == settings.selectedId } ?: videos.first()
        IntroMode.SEQUENTIAL -> videos[(settings.lastPlayedIndex + 1).mod(videos.size)]
        IntroMode.RANDOM -> videos[random.nextInt(videos.size)]
        IntroMode.SEASONAL -> {
            val id = if (season == Season.WINTER) BUILTIN_WINTER_ID else BUILTIN_SUMMER_ID
            videos.firstOrNull { it.id == id } ?: videos.first()
        }
    }
}
