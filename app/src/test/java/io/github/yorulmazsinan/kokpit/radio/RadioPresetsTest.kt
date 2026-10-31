package io.github.yorulmazsinan.kokpit.radio

import io.github.yorulmazsinan.kokpit.headunit.RdsStation
import io.github.yorulmazsinan.kokpit.headunit.cleanPsName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioPresetsTest {
    @Test
    fun roundTripsNamesAndEmptySlots() {
        val presets = listOf(RadioPreset(984, "Kral FM"), null, RadioPreset(914), RadioPreset(1040, "A, B: C"))
        assertEquals(presets, RadioPresetCodec.decode(RadioPresetCodec.encode(presets)))
    }

    @Test
    fun readsTheOldCommaList() {
        assertEquals(listOf(RadioPreset(984), RadioPreset(914)), RadioPresetCodec.decode("984,914"))
        assertEquals(emptyList<RadioPreset?>(), RadioPresetCodec.decode(null))
    }

    @Test
    fun savingFillsTheChosenSlotOnly() {
        val presets = listOf(RadioPreset(984), RadioPreset(914))
        assertEquals(
            listOf(RadioPreset(984), RadioPreset(914), null, RadioPreset(953, "Power FM")),
            presets.withSlot(3, RadioPreset(953, "Power FM")),
        )
        assertEquals(listOf(RadioPreset(1040), RadioPreset(914)), presets.withSlot(0, RadioPreset(1040)))
    }

    @Test
    fun rdsNameOnlyCountsForItsOwnFrequency() {
        val presets = listOf(RadioPreset(914, "TRT FM"))
        assertEquals("Kral FM", stationNameFor(984, RdsStation(984, "Kral FM"), presets))
        assertNull(stationNameFor(984, RdsStation(953, "Power FM"), presets))
        assertEquals("TRT FM", stationNameFor(914, RdsStation(984, "Kral FM"), presets))
    }

    @Test
    fun cleansTheTunerBuffer() {
        assertEquals("KRAL FM", cleanPsName("KRAL  FM\u0000\u0000\u0000"))
        assertNull(cleanPsName("\u0000\u0000  "))
    }
}
