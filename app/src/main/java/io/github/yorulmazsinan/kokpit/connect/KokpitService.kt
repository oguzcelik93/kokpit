package io.github.yorulmazsinan.kokpit.connect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.yorulmazsinan.kokpit.KokpitApp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.intro.IntroActivity
import io.github.yorulmazsinan.kokpit.MainActivity
import io.github.yorulmazsinan.kokpit.system.HomeGuard
import io.github.yorulmazsinan.kokpit.carplay.isIphoneAttached
import io.github.yorulmazsinan.kokpit.intro.pickIntroVideo
import io.github.yorulmazsinan.kokpit.intro.IntroGate
import io.github.yorulmazsinan.kokpit.intro.allIntroVideos
import io.github.yorulmazsinan.kokpit.intro.currentTemperature
import io.github.yorulmazsinan.kokpit.intro.seasonFor
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Head units sleep rather than power off when the ignition goes off. This service spots the
 * wake-up three ways (ignition broadcast, restart after being killed, a gap in its own
 * heartbeat) and runs the wake-up routine once: trip bookkeeping, hotspot and intro video.
 */
class KokpitService : Service() {
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Screen events only keep the trip clock honest; ignition comes from POWER_ACTIONS.
            val now = SystemClock.elapsedRealtime()
            val container = (application as KokpitApp).container
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    log("screen off")
                    screenOffAt = now
                    container.drive.onSleep(now)
                    // Possibly the ignition: remember where the car is now, while GPS still knows.
                    container.drive.lastPosition()?.let { (lat, lon) -> container.car.park(lat, lon) }
                }
                Intent.ACTION_SCREEN_ON -> {
                    container.drive.onWake(now)
                    val off = screenOffMeansIgnition(screenOffAt, now)
                    screenOffAt = null
                    log("screen on" + (off?.let { " after ${it / 1000} s" } ?: ""))
                    // On units without an ignition broadcast, a long screen-off is the ignition cycle.
                    if (off != null) onEvent(WakeEvent.Wake(now, off)) else beat()
                }
            }
        }
    }

    private var heartbeat: Job? = null
    private var screenOffAt: Long? = null

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onPower(intent.action)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_running))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, SERVICE_NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val power = IntentFilter().apply { POWER_ACTIONS.keys.forEach(::addAction) }
        ContextCompat.registerReceiver(this, powerReceiver, power, ContextCompat.RECEIVER_EXPORTED)
        // Started again after QuickBoot killed us at ignition off: that start is the wake-up.
        PowerEventLog.takeSleepAt(this)?.let { sleepAt ->
            val now = SystemClock.elapsedRealtime()
            log("restart after sleep")
            onEvent(WakeEvent.Wake(now, sleptFor(sleepAt, now)))
        }
        heartbeat = (application as KokpitApp).container.scope.launch(Dispatchers.Main) {
            while (true) {
                beat()
                delay(HEARTBEAT_MS)
            }
        }
    }

    private fun beat() {
        val now = SystemClock.elapsedRealtime()
        val gap = heartbeatGap(PowerEventLog.beat(this, now), now) ?: return
        log("wake after ${gap / 1000} s asleep")
        onEvent(WakeEvent.Wake(now, gap))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_POWER_ACTION)?.let(::onPower)
        if (intent?.getBooleanExtra(EXTRA_COLD_BOOT, false) == true) {
            log("boot")
            onEvent(WakeEvent.Wake(SystemClock.elapsedRealtime(), sleptMs = null))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        heartbeat?.cancel()
        unregisterReceiver(screenReceiver)
        unregisterReceiver(powerReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onPower(action: String?) {
        val signal = powerSignalFor(action) ?: return
        val now = SystemClock.elapsedRealtime()
        log("$signal ($action)")
        when (signal) {
            PowerSignal.OFF -> {
                PowerEventLog.storeSleepAt(this, now)
                onEvent(WakeEvent.Sleep(now))
            }
            PowerSignal.ON -> {
                val slept = sleptFor(PowerEventLog.takeSleepAt(this), now)
                onEvent(WakeEvent.Wake(now, slept))
            }
        }
    }

    private fun log(event: String) {
        PowerEventLog.add(this, "${java.time.LocalDateTime.now().withNano(0)} $event")
    }

    private fun onEvent(event: WakeEvent) {
        if (event is WakeEvent.Wake) {
            if (isRepeatWake(lastWakeAt, event.elapsedMs)) return
            lastWakeAt = event.elapsedMs
        }
        WakeEvents.emit(event)
        val container = (application as KokpitApp).container
        when (event) {
            is WakeEvent.Sleep -> {
                container.awake = false
                container.drive.onSleep(event.elapsedMs)
                container.trips.end(container.drive.distanceMeters())
                // Ignition off: remember where the car was left (written synchronously).
                container.drive.lastPosition()?.let { (lat, lon) -> container.car.park(lat, lon) }
            }
            is WakeEvent.Wake -> {
                container.drive.onWake(event.elapsedMs)
                container.awake = true
                container.trips.begin(container.drive.distanceMeters())
                // After the welcome clip: the music that was on when the car was switched off.
                container.scope.launch(Dispatchers.IO) {
                    delay(RESUME_DELAY_MS)
                    if (container.settings.current().autoResume && container.awake) runCatching { container.resume.resume() }
                }
                container.scope.launch {
                    val played = playIntro(event.sleptMs)
                    // Some firmwares (XY Auto) open their own launcher at every ignition-on,
                    // even when Kokpit is the default home: come back to the front.
                    if (!played) bringKokpitBack()
                }
                container.wifi.reconnect(container.scope)
            }
        }
    }

    private suspend fun bringKokpitBack() {
        val container = (application as KokpitApp).container
        val settings = container.settings.current()
        if (!settings.wakeToKokpit) return
        // CarPlay is about to open on its own; don't cover it.
        if (settings.autoCarPlayOnUsb && isIphoneAttached(this)) return
        delay(WAKE_FRONT_DELAY_MS)
        // With usage access only the vendor launcher is replaced; without it, anything but Kokpit.
        if (HomeGuard.hasUsageAccess(this)) {
            HomeGuard.check(this)
        } else if (!MainActivity.isResumed) {
            log("bring Kokpit to front")
            MainActivity.bringToFront(this)
        }
    }

    /** Returns true when the welcome clip started (it hands over to Kokpit by itself). */
    private suspend fun playIntro(sleptMs: Long?): Boolean {
        val container = (application as KokpitApp).container
        val intro = container.settings.current().intro
        val videos = allIntroVideos(container.introLibrary)
        val season = seasonFor(io.github.yorulmazsinan.kokpit.time.TrueTime.date(), currentTemperature(container.weather.state.value))
        if (IntroGate.recentlyPlayed()) return true
        val video = pickIntroVideo(intro, videos, sleptMs, Random.Default, season) ?: return false
        if (!introAllowedAt(container.drive.state.value.speedKmh)) {
            log("intro skipped: moving")
            return false
        }
        container.settings.update { it.copy(intro = it.intro.copy(lastPlayedIndex = videos.indexOf(video))) }
        startActivity(IntroActivity.intent(this, video.id, intro.withSound, returnHome = true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    companion object {
        private const val RESUME_DELAY_MS = 9_000L
        private const val WAKE_FRONT_DELAY_MS = 2_500L

        /** Last handled wake-up, shared by every path that reports one. */
        @Volatile private var lastWakeAt: Long? = null
        const val CHANNEL_ID = "kokpit"
        private const val SERVICE_NOTIFICATION_ID = 1
        private const val EXTRA_COLD_BOOT = "cold_boot"
        private const val EXTRA_POWER_ACTION = "power_action"

        fun start(context: Context, coldBoot: Boolean = false, powerAction: String? = null) {
            val intent = Intent(context, KokpitService::class.java)
                .putExtra(EXTRA_COLD_BOOT, coldBoot)
                .putExtra(EXTRA_POWER_ACTION, powerAction)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_MIN)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
