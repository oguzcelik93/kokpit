package io.github.yorulmazsinan.kokpit.panel

import android.content.Context
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PINs anyone would try first. */
private val WEAK_PINS = setOf("1234", "4321", "1212", "1122", "0123", "9876", "2580", "1004", "2000", "1010")

/** Exactly four digits, not all the same and not an obvious one. */
fun isAcceptablePin(pin: String): Boolean =
    pin.length == 4 && pin.all(Char::isDigit) && pin.toSet().size > 1 && pin !in WEAK_PINS

/** After this many wrong PINs in a row, the panel locks for a while. */
const val PIN_TRIES = 5

/** Lock length: 5 min, doubling with each further lock, at most an hour. */
fun lockMillis(locksSoFar: Int): Long = (5 * 60_000L shl locksSoFar.coerceIn(0, 4)).coerceAtMost(60 * 60_000L)

/** Slow, salted hash, so the stored value can't be turned back into the PIN. */
fun hashPin(pin: String, salt: ByteArray): ByteArray =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 20_000, 256))
        .encoded

private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
    return diff == 0
}

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val triesLeft: Int) : PinResult
    data class Locked(val untilMs: Long) : PinResult
}

/**
 * The owner's 4-digit panel PIN: set on the first connection, kept only as a salted slow
 * hash, locked after [PIN_TRIES] wrong tries (longer each time), reset only on the unit.
 */
class PanelPin(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val prefs = context.applicationContext.getSharedPreferences("panel_pin", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains(KEY_HASH)

    fun lockedUntil(): Long = prefs.getLong(KEY_LOCKED_UNTIL, 0L).takeIf { it > clock() } ?: 0L

    /** First connection only. */
    @Synchronized
    fun create(pin: String): Boolean {
        if (isSet || !isAcceptablePin(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_SALT, salt.hex()).putString(KEY_HASH, hashPin(pin, salt).hex())
            .putInt(KEY_FAILS, 0).putInt(KEY_LOCKS, 0).remove(KEY_LOCKED_UNTIL).commit()
        return true
    }

    @Synchronized
    fun check(pin: String): PinResult {
        lockedUntil().takeIf { it > 0 }?.let { return PinResult.Locked(it) }
        val salt = prefs.getString(KEY_SALT, null)?.unhex() ?: return PinResult.Wrong(0)
        val hash = prefs.getString(KEY_HASH, null)?.unhex() ?: return PinResult.Wrong(0)
        val ok = pin.length == 4 && pin.all(Char::isDigit) && constantTimeEquals(hashPin(pin, salt), hash)
        if (ok) {
            prefs.edit().putInt(KEY_FAILS, 0).putInt(KEY_LOCKS, 0).commit()
            return PinResult.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        if (fails >= PIN_TRIES) {
            val locks = prefs.getInt(KEY_LOCKS, 0)
            val until = clock() + lockMillis(locks)
            prefs.edit().putInt(KEY_FAILS, 0).putInt(KEY_LOCKS, locks + 1).putLong(KEY_LOCKED_UNTIL, until).commit()
            return PinResult.Locked(until)
        }
        prefs.edit().putInt(KEY_FAILS, fails).commit()
        return PinResult.Wrong(PIN_TRIES - fails)
    }

    /** On the unit only (physical access): forget the PIN; the next connection sets a new one. */
    fun reset() {
        prefs.edit().clear().commit()
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    private fun String.unhex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_FAILS = "fails"
        const val KEY_LOCKS = "locks"
        const val KEY_LOCKED_UNTIL = "locked_until"
    }
}
