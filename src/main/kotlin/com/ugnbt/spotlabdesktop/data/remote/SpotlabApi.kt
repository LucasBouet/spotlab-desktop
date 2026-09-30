package com.ugnbt.spotlabdesktop.data.remote

import com.ugnbt.spotlabdesktop.data.remote.dto.AlbumSearchResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.ApiErrorDto
import com.ugnbt.spotlabdesktop.data.remote.dto.ArtistPageDto
import com.ugnbt.spotlabdesktop.data.remote.dto.ArtistSearchResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.AuthResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.CreatePlaylistResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerAlbumDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerArtistDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeviceDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeviceResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DevicesResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.LikedIdsResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.LikedTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.LikedTracksResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.LyricsDto
import com.ugnbt.spotlabdesktop.data.remote.dto.MeResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlayEventDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistDetailDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistImportEvent
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistImportEventDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistMembershipDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistMembershipResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistRefDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistSummaryDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistToggleResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistTrackInputDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistsResponseDto
import com.ugnbt.spotlabdesktop.data.remote.dto.RecommendationsDto
import com.ugnbt.spotlabdesktop.data.remote.dto.RegisterDeviceDto
import com.ugnbt.spotlabdesktop.data.remote.dto.ServerConfigDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SmartPlaylistsDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SyncAction
import com.ugnbt.spotlabdesktop.data.remote.dto.SyncCommandDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SyncCommandResultDto
import com.ugnbt.spotlabdesktop.data.remote.dto.TrackMetadataDto
import com.ugnbt.spotlabdesktop.data.remote.dto.TrackSearchResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class LoginBody(val email: String, val password: String)

@Serializable
private data class RegisterBody(val name: String, val email: String, val password: String)

@Serializable
private data class ActivateBody(val code: String, val email: String, val password: String, val name: String)

@Serializable
private data class NameBody(val name: String)

@Serializable
private data class ImportPlaylistBody(val link: String, val destination: String, val name: String? = null)

/**
 * The Spotlab endpoints the desktop v1 client uses — a deliberate subset of
 * the full API (no admin/social/blend/jam/lyrics/stats routes yet, see the
 * plan's "hors périmètre v1"). Hand-rolled on OkHttp rather than Retrofit for
 * the same reason as the Android client: a runtime-configurable base URL.
 */
class SpotlabApi(private val http: SpotlabHttp, private val json: Json) {

    /** Set by AuthRepository: a 401 on an authenticated call means the session died. */
    var onUnauthorized: (() -> Unit)? = null

    // ---------------------------------------------------------------- auth

    suspend fun config(): ServerConfigDto = getJson("api/config")

    suspend fun probeConfig(baseUrl: String): ServerConfigDto {
        val url = "${baseUrl.trimEnd('/')}/api/config".toHttpUrlOrNull()
            ?: throw ApiException(0, "Adresse du serveur invalide.")
        return decode(request { Request.Builder().url(url).get().build() })
    }

    suspend fun login(email: String, password: String): AuthResponseDto =
        postJson("api/auth/login", LoginBody(email, password))

    suspend fun register(name: String, email: String, password: String): AuthResponseDto =
        postJson("api/auth/register", RegisterBody(name, email, password))

    suspend fun activate(code: String, name: String, email: String, password: String): AuthResponseDto =
        postJson("api/activate", ActivateBody(code, email, password, name))

    suspend fun me(): MeResponseDto = getJson("api/auth/me")

    suspend fun refresh(): AuthResponseDto = decode(postEmpty("api/auth/refresh"))

    suspend fun logout() {
        postEmpty("api/auth/logout")
    }

    // ------------------------------------------------------------- catalog

    suspend fun searchTracks(query: String, limit: Int = 24, index: Int = 0): List<DeezerTrackDto> =
        getJson<TrackSearchResponseDto>("api/search", searchParams(query, "track", limit, index)).data

    suspend fun searchAlbums(query: String, limit: Int = 24, index: Int = 0): List<DeezerAlbumDto> =
        getJson<AlbumSearchResponseDto>("api/search", searchParams(query, "album", limit, index)).data

