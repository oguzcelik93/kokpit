package io.github.yorulmazsinan.kokpit.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.yorulmazsinan.kokpit.MainActivity
import io.github.yorulmazsinan.kokpit.storage.exportDirs
import io.github.yorulmazsinan.kokpit.storage.removableRoots
import io.github.yorulmazsinan.kokpit.storage.writeFirst
import java.io.File
import java.time.Instant

/** Crashes inside this window count towards the crash loop. */
const val CRASH_WINDOW_MS = 3 * 60_000L

/** This many crashes inside [CRASH_WINDOW_MS] switch Kokpit to safe mode. */
const val CRASH_LIMIT = 3

/** Keeps only the crash times still inside the window ending at [now]. */
fun recentCrashes(times: List<Long>, now: Long): List<Long> = times.filter { now - it in 0..CRASH_WINDOW_MS }

fun isCrashLoop(times: List<Long>, now: Long): Boolean = recentCrashes(times, now).size >= CRASH_LIMIT

/** What a file on the USB stick's root asks Kokpit to do. */
enum class UsbTrigger(val fileName: String) {
    /** Safe mode + hand the screen back to the manufacturer launcher. */
    RECOVER("KOKPIT-KURTARMA"),

    /** Wipe Kokpit's own settings and start fresh (also leaves safe mode). */
    RESET("KOKPIT-SIFIRLA"),

    /** Leave safe mode. */
    NORMAL("KOKPIT-NORMAL"),
}

/** `KOKPIT-KURTARMA`, `kokpit-kurtarma.txt` … match; anything else does not. */
fun triggerFor(fileName: String): UsbTrigger? {
    val base = fileName.substringBeforeLast('.').takeIf { fileName.contains('.') } ?: fileName
    return UsbTrigger.entries.firstOrNull { it.fileName.equals(base.trim(), ignoreCase = true) }
}

/**
 * The "back door": whatever happens to Kokpit, the unit can be returned to how it was.
 *
 * - **Crash loop:** three crashes in three minutes switch to safe mode on their own.
 * - **Safe mode:** Home goes straight to the manufacturer launcher; Kokpit's icon opens
 *   only the Recovery screen.
 * - **USB stick:** an empty file named `KOKPIT-KURTARMA` (or `-SIFIRLA`, `-NORMAL`) on the
 *   stick's root does the same without touching the screen.
 * - **Hidden gesture:** long-press the clock on the home screen.
 *
 * Plain SharedPreferences with commit(): the crash handler runs just before the process
 * dies, so nothing asynchronous may be relied on.
 */
object Recovery {
    private const val PREFS = "recovery"
    private const val KEY_CRASHES = "crashes"
    private const val KEY_SAFE = "safe_mode"
    private const val KEY_REASON = "safe_reason"
    private const val KEY_HANDLED = "handled_triggers"
    const val RESULT_FILE = "KOKPIT-KURTARMA-SONUC.txt"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSafeMode(context: Context): Boolean = prefs(context).getBoolean(KEY_SAFE, false)

    fun safeModeReason(context: Context): String? = prefs(context).getString(KEY_REASON, null)

    fun setSafeMode(context: Context, on: Boolean, reason: String? = null) {
        prefs(context).edit().putBoolean(KEY_SAFE, on).putString(KEY_REASON, if (on) reason else null).remove(KEY_CRASHES).commit()
    }

    /** Called from the uncaught-exception handler. */
    fun recordCrash(context: Context, now: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val times = p.getString(KEY_CRASHES, "").orEmpty().split(',').mapNotNull(String::toLongOrNull) + now
        val kept = recentCrashes(times, now)
        val edit = p.edit().putString(KEY_CRASHES, kept.joinToString(","))
        if (isCrashLoop(kept, now)) edit.putBoolean(KEY_SAFE, true).putString(KEY_REASON, "crash")
        edit.commit()
    }

    /** Kokpit stayed up long enough: forget earlier crashes. */
    fun markStable(context: Context) {
        prefs(context).edit().remove(KEY_CRASHES).apply()
    }

