package io.github.yorulmazsinan.kokpit.phone

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "tr-w1024dp-h600dp-land-mdpi")
class PhoneScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun keypadBuildsNumberAndCallDialsIt() {
        var dialled = ""
        compose.setContent { KokpitTheme { PhoneContent(emptyList(), emptyList(), onBack = {}, onDial = { dialled = it }) } }
        listOf("0", "5", "3", "2").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithTag("dial_display").assertTextEquals("0532")
        compose.onNodeWithText("Ara").performClick()
        assertEquals("0532", dialled)
    }
}