    suspend fun searchArtists(query: String, limit: Int = 24, index: Int = 0): List<DeezerArtistDto> =
        getJson<ArtistSearchResponseDto>("api/search", searchParams(query, "artist", limit, index)).data

    private fun searchParams(query: String, type: String, limit: Int, index: Int) = mapOf(
        "q" to query,
        "type" to type,
        "limit" to limit.toString(),
        "index" to index.takeIf { it > 0 }?.toString(),
    )

    suspend fun track(trackId: Long): DeezerTrackDto = getJson("api/track/$trackId")

    suspend fun album(albumId: Long): DeezerAlbumDto = getJson("api/album/$albumId")

    suspend fun artist(artistId: Long): ArtistPageDto = getJson("api/artist/$artistId")

    /**
     * Lyrics for a track identified by metadata, not by id — the server forwards
     * to lrclib. `track`/`artist` are required (400 without them); the server
     * allows lrclib up to 15s, comfortably inside this client's 30s read timeout.
     */
    suspend fun lyrics(track: String, artist: String, album: String, durationSeconds: Int): LyricsDto =
        getJson(
            "api/lyrics",
            mapOf(
                "track" to track,
                "artist" to artist,
                "album" to album,
                "duration" to durationSeconds.takeIf { it > 0 }?.toString(),
            ),
        )

    // ---------------------------------------------------------------- home

    /** [window] must be "day", "week" or "all". */
    suspend fun recommendations(window: String, refresh: Boolean = false): RecommendationsDto =
        getJson("api/recommendations", mapOf("window" to window, "refresh" to "1".takeIf { refresh }))

    suspend fun smartPlaylists(refresh: Boolean = false): SmartPlaylistsDto =
        getJson("api/smart-playlists", mapOf("refresh" to "1".takeIf { refresh }))

    // ------------------------------------------------------------- library

    suspend fun likedTracks(sort: String? = null): List<LikedTrackDto> =
        getJson<LikedTracksResponseDto>("api/library/tracks", mapOf("sort" to sort)).tracks

    suspend fun likedTrackIds(): Set<Long> =
        getJson<LikedIdsResponseDto>("api/library/likes").ids.toSet()

    suspend fun like(trackId: Long, metadata: TrackMetadataDto) {
        putJson("api/library/likes/$trackId", metadata)
    }

    suspend fun unlike(trackId: Long) {
        delete("api/library/likes/$trackId")
    }

    // ----------------------------------------------------------- playlists

    suspend fun playlists(): List<PlaylistSummaryDto> =
        getJson<PlaylistsResponseDto>("api/playlists").playlists

    suspend fun playlist(playlistId: String): PlaylistDetailDto =
        getJson("api/playlists/$playlistId")

    suspend fun createPlaylist(name: String): PlaylistRefDto =
        postJson<NameBody, CreatePlaylistResponseDto>("api/playlists", NameBody(name)).playlist

    suspend fun deletePlaylist(playlistId: String) {
        delete("api/playlists/$playlistId")
    }

    suspend fun playlistMembership(trackId: Long): List<PlaylistMembershipDto> =
        getJson<PlaylistMembershipResponseDto>(
            "api/playlists/membership",
            mapOf("trackId" to trackId.toString()),
        ).playlists

    suspend fun togglePlaylistTrack(
        playlistId: String,
        input: PlaylistTrackInputDto,
    ): Boolean = postJson<PlaylistTrackInputDto, PlaylistToggleResponseDto>(
        "api/playlists/$playlistId/tracks",
        input.copy(toggle = true),
    ).added

    suspend fun removeFromPlaylist(playlistId: String, rowKey: String) {
        delete("api/playlists/$playlistId/tracks/$rowKey")
    }

