package com.ugnbt.spotlabdesktop.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * VLC opens the stream URL itself, entirely outside our OkHttp client — so it
 * never presents this app's mTLS client certificate or bearer token, and a
 * server behind nginx's `ssl_verify_client on` refuses the connection before
 * a single byte of audio arrives ("input can't be opened").
 *
 * This relays each track through a tiny loopback HTTP server instead: VLC
 * talks to plain `http://127.0.0.1` (no TLS, no certificate to present), and
 * every request is forwarded upstream through the same authenticated
 * [OkHttpClient] the rest of the app already uses — mTLS and the bearer
 * header both attach automatically, the same way they do for every JSON call.
 */
class StreamProxy(
    private val client: () -> OkHttpClient,
    private val upstreamUrl: (Long) -> String,
) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val port: Int get() = server.address.port

    init {
        server.createContext("/stream/", ::handle)
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            val trackId = exchange.requestURI.path.removePrefix("/stream/").toLongOrNull()
            if (trackId == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }

            val request = Request.Builder().url(upstreamUrl(trackId)).apply {
                exchange.requestHeaders.getFirst("Range")?.let { header("Range", it) }
            }.build()

            client().newCall(request).execute().use { response ->
                response.header("Content-Type")?.let { exchange.responseHeaders.add("Content-Type", it) }
                response.header("Accept-Ranges")?.let { exchange.responseHeaders.add("Accept-Ranges", it) }
                response.header("Content-Range")?.let { exchange.responseHeaders.add("Content-Range", it) }

                val body = response.body
                val length = body?.contentLength()?.takeIf { it >= 0 } ?: 0L
                exchange.sendResponseHeaders(response.code, length)
                body?.byteStream()?.use { input -> exchange.responseBody.use { output -> input.copyTo(output) } }
            }
        } catch (_: Exception) {
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            exchange.close()
        }
    }

    fun localUrl(trackId: Long): String = "http://127.0.0.1:$port/stream/$trackId"

    fun stop() = server.stop(0)
}
