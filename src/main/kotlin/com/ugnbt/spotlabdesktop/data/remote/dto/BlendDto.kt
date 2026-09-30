package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BlendTrackDto(
    val id: Long,
    val title: String = "",
    val duration: Int = 0,
    val artist: DeezerArtistDto? = null,
    val album: DeezerAlbumRefDto? = null,
    val addedByUserId: String = "",
    val addedByName: String = "",
)

@Serializable
data class BlendDto(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val cover: String = "",
    val partnerUserId: String = "",
    val tracks: List<BlendTrackDto> = emptyList(),
)

@Serializable
data class BlendsResponseDto(val blends: List<BlendDto> = emptyList())
