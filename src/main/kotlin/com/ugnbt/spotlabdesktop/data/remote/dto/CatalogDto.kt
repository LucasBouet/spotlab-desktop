package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Catalog metadata is Deezer's own shape, proxied by the server. Note the
// nesting (`artist.name`, `album.cover_medium`): the playback queue uses a
// flattened shape instead — see QueueItemDto.

@Serializable
data class DeezerArtistDto(
    val id: Long? = null,
    val name: String = "",
    @SerialName("picture_medium") val pictureMedium: String? = null,
    @SerialName("picture_big") val pictureBig: String? = null,
    @SerialName("nb_fan") val nbFan: Long? = null,
)

@Serializable
data class DeezerAlbumRefDto(
    val id: Long? = null,
    val title: String = "",
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
)

@Serializable
data class DeezerTrackDto(
    val id: Long,
    val title: String = "",
    val duration: Int = 0,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumRefDto? = null,
    val contributors: List<DeezerArtistDto> = emptyList(),
)

@Serializable
data class AlbumTracksDto(val data: List<DeezerTrackDto> = emptyList())

/** `GET /api/album/{id}`, and also one row of `GET /api/search?type=album`. */
@Serializable
data class DeezerAlbumDto(
    val id: Long,
    val title: String = "",
    @SerialName("cover_medium") val coverMedium: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("nb_tracks") val nbTracks: Int = 0,
    @SerialName("record_type") val recordType: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val duration: Int = 0,
    val artist: DeezerArtistDto? = null,
    val tracks: AlbumTracksDto? = null,
)

/** `GET /api/artist/{id}` — no discography, only the top titles. */
@Serializable
data class ArtistPageDto(
    val artist: DeezerArtistDto? = null,
    val topTracks: List<DeezerTrackDto> = emptyList(),
)

@Serializable
data class TrackSearchResponseDto(val data: List<DeezerTrackDto> = emptyList())

@Serializable
data class AlbumSearchResponseDto(val data: List<DeezerAlbumDto> = emptyList())

@Serializable
data class ArtistSearchResponseDto(val data: List<DeezerArtistDto> = emptyList())
