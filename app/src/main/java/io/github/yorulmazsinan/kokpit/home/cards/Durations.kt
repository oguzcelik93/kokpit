package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yorulmazsinan.kokpit.R

@Composable
fun relativeDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) stringResource(R.string.duration_hours_minutes, h.toInt(), m.toInt())
    else stringResource(R.string.duration_minutes, m.toInt())
}

/** "1:05" or "1:02:05". */
fun elapsedText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
