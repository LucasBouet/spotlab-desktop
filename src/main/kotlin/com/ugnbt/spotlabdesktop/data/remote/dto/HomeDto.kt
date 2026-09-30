package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /api/recommendations?window=day|week|all` */
@Serializable
data class RecommendationsDto(
    val window: String = "",
    val effectiveWindow: String = "",
    val basedOn: List<String> = emptyList(),
    val tracks: List<DeezerTrackDto> = emptyList(),
    val albums: List<DeezerAlbumDto> = emptyList(),
)

/** One row of `GET /api/smart-playlists` — an auto-generated home shelf entry,
 *  fully populated (its own [tracks]), read-only. */
@Serializable
data class SmartPlaylistDto(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val subtitle: String = "",
    val cover: String = "",
    val tracks: List<DeezerTrackDto> = emptyList(),
)

@Serializable
data class SmartPlaylistsDto(val playlists: List<SmartPlaylistDto> = emptyList())
