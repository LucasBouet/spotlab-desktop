package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /api/lyrics` — the server's proxy over lrclib. */
@Serializable
data class LyricsDto(
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)
