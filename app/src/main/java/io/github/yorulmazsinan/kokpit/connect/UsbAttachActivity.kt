package io.github.yorulmazsinan.kokpit.connect

import android.app.Activity
import android.os.Bundle
import io.github.yorulmazsinan.kokpit.KokpitApp
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.system.safeStart
import kotlinx.coroutines.runBlocking

/**
 * Receives "iPhone plugged in" (Apple USB vendor id). Enabled only while the user has
 * "open CarPlay on USB" on, so it never competes with the CarPlay app otherwise.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KokpitApp).container
        // The process may have been cold-started by this very intent, so read from disk.
        val target = runBlocking {
            if (container.settings.current().autoCarPlayOnUsb) container.dock.target(DockSlot.CARPLAY) else null
        }
        target?.let(container.installedApps::launchIntent)?.let { safeStart(this, it) }
        finish()
    }
}
