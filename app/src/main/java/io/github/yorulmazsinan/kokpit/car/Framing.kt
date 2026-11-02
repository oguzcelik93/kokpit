package io.github.yorulmazsinan.kokpit.car

import kotlin.math.max

/** Most a photo can be zoomed in on the card. */
const val FRAMING_MAX_SCALE = 4f

/**
 * How a photo sits in the card: [scale] 1 = the card just filled (cropped, no bars), more
 * = zoomed in; [fx]/[fy] in −1…1 = which part shows (−1 left/top edge, 0 middle, 1 right/bottom).
 */
data class Framing(val scale: Float = 1f, val fx: Float = 0f, val fy: Float = 0f) {
    fun encode(): String = "$scale,$fx,$fy"

    companion object {
        fun decode(text: String?): Framing? {
            val p = text?.split(',')?.mapNotNull { it.toFloatOrNull() } ?: return null
            if (p.size != 3) return null
            return Framing(p[0].coerceIn(1f, FRAMING_MAX_SCALE), p[1].coerceIn(-1f, 1f), p[2].coerceIn(-1f, 1f))
        }
    }
}

/** Where the photo is drawn, in card pixels: left, top, width, height (always covers the card). */
data class DrawRect(val left: Float, val top: Float, val width: Float, val height: Float)

fun drawRect(cardW: Float, cardH: Float, imgW: Float, imgH: Float, f: Framing): DrawRect {
    val s = max(cardW / imgW, cardH / imgH) * f.scale
    val w = imgW * s
    val h = imgH * s
    val left = (cardW - w) / 2 - f.fx * (w - cardW) / 2
    val top = (cardH - h) / 2 - f.fy * (h - cardH) / 2
    return DrawRect(left, top, w, h)
}

/**
 * One pinch/drag step: the photo grows by [zoom] and moves with the fingers by [dx]/[dy]
 * card pixels; it never uncovers the card.
 */
fun Framing.gesture(zoom: Float, dx: Float, dy: Float, cardW: Float, cardH: Float, imgW: Float, imgH: Float): Framing {
    val scale = (scale * zoom).coerceIn(1f, FRAMING_MAX_SCALE)
    val r = drawRect(cardW, cardH, imgW, imgH, copy(scale = scale))
    val spareX = (r.width - cardW) / 2
    val spareY = (r.height - cardH) / 2
    // Moving the fingers right shows more of the left: fx goes down.
    val fx = if (spareX <= 0.5f) 0f else (fx - dx / spareX).coerceIn(-1f, 1f)
    val fy = if (spareY <= 0.5f) 0f else (fy - dy / spareY).coerceIn(-1f, 1f)
    return Framing(scale, fx, fy)
}
