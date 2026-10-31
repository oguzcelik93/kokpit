package io.github.yorulmazsinan.kokpit.radio

import io.github.yorulmazsinan.kokpit.headunit.RdsStation

const val RADIO_PRESET_SLOTS = 6

/** A saved station: frequency in tenths of MHz and, when RDS sent one, its name. */
data class RadioPreset(val frequency: Int, val name: String? = null)

/**
 * Presets live in fixed slots (a slot may be empty) as one preference string. Old builds
 * stored "984,914"; those still read, filling the first slots in order.
 */
object RadioPresetCodec {
    private const val SLOT = '\u001E'
    private const val FIELD = '\u001F'

    fun encode(presets: List<RadioPreset?>): String =
        presets.joinToString(SLOT.toString()) { p -> p?.let { "${it.frequency}$FIELD${it.name.orEmpty()}" }.orEmpty() }

    fun decode(value: String?): List<RadioPreset?> {
        if (value.isNullOrEmpty()) return emptyList()
        if (SLOT !in value && FIELD !in value) return value.split(',').mapNotNull { it.trim().toIntOrNull()?.let(::RadioPreset) }
        return value.split(SLOT).map { slot ->
            val parts = slot.split(FIELD, limit = 2)
            parts[0].toIntOrNull()?.let { RadioPreset(it, parts.getOrNull(1)?.takeIf(String::isNotBlank)) }
        }
    }
}

/** Puts [preset] in [slot], keeping the other slots where they are. */
fun List<RadioPreset?>.withSlot(slot: Int, preset: RadioPreset): List<RadioPreset?> =
    List(maxOf(size, slot + 1).coerceAtMost(RADIO_PRESET_SLOTS)) { i -> if (i == slot) preset else getOrNull(i) }

/** RDS name when it belongs to [frequency], otherwise the name saved with that frequency. */
fun stationNameFor(frequency: Int, rds: RdsStation?, presets: List<RadioPreset?>): String? =
    rds?.takeIf { it.frequency == frequency }?.name ?: presets.firstOrNull { it?.frequency == frequency }?.name
