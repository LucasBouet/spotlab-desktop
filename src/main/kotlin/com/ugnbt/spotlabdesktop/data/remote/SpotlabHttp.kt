package com.ugnbt.spotlabdesktop.data.remote

import com.ugnbt.spotlabdesktop.data.local.ClientCertStore
import com.ugnbt.spotlabdesktop.data.local.SpotlabSettings
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response

/** A failure the server described itself; [message] is French and displayable. */
class ApiException(val status: Int, override val message: String) : Exception(message)

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is UnknownHostException -> "Serveur introuvable. Vérifiez l'adresse."
    is SocketTimeoutException -> "Le serveur ne répond pas."
    is IOException -> "Connexion impossible. Le serveur est-il démarré ?"
    is SerializationException -> "Réponse illisible. Le serveur est-il d'une autre version ?"
    else -> message?.takeIf { it.isNotBlank() }
        ?: "Erreur inattendue (${this::class.java.simpleName})."
}

/**
 * Accepts what a user actually types ("192.168.1.20:3000") and returns a usable
 * root, or null. Defaults to http — a LAN box rarely has a certificate.
 */
fun normalizeServerUrl(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "http://$trimmed"
    }
    val url = withScheme.toHttpUrlOrNull() ?: return null
    return url.toString().trimEnd('/')
}

/**
 * The single HTTP entry point: one client, one place where the bearer token is
 * attached, one place that knows where the server lives.
 *
 * [loadKeyManagers] presents this device's mTLS client certificate when one is
 * enrolled — only load-bearing against a server behind nginx's
 * `ssl_verify_client on` (see [ClientCertStore]'s doc comment); a plain LAN
 * address never needs it and null key managers just fall back to a plain
 * client instead of crashing.
 */
class SpotlabHttp(
    private val settings: () -> SpotlabSettings,
    private val loadKeyManagers: () -> Array<KeyManager>?,
) {

    val client: OkHttpClient by lazy {
        val (sslSocketFactory, trustManager) = buildSslContext()
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (sslSocketFactory != null && trustManager != null) {
                    sslSocketFactory(sslSocketFactory, trustManager)
                }
            }
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (request.header("Accept") == null) {
                    builder.header("Accept", "application/json")
                }
                settings().token?.takeIf { it.isNotBlank() }?.let { token ->
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    /** For the SSE stream and prefetch: neither may be cut off by a read timeout. */
    val streamingClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    private fun buildSslContext(): Pair<javax.net.ssl.SSLSocketFactory?, X509TrustManager?> {
        val keyManagers = loadKeyManagers() ?: return null to null
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManager = trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull() ?: return null to null

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers, arrayOf(trustManager), null)
        return sslContext.socketFactory to trustManager
    }

    fun url(path: String, params: Map<String, String?> = emptyMap()): HttpUrl {
        val base = settings().baseUrl?.takeIf { it.isNotBlank() }
            ?: throw ApiException(0, "Aucun serveur Spotlab configuré.")
        val root = base.toHttpUrlOrNull()
            ?: throw ApiException(0, "Adresse du serveur invalide.")
        return root.newBuilder()
            .addPathSegments(path.trimStart('/'))
            .apply {
                for ((key, value) in params) {
                    if (!value.isNullOrBlank()) addQueryParameter(key, value)
                }
            }
            .build()
    }

    /** Fetched by the player, not by this client — it only needs the URL. */
    fun streamUrl(trackId: Long): String = url("api/stream/$trackId").toString()

    fun bearerHeaders(): Map<String, String> =
        settings().token?.takeIf { it.isNotBlank() }
            ?.let { mapOf("Authorization" to "Bearer $it") }
            ?: emptyMap()
}

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
}
