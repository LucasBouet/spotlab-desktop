package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.ArtistPageDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerAlbumDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerArtistDto
import com.ugnbt.spotlabdesktop.data.remote.dto.DeezerTrackDto
import com.ugnbt.spotlabdesktop.data.repository.LibraryState
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.ui.model.toQueueItems
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack
import kotlinx.coroutines.delay

private sealed interface SearchDetail {
    data class Album(val id: Long, val title: String) : SearchDetail
    data class Artist(val id: Long, val name: String) : SearchDetail
}

@Composable
fun SearchScreen(api: SpotlabApi, playback: PlaybackRepository, library: LibraryState, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var tracks by remember { mutableStateOf<List<DeezerTrackDto>>(emptyList()) }
    var albums by remember { mutableStateOf<List<DeezerAlbumDto>>(emptyList()) }
    var artists by remember { mutableStateOf<List<DeezerArtistDto>>(emptyList()) }
    var detail by remember { mutableStateOf<SearchDetail?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            tracks = emptyList(); albums = emptyList(); artists = emptyList()
            return@LaunchedEffect
        }
        delay(350) // debounce
        runCatching { tracks = api.searchTracks(query) }
        runCatching { albums = api.searchAlbums(query) }
        runCatching { artists = api.searchArtists(query) }
    }

    val currentDetail = detail
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (currentDetail == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                FloatingSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                TabRow(selectedTabIndex = tab, modifier = Modifier.widthIn(max = 420.dp)) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Titres") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Albums") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Artistes") })
                }
            }
            when (tab) {
                0 -> {
                    val likedIds by library.likedIds.collectAsState()
                    LazyColumn {
                        items(tracks, key = { it.id }) { track ->
                            val uiTrack = track.toUiTrack()
                            TrackRow(
                                uiTrack,
                                onClick = {
                                    val items = tracks.map { it.toUiTrack() }.toQueueItems()
                                    val index = tracks.indexOf(track)
                                    playback.playContext("search:$query", items, index)
                                },
                                liked = uiTrack.id in likedIds,
                                onToggleLike = { library.toggle(uiTrack) },
                                playlistApi = api,
                            )
                        }
                    }
                }
                1 -> LazyColumn {
                    items(albums, key = { it.id }) { album ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { detail = SearchDetail.Album(album.id, album.title) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = album.coverMedium,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Column {
                                Text(album.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    album.artist?.name.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                2 -> LazyColumn {
                    items(artists, key = { it.id ?: it.name }) { artist ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { artist.id?.let { detail = SearchDetail.Artist(it, artist.name) } }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = artist.pictureMedium,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { detail = null }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    when (currentDetail) {
                        is SearchDetail.Album -> currentDetail.title
                        is SearchDetail.Artist -> currentDetail.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (currentDetail) {
                is SearchDetail.Album -> AlbumDetail(api, playback, library, currentDetail.id)
                is SearchDetail.Artist -> ArtistDetail(api, playback, library, currentDetail.id)
            }
        }
    }
}

@Composable
private fun FloatingSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (query.isEmpty()) {
                    Text(
                        "Rechercher un titre, un album, un artiste…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AlbumDetail(api: SpotlabApi, playback: PlaybackRepository, library: LibraryState, albumId: Long) {
    var album by remember(albumId) { mutableStateOf<DeezerAlbumDto?>(null) }
    LaunchedEffect(albumId) { runCatching { album = api.album(albumId) } }
    val tracks = album?.tracks?.data.orEmpty()
    val likedIds by library.likedIds.collectAsState()
    LazyColumn {
        items(tracks, key = { it.id }) { track ->
            val uiTrack = track.toUiTrack()
            TrackRow(
                uiTrack,
                onClick = {
                    val items = tracks.map { it.toUiTrack() }.toQueueItems()
                    playback.playContext("album:$albumId", items, tracks.indexOf(track))
                },
                liked = uiTrack.id in likedIds,
                onToggleLike = { library.toggle(uiTrack) },
                playlistApi = api,
            )
        }
    }
}

@Composable
private fun ArtistDetail(api: SpotlabApi, playback: PlaybackRepository, library: LibraryState, artistId: Long) {
    var page by remember(artistId) { mutableStateOf<ArtistPageDto?>(null) }
    LaunchedEffect(artistId) { runCatching { page = api.artist(artistId) } }
    val tracks = page?.topTracks.orEmpty()
    val likedIds by library.likedIds.collectAsState()
    LazyColumn {
        items(tracks, key = { it.id }) { track ->
            val uiTrack = track.toUiTrack()
            TrackRow(
                uiTrack,
                onClick = {
                    val items = tracks.map { it.toUiTrack() }.toQueueItems()
                    playback.playContext("artist:$artistId", items, tracks.indexOf(track))
                },
                liked = uiTrack.id in likedIds,
                onToggleLike = { library.toggle(uiTrack) },
                playlistApi = api,
            )
        }
    }
}
