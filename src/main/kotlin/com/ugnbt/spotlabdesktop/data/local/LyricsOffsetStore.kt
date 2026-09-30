package com.ugnbt.spotlabdesktop.data.local

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-track lyrics timing corrections, in seconds. A plain JSON map file
 * (`~/.config/spotlab/lyrics_offsets.json`) rather than a DataStore entry —
 * same reasoning as the Android client's version: this grows with every
 * track ever corrected and has nothing to do with [SettingsStore]'s
 * synchronously-read snapshot.
 */
class LyricsOffsetStore(configDir: File) {

    @Serializable
    private data class Offsets(val byTrackId: Map<String, Double> = emptyMap())

    private val file = File(configDir, "lyrics_offsets.json")
    private val json = Json { ignoreUnknownKeys = true }

    private var cache: Offsets = read()

    suspend fun offsetFor(trackId: Long): Double = withContext(Dispatchers.IO) {
        cache.byTrackId[trackId.toString()] ?: 0.0
    }

    /** Zero is stored as *absent*, same as the Android client. */
    suspend fun setOffset(trackId: Long, seconds: Double) = withContext(Dispatchers.IO) {
        val key = trackId.toString()
        cache = if (seconds == 0.0) {
            cache.copy(byTrackId = cache.byTrackId - key)
        } else {
            cache.copy(byTrackId = cache.byTrackId + (key to seconds))
        }
        write(cache)
    }

    private fun read(): Offsets {
        if (!file.exists()) return Offsets()
        return runCatching { json.decodeFromString<Offsets>(file.readText()) }.getOrDefault(Offsets())
    }

    private fun write(offsets: Offsets) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(Offsets.serializer(), offsets))
    }
}
