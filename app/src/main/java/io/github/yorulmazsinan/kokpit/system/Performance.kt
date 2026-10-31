package io.github.yorulmazsinan.kokpit.system

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * Packages that ship on MT8227L / FYT / TS head units and do nothing useful in a car.
 * Kept in sync with `araclar/listeler/guvenli.txt` (the ADB script's list). Nothing here
 * is needed for radio, Bluetooth, CarPlay, GPS or the vendor's MCU link.
 */
val KNOWN_BLOAT: Map<String, String> = linkedMapOf(
    "com.mediatek.mtklogger" to "MTK Logger (sürekli log yazar, belleği ve depolamayı yorar)",
    "com.mediatek.duraspeed" to "DuraSpeed (arka plan uygulamalarını öldürür; Kokpit ve ZLink'i de)",
    "com.mediatek.ygps" to "YGPS test aracı",
    "com.android.printspooler" to "Yazdırma servisi",
    "com.android.bips" to "Yazıcı eklentisi",
    "com.android.printservice.recommendation" to "Yazıcı önerileri",
    "com.android.dreams.basic" to "Ekran koruyucu",
    "com.android.dreams.phototable" to "Fotoğraf ekran koruyucu",
    "com.android.wallpaper.livepicker" to "Canlı duvar kâğıdı seçici",
    "com.android.galaxy4" to "Canlı duvar kâğıdı",
    "com.android.noisefield" to "Canlı duvar kâğıdı",
    "com.android.phasebeam" to "Canlı duvar kâğıdı",
    "com.android.magicsmoke" to "Canlı duvar kâğıdı",
    "com.android.musicvis" to "Müzik görselleştirici duvar kâğıdı",
    "com.android.egg" to "Android paskalya yumurtası",
    "com.android.email" to "E-posta",
    "com.android.exchange" to "Exchange hesap servisi",
    "com.android.quicksearchbox" to "Hızlı arama kutusu",
    "com.android.bookmarkprovider" to "Yer imi sağlayıcı",
    "com.android.providers.partnerbookmarks" to "Üretici yer imleri",
    "com.android.stk" to "SIM araç seti",
    "com.android.cellbroadcastreceiver" to "Hücre yayını uyarıları",
    "com.android.wallpaperbackup" to "Duvar kâğıdı yedeği",
    "com.android.sharedstoragebackup" to "Depolama yedeği",
    "com.android.calllogbackup" to "Arama kaydı yedeği",
    "com.android.onetimeinitializer" to "Tek seferlik başlatıcı",
    "com.android.smspush" to "WAP push",
    "com.google.android.feedback" to "Google geri bildirim",
    "com.google.android.apps.youtube.music" to "YouTube Music (kullanmıyorsanız)",
    "com.google.android.videos" to "Google TV / Play Filmler",
    "com.google.android.apps.photos" to "Google Fotoğraflar",
    "com.google.android.apps.docs" to "Google Drive",
)

data class BloatEntry(val packageName: String, val description: String, val label: String, val enabled: Boolean)

/** Animation scales: 1 is stock, 0.5 feels twice as fast, 0 turns them off. */
private val ANIMATION_KEYS = listOf(
    Settings.Global.WINDOW_ANIMATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.ANIMATOR_DURATION_SCALE,
)

object Performance {
    private const val PREFS = "performance"
    private const val KEY_SAVED = "saved_animation_"

    fun knownBloat(pm: PackageManager): List<BloatEntry> = KNOWN_BLOAT.mapNotNull { (pkg, desc) ->
        val info = runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS) }.getOrNull() ?: return@mapNotNull null
        BloatEntry(pkg, desc, info.loadLabel(pm).toString(), info.enabled)
    }

    /** Every app currently switched off, so it can be turned back on from its info page. */
    fun disabledApps(pm: PackageManager): List<BloatEntry> =
        pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .filter { !it.enabled }
            .map { BloatEntry(it.packageName, KNOWN_BLOAT[it.packageName].orEmpty(), it.loadLabel(pm).toString(), false) }
            .sortedBy { it.label.lowercase() }

    fun appInfoIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Ends cached background processes of other apps (KILL_BACKGROUND_PROCESSES, a normal
     * permission). Foreground apps and running services are left alone, so CarPlay,
     * Bluetooth and radio keep working. Returns MB of RAM freed, roughly.
     */
    fun freeMemory(context: Context): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        val before = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
        context.packageManager.getInstalledApplications(0)
            .filter { it.packageName != context.packageName && it.flags and ApplicationInfo.FLAG_PERSISTENT == 0 }
            .forEach { runCatching { am.killBackgroundProcesses(it.packageName) } }
        val after = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
        return ((after - before) / (1024 * 1024)).coerceAtLeast(0)
    }

    fun memorySummary(context: Context): Pair<Long, Long> {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return info.availMem / (1024 * 1024) to info.totalMem / (1024 * 1024)
    }

    /** True once `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` was run. */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun currentAnimationScale(context: Context): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)

    /** Sets all three animation scales, remembering the originals the first time. */
    fun setAnimationScale(context: Context, scale: Float): Boolean {
        if (!canWriteSecureSettings(context)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        ANIMATION_KEYS.forEach { key ->
            if (!prefs.contains(KEY_SAVED + key)) edit.putFloat(KEY_SAVED + key, Settings.Global.getFloat(context.contentResolver, key, 1f))
        }
        edit.commit()
        return ANIMATION_KEYS.all { runCatching { Settings.Global.putFloat(context.contentResolver, it, scale) }.getOrDefault(false) }
    }

    /** Puts back the animation scales saved before Kokpit first changed them. */
    fun restoreAnimations(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!canWriteSecureSettings(context)) return
        val edit = prefs.edit()
        ANIMATION_KEYS.forEach { key ->
            if (prefs.contains(KEY_SAVED + key)) {
                runCatching { Settings.Global.putFloat(context.contentResolver, key, prefs.getFloat(KEY_SAVED + key, 1f)) }
                edit.remove(KEY_SAVED + key)
            }
        }
        edit.commit()
    }
}
