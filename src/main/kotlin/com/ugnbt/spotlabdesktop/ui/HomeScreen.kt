package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerAlbumDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.RecommendationsDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SmartPlaylistDto
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.ui.model.toQueueItems
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack
import kotlinx.coroutines.launch

/**
 * A simplified port of the Android client's `ui/home/HomeScreen.kt` — the
 * shelf-pinning/reordering machinery there is left out (that home screen is
 * ~600 lines mostly about that); this keeps the part that matters for a first
 * pass, showing the same recommendations and smart playlists.
 */
@Composable
fun HomeScreen(api: SpotlabApi, playback: PlaybackRepository, modifier: Modifier = Modifier) {
    var recommendations by remember { mutableStateOf<RecommendationsDto?>(null) }
    var smartPlaylists by remember { mutableStateOf<List<SmartPlaylistDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { recommendations = api.recommendations("week") }
        runCatching { smartPlaylists = api.smartPlaylists().playlists }
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Accueil", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        val recs = recommendations
        if (recs != null && recs.tracks.isNotEmpty()) {
            val caption = recs.basedOn.takeIf { it.isNotEmpty() }
                ?.let { "Basé sur " + it.joinToString(", ") }
                ?: "Les tendances du moment"
            ShelfSection(title = "Recommandé pour vous", subtitle = caption) {
                items(recs.tracks, key = { it.id }) { track ->
                    TrackCard(track) {
                        val items = recs.tracks.map { it.toUiTrack() }.toQueueItems()
                        playback.playContext("recommendations", items, recs.tracks.indexOf(track))
                    }
                }
            }
        }

        if (smartPlaylists.isNotEmpty()) {
            ShelfSection(title = "Playlists pour vous") {
                items(smartPlaylists, key = { it.id }) { playlist ->
                    SmartPlaylistCard(playlist) {
                        val items = playlist.tracks.map { it.toUiTrack() }.toQueueItems()
                        if (items.isNotEmpty()) playback.playContext("smart:${playlist.id}", items, 0)
                    }
                }
            }
        }

        if (recs != null && recs.albums.isNotEmpty()) {
            ShelfSection(title = "Albums à découvrir") {
                items(recs.albums, key = { it.id }) { album ->
                    AlbumCard(album, api, playback)
                }
            }
        }
    }
}

@Composable
private fun ShelfSection(
    title: String,
    subtitle: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun TrackCard(track: DeezerTrackDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(140.dp).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = track.album?.coverMedium,
            contentDescription = null,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            track.artist?.name.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SmartPlaylistCard(playlist: SmartPlaylistDto, onClick: () -> Unit) {
    Column(modifier = Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = playlist.cover,
            contentDescription = null,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            playlist.subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumCard(album: DeezerAlbumDto, api: SpotlabApi, playback: PlaybackRepository) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.width(140.dp).clickable {
            scope.launch {
                val full = runCatching { api.album(album.id) }.getOrNull() ?: return@launch
                val tracks = full.tracks?.data.orEmpty()
                if (tracks.isNotEmpty()) {
                    playback.playContext("album:${album.id}", tracks.map { it.toUiTrack() }.toQueueItems(), 0)
                }
            }
        },
    ) {
        AsyncImage(
            model = album.coverMedium,
            contentDescription = null,
            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            album.artist?.name.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
