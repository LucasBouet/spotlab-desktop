package com.ugnbt.spotlabdesktop.ui.model

import com.ugnbt.spotlabdesktop.data.remote.dto.BlendTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerArtistDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.LikedTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistTrackInputDto
import com.ugnbt.spotlabdesktop.data.remote.dto.QueueItemDto
import com.ugnbt.spotlabdesktop.data.remote.dto.TrackMetadataDto
import java.util.UUID

/** One shape for every list in the app — ported from the Android client's `UiTrack`. */
data class UiTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val cover: String,
    val duration: Int,
    val artistId: Long? = null,
    val albumId: Long? = null,
    val rowKey: String? = null,
)

private fun List<DeezerArtistDto>.joinedNames(): String {
    val names = LinkedHashSet<String>()
    for (contributor in this) {
        val name = contributor.name.trim()
        if (name.isNotEmpty()) names.add(name)
    }
    return names.joinToString(", ")
}

fun DeezerTrackDto.toUiTrack() = UiTrack(
    id = id,
    title = title,
    artist = contributors.joinedNames().ifBlank { artist?.name.orEmpty() },
    album = album?.title.orEmpty(),
    cover = album?.coverMedium ?: album?.coverBig.orEmpty(),
    duration = duration,
    artistId = artist?.id,
    albumId = album?.id,
)

fun BlendTrackDto.toUiTrack() = UiTrack(
    id = id,
    title = title,
    artist = artist?.name.orEmpty(),
    album = album?.title.orEmpty(),
    cover = album?.coverMedium ?: album?.coverBig.orEmpty(),
    duration = duration,
    artistId = artist?.id,
)

fun LikedTrackDto.toUiTrack() = UiTrack(
    id = deezerTrackId,
    title = title,
    artist = artistName,
    album = albumTitle,
    cover = albumCover,
    duration = duration,
    artistId = artistId,
)

fun QueueItemDto.toUiTrack() = UiTrack(
    id = id,
    title = title,
    artist = artist,
    album = album,
    cover = cover,
    duration = duration,
)

fun PlaylistTrackDto.toUiTrack() = UiTrack(
    id = id,
    title = title,
    artist = artist?.name.orEmpty(),
    album = album?.title.orEmpty(),
    cover = album?.coverMedium.orEmpty(),
    duration = duration,
    artistId = artist?.id,
    albumId = album?.id,
    rowKey = rowKey,
)

fun UiTrack.toQueueItem(isManual: Boolean = false) = QueueItemDto(
    id = id,
    title = title,
    artist = artist,
    album = album,
    cover = cover,
    duration = duration,
    uid = UUID.randomUUID().toString(),
    isManual = isManual,
)

fun UiTrack.toMetadata() = TrackMetadataDto(
    title = title,
    artistName = artist,
    artistId = artistId,
    albumTitle = album,
    albumCover = cover,
    duration = duration,
)

fun UiTrack.toPlaylistInput(toggle: Boolean? = null) = PlaylistTrackInputDto(
    deezerTrackId = id,
    title = title,
    artistName = artist,
    artistId = artistId,
    albumTitle = album,
    albumCover = cover,
    duration = duration,
    toggle = toggle,
)

fun List<UiTrack>.toQueueItems(): List<QueueItemDto> = map { it.toQueueItem() }
