package io.github.yorulmazsinan.kokpit.calls

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

@RunWith(RobolectricTestRunner::class)
class CallBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun incomingCallCanBeAnsweredOrDeclined() {
        var answered = 0
        var declined = 0
        compose.setContent { KokpitTheme { CallBar(ActiveCall(CallPhase.INCOMING, "0555", "Ece", null), { answered++ }, { declined++ }, {}) } }
        compose.onNodeWithText("Ece").assertExists()
        compose.onNodeWithTag("call_answer").performClick()
        compose.onNodeWithTag("call_decline").performClick()
        assertEquals(1, answered)
        assertEquals(1, declined)
    }

    @Test
    fun activeCallOnlyHangsUp() {
        var hungUp = 0
        compose.setContent { KokpitTheme { CallBar(ActiveCall(CallPhase.ACTIVE, "0555", null, 0L), {}, { hungUp++ }, {}) } }
        compose.onNodeWithText("0555").assertExists()
        compose.onNodeWithTag("call_answer").assertDoesNotExist()
        compose.onNodeWithTag("call_hang_up").performClick()
        assertEquals(1, hungUp)
    }
}
