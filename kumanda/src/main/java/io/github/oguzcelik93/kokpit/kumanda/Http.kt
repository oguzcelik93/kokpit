package io.github.oguzcelik93.kokpit.kumanda

import java.io.InputStream
import java.io.OutputStream

/** Same limits as the unit (PANEL_MAX_HEAD, PANEL_MAX_BODY): larger requests never leave the phone. */
const val MAX_HEAD = 8_192
const val MAX_BODY = 25L * 1024 * 1024

/** The request or response head, up to and including the blank line; null if the stream ends or it is too long. */
fun readHttpHead(input: InputStream): String? {
    val sb = StringBuilder()
    while (sb.length < MAX_HEAD) {
        val c = input.read()
        if (c < 0) return null
        sb.append(c.toChar())
        if (sb.endsWith("\r\n\r\n")) return sb.toString()
    }
    return null
}

/** A header's value (name is case-insensitive), or null. */
fun httpHeader(head: String, name: String): String? =
    head.split("\r\n").drop(1).firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) && ':' in it }
        ?.substringAfter(':')?.trim()

/** Content-Length: 0 when absent, null when malformed. */
fun contentLength(head: String): Long? {
    val v = httpHeader(head, "content-length") ?: return 0L
    return v.toLongOrNull()?.takeIf { it >= 0 }
}

/** Copies exactly [length] bytes; false if [input] ends first. */
fun copyExactly(input: InputStream, output: OutputStream, length: Long): Boolean {
    val buf = ByteArray(16 * 1024)
    var left = length
    while (left > 0) {
        val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
        if (n < 0) return false
        output.write(buf, 0, n)
        left -= n
    }
    return true
}

/** Writes to [target] until it fails, then quietly drops the rest (the reader stays in step). */
class ForgivingOutput(private val target: OutputStream) : OutputStream() {
    var failed = false
        private set

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (failed) return
        try { target.write(b, off, len) } catch (_: java.io.IOException) { failed = true }
    }
    override fun flush() {
        if (!failed) try { target.flush() } catch (_: java.io.IOException) { failed = true }
    }
}

/** A small plain-text response for the WebView. */
fun writeSimpleReply(out: OutputStream, code: Int, text: String) {
    val body = text.toByteArray()
    out.write("HTTP/1.1 $code Kumanda\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
    out.write(body)
    out.flush()
}

/** The header a remembered phone's key travels in (the unit's DEVICE_KEY_HEADER). */
const val DEVICE_KEY_HEADER = "X-Kokpit-Device-Key"

private fun isKey(text: String) = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }

/**
 * The request [head] as sent to the unit: any device-key header from the page is dropped,
 * and this phone's [key] for the unit (if it has one) is added.
 */
fun withDeviceKey(head: ByteArray, key: String?): ByteArray {
    val text = String(head, Charsets.ISO_8859_1)
    val lines = text.removeSuffix("\r\n\r\n").split("\r\n")
        .filterNot { it.substringBefore(':').trim().equals(DEVICE_KEY_HEADER, ignoreCase = true) }
    val extra = if (key != null && isKey(key)) listOf("$DEVICE_KEY_HEADER: $key") else emptyList()
    return ((lines + extra).joinToString("\r\n") + "\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
}

/** Takes a new device key out of the unit's response head: (head without it, key or null). */
fun takeDeviceKey(head: String): Pair<String, String?> {
    var key: String? = null
    val kept = head.removeSuffix("\r\n\r\n").split("\r\n").filter { line ->
        val isIt = line.substringBefore(':').trim().equals(DEVICE_KEY_HEADER, ignoreCase = true) && ':' in line
        if (isIt) key = line.substringAfter(':').trim().takeIf(::isKey)
        !isIt
    }
    return (kept.joinToString("\r\n") + "\r\n\r\n") to key
}
