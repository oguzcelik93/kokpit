package io.github.yorulmazsinan.kokpit.system

import io.github.yorulmazsinan.kokpit.car.localIpv4
import io.github.yorulmazsinan.kokpit.car.parseRequestLine
import io.github.yorulmazsinan.kokpit.car.tokenMatches
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/** Escapes text for an HTML page. */
fun htmlEscape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * Shows the diagnostics report (the same text as the Diagnostics screen) on the phone, so it
 * can be copied: a read-only page on the local network, only while the driver asks for it,
 * behind a random token, closed after ten minutes or when the screen is left.
 */
class ReportServer(private val report: String) {
    private var server: ServerSocket? = null
    val token: String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    fun start(onStopped: () -> Unit): String? {
        val ip = localIpv4() ?: return null
        val socket = ServerSocket(0)
        server = socket
        val started = System.currentTimeMillis()
        Thread {
            try {
                socket.soTimeout = 1_000
                while (!socket.isClosed && System.currentTimeMillis() - started < TIMEOUT_MS) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    runCatching { handle(client) }
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

    private fun handle(client: Socket) = client.use { s ->
        s.soTimeout = 15_000
        val input = BufferedInputStream(s.getInputStream())
        val head = StringBuilder()
        while (head.length < 8_192 && !head.endsWith("\r\n\r\n")) {
            val c = input.read()
            if (c < 0) break
            head.append(c.toChar())
        }
        val (method, path, given) = parseRequestLine(head.lineSequence().firstOrNull().orEmpty()) ?: return
        val out = s.getOutputStream()
        fun reply(code: String, type: String, body: String, extra: String = "") {
            val bytes = body.toByteArray()
            out.write("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n$extra\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }
        when {
            !tokenMatches(token, given) -> reply("403 Forbidden", "text/plain", "forbidden")
            method != "GET" -> reply("405 Method Not Allowed", "text/plain", "read only")
            path == "/" -> reply("200 OK", "text/html; charset=utf-8", page())
            path == "/kokpit-rapor.txt" -> reply("200 OK", "text/plain; charset=utf-8", report, "Content-Disposition: attachment; filename=\"kokpit-rapor.txt\"\r\n")
            else -> reply("404 Not Found", "text/plain", "not found")
        }
    }

    private fun page(): String = """
        <!doctype html><html lang="tr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1"><title>Kokpit raporu</title>
        <style>body{font-family:system-ui,sans-serif;background:#0b0d0a;color:#d8eec4;margin:0;padding:16px}
        h1{font-size:20px;color:#a9ec62}.b{display:inline-block;margin:6px 8px 12px 0;padding:14px 20px;border-radius:12px;background:#8dd443;color:#0b0d0a;font-weight:700;text-decoration:none;border:0;font-size:16px}
        textarea{width:100%;height:65vh;background:#111;color:#d8eec4;border:1px solid #2c3a1e;border-radius:8px;font:12px monospace}</style></head><body>
        <h1>Kokpit teşhis raporu</h1>
        <button class="b" id="c">Kopyala</button><a class="b" href="/kokpit-rapor.txt?t=$token">İndir</a><span id="s"></span>
        <textarea id="r" readonly>${htmlEscape(report)}</textarea>
        <script>document.getElementById('c').onclick=function(){var r=document.getElementById('r');r.focus();r.select();
        r.setSelectionRange(0,r.value.length);var ok=false;try{ok=document.execCommand('copy')}catch(e){}
        document.getElementById('s').textContent=ok?' Kopyalandı ✓':' Metni uzun basıp seçin';};</script>
        </body></html>
    """.trimIndent()

    private companion object {
        const val TIMEOUT_MS = 10 * 60_000L
    }
}
