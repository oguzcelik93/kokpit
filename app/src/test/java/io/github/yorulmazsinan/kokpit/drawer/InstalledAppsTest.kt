package io.github.yorulmazsinan.kokpit.drawer

import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstalledAppsTest {
    @Test
    fun launcherIntentStartsAppInItsOwnTask() {
        val intent = launcherIntent(ComponentName("com.example.maps", "com.example.maps.Main"))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0)
        assertEquals(Intent.ACTION_MAIN, intent.action)
    }
}
