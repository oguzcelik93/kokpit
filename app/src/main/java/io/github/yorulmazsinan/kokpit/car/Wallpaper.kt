package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.yorulmazsinan.kokpit.storage.removableRoots
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** EXIF orientation → degrees to rotate clockwise (phones save portraits sideways and note it here). */
fun exifRotation(orientation: Int): Int = when (orientation) {
    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
    ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
    else -> 0
}

/** Largest power-of-two step that keeps the image at least [maxW]×[maxH]: a phone photo never fills 1 GB of RAM. */
fun sampleSizeFor(width: Int, height: Int, maxW: Int, maxH: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= maxW && height / (sample * 2) >= maxH) sample *= 2
    return sample
}

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

/** Photos on USB sticks, SD cards and the unit's Download / Pictures / DCIM / Bluetooth folders. */
fun findImages(roots: List<File>, maxDepth: Int = 3): List<File> {
    val found = linkedSetOf<File>()
    fun walk(dir: File, level: Int) {
        dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.forEach { child ->
            when {
                child.name.startsWith(".") -> Unit
                child.isDirectory -> if (level < maxDepth && child.name != "Android") walk(child, level + 1)
                child.extension.lowercase() in IMAGE_EXT && child.length() > 10_000 -> found += child.canonicalFile
            }
        }
    }
    roots.map { it.canonicalFile }.distinct().filter { it.isDirectory }.forEach { walk(it, 1) }
    return found.toList().take(200)
}

/** The home screen card's photo, stored once at screen size so drawing it costs nothing. */
class WallpaperStore(private val context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, "wallpaper.jpg")
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()

    init {
        scope.launch { _image.value = withContext(Dispatchers.IO) { load() } }
    }

    fun searchRoots(): List<File> {
        @Suppress("DEPRECATION")
        val public = listOf(Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM)
            .map { Environment.getExternalStoragePublicDirectory(it) }
        @Suppress("DEPRECATION")
        val bluetooth = File(Environment.getExternalStorageDirectory(), "Bluetooth")
        return removableRoots(context) + public + bluetooth
    }

    /** Decodes, shrinks to screen size and saves. Returns false for anything that isn't a picture. Blocking. */
    fun save(input: InputStream): Boolean {
        val tmp = File(context.cacheDir, "wallpaper-in")
        input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_W, MAX_H) }
            val decoded = BitmapFactory.decodeFile(tmp.path, options) ?: return false
            val degrees = runCatching { exifRotation(ExifInterface(tmp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) }.getOrDefault(0)
            val scaled = scaleDown(rotate(decoded, degrees))
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            _image.value = scaled.asImageBitmap()
            return true
        } finally {
            tmp.delete()
        }
    }

    /** Turns the saved photo a quarter clockwise, for pictures that carry no orientation. Blocking. */
    fun rotateQuarter(): Boolean {
        val bitmap = file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } ?: return false
        val turned = rotate(bitmap, 90)
        file.outputStream().use { turned.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        _image.value = turned.asImageBitmap()
        return true
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun clear() {
        file.delete()
        _image.value = null
    }

    private fun load(): ImageBitmap? = file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val factor = minOf(MAX_W.toFloat() / bitmap.width, MAX_H.toFloat() / bitmap.height)
        if (factor >= 1f) return bitmap
        // Cover the card: keep the larger side's ratio so cropping still fills it.
        val cover = maxOf(MAX_W.toFloat() / bitmap.width, MAX_H.toFloat() / bitmap.height).coerceAtMost(1f)
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * cover).toInt(), (bitmap.height * cover).toInt(), true)
    }

    private companion object {
        const val MAX_W = 1280
        const val MAX_H = 720
    }
}
