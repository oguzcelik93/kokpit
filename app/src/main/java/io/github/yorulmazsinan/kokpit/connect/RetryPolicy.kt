package io.github.yorulmazsinan.kokpit.connect

/** Retry every [intervalMs] until [windowMs] has passed since the first attempt. */
class RetryPolicy(val intervalMs: Long = 20_000, val windowMs: Long = 3 * 60_000) {
    /** Delay before the next attempt, or null to give up. */
    fun nextAttemptDelay(startedElapsedMs: Long, nowElapsedMs: Long): Long? {
        val remaining = windowMs - (nowElapsedMs - startedElapsedMs)
        return if (remaining <= 0) null else minOf(intervalMs, remaining)
    }
}
