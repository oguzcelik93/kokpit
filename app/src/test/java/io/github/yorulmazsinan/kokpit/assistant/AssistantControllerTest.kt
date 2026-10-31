package io.github.yorulmazsinan.kokpit.assistant

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AssistantControllerTest {
    private val noActions = object : AssistantActions {
        override fun openApp(name: String) = ""
        override fun media(action: String) = ""
        override fun navigate(destination: String) = ""
        override fun tuneRadio(tenths: Int) = ""
        override fun call(query: String) = ""
        override fun volume(action: String) = ""
        override fun status() = ""
        override fun connectPhoneWifi() = ""
    }

    private fun controller(settings: AssistantSettings) =
        AssistantController(ApplicationProvider.getApplicationContext(), CoroutineScope(SupervisorJob() + Dispatchers.Main), { settings }, noActions)

    @Test
    fun withoutAKeyItAsksForSetupAndClosesCleanly() {
        val c = controller(AssistantSettings())
        c.open()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AssistantState.NeedsSetup(SetupProblem.NO_KEY), c.state.value)
        c.close()
        assertEquals(AssistantState.Hidden, c.state.value)
    }

    @Test
    fun withAKeyButNoMicrophonePermissionItAsksForIt() {
        val c = controller(AssistantSettings(keys = mapOf(AiProvider.CLAUDE to "sk-ant-x")))
        c.open()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AssistantState.NeedsSetup(SetupProblem.NO_MIC_PERMISSION), c.state.value)
    }
}
