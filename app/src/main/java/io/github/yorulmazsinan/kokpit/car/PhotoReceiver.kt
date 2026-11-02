package io.github.yorulmazsinan.kokpit.car

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/** Upload size limit: a phone photo, not a video. */
const val MAX_PHOTO_BYTES = 25 * 1024 * 1024

/** `POST /upload?t=abc HTTP/1.1` → ("POST", "/upload", "abc") */
fun parseRequestLine(line: String): Triple<String, String, String?>? {
    val parts = line.trim().split(' ')
    if (parts.size < 2) return null
    val target = parts[1]
    val path = target.substringBefore('?')
    val token = target.substringAfter('?', "").split('&').firstOrNull { it.startsWith("t=") }?.removePrefix("t=")
    return Triple(parts[0], path, token)
}

/** Constant-time compare, so the token can't be guessed byte by byte. */
fun tokenMatches(expected: String, given: String?): Boolean {
    // An empty secret (no panel open) matches nothing, not even an empty guess.
    if (expected.isEmpty() || given == null || given.length != expected.length) return false
    var diff = 0
    for (i in expected.indices) diff = diff or (expected[i].code xor given[i].code)
    return diff == 0
}

/** The unit's address on the hotspot (e.g. 172.20.10.3), or null when not on a network. */
fun localIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }?.hostAddress
}.getOrNull()

/**
 * "Send from phone": while the screen is open, a one-shot web page on the local network lets
 * the phone (which is the unit's hotspot anyway) pick a photo and send it. Safety:
 * - runs only while asked, stops after one photo or [TIMEOUT_MS];
 * - a random 128-bit token in the QR link; anything without it gets 403;
 * - two paths only (the page and the upload), size limit, and the upload must decode as an
 *   image or it is thrown away.
 */
class PhotoReceiver(private val onPhoto: (InputStream) -> Boolean) {
    private var server: ServerSocket? = null
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Starts listening and returns the link for the QR code, or null without a network. */
    fun start(onReceived: () -> Unit, onStopped: () -> Unit): String? {
        val ip = localIpv4() ?: return null
        val socket = ServerSocket(0)
        server = socket
        val started = System.currentTimeMillis()
        Thread {
            try {
                socket.soTimeout = 1_000
                while (!socket.isClosed && System.currentTimeMillis() - started < TIMEOUT_MS) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    if (handle(client)) {
                        onReceived()
                        break
                    }
                }
            } finally {
                runCatching { socket.close() }
                onStopped()
            }
        }.apply { isDaemon = true }.start()
        return "http://$ip:${socket.localPort}/?t=$token"
    }

    fun stop() {
        runCatching { server?.close() }
    }

    /** Returns true when a photo was received and accepted. */
    private fun handle(client: Socket): Boolean = client.use { s ->
        s.soTimeout = 30_000
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        val head = readHead(input) ?: return false
        val lines = head.split("\r\n")
        val (method, path, given) = parseRequestLine(lines.first()) ?: return false
        fun reply(code: String, type: String, body: String) {
            val bytes = body.toByteArray()
            out.write("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }
        if (!tokenMatches(token, given)) {
            reply("403 Forbidden", "text/plain", "forbidden")
            return false
        }
        when {
            method == "GET" && path == "/" -> { reply("200 OK", "text/html; charset=utf-8", page()); false }
            method == "POST" && path == "/upload" -> {
                val length = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull()
                if (length == null || length <= 0 || length > MAX_PHOTO_BYTES) {
                    reply("413 Payload Too Large", "text/plain", "too large")
                    return false
                }
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(bytes, read, length - read)
                    if (n < 0) break
                    read += n
                }
                val ok = read == length && onPhoto(ByteArrayInputStream(bytes))
                reply(if (ok) "200 OK" else "400 Bad Request", "text/plain", if (ok) "ok" else "not an image")
                ok
            }
            else -> { reply("404 Not Found", "text/plain", "not found"); false }
        }
    }

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8_192) {
            val c = input.read()
            if (c < 0) return null
            sb.append(c.toChar())
            if (sb.endsWith("\r\n\r\n")) return sb.toString().trimEnd()
        }
        return null
    }

    private fun page(): String = """
        <!doctype html><html lang="tr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Kokpit'e fotoğraf gönder</title>
        <style>body{font-family:system-ui,sans-serif;background:#0b0d0a;color:#d8eec4;margin:0;padding:32px 20px;text-align:center}
        h1{font-size:22px;color:#a9ec62}label{display:inline-block;margin-top:24px;padding:18px 28px;border-radius:14px;background:#8dd443;color:#0b0d0a;font-weight:700;font-size:18px}
        input{display:none}#s{margin-top:24px;font-size:17px}</style></head><body>
        <h1>Kokpit'e fotoğraf gönder</h1><p>Seçtiğiniz fotoğraf ana ekrandaki kartta görünecek.</p>
        <label>Fotoğraf seç<input id="f" type="file" accept="image/*"></label><p id="s"></p>
        <script>
        document.getElementById('f').onchange=function(e){var f=e.target.files[0];if(!f)return;var s=document.getElementById('s');
        s.textContent='Gönderiliyor…';fetch('/upload?t=$token',{method:'POST',body:f}).then(function(r){
        s.textContent=r.ok?'Gönderildi ✓ Ünitede görünüyor.':'Olmadı: bu bir fotoğraf değil ya da çok büyük.'}).catch(function(){s.textContent='Bağlantı koptu, tekrar deneyin.'})};
        </script></body></html>
    """.trimIndent()

    private companion object {
        const val TIMEOUT_MS = 5 * 60_000L
    }
}
