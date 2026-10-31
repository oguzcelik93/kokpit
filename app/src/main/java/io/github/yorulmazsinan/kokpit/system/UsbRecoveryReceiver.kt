package io.github.yorulmazsinan.kokpit.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yorulmazsinan.kokpit.storage.removableRoots
import io.github.yorulmazsinan.kokpit.KokpitApp

/**
 * A USB stick was mounted: look for the recovery files on its root. This works even when
 * Kokpit's screen is unusable, as long as the unit itself boots.
 */
class UsbRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_MOUNTED) return
        // The broadcast's own path is ignored: only volumes Android lists as removable are read.
        val roots = removableRoots(context)
        val pending = goAsync()
        Thread {
            try {
                // A newer Kokpit APK on the stick is copied over and offered for install.
                runCatching { (context.applicationContext as KokpitApp).container.updater.checkUsb(roots) }
                when (Recovery.checkUsbTriggers(context, roots)) {
                    UsbTrigger.RESET, UsbTrigger.NORMAL -> Recovery.restart(context)
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
