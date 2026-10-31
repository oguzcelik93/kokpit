package io.github.yorulmazsinan.kokpit.headunit

/**
 * The tuner keeps the last station's RDS name in its buffer after a retune, until the new
 * station sends its own. Right after a frequency change that leftover name is ignored, unless
 * it was already seen on this frequency before.
 */
class RdsTracker {
    private val confirmed = mutableMapOf<Int, String>()
    private var frequency: Int? = null
    private var leftover: String? = null
    private var current: String? = null

    /** The name to show for [frequency], given what the tuner reports now. */
    fun update(frequency: Int, name: String?): String? {
        if (frequency != this.frequency) {
            leftover = current ?: leftover
            current = null
            this.frequency = frequency
        }
        if (name != null && (name != leftover || confirmed[frequency] == name)) {
            current = name
            leftover = null
            confirmed[frequency] = name
        }
        return current
    }
}
