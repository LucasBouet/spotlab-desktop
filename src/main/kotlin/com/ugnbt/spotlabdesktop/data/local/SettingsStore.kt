package com.ugnbt.spotlabdesktop.data.local

import com.ugnbt.spotlabdesktop.player.AudioCacheSize
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SpotlabSettings(
    /** Root of the Spotlab server, without a trailing slash. */
    val baseUrl: String? = null,
    val token: String? = null,
    val expiresAt: String? = null,
    /** Identifies this install to the sync engine; generated once, kept forever. */
    val deviceId: String = "",
    val deviceName: String = "",
    val audioCacheMaxBytes: Long = AudioCacheSize.Default.bytes,
) {
    val hasServer: Boolean get() = !baseUrl.isNullOrBlank()
    val hasSession: Boolean get() = !token.isNullOrBlank()
}

/**
 * Persisted client state, mirrored in memory for synchronous reads (the OkHttp
 * interceptor injecting the bearer token needs one). Backed by a plain JSON
 * file rather than DataStore — there is no Android Context here, and a single
 * desktop process needs no cross-process coordination.
 */
class SettingsStore(configDir: File) {

    private val file = File(configDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SpotlabSettings> = _state.asStateFlow()
    val current: SpotlabSettings get() = _state.value

    /** Mints the device id on first launch; every later start reuses it. */
    suspend fun prime(defaultDeviceName: String) = update {
        var next = it
        if (next.deviceId.isBlank()) next = next.copy(deviceId = UUID.randomUUID().toString())
        if (next.deviceName.isBlank()) next = next.copy(deviceName = defaultDeviceName)
        next
    }

    suspend fun setBaseUrl(url: String) = update { it.copy(baseUrl = url) }

    suspend fun setSession(token: String, expiresAt: String?) = update {
        it.copy(token = token, expiresAt = expiresAt)
    }

    suspend fun clearSession() = update { it.copy(token = null, expiresAt = null) }

    suspend fun setDeviceName(name: String) = update { it.copy(deviceName = name) }

    /** Takes effect on the next launch — [com.ugnbt.spotlabdesktop.player.StreamProxy]
     *  reads it once at construction. */
    suspend fun setAudioCacheMaxBytes(bytes: Long) = update { it.copy(audioCacheMaxBytes = bytes) }

    private suspend fun update(transform: (SpotlabSettings) -> SpotlabSettings) =
        withContext(Dispatchers.IO) {
            val next = transform(_state.value)
            write(next)
            _state.value = next
        }

    private fun read(): SpotlabSettings {
        if (!file.exists()) return SpotlabSettings()
        return runCatching { json.decodeFromString<SpotlabSettings>(file.readText()) }
            .getOrDefault(SpotlabSettings())
    }

    private fun write(settings: SpotlabSettings) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(SpotlabSettings.serializer(), settings))
    }
}
