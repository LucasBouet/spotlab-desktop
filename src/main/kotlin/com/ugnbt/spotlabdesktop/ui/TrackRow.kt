package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistMembershipDto
import com.ugnbt.spotlabdesktop.player.formatDuration
import com.ugnbt.spotlabdesktop.ui.model.UiTrack
import com.ugnbt.spotlabdesktop.ui.model.toPlaylistInput
import kotlinx.coroutines.launch

@Composable
fun TrackRow(
    track: UiTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean? = null,
    onToggleLike: (() -> Unit)? = null,
    /** Non-null enables the "add to playlist" button, self-contained (its own dropdown). */
    playlistApi: SpotlabApi? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = track.cover,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (playlistApi != null) {
            AddToPlaylistButton(playlistApi, track)
        }
        if (onToggleLike != null) {
            IconButton(onClick = onToggleLike, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (liked == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Aimer",
                    tint = if (liked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            formatDuration(track.duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddToPlaylistButton(api: SpotlabApi, track: UiTrack) {
    var expanded by remember { mutableStateOf(false) }
    var memberships by remember { mutableStateOf<List<PlaylistMembershipDto>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Box {
        IconButton(
            modifier = Modifier.size(32.dp),
            onClick = {
                expanded = true
                scope.launch { runCatching { memberships = api.playlistMembership(track.id) } }
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Ajouter à une playlist", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (memberships.isEmpty()) {
                DropdownMenuItem(text = { Text("Aucune playlist — créez-en une d'abord") }, onClick = {})
            }
            memberships.forEach { membership ->
                DropdownMenuItem(
                    text = { Text(membership.name) },
                    leadingIcon = if (membership.hasTrack) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        scope.launch {
                            val added = runCatching {
                                api.togglePlaylistTrack(membership.id, track.toPlaylistInput())
                            }.getOrNull()
                            if (added != null) {
                                memberships = memberships.map {
                                    if (it.id == membership.id) it.copy(hasTrack = added) else it
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
