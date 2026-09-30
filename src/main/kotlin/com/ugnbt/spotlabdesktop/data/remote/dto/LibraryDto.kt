package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LikedTrackDto(
    val id: String? = null,
    val deezerTrackId: Long,
    val title: String = "",
    val artistName: String = "",
    val artistId: Long? = null,
    val albumTitle: String = "",
    val albumCover: String = "",
    val duration: Int = 0,
    val createdAt: String? = null,
)

@Serializable
data class LikedTracksResponseDto(val tracks: List<LikedTrackDto> = emptyList())

@Serializable
data class LikedIdsResponseDto(val ids: List<Long> = emptyList())

@Serializable
data class TrackMetadataDto(
    val title: String,
    val artistName: String,
    val artistId: Long? = null,
    val albumTitle: String = "",
    val albumCover: String = "",
    val duration: Int = 0,
)

/** Body of `POST /api/plays` — logged once a track passes the ~30 s threshold. */
@Serializable
data class PlayEventDto(
    val deezerTrackId: Long,
    val title: String,
    val artistName: String = "",
    val albumTitle: String = "",
    val albumCover: String = "",
    val duration: Int,
)
