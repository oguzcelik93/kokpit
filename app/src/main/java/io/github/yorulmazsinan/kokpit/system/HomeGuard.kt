package io.github.yorulmazsinan.kokpit.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import io.github.yorulmazsinan.kokpit.MainActivity
import io.github.yorulmazsinan.kokpit.connect.PowerEventLog
import java.time.LocalTime

/** At most this many bring-backs per minute: never fight the firmware in a loop. */
const val GUARD_MAX_PER_MINUTE = 3

/** Keeps only bring-back times from the last minute and says whether another is allowed. */
fun guardAllowed(recent: List<Long>, now: Long): Boolean = recent.count { now - it in 0..60_000L } < GUARD_MAX_PER_MINUTE

/**
 * Some firmwares (XY Auto) push their own launcher to the front even when Kokpit is the
 * default home: after the reversing camera, at start-up, from vendor services. Kokpit then
 * comes back — but only when the thing in front is the vendor *launcher*, never when the
 * driver opened radio, maps or anything else. Needs "usage access" to see what is in front.
 */
object HomeGuard {
    private val handler = Handler(Looper.getMainLooper())
    private val recent = ArrayDeque<Long>()

    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
    }

    /** Every other HOME app (the vendor launcher), minus Android's boot-time placeholder. */
    fun vendorHomes(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName && it != "com.android.settings" }
            .toSet()
    }

    /** The app that most recently came to the front, from the last [windowMs]. */
    fun foregroundPackage(context: Context, windowMs: Long = 15_000): String? = runCatching {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - windowMs, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = event.packageName
        }
        last
    }.getOrNull()

    /** Kokpit just left the screen: check shortly whether the vendor launcher took it. */
    fun onKokpitStopped(context: Context, enabled: Boolean) {
        if (!enabled) return
        val app = context.applicationContext
        handler.postDelayed({ check(app) }, 1_500)
    }

    /** Also used at ignition-on. Returns true when Kokpit was brought back. */
    fun check(context: Context): Boolean {
        if (MainActivity.isResumed || !isDefaultHome(context) || !hasUsageAccess(context)) return false
        val front = foregroundPackage(context) ?: return false
        if (front !in vendorHomes(context)) return false
        val now = System.currentTimeMillis()
        while (recent.isNotEmpty() && now - recent.first() > 60_000) recent.removeFirst()
        if (!guardAllowed(recent.toList(), now)) {
            PowerEventLog.add(context, "${LocalTime.now().withNano(0)} $front ısrarla öne geçiyor, koruma 1 dk bekliyor")
            return false
        }
        recent.addLast(now)
        PowerEventLog.add(context, "${LocalTime.now().withNano(0)} $front öne geçti, Kokpit geri getirildi")
        MainActivity.bringToFront(context)
        return true
    }
}
