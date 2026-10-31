package io.github.yorulmazsinan.kokpit.system

import android.content.Context
import io.github.yorulmazsinan.kokpit.BuildConfig
import io.github.yorulmazsinan.kokpit.storage.exportDirs
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

const val CRASH_FILE_NAME = "kokpit-crash.txt"

fun crashReport(error: Throwable, threadName: String, version: String, timeMs: Long): String {
    val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
    return "=== ${Instant.ofEpochMilli(timeMs)} · Kokpit $version · thread $threadName ===\n$trace\n"
}

fun appendCrash(file: File, report: String) {
    runCatching {
        file.parentFile?.mkdirs()
        file.appendText(report)
    }
}

/** Writes uncaught crashes to app storage and to the USB stick, then lets Android handle them. */
fun installCrashLog(context: Context) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    val app = context.applicationContext
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        val report = crashReport(error, thread.name, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        appendCrash(File(app.filesDir, CRASH_FILE_NAME), report)
        // Counts towards the crash loop that switches Kokpit to safe mode.
        runCatching { Recovery.recordCrash(app) }
        runCatching { exportDirs(app) }.getOrDefault(emptyList()).forEach { appendCrash(File(it, CRASH_FILE_NAME), report) }
        previous?.uncaughtException(thread, error)
    }
}

fun readCrashLog(context: Context): String? =
    File(context.filesDir, CRASH_FILE_NAME).takeIf { it.exists() }?.readText()?.takeLast(8_000)
