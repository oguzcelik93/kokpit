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
import io.github.yorulmazsinan.kokpit.time.TrueTime
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

/** File signatures of the picture formats Android can decode: JPEG, PNG, GIF, WebP, HEIC/HEIF. */
fun looksLikeImage(head: ByteArray): Boolean {
    fun at(i: Int) = head.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
    fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
    return (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) ||
        (at(0) == 0x89 && ascii(1, "PNG")) ||
        ascii(0, "GIF8") ||
        (ascii(0, "RIFF") && ascii(8, "WEBP")) ||
        (ascii(4, "ftyp") && listOf("heic", "heix", "heif", "mif1", "msf1", "hevc").any { ascii(8, it) })
}

/** Taller than wide (with a little slack for near-square photos). */
fun isPortrait(width: Int, height: Int): Boolean = height > width * 1.05f

/** The longest side Kokpit keeps: plenty for a 1024×600 screen, small in RAM. */
const val WALLPAPER_MAX_SIDE = 1280

/** Most photos kept in the album; the oldest goes when a new one comes. */
const val WALLPAPER_MAX_COUNT = 30

/** What the card draws: the photo, a blurred fill for the bars around a portrait photo, and its shape. */
data class WallpaperImage(val photo: ImageBitmap, val backdrop: ImageBitmap, val portrait: Boolean)

/**
 * The home screen card's photo album. Every photo is stored once, already shrunk and turned
 * the right way up, so showing it costs nothing; one of them is selected.
 */
class WallpaperStore(private val context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "wallpapers")
    private val prefs = context.getSharedPreferences("wallpaper", Context.MODE_PRIVATE)
    private val _image = MutableStateFlow<WallpaperImage?>(null)
    val image: StateFlow<WallpaperImage?> = _image.asStateFlow()
    private val _photos = MutableStateFlow<List<String>>(emptyList())
    /** Photo ids, newest first. */
    val photos: StateFlow<List<String>> = _photos.asStateFlow()
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()
    private val thumbs = mutableMapOf<String, ImageBitmap>()

    init {
        scope.launch(Dispatchers.IO) {
            migrateSingleWallpaper()
            refreshList()
            val chosen = prefs.getString(KEY_SELECTED, null)?.takeIf { it in _photos.value } ?: _photos.value.firstOrNull()
            _selected.value = chosen
            _image.value = chosen?.let(::load)
        }
    }

    fun searchRoots(): List<File> {
        @Suppress("DEPRECATION")
        val public = listOf(Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM)
            .map { Environment.getExternalStoragePublicDirectory(it) }
        @Suppress("DEPRECATION")
        val bluetooth = File(Environment.getExternalStorageDirectory(), "Bluetooth")
        return removableRoots(context) + public + bluetooth
    }

    /** Adds a photo to the album and shows it. False for anything that isn't a picture. Blocking. */
    fun save(input: InputStream): Boolean {
        val tmp = File(context.cacheDir, "wallpaper-in")
        input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
        try {
            val head = ByteArray(16).also { buf -> tmp.inputStream().use { it.read(buf) } }
            if (!looksLikeImage(head)) return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val degrees = runCatching { exifRotation(ExifInterface(tmp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) }.getOrDefault(0)
            val sideways = degrees == 90 || degrees == 270
            val w = if (sideways) bounds.outHeight else bounds.outWidth
            val h = if (sideways) bounds.outWidth else bounds.outHeight
            val (maxW, maxH) = if (isPortrait(w, h)) 720 to WALLPAPER_MAX_SIDE else WALLPAPER_MAX_SIDE to 720
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, if (sideways) maxH else maxW, if (sideways) maxW else maxH)
            val decoded = BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
            val finalBitmap = fitLongestSide(rotate(decoded, degrees))
            dir.mkdirs()
            val id = TrueTime.nowMs().toString()
            File(dir, "$id.jpg").outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            trimAlbum()
            refreshList()
            select(id, finalBitmap)
            return true
        } finally {
            tmp.delete()
        }
    }

    /** Shows another photo from the album. */
    fun select(id: String) {
        scope.launch(Dispatchers.IO) { BitmapFactory.decodeFile(File(dir, "$id.jpg").path)?.let { select(id, it) } }
    }

    /** Turns the selected photo a quarter clockwise. Blocking. */
    fun rotateQuarter(): Boolean {
        val id = _selected.value ?: return false
        val file = File(dir, "$id.jpg")
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return false
        val turned = rotate(bitmap, 90)
        file.outputStream().use { turned.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        thumbs.remove(id)
        select(id, turned)
        _photos.value = _photos.value.toList() // redraw thumbnails
        return true
    }

    /** Removes a photo; when it was the one shown, the newest remaining takes its place. */
    fun delete(id: String) {
        scope.launch(Dispatchers.IO) {
            File(dir, "$id.jpg").delete()
            thumbs.remove(id)
            refreshList()
            if (_selected.value == id) {
                val next = _photos.value.firstOrNull()
                if (next == null) {
                    _selected.value = null
                    _image.value = null
                    prefs.edit().remove(KEY_SELECTED).apply()
                } else {
                    BitmapFactory.decodeFile(File(dir, "$next.jpg").path)?.let { select(next, it) }
                }
            }
        }
    }

    /** A small picture for the album strip (cached). Blocking. */
    fun thumbnail(id: String): ImageBitmap? = thumbs[id] ?: run {
        val file = File(dir, "$id.jpg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, 200, 200)
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()?.also { thumbs[id] = it }
    }

    private fun select(id: String, bitmap: Bitmap) {
        // Picture first, then the selection: whoever sees the new id also sees its picture.
        _image.value = toImage(bitmap)
        prefs.edit().putString(KEY_SELECTED, id).apply()
        _selected.value = id
    }

    private fun load(id: String): WallpaperImage? = BitmapFactory.decodeFile(File(dir, "$id.jpg").path)?.let(::toImage)

    /**
     * A tiny copy, drawn stretched, blurs by itself (bilinear upscaling): the fill for the
     * sides of a portrait photo, with no blur API (Android 9 has none for apps).
     */
    private fun toImage(bitmap: Bitmap): WallpaperImage {
        val tinyW = 24
        val tinyH = (tinyW * bitmap.height / bitmap.width).coerceAtLeast(1)
        val backdrop = Bitmap.createScaledBitmap(bitmap, tinyW, tinyH, true)
        return WallpaperImage(bitmap.asImageBitmap(), backdrop.asImageBitmap(), isPortrait(bitmap.width, bitmap.height))
    }

    private fun refreshList() {
        _photos.value = dir.listFiles { f -> f.extension == "jpg" }.orEmpty()
            .map { it.nameWithoutExtension }
            .sortedByDescending { it.toLongOrNull() ?: 0L }
    }

    private fun trimAlbum() {
        dir.listFiles { f -> f.extension == "jpg" }.orEmpty()
            .sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            .drop(WALLPAPER_MAX_COUNT - 1)
            .forEach { it.delete(); thumbs.remove(it.nameWithoutExtension) }
    }

    /** 0.10 kept one photo as wallpaper.jpg; it becomes the album's first photo. */
    private fun migrateSingleWallpaper() {
        val old = File(context.filesDir, "wallpaper.jpg")
        if (!old.exists()) return
        dir.mkdirs()
        val id = old.lastModified().toString()
        if (old.renameTo(File(dir, "$id.jpg"))) prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    private fun fitLongestSide(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= WALLPAPER_MAX_SIDE) return bitmap
        val f = WALLPAPER_MAX_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt(), (bitmap.height * f).toInt(), true)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
