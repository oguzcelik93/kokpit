package io.github.yorulmazsinan.kokpit

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The target unit runs Android 8.1 (API 27); make sure the app starts there. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], qualifiers = "w1024dp-h600dp-land-mdpi")
class MainActivityLaunchTest {
    @Test
    fun launchesOnApi27() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
