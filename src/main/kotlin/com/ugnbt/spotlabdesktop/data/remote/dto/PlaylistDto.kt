package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlaylistSummaryDto(
    val id: String,
    val name: String = "",
    val pinned: Boolean = false,
    val position: Long = 0,
    val trackCount: Int = 0,
    val covers: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlaylistsResponseDto(val playlists: List<PlaylistSummaryDto> = emptyList())

@Serializable
data class PlaylistTrackDto(
    val rowKey: String,
    val id: Long,
    val title: String = "",
    val duration: Int = 0,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumRefDto? = null,
    val addedAt: String? = null,
)

/** `GET /api/playlists/{id}` — returned unwrapped, unlike the list endpoint. */
@Serializable
data class PlaylistDetailDto(
    val id: String,
    val name: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val tracks: List<PlaylistTrackDto> = emptyList(),
)

@Serializable
data class PlaylistRefDto(val id: String, val name: String = "")

/** `POST /api/playlists` */
@Serializable
data class CreatePlaylistResponseDto(val playlist: PlaylistRefDto)

@Serializable
data class PlaylistMembershipDto(
    val id: String,
    val name: String = "",
    val hasTrack: Boolean = false,
)

@Serializable
data class PlaylistMembershipResponseDto(
    val playlists: List<PlaylistMembershipDto> = emptyList(),
)

@Serializable
data class PlaylistToggleResponseDto(val added: Boolean = false)

/** Body of `POST /api/playlists/{id}/tracks`. */
@Serializable
data class PlaylistTrackInputDto(
    val deezerTrackId: Long,
    val title: String,
    val artistName: String = "",
    val artistId: Long? = null,
    val albumTitle: String = "",
    val albumCover: String = "",
    val duration: Int = 0,
    val toggle: Boolean? = null,
)

/**
 * One line of the NDJSON stream `POST /api/playlists/import` answers with —
 * every possible field across the three event shapes (progress/done/error)
 * on one flat DTO. Never decoded directly by UI code — see
 * [PlaylistImportEvent] for the type callers actually work with.
 */
@Serializable
internal data class PlaylistImportEventDto(
    val type: String = "",
    val fetched: Int = 0,
    val total: Int = 0,
    val destination: String? = null,
    val playlistId: String? = null,
    val trackCount: Int = 0,
    val message: String? = null,
)

/** What [PlaylistImportEventDto] decodes into for everything outside SpotlabApi. */
sealed interface PlaylistImportEvent {
    data class Progress(val fetched: Int, val total: Int) : PlaylistImportEvent
    data class Done(val destination: String, val playlistId: String?, val trackCount: Int) : PlaylistImportEvent
    data class Failed(val message: String) : PlaylistImportEvent
}
