package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.LikedTrackDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistSummaryDto
import com.ugnbt.spotlabdesktop.data.repository.LibraryState
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.ui.model.toQueueItems
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack

@Composable
fun LikedScreen(api: SpotlabApi, playback: PlaybackRepository, library: LibraryState, modifier: Modifier = Modifier) {
    var tracks by remember { mutableStateOf<List<LikedTrackDto>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<PlaylistSummaryDto>>(emptyList()) }
    val likedIds by library.likedIds.collectAsState()

    // Re-fetch whenever the liked-ids set changes size — covers both this
    // screen unliking a track and a like happening elsewhere (search, album).
    LaunchedEffect(likedIds.size) { runCatching { tracks = api.likedTracks() } }
    LaunchedEffect(Unit) { runCatching { playlists = api.playlists() } }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Titres likés", style = MaterialTheme.typography.titleLarge)
        ScrollableLazyColumn {
            items(tracks, key = { it.deezerTrackId }) { track ->
                val uiTrack = track.toUiTrack()
                TrackRow(
                    uiTrack,
                    onClick = {
                        val items = tracks.map { it.toUiTrack() }.toQueueItems()
                        playback.playContext("liked", items, tracks.indexOf(track))
                    },
                    liked = uiTrack.id in likedIds,
                    onToggleLike = { library.toggle(uiTrack) },
                    playlistApi = api,
                    playback = playback,
                    playlists = playlists,
                )
            }
        }
    }
}
