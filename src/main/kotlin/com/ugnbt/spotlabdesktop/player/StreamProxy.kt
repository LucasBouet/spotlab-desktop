package com.ugnbt.spotlabdesktop.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Relays each track through a loopback HTTP server (see the class-level
 * rationale kept below) — and, on top of that, a disk cache: a track already
 * downloaded once is never fetched over the network again, which is the
 * actual point of this feature (the same "burns mobile data" complaint the
 * Android client's audio cache addresses, for whoever uses this desktop
 * client on a metered link).
 *
 * VLC opens the stream URL itself, entirely outside our OkHttp client — so it
 * never presents this app's mTLS client certificate or bearer token, and a
 * server behind nginx's `ssl_verify_client on` refuses the connection before
 * a single byte of audio arrives ("input can't be opened"). VLC talks to
 * plain `http://127.0.0.1` (no TLS, no certificate to present) instead, and
 * every request is forwarded upstream through the same authenticated
 * [OkHttpClient] the rest of the app already uses.
 */
class StreamProxy(
    private val client: () -> OkHttpClient,
    private val upstreamUrl: (Long) -> String,
    private val cacheDir: File,
    private val maxCacheBytes: () -> Long,
) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    val port: Int get() = server.address.port

    init {
        cacheDir.mkdirs()
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
            val cacheFile = File(cacheDir, "$trackId.cache")
            val range = exchange.requestHeaders.getFirst("Range")

            if (cacheFile.exists()) {
                serveFromCache(exchange, cacheFile, range)
            } else {
                proxyAndCache(exchange, trackId, range, cacheFile)
            }
        } catch (_: Exception) {
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            exchange.close()
        }
    }

    private fun serveFromCache(exchange: HttpExchange, file: File, range: String?) {
        val length = file.length()
        var start = 0L
        var end = length - 1
        val match = range?.let { Regex("""bytes=(\d*)-(\d*)""").find(it) }
        if (match != null) {
            val (startGroup, endGroup) = match.destructured
            startGroup.toLongOrNull()?.let { start = it }
            endGroup.toLongOrNull()?.let { end = it }
        }
        end = end.coerceAtMost(length - 1)
        if (start > end) start = end
        val contentLength = (end - start + 1).coerceAtLeast(0)

        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        if (match != null) {
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$length")
            exchange.sendResponseHeaders(206, contentLength)
        } else {
            exchange.sendResponseHeaders(200, contentLength)
        }

        RandomAccessFile(file, "r").use { randomAccessFile ->
            randomAccessFile.seek(start)
            val buffer = ByteArray(64 * 1024)
            var remaining = contentLength
            exchange.responseBody.use { output ->
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = randomAccessFile.read(buffer, 0, toRead)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    /**
     * A response code of exactly 200 (not 206) means the upstream handed us
     * the whole file starting at byte 0, whatever Range header VLC's initial
     * probe sent along with the request — that's what's worth caching. A
     * genuine 206 (server already had the track cached and honored a
     * mid-file Range) is left alone: caching a fragment under the track's
     * whole-file cache path would corrupt the next cache hit.
     */
    private fun proxyAndCache(exchange: HttpExchange, trackId: Long, range: String?, cacheFile: File) {
        val request = Request.Builder().url(upstreamUrl(trackId)).apply {
            range?.let { header("Range", it) }
        }.build()

        client().newCall(request).execute().use { response ->
            response.header("Content-Type")?.let { exchange.responseHeaders.add("Content-Type", it) }
            response.header("Accept-Ranges")?.let { exchange.responseHeaders.add("Accept-Ranges", it) }
            response.header("Content-Range")?.let { exchange.responseHeaders.add("Content-Range", it) }

            val body = response.body
            val length = body?.contentLength()?.takeIf { it >= 0 } ?: 0L
            exchange.sendResponseHeaders(response.code, length)

            val tempFile = if (response.code == 200) File(cacheDir, "$trackId.partial") else null
            val buffer = ByteArray(64 * 1024)

            body?.byteStream()?.use { input ->
                exchange.responseBody.use { output ->
                    tempFile?.outputStream()?.use { cacheOut ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            cacheOut.write(buffer, 0, read)
                        }
                    } ?: input.copyTo(output)
                }
            }

            if (tempFile != null && tempFile.exists()) {
                tempFile.renameTo(cacheFile)
                enforceCacheLimit()
            }
        }
    }

    private fun cachedFiles(): Array<File> = cacheDir.listFiles { f -> f.extension == "cache" } ?: emptyArray()

    private fun enforceCacheLimit() {
        val files = cachedFiles()
        var total = files.sumOf { it.length() }
        val cap = maxCacheBytes()
        if (total <= cap) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= cap) break
            total -= file.length()
            file.delete()
        }
    }

    fun currentSizeBytes(): Long = cachedFiles().sumOf { it.length() }

    fun clear() {
        cachedFiles().forEach { it.delete() }
    }

    fun localUrl(trackId: Long): String = "http://127.0.0.1:$port/stream/$trackId"

    fun stop() = server.stop(0)
}
