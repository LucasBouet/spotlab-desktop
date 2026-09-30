package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class StatEntryDto(
    val label: String = "",
    val sublabel: String? = null,
    val cover: String? = null,
    val count: Int = 0,
)

@Serializable
data class ListeningStatsDto(
    val totalPlays: Int = 0,
    val totalSeconds: Int = 0,
    val topTracks: List<StatEntryDto> = emptyList(),
    val topAlbums: List<StatEntryDto> = emptyList(),
    val topGenres: List<StatEntryDto> = emptyList(),
)
