package io.github.oguzcelik93.kokpit.kumanda

import android.content.Context

/**
 * If Kumanda ever crashes, the error is kept (on this phone only) and shown at the next
 * start, so it can be copied and sent instead of guessed at.
 */
object CrashNote {
    private const val PREFS = "crash"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val trace = "Kumanda ${BuildConfigName.version(app)} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}\n" +
                    android.util.Log.getStackTraceString(e).take(6_000)
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last", trace).commit()
            }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The saved crash (cleared once read), or null. */
    fun take(context: Context): String? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = p.getString("last", null) ?: return null
        p.edit().remove("last").apply()
        return t
    }
}

private object BuildConfigName {
    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
