package io.github.oguzcelik93.kokpit.kumanda

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Lets this app's WebView talk to the unit as if over Wi-Fi: listens on 127.0.0.1 only (a
 * random port, never the network) and passes each request unchanged over [link]. It adds
 * nothing and decides nothing: the unit still demands the QR's token, then the PIN.
 */
class LocalBridge(private val link: UnitLink) {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val port: Int = server.localPort

    fun start() {
        Thread {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                Thread { runCatching { handle(client) } }.apply { isDaemon = true; name = "kumanda-istek" }.start()
            }
        }.apply { isDaemon = true; name = "kumanda-kopru" }.start()
    }

    private fun handle(client: Socket): Unit = client.use { c ->
        c.soTimeout = 60_000
        val input = BufferedInputStream(c.getInputStream())
        val out = BufferedOutputStream(c.getOutputStream(), 16 * 1024)
        val head = readHttpHead(input) ?: return
        // Only our own page: a web page in another app pointing at this port sends a different Host.
        if (httpHeader(head, "host") != "127.0.0.1:$port") return writeSimpleReply(out, 403, "forbidden")
        if (httpHeader(head, "transfer-encoding") != null) return writeSimpleReply(out, 411, "Uzunluk gerekli")
        val length = contentLength(head) ?: return writeSimpleReply(out, 400, "Bozuk istek")
        if (length > MAX_BODY) return writeSimpleReply(out, 413, "Dosya çok büyük (en çok 25 MB)")
        val method = head.substringBefore(' ')
        try {
            link.exchange(head.toByteArray(Charsets.ISO_8859_1), input, length, out, retryable = length == 0L && method == "GET")
            out.flush()
        } catch (e: LinkException) {
            runCatching { writeSimpleReply(out, 502, e.message.orEmpty()) }
        }
    }

    fun stop() {
        runCatching { server.close() }
    }
}
