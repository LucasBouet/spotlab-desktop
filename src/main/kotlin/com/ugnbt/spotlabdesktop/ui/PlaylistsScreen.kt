package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistDetailDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistSummaryDto
import com.ugnbt.spotlabdesktop.data.repository.LibraryState
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.ui.model.toQueueItems
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(api: SpotlabApi, playback: PlaybackRepository, library: LibraryState, modifier: Modifier = Modifier) {
    var playlists by remember { mutableStateOf<List<PlaylistSummaryDto>>(emptyList()) }
    var openedId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) { runCatching { playlists = api.playlists() } }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        val id = openedId
        if (id == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = "Importer depuis Deezer")
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Créer une playlist")
                }
            }
            ScrollableLazyColumn {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { openedId = playlist.id }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${playlist.trackCount} titres",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { api.deletePlaylist(playlist.id) }
                                refreshKey++
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            var detail by remember(id) { mutableStateOf<PlaylistDetailDto?>(null) }
            var detailRefreshKey by remember(id) { mutableStateOf(0) }
            LaunchedEffect(id, detailRefreshKey) { runCatching { detail = api.playlist(id) } }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openedId = null }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    detail?.name ?: playlists.firstOrNull { it.id == id }?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val tracks = detail?.tracks.orEmpty()
            val likedIds by library.likedIds.collectAsState()
            ScrollableLazyColumn {
                items(tracks, key = { it.rowKey }) { track ->
                    val uiTrack = track.toUiTrack()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TrackRow(
                            uiTrack,
                            onClick = {
                                val items = tracks.map { it.toUiTrack() }.toQueueItems()
                                playback.playContext("playlist:$id", items, tracks.indexOf(track))
                            },
                            liked = uiTrack.id in likedIds,
                            onToggleLike = { library.toggle(uiTrack) },
                            playlistApi = api,
                            playback = playback,
                            playlists = playlists,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { api.removeFromPlaylist(id, track.rowKey) }
                                detailRefreshKey++
                                refreshKey++
                            }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Retirer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nouvelle playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        scope.launch {
                            runCatching { api.createPlaylist(trimmed) }
                            refreshKey++
                        }
                    }
                    showCreateDialog = false
                }) { Text("Créer") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Annuler") }
            },
        )
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            api = api,
            onDismiss = { showImportDialog = false },
            onImported = { showImportDialog = false; refreshKey++ },
        )
    }
}
