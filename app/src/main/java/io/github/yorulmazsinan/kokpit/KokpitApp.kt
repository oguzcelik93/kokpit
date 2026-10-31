package io.github.yorulmazsinan.kokpit

import android.app.Application
import io.github.yorulmazsinan.kokpit.system.Recovery
import io.github.yorulmazsinan.kokpit.system.installCrashLog

class KokpitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLog(this)
        // A USB stick with KOKPIT-KURTARMA / -SIFIRLA / -NORMAL on it is honoured first.
        // Off the main thread: on a 1 GB unit every millisecond before the first frame shows.
        Thread { runCatching { Recovery.checkUsbTriggers(this) } }.start()
        // In safe mode nothing starts in the background: whatever crashed stays asleep.
        container = AppContainer(this).also { if (!Recovery.isSafeMode(this)) it.start() }
    }
}
