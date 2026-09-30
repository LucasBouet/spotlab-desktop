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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.BlendDto
import com.ugnbt.spotlabdesktop.data.remote.dto.FriendDto
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.player.formatDuration
import com.ugnbt.spotlabdesktop.ui.model.toQueueItems
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack
import kotlinx.coroutines.launch

private fun FriendDto.displayName(): String = name?.takeIf { it.isNotBlank() } ?: email

@Composable
fun BlendScreen(api: SpotlabApi, playback: PlaybackRepository, modifier: Modifier = Modifier) {
    var blends by remember { mutableStateOf<List<BlendDto>>(emptyList()) }
    var friends by remember { mutableStateOf<List<FriendDto>>(emptyList()) }
    var opened by remember { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { blends = api.blends() }
        runCatching { friends = api.social().friends }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        val id = opened
        if (id == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Blend", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { pickerOpen = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Nouveau blend")
                    }
                    DropdownMenu(
                        expanded = pickerOpen,
                        onDismissRequest = { pickerOpen = false },
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    ) {
                        if (friends.isEmpty()) {
                            DropdownMenuItem(text = { Text("Ajoutez d'abord un ami") }, onClick = {})
                        }
                        friends.forEach { friend ->
                            DropdownMenuItem(
                                text = { Text(friend.displayName()) },
                                onClick = {
                                    pickerOpen = false
                                    scope.launch {
                                        runCatching { api.createBlend(friend.userId) }
                                        runCatching { blends = api.blends() }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            ScrollableLazyColumn {
                items(blends, key = { it.id }) { blend ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { opened = blend.id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = blend.cover,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(blend.title, style = MaterialTheme.typography.bodyLarge)
                            Text(blend.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { api.deleteBlend(blend.id) }
                                blends = blends.filterNot { it.id == blend.id }
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            val blend = blends.firstOrNull { it.id == id }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { opened = null }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                Text(blend?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
            val tracks = blend?.tracks.orEmpty()
            ScrollableLazyColumn {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track.toUiTrack(),
                        onClick = {
                            val items = tracks.map { it.toUiTrack() }.toQueueItems()
                            playback.playContext("blend:$id", items, tracks.indexOf(track))
                        },
                        playback = playback,
                        trailing = {
                            if (track.addedByName.isNotBlank()) {
                                Text(
                                    "· ${track.addedByName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
