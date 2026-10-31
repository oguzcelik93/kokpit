package io.github.yorulmazsinan.kokpit.storage

import android.content.Context
import java.io.File

/**
 * Some head-unit firmwares ship without Android's document picker, so Kokpit reads
 * USB sticks and SD cards directly. These helpers stay free of Android types for testing.
 */
fun findVideos(roots: List<File>, maxDepth: Int = 4): List<File> {
    val found = linkedSetOf<File>()
    fun walk(dir: File, level: Int) {
        dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
            when {
                child.name.startsWith(".") -> Unit
                child.isDirectory -> if (level < maxDepth && child.name != "Android") walk(child, level + 1)
                child.extension.equals("mp4", ignoreCase = true) -> found += child.canonicalFile
            }
        }
    }
    roots.map { it.canonicalFile }.distinct().filter { it.isDirectory }.forEach { walk(it, 1) }
    return found.toList()
}

/** `/storage/XXXX/Android/data/<pkg>/files` → `/storage/XXXX`; null for paths outside a volume's app folder. */
fun volumeRoot(appFilesDir: File): File? {
    val path = appFilesDir.path
    val marker = path.indexOf("/Android/data/")
    return if (marker > 0) File(path.substring(0, marker)) else null
}

/** Writes [text] as [name] into every writable directory and returns the files that were written. */
fun writeToDirs(dirs: List<File>, name: String, text: String): List<File> = dirs.mapNotNull { dir ->
    runCatching {
        dir.mkdirs()
        File(dir, name).apply { writeText(text) }
    }.getOrNull()
}

/** Removable volumes (USB, SD) visible to the app, excluding internal storage. */
fun removableRoots(context: Context): List<File> {
    val internal = context.getExternalFilesDir(null)?.let(::volumeRoot)
    val fromApi = context.getExternalFilesDirs(null).filterNotNull().mapNotNull(::volumeRoot)
    val fromStorage = File("/storage").listFiles().orEmpty()
        .filter { it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead() }
    val fromMnt = listOf("/mnt/usb_storage", "/mnt/media_rw", "/mnt/usbotg", "/mnt/udisk")
        .map(::File).filter { it.isDirectory && it.canRead() }
    return (fromApi + fromStorage + fromMnt).filter { it != internal }.distinctBy { it.canonicalPath }
}

/** App-specific folders on removable volumes: always writable without any permission. */
fun removableAppDirs(context: Context): List<File> {
    val internal = context.getExternalFilesDir(null)
    return context.getExternalFilesDirs(null).filterNotNull().filter { it != internal }
}

/** Where Kokpit writes files for the user: USB roots first, then USB app folders, then internal storage. */
fun exportDirs(roots: List<File>, appDirs: List<File>, internal: File?): List<File> =
    (roots.map { File(it, "Kokpit") } + appDirs + listOfNotNull(internal?.let { File(it, "Kokpit") }))
        .distinctBy { it.absolutePath }

/** Writes into the first directory that accepts the file; returns it, or null if none did. */
fun writeFirst(dirs: List<File>, name: String, text: String): File? =
    dirs.firstNotNullOfOrNull { dir ->
        runCatching {
            dir.mkdirs()
            File(dir, name).apply { writeText(text) }.takeIf { it.length() > 0 || text.isEmpty() }
        }.getOrNull()
    }

fun exportDirs(context: Context): List<File> =
    exportDirs(removableRoots(context), removableAppDirs(context), android.os.Environment.getExternalStorageDirectory())
