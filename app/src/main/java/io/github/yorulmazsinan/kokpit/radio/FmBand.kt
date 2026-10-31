package io.github.yorulmazsinan.kokpit.radio

/** FM frequencies in tenths of MHz (984 = 98.4 MHz), 87.5–108.0 with wrap-around tuning. */
object FmBand {
    const val MIN = 875
    const val MAX = 1080

    fun step(frequency: Int, direction: Int): Int {
        val next = frequency + direction
        return when {
            next > MAX -> MIN
            next < MIN -> MAX
            else -> next
        }
    }

    fun format(frequency: Int): String = "${frequency / 10}.${frequency % 10}"

    fun fraction(frequency: Int): Float = (frequency - MIN).toFloat() / (MAX - MIN)
}
