package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.repository.LibraryState
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.player.PlaybackController
import com.ugnbt.spotlabdesktop.player.PlaybackPosition
import com.ugnbt.spotlabdesktop.player.formatDuration
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ticks while playing so the seek bar advances without waiting on the
 * server. Shared with [NowPlayingScreen] — not file-private, since Kotlin
 * top-level `private` scopes to the file, not the package.
 */
@Composable
fun rememberLivePosition(playback: PlaybackRepository): Double {
    val state by playback.playback.collectAsState()
    val buffering by playback.localBuffering.collectAsState()
    val connected by playback.connected.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state?.isPlaying, buffering, connected) {
        while (state?.isPlaying == true && !buffering && connected) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    return PlaybackPosition.positionAt(state, now)
}

@Composable
fun PlayerBar(
    playback: PlaybackRepository,
    controller: PlaybackController,
    library: LibraryState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by playback.displayedCurrent.collectAsState()
    val state by playback.playback.collectAsState()
    val likedIds by library.likedIds.collectAsState()
    val position = rememberLivePosition(playback)
    var volume by remember { mutableStateOf(80) }
    var dragPosition by remember { mutableStateOf<Double?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = current?.cover,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = current != null, onClick = onExpand),
        )
        Column(modifier = Modifier.width(180.dp).clickable(enabled = current != null, onClick = onExpand)) {
            Text(
                current?.title ?: "Rien en lecture",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                current?.artist.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (current != null) {
            val liked = current!!.id in likedIds
            IconButton(onClick = { library.toggle(current!!.toUiTrack()) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Aimer",
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = playback::toggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Aléatoire",
                        tint = if (state?.shuffle == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = playback::skipPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Précédent")
                }
                IconButton(onClick = playback::togglePlay) {
                    Icon(
                        if (state?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Lecture",
                    )
                }
                IconButton(onClick = playback::skipNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Suivant")
                }
                IconButton(onClick = playback::cycleRepeat) {
                    Icon(
                        if (state?.repeat == "one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Répétition",
                        tint = if (state?.repeat != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration((dragPosition ?: position).toInt()), style = MaterialTheme.typography.bodySmall)
                ThinSlider(
                    value = (dragPosition ?: position).toFloat(),
                    valueRange = 0f..(current?.duration?.takeIf { it > 0 }?.toFloat() ?: 1f),
                    onValueChange = { dragPosition = it.toDouble() },
                    onValueChangeFinished = {
                        dragPosition?.let { playback.seek(it) }
                        dragPosition = null
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(formatDuration(current?.duration), style = MaterialTheme.typography.bodySmall)
            }
        }

        QueueButton(playback)
        DevicesButton(playback)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(120.dp)) {
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            ThinSlider(
                value = volume.toFloat(),
                valueRange = 0f..100f,
                onValueChange = {
                    volume = it.toInt()
                    controller.player.setVolume(volume)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QueueButton(playback: PlaybackRepository) {
    var expanded by remember { mutableStateOf(false) }
    val state by playback.playback.collectAsState()
    val pendingQueue by playback.pendingQueue.collectAsState()
    val queueDrag = remember { QueueDragState() }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.QueueMusic, contentDescription = "File d'attente", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            // A plain Popup, not DropdownMenu: DropdownMenu measures its content
            // for an intrinsic width/height to animate its reveal, and a
            // LazyColumn (built on SubcomposeLayout, same as any lazy list)
            // can't be measured that way — it crashes ("Asking for intrinsic
            // measurements of SubcomposeLayout layouts is not supported").
            Popup(
                alignment = Alignment.BottomEnd,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier.width(420.dp).heightIn(max = 640.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    val queue = pendingQueue ?: state?.queue.orEmpty()
                    if (queue.isEmpty()) {
                        Box(Modifier.padding(16.dp)) {
                            Text("File d'attente vide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn {
                            item { QueueHeader(queue.size) }
                            queueSection(
                                queue = queue,
                                drag = queueDrag,
                                onPlay = playback::playFromQueue,
                                onRemove = playback::removeFromQueue,
                                onReorder = playback::reorderQueue,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesButton(playback: PlaybackRepository) {
    var expanded by remember { mutableStateOf(false) }
    val devices by playback.devices.collectAsState()
    val state by playback.playback.collectAsState()
    val active = state?.activeDeviceIds.orEmpty()
    val scope = rememberCoroutineScope()

    Box {
        IconButton(onClick = {
            expanded = true
            scope.launch { playback.refreshDevices() }
        }) {
            Icon(Icons.Filled.Devices, contentDescription = "Sortie audio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (devices.isEmpty()) {
                DropdownMenuItem(text = { Text("Aucun appareil") }, onClick = {})
            }
            devices.forEach { device ->
                val isActive = device.deviceId in active
                DropdownMenuItem(
                    text = { Text(device.name.ifBlank { device.platform }) },
                    leadingIcon = if (isActive) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = { playback.toggleOutputDevice(device.deviceId) },
                )
            }
        }
    }
}
