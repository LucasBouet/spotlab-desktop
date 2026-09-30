package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistMembershipDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistSummaryDto
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.player.formatDuration
import com.ugnbt.spotlabdesktop.ui.model.UiTrack
import com.ugnbt.spotlabdesktop.ui.model.toPlaylistInput
import com.ugnbt.spotlabdesktop.ui.model.toQueueItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: UiTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean? = null,
    onToggleLike: (() -> Unit)? = null,
    /** Non-null enables the "add to playlist" button, self-contained (its own dropdown). */
    playlistApi: SpotlabApi? = null,
    /** Non-null enables "Lire ensuite"/"Ajouter à la file d'attente" in the
     *  right-click menu. */
    playback: PlaybackRepository? = null,
    /** For the right-click menu's "Ajouter à «X»" entries — the caller's
     *  own playlist list, fetched once per screen rather than once per row. */
    playlists: List<PlaylistSummaryDto> = emptyList(),
    /** Extra content after the duration — e.g. Blend's "ajouté par X" caption. */
    trailing: (@Composable () -> Unit)? = null,
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Initial pass, and never consumed: this only reads where the
                // pointer went down, it must not steal the event from the
                // click/right-click detectors below.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                event.changes.firstOrNull()?.position?.let { position ->
                                    menuOffset = DpOffset(position.x.toDp(), position.y.toDp())
                                }
                            }
                        }
                    }
                }
                .clickable(onClick = onClick)
                .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) { showMenu = true }
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
            trailing?.invoke()
        }

        // A themed Material3 DropdownMenu instead of Compose Desktop's
        // built-in right-click representation — the latter renders as a
        // plain native-looking Swing popup with no way to match this app's
        // colors, which is exactly what looked out of place here.
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = menuOffset,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            DropdownMenuItem(text = { Text("Lire maintenant") }, onClick = { showMenu = false; onClick() })
            if (playback != null) {
                DropdownMenuItem(
                    text = { Text("Lire ensuite") },
                    onClick = { showMenu = false; playback.queuePlayNext(track.toQueueItem()) },
                )
                DropdownMenuItem(
                    text = { Text("Ajouter à la file d'attente") },
                    onClick = { showMenu = false; playback.queueAddToEnd(track.toQueueItem()) },
                )
            }
            if (onToggleLike != null) {
                DropdownMenuItem(
                    text = { Text(if (liked == true) "Retirer des favoris" else "Aimer") },
                    onClick = { showMenu = false; onToggleLike() },
                )
            }
            if (playlistApi != null && playlists.isNotEmpty()) {
                HorizontalDivider()
                playlists.forEach { playlist ->
                    DropdownMenuItem(
                        text = { Text("Ajouter à « ${playlist.name} »") },
                        onClick = {
                            showMenu = false
                            scope.launch { runCatching { playlistApi.addToPlaylist(playlist.id, track.toPlaylistInput()) } }
                        },
                    )
                }
            }
        }
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
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
