package com.ugnbt.spotlabdesktop.data.local

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory

/**
 * The device's mTLS client certificate — only needed when this client points
 * at a server sitting behind nginx's `ssl_verify_client on` (the public
 * `spotlab.ugnbt.com` domain; see spotlab-go/docs/MTLS.md). A LAN address
 * (e.g. `http://192.168.1.107:...`) hits the Go service directly and needs no
 * certificate at all — so unlike the Android client, this store never blocks
 * startup; [com.ugnbt.spotlabdesktop.data.remote.SpotlabHttp] just falls back
 * to a plain client when nothing is enrolled.
 *
 * Issued offline by `cmd/mtls-ca -issue` on the server, pasted as the same
 * base64 blob (client cert PEM + PKCS8 private key PEM) the Android app
 * accepts. Stored unencrypted at `~/.config/spotlab/client-cert.p12` with
 * `0600` permissions — the same trust model ssh/gpg already use for private
 * keys on a desktop, no Android-Keystore equivalent needed here.
 */
class ClientCertStore(configDir: File) {

    private val file = File(configDir, "client-cert.p12")

    @Volatile
    private var cached: KeyStore? = loadFromDisk()

    fun hasEnrolledCert(): Boolean = cached != null

    /** Throws [IllegalArgumentException] with a message fit to show the user. */
    fun enroll(code: String) {
        val keyStore = parseBlob(code)
        writeToDisk(keyStore)
        cached = keyStore
    }

    fun clear() {
        cached = null
        file.delete()
    }

    fun loadKeyManagers(): Array<KeyManager>? {
        val keyStore = cached ?: return null
        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore, PASSWORD)
        return factory.keyManagers
    }

    private fun loadFromDisk(): KeyStore? {
        if (!file.exists()) return null
        return runCatching {
            file.inputStream().use { input ->
                KeyStore.getInstance("PKCS12").apply { load(input, PASSWORD) }
            }
        }.getOrNull()
    }

    private fun writeToDisk(keyStore: KeyStore) {
        file.parentFile?.mkdirs()
        file.outputStream().use { output -> keyStore.store(output, PASSWORD) }
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
    }

    private fun parseBlob(code: String): KeyStore {
        val decoded = runCatching { Base64.getDecoder().decode(code.trim()) }
            .getOrElse { throw IllegalArgumentException("Code illisible : ce n'est pas du base64 valide.") }
        val text = String(decoded, Charsets.UTF_8)

        val cert = runCatching { parseCertificate(text) }
            .getOrElse { throw IllegalArgumentException("Code invalide : certificat introuvable.") }
        val privateKey = runCatching { parsePrivateKey(text) }
            .getOrElse { throw IllegalArgumentException("Code invalide : clé privée introuvable.") }

        return KeyStore.getInstance("PKCS12").apply {
            load(null)
            setKeyEntry(ALIAS, privateKey, PASSWORD, arrayOf(cert))
        }
    }

    private fun parseCertificate(text: String): Certificate {
        val pem = extractPemBlock(text, "CERTIFICATE")
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem))
    }

    /** PKCS8 only (`cmd/mtls-ca` emits "PRIVATE KEY", never SEC1). */
    private fun parsePrivateKey(text: String): PrivateKey {
        val der = extractPemBlock(text, "PRIVATE KEY")
        val spec = PKCS8EncodedKeySpec(der)
        return KeyFactory.getInstance("EC").generatePrivate(spec)
    }

    private fun extractPemBlock(text: String, label: String): ByteArray {
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        val start = text.indexOf(begin).takeIf { it >= 0 }
            ?: throw IllegalArgumentException("bloc $label absent")
        val stop = text.indexOf(end, start).takeIf { it >= 0 }
            ?: throw IllegalArgumentException("bloc $label incomplet")
        val body = text.substring(start + begin.length, stop)
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        return Base64.getDecoder().decode(body)
    }

    private companion object {
        val PASSWORD = "spotlab-mtls".toCharArray()
        const val ALIAS = "client"
    }
}
