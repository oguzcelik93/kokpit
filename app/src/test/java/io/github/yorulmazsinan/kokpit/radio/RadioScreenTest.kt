package io.github.yorulmazsinan.kokpit.radio

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
class RadioScreenTest {
    @get:Rule val compose = createComposeRule()

    private val presets = listOf(RadioPreset(984, "Kral FM"), RadioPreset(914))

    @Test
    fun showsFrequencyNoticeAndTunesUp() {
        var tuned = 0
        compose.setContent {
            KokpitTheme { RadioScreen(984, presets, null, onTune = { tuned = it }, onSavePreset = {}, onOpenVendorRadio = {}, onBack = {}) }
        }
        compose.onNodeWithTag("radio_frequency").assertTextEquals("98.4")
        compose.onNodeWithTag("radio_notice").assertExists()
        compose.onNodeWithTag("radio_up").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(985, tuned)
    }

    @Test
    fun stationNameLeadsWhenKnown() {
        compose.setContent { KokpitTheme { RadioScreen(984, presets, "Kral FM", {}, {}, {}, {}, connected = true) } }
        compose.onNodeWithTag("radio_station_name").assertTextEquals("Kral FM")
        compose.onNodeWithTag("radio_frequency").assertTextEquals("98.4")
    }

    @Test
    fun tapTunesAndHoldSavesIntoThatSlot() {
        var tuned = 0
        var savedSlot = -1
        compose.setContent {
            KokpitTheme { RadioScreen(984, presets, null, onTune = { tuned = it }, onSavePreset = { savedSlot = it }, onOpenVendorRadio = {}, onBack = {}, connected = true) }
        }
        compose.onNodeWithTag("radio_preset_1").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(914, tuned)
        compose.onNodeWithTag("radio_preset_4").performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(4, savedSlot)
    }

    @Test
    fun draggingTheDialLeftTunesHigher() {
        var tuned = 0
        compose.setContent { KokpitTheme { RadioScreen(984, presets, null, onTune = { tuned = it }, onSavePreset = {}, onOpenVendorRadio = {}, onBack = {}, connected = true) } }
        compose.onNodeWithTag("radio_dial").performTouchInput { swipeLeft() }
        assertTrue("tuned $tuned", tuned > 984)
    }
}
