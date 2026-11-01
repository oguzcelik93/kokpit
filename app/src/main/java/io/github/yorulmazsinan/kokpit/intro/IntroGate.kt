package io.github.yorulmazsinan.kokpit.intro

import android.os.SystemClock

/** Two paths can ask for the welcome clip at once (ignition wake, Kokpit opening); play it once. */
const val INTRO_REPEAT_GUARD_MS = 120_000L

fun introRecentlyPlayed(lastStartElapsed: Long?, nowElapsed: Long): Boolean =
    lastStartElapsed != null && lastStartElapsed <= nowElapsed && nowElapsed - lastStartElapsed < INTRO_REPEAT_GUARD_MS

object IntroGate {
    @Volatile private var lastStart: Long? = null

    fun recentlyPlayed(): Boolean = introRecentlyPlayed(lastStart, SystemClock.elapsedRealtime())

    fun markStarted() {
        lastStart = SystemClock.elapsedRealtime()
    }
}
