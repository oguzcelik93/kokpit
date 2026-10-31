package io.github.yorulmazsinan.kokpit.intro

import java.io.File
import java.io.InputStream
import java.util.UUID

sealed class IntroImportError : Exception() {
    class TooLarge : IntroImportError()
    class UnsupportedType : IntroImportError()
}

/**
 * Intro videos copied into app storage once, so the USB stick can be removed. Each video is
 * `<id>.mp4` with its original name in `<id>.name`.
 */
class IntroVideoLibrary(private val dir: File) {
    fun list(): List<IntroVideo> {
        val files = dir.listFiles { f -> f.extension == "mp4" }.orEmpty()
        return files.sortedBy { it.lastModified() }.map { file ->
            val id = file.nameWithoutExtension
            IntroVideo(id, nameFile(id).takeIf { it.exists() }?.readText() ?: file.name, file.length())
        }
    }

    fun import(input: InputStream, displayName: String, sizeBytes: Long): Result<IntroVideo> {
        if (!displayName.endsWith(".mp4", ignoreCase = true)) return Result.failure(IntroImportError.UnsupportedType())
        if (sizeBytes > MAX_BYTES) return Result.failure(IntroImportError.TooLarge())
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.mp4")
        var copied = 0L
        target.outputStream().use { out ->
            input.use { src ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = src.read(buffer)
                    if (n < 0) break
                    copied += n
                    if (copied > MAX_BYTES) {
                        out.close()
                        target.delete()
                        return Result.failure(IntroImportError.TooLarge())
                    }
                    out.write(buffer, 0, n)
                }
            }
        }
        nameFile(id).writeText(displayName)
        return Result.success(IntroVideo(id, displayName, copied))
    }

    fun delete(id: String) {
        File(dir, "$id.mp4").delete()
        nameFile(id).delete()
    }

    fun fileFor(id: String): File? = File(dir, "$id.mp4").takeIf { it.exists() }

    private fun nameFile(id: String) = File(dir, "$id.name")

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