    /** Opens the manufacturer launcher. Returns false when there is none. */
    fun openVendorHome(context: Context): Boolean {
        val shortcut = vendorHomeShortcut(context.packageManager, context.packageName) ?: return false
        return safeStart(context, Intent(shortcut.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Forgets "Kokpit → Always": the next Home press asks again. Allowed for one's own package. */
    @Suppress("DEPRECATION")
    fun clearHomeChoice(context: Context) {
        runCatching { context.packageManager.clearPackagePreferredActivities(context.packageName) }
    }

    /** Deletes Kokpit's own settings, dock choices and caches (not intro videos). */
    fun wipeKokpitSettings(context: Context) {
        File(context.filesDir, "datastore").listFiles().orEmpty().forEach { it.delete() }
        context.cacheDir.deleteRecursively()
    }

    /** Full hand-back: safe mode on, Home choice cleared, animations restored, stock launcher up. */
    fun handBack(context: Context, reason: String) {
        setSafeMode(context, true, reason)
        // Apps Kokpit switched off over ADB come back too, when ADB is still reachable.
        Thread { runCatching { LocalAdb(context).takeIf { it.disabledByKokpit.isNotEmpty() && it.isAvailable() }?.restore() } }.start()
        clearHomeChoice(context)
        Performance.restoreAnimations(context)
        openVendorHome(context)
    }

    /** Restarts Kokpit's process so it comes up with fresh state. */
    fun restart(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        if (context is Activity) context.finishAffinity()
        Process.killProcess(Process.myPid())
    }

    /**
     * Looks for trigger files on every visible USB/SD root and acts on each new one once
     * (same file, same timestamp → already handled). Returns the trigger acted on.
     */
    fun checkUsbTriggers(context: Context, roots: List<File> = removableRoots(context)): UsbTrigger? {
        val p = prefs(context)
        val handled = p.getStringSet(KEY_HANDLED, emptySet()).orEmpty()
        val found = roots.flatMap { root -> root.listFiles().orEmpty().filter { it.isFile }.mapNotNull { f -> triggerFor(f.name)?.let { it to f } } }
        // RESET beats RECOVER beats NORMAL when several files are present.
        val (trigger, file) = found.minByOrNull { it.first.ordinal.let { o -> listOf(1, 0, 2)[o] } } ?: return null
        val signature = "${file.absolutePath}@${file.lastModified()}"
        if (signature in handled) return null
        p.edit().putStringSet(KEY_HANDLED, handled + signature).commit()
        when (trigger) {
            UsbTrigger.RECOVER -> handBack(context, "usb")
            UsbTrigger.RESET -> {
                wipeKokpitSettings(context)
                Performance.restoreAnimations(context)
                clearHomeChoice(context)
                setSafeMode(context, false)
            }
            UsbTrigger.NORMAL -> setSafeMode(context, false)
        }
        writeResult(context, trigger, file.parentFile)
        return trigger
    }

    private fun writeResult(context: Context, trigger: UsbTrigger, root: File?) {
        val text = buildString {
            appendLine("Kokpit kurtarma · ${Instant.now()}")
            appendLine("İşlem: ${trigger.fileName}")
            appendLine("Güvenli mod: ${if (isSafeMode(context)) "AÇIK" else "kapalı"}")
            appendLine()
            when (trigger) {
                UsbTrigger.RECOVER -> appendLine("Ana ekran üreticinin ekranına bırakıldı. Ev tuşuna basıp üreticinin ekranını 'Her zaman' seçin.\nNormale dönmek için bu dosyayı silip KOKPIT-NORMAL adlı boş bir dosya koyun.")
                UsbTrigger.RESET -> appendLine("Kokpit ayarları silindi, Kokpit ilk kurulum haline döndü.")
                UsbTrigger.NORMAL -> appendLine("Güvenli moddan çıkıldı.")
            }
            readCrashLog(context)?.let { appendLine(); appendLine("Son hatalar:"); appendLine(it.takeLast(3_000)) }
        }
        val dirs = listOfNotNull(root) + exportDirs(context)
        writeFirst(dirs, RESULT_FILE, text)
    }
}
