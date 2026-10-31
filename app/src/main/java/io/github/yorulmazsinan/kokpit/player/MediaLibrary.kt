package io.github.yorulmazsinan.kokpit.player

import java.io.File

enum class MediaKind { AUDIO, VIDEO }

data class MediaFile(val path: String, val name: String, val folder: String, val kind: MediaKind)

private val AUDIO = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")
private val VIDEO = setOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "ts", "m4v")

fun mediaKindOf(file: File): MediaKind? = when (file.extension.lowercase()) {
    in AUDIO -> MediaKind.AUDIO
    in VIDEO -> MediaKind.VIDEO
    else -> null
}

/** Music and video on USB sticks / SD cards, folder by folder, skipping hidden and Android folders. */
fun findMedia(roots: List<File>, maxDepth: Int = 5, limit: Int = 2_000): List<MediaFile> {
    val found = linkedMapOf<String, MediaFile>()
    fun walk(dir: File, level: Int) {
        dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }.forEach { child ->
            if (found.size >= limit) return
            when {
                child.name.startsWith(".") -> Unit
                child.isDirectory -> if (level < maxDepth && child.name != "Android" && child.name != "LOST.DIR") walk(child, level + 1)
                else -> mediaKindOf(child)?.let { kind ->
                    val file = child.canonicalFile
                    found.getOrPut(file.path) { MediaFile(file.path, file.nameWithoutExtension, file.parentFile?.name.orEmpty(), kind) }
                }
            }
        }
    }
    roots.map { it.canonicalFile }.distinct().filter { it.isDirectory }.forEach { walk(it, 1) }
    return found.values.toList()
}

/**
 * Whether the car counts as moving, with hysteresis so GPS jitter at a standstill does not
 * flicker the picture: on from 10 km/h, off below 5 km/h; no reading keeps the last answer.
 */
fun stillMoving(wasMoving: Boolean, speedKmh: Int?): Boolean = when {
    speedKmh == null -> wasMoving
    speedKmh >= 10 -> true
    speedKmh < 5 -> false
    else -> wasMoving
}
