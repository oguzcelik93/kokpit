package io.github.oguzcelik93.kokpit.kumanda

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Photos are shrunk to this longest side before the slow Bluetooth trip (the unit's screen is 1024 wide). */
private const val SHARE_PHOTO_SIDE = 1600

/**
 * "Paylaş → Kokpit": a place from a maps app becomes a route waiting on the unit ("Yola
 * çık"), a photo from the gallery goes into the unit's album. Only a phone the unit
 * remembers can do this (a key the unit gave it after the PIN); nothing else is reachable.
 */
class ShareActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#d8eec4"))
            setBackgroundColor(Color.parseColor("#0b0d0a"))
            gravity = Gravity.CENTER
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            text = getString(R.string.share_sending)
        }
        setContentView(status)
        val link = UnitLink(this)
        when {
            link.lastUnit == null || !link.remembered -> finishWith(getString(R.string.share_not_remembered))
            Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ->
                finishWith(getString(R.string.need_permission))
            !link.isEnabled -> finishWith(getString(R.string.bt_off))
            else -> Thread { send(link, intent) }.start()
        }
    }

    private fun send(link: UnitLink, intent: Intent) {
        val message = try {
            link.connect(null)
            val type = intent.type.orEmpty()
            if (type.startsWith("image/")) sendPhotos(link, imageUris(intent)) else sendRoute(link, intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        } catch (e: LinkException) {
            e.message.orEmpty()
        } finally {
            link.close()
        }
        main.post { finishWith(message) }
    }

    private fun sendRoute(link: UnitLink, text: String): String {
        val first = firstLink(text)
        val resolved = first?.takeIf(::isShortMapsLink)?.let(::follow)
        val place = sharedPlace(text, resolved) ?: return getString(R.string.share_no_place)
        val body = JSONObject().apply {
            if (place.lat != null && place.lon != null) { put("lat", place.lat.toString()); put("lon", place.lon.toString()) }
            put("label", place.label)
        }.toString().toByteArray()
        val (code, reply) = link.request("POST", "/api/share/nav", body, "application/json")
        return answer(link, code, reply, getString(R.string.share_route_done, place.label))
    }

    private fun sendPhotos(link: UnitLink, uris: List<Uri>): String {
        if (uris.isEmpty()) return getString(R.string.share_no_photo)
        var sent = 0
        var last = ""
        uris.take(10).forEachIndexed { i, uri ->
            main.post { status.text = getString(R.string.share_photo_progress, i + 1, uris.size.coerceAtMost(10)) }
            val bytes = shrink(uri) ?: return@forEachIndexed
            val (code, reply) = link.request("POST", "/api/share/photo", bytes, "image/jpeg")
            if (code == 200) sent++ else last = answer(link, code, reply, "")
            if (code == 403) return last
        }
        return if (sent > 0) getString(R.string.share_photo_done, sent) else last.ifEmpty { getString(R.string.share_no_photo) }
    }

    /** A refusal for "not remembered" means the unit forgot this phone: forget its key too. */
    private fun answer(link: UnitLink, code: Int, reply: String, ok: String): String = when (code) {
        200 -> ok
        403 -> { link.forgetKey(); reply.ifBlank { getString(R.string.share_not_remembered) } }
        else -> reply.ifBlank { getString(R.string.share_failed) }
    }

    private fun imageUris(intent: Intent): List<Uri> {
        @Suppress("DEPRECATION")
        return when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        }
    }

    /** Upright (EXIF applied) JPEG, longest side [SHARE_PHOTO_SIDE]: a few hundred KB instead of several MB. */
    private fun shrink(uri: Uri): ByteArray? = runCatching {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
                val big = maxOf(info.size.width, info.size.height)
                if (big > SHARE_PHOTO_SIDE) d.setTargetSize(info.size.width * SHARE_PHOTO_SIDE / big, info.size.height * SHARE_PHOTO_SIDE / big)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SHARE_PHOTO_SIDE) sample *= 2
            val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
            val degrees = contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (degrees == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    }.getOrNull()

    /** Follows a short maps link (https only, a few hops) to the address that has the place in it. */
    private fun follow(start: String): String? {
        var url = start
        repeat(6) {
            if (!url.startsWith("https://")) return null
            val c = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 6_000
                c.readTimeout = 6_000
                c.requestMethod = "GET"
                val code = c.responseCode
                val next = c.getHeaderField("Location") ?: return url.takeIf { code in 200..299 }
                url = URL(URL(url), next).toString()
                // Google's cookie page carries the real address in "continue".
                if ("consent." in url) Uri.parse(url).getQueryParameter("continue")?.let { url = it }
                if (coordinatesIn(url) != null) return url
            } catch (_: Exception) {
                return null
            } finally {
                c.disconnect()
            }
        }
        return url
    }

    private fun finishWith(message: String) {
        status.text = message
        main.postDelayed({ finish() }, 2_500)
    }
}
