package io.github.yorulmazsinan.kokpit.panel

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** At most this many remembered phones; a new one pushes out the oldest. */
const val MAX_TRUSTED = 4

/** A phone the owner chose to remember ("Bu telefonu hatırla"). */
data class TrustedPhone(val address: String, val name: String, val sinceMs: Long)

fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * Remembered phones: a phone that once entered the PIN with "Bu telefonu hatırla" gets a
 * random key, and from then on skips the PIN — but only over a paired Bluetooth link from
 * that very phone (the link's own address), and only when the key matches. The unit keeps a
 * hash of the key, never the key. Forgotten one by one, or all with the PIN reset, on the unit.
 */
class PanelTrust(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.applicationContext.getSharedPreferences("panel_trust", Context.MODE_PRIVATE)

    fun list(): List<TrustedPhone> = prefs.all.mapNotNull { (k, v) ->
        val parts = (v as? String)?.split('|', limit = 3) ?: return@mapNotNull null
        if (!k.startsWith(PREFIX) || parts.size < 3) return@mapNotNull null
        TrustedPhone(k.removePrefix(PREFIX), parts[2], parts[1].toLongOrNull() ?: 0L)
    }.sortedByDescending { it.sinceMs }

    /** Remembers [address] and returns its new key (shown to Kokpit Kumanda once). */
    @Synchronized
    fun add(address: String, name: String): String {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val edit = prefs.edit()
        list().filter { it.address != address }.drop(MAX_TRUSTED - 1).forEach { edit.remove(PREFIX + it.address) }
        edit.putString(PREFIX + address, "${sha256Hex(key)}|${clock()}|${name.filterNot(Char::isISOControl).take(60)}").commit()
        return key
    }

    fun check(address: String?, key: String?): Boolean {
        if (address == null || !isDeviceKey(key)) return false
        val stored = prefs.getString(PREFIX + address, null)?.substringBefore('|') ?: return false
        return io.github.yorulmazsinan.kokpit.car.tokenMatches(stored, sha256Hex(key!!))
    }

    fun remove(address: String) {
        prefs.edit().remove(PREFIX + address).commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFIX = "d_"
    }
}
