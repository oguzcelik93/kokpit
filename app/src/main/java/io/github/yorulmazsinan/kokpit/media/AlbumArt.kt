package io.github.yorulmazsinan.kokpit.media

import android.graphics.Bitmap

private const val MAX_ART_PX = 256

/**
 * Media apps hand over their own bitmaps, sometimes huge and sometimes recycled later.
 * Keep a small private copy so drawing it can never crash Kokpit.
 */
fun safeAlbumArt(source: Bitmap?): Bitmap? {
    if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) return null
    return runCatching {
        val scale = minOf(1f, MAX_ART_PX.toFloat() / maxOf(source.width, source.height))
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val copy = Bitmap.createScaledBitmap(source, w, h, true)
        if (copy === source) source.copy(Bitmap.Config.ARGB_8888, false) else copy
    }.getOrNull()
}