    /**
     * `POST /api/playlists/import` answers NDJSON, not one JSON document — a
     * full playlist import is dozens of throttled Deezer round-trips
     * server-side, so the response streams `progress` events as pages come
     * in, then exactly one `done` or `error`. [destination] is "playlist" or
     * "liked".
     */
    fun importDeezerPlaylist(link: String, destination: String, name: String?): Flow<PlaylistImportEvent> = flow {
        val body = jsonBody(ImportPlaylistBody(link, destination, name?.takeIf { it.isNotBlank() }))
        val request = Request.Builder().url(http.url("api/playlists/import")).post(body).build()

        http.streamingClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, errorMessage(response.body?.string().orEmpty(), response.code))
            }
            val source = response.body?.source() ?: return@use
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val event = runCatching { json.decodeFromString<PlaylistImportEventDto>(line) }.getOrNull()
                    ?: continue
                when (event.type) {
                    "progress" -> emit(PlaylistImportEvent.Progress(event.fetched, event.total))
                    "done" -> emit(
                        PlaylistImportEvent.Done(event.destination.orEmpty(), event.playlistId, event.trackCount),
                    )
                    "error" -> emit(PlaylistImportEvent.Failed(event.message ?: "Import impossible."))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------- devices

    suspend fun registerDevice(deviceId: String, name: String, platform: String): DeviceDto? =
        postJson<RegisterDeviceDto, DeviceResponseDto>(
            "api/devices/register",
            RegisterDeviceDto(deviceId, name, platform),
        ).device

    suspend fun devices(): List<DeviceDto> =
        getJson<DevicesResponseDto>("api/devices").devices

    suspend fun renameDevice(deviceId: String, name: String) {
        patchJson("api/devices/$deviceId", NameBody(name))
    }

    suspend fun forgetDevice(deviceId: String) {
        delete("api/devices/$deviceId")
    }

    // ------------------------------------------------------------ playback

    suspend fun sendCommand(deviceId: String, action: SyncAction): SyncCommandResultDto =
        postJson("api/sync/command", SyncCommandDto(deviceId, action))

    suspend fun logPlay(event: PlayEventDto) {
        postBody("api/plays", event)
    }

    /** Warms the server-side cache for the next queue item; also makes it seekable. */
    suspend fun prefetch(trackId: Long) {
        postEmpty("api/prefetch/$trackId", http.streamingClient)
    }

    fun streamUrl(trackId: Long): String = http.streamUrl(trackId)

    // ------------------------------------------------------------ plumbing

    private suspend fun request(
        client: OkHttpClient = http.client,
        build: () -> Request,
    ): String = withContext(Dispatchers.IO) {
        val httpRequest = build()
        client.newCall(httpRequest).awaitResponse().use { response ->
            val text = response.body.string()
            if (response.isSuccessful) return@withContext text

            if (response.code == 401 && httpRequest.header("Authorization") != null) {
                onUnauthorized?.invoke()
            }
            throw ApiException(response.code, errorMessage(text, response.code))
        }
    }

    private fun errorMessage(body: String, status: Int): String =
        runCatching { json.decodeFromString<ApiErrorDto>(body).error }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Le serveur a répondu $status."

    private suspend inline fun <reified T> decode(body: String): T =
        withContext(Dispatchers.IO) { json.decodeFromString<T>(body) }

    private inline fun <reified B> jsonBody(body: B): RequestBody =
        json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE)

    private suspend inline fun <reified T> getJson(
        path: String,
        params: Map<String, String?> = emptyMap(),
    ): T = decode(request { Request.Builder().url(http.url(path, params)).get().build() })

    private suspend inline fun <reified B> postBody(path: String, body: B): String =
        request { Request.Builder().url(http.url(path)).post(jsonBody(body)).build() }

    private suspend inline fun <reified B, reified T> postJson(path: String, body: B): T =
        decode(postBody(path, body))

    private suspend inline fun <reified B> putJson(path: String, body: B): String =
        request { Request.Builder().url(http.url(path)).put(jsonBody(body)).build() }

    private suspend inline fun <reified B> patchJson(path: String, body: B): String =
        request { Request.Builder().url(http.url(path)).patch(jsonBody(body)).build() }

    private suspend fun postEmpty(
        path: String,
        client: OkHttpClient = http.client,
    ): String = request(client) {
        Request.Builder()
            .url(http.url(path))
            .post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private suspend fun delete(path: String): String =
        request { Request.Builder().url(http.url(path)).delete().build() }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val EMPTY_JSON = "{}"
    }
}
