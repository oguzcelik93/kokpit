package io.github.yorulmazsinan.kokpit.connect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yorulmazsinan.kokpit.system.Recovery

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Recovery.isSafeMode(context)) return
        if (powerSignalFor(intent.action) != null) {
            KokpitService.start(context, powerAction = intent.action)
        } else if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            KokpitService.start(context, coldBoot = true)
        }
    }
}
