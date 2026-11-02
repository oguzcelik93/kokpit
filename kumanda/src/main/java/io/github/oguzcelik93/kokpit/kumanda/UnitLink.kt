package io.github.oguzcelik93.kokpit.kumanda

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** A problem the user should read (Turkish, plain words). */
class LinkException(message: String) : Exception(message)

/**
 * The one Bluetooth link to the unit's panel: a *secure* RFCOMM connection, so it only
 * reaches a unit this phone is already paired with, and the traffic is encrypted. It carries
 * the panel's requests one at a time, unchanged; the unit checks the token, PIN and session.
 */
@SuppressLint("MissingPermission")
class UnitLink(context: Context) {
    private val adapter: BluetoothAdapter? = runCatching {
        context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
    }.getOrNull()
    private val prefs = context.applicationContext.getSharedPreferences("unit", Context.MODE_PRIVATE)
    /** Keys from units that chose to remember this phone (only the unit can hand one out). */
    private val keys = context.applicationContext.getSharedPreferences("unit_keys", Context.MODE_PRIVATE)
    private val uuid = UUID.fromString(PANEL_BT_UUID)

    private var device: BluetoothDevice? = null
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    val hasBluetooth: Boolean get() = adapter != null

    /** The unit this phone last connected to (for "Bağlan" without a QR), or null. */
    val lastUnit: String? get() = prefs.getString(KEY_LAST, null)

    /** Whether the last unit remembers this phone (it gave us a key). */
    val remembered: Boolean get() = lastUnit?.let { keys.contains(it) } == true

    /**
     * "Bağlan": asks the unit to open the panel for this phone. The unit shows "İzin ver" and
     * this waits (up to a minute) for the tap there; returns the panel's one-time token.
     */
    fun knock(): String {
        val (code, body) = request("POST", "/api/knock", null, null)
        if (code != 200) throw LinkException(body.ifBlank { "Ünite izin vermedi." })
        return body.trim().takeIf { t -> t.length == 32 && t.all { it in '0'..'9' || it in 'a'..'f' } }
            ?: throw LinkException("Ünite beklenmedik bir yanıt verdi.")
    }

    /** One request with a small body; returns (status code, text). For shares and "Bağlan". */
    fun request(method: String, path: String, body: ByteArray?, type: String?): Pair<Int, String> {
        val length = body?.size ?: 0
        val head = buildString {
            append("$method $path HTTP/1.1\r\nHost: kokpit\r\n")
            if (type != null) append("Content-Type: $type\r\n")
            append("Content-Length: $length\r\n\r\n")
        }
        val out = java.io.ByteArrayOutputStream()
        exchange(head.toByteArray(Charsets.ISO_8859_1), (body ?: ByteArray(0)).inputStream(), length.toLong(), out, retryable = body == null && method == "GET")
        val text = out.toString("UTF-8")
        val code = text.substringAfter(' ').substringBefore(' ').toIntOrNull() ?: 0
        return code to text.substringAfter("\r\n\r\n", "")
    }
    val isEnabled: Boolean get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    /** Finds the unit among the paired devices and connects; returns its name. Slow: call off the main thread. */
    @Synchronized
    fun connect(unitName: String?): String {
        close()
        val a = adapter ?: throw LinkException("Bu telefonda Bluetooth yok.")
        if (!isEnabled) throw LinkException("Telefonun Bluetooth'u kapalı.")
        val bonded = runCatching { a.bondedDevices.orEmpty().toList() }.getOrElse {
            throw LinkException("Bluetooth izni verilmedi.")
        }
        if (bonded.isEmpty()) throw LinkException("Telefon hiçbir cihazla eşleşmemiş. Önce telefonu üniteyle Bluetooth'tan eşleştirin.")
        runCatching { a.cancelDiscovery() } // a running scan slows connecting down
        val byAddress = bonded.associateBy { it.address }
        val order = tryOrder(bonded.map { Paired(it.address, runCatching { it.name }.getOrNull()) }, unitName, prefs.getString(KEY_LAST, null))
        for (p in order) {
            val d = byAddress[p.address] ?: continue
            if (open(d)) {
                device = d
                prefs.edit().putString(KEY_LAST, d.address).apply()
                return p.name ?: d.address
            }
        }
        throw LinkException(
            "Ünite bulunamadı. Ünitede \"Telefondan yönet\" ekranı açık mı? Telefon üniteyle eşleşmiş mi" +
                (unitName?.let { " (ünitenin adı: $it)" } ?: "") + "?",
        )
    }

    private fun open(d: BluetoothDevice): Boolean {
        val s = runCatching { d.createRfcommSocketToServiceRecord(uuid) }.getOrNull() ?: return false
        return try {
            s.connect()
            socket = s
            input = BufferedInputStream(s.inputStream)
            output = BufferedOutputStream(s.outputStream, 16 * 1024)
            true
        } catch (_: IOException) {
            runCatching { s.close() }
            false
        } catch (_: SecurityException) {
            runCatching { s.close() }
            false
        }
    }

    /**
     * Sends one request ([head] + [bodyLength] bytes from [body]) and copies the unit's whole
     * response to [client]. A request without a body ([retryable]) is sent again once on a
     * fresh link if the old one had dropped.
     */
    @Synchronized
    fun exchange(head: ByteArray, body: InputStream, bodyLength: Long, client: OutputStream, retryable: Boolean) {
        var attempt = 0
        while (true) {
            try {
                if (socket == null) {
                    val d = device ?: throw LinkException("Üniteye bağlı değil.")
                    if (!open(d)) throw LinkException("Ünite yanıt vermiyor. Panel kapanmış olabilir: ünitede yeniden açıp QR'ı tekrar okutun.")
                }
                val out = output!!
                val inp = input!!
                out.write(withDeviceKey(head, device?.address?.let { keys.getString(it, null) }))
                if (bodyLength > 0 && !copyExactly(body, out, bodyLength)) throw IOException("istek yarım kaldı")
                out.flush()
                val rawHead = readHttpHead(inp) ?: throw IOException("yanıt yok")
                val length = contentLength(rawHead) ?: throw IOException("bozuk yanıt")
                // A new key ("Bu telefonu hatırla") is kept here and never reaches the page.
                val (responseHead, newKey) = takeDeviceKey(rawHead)
                if (newKey != null) device?.address?.let { keys.edit().putString(it, newKey).apply() }
                // The WebView may have gone away; the unit's response is still read to the end.
                val sink = ForgivingOutput(client)
                sink.write(responseHead.toByteArray(Charsets.ISO_8859_1))
                if (!copyExactly(inp, sink, length)) throw IOException("yanıt yarım kaldı")
                sink.flush()
                return
            } catch (e: IOException) {
                dropSocket()
                if (!retryable || attempt++ > 0) throw LinkException("Ünite ile Bluetooth bağlantısı koptu. Tekrar deneyin; olmazsa ünitede paneli yeniden açın.")
            }
        }
    }

    private fun dropSocket() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    @Synchronized
    fun close() {
        dropSocket()
        device = null
    }

    /** Forget the unit's key (it was forgotten on the unit, or the PIN was reset there). */
    fun forgetKey() {
        lastUnit?.let { keys.edit().remove(it).apply() }
    }

    private companion object {
        const val KEY_LAST = "last_unit"
    }
}
