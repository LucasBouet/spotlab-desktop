package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.dto.QueueItemDto
import com.ugnbt.spotlabdesktop.data.repository.LibraryState
import com.ugnbt.spotlabdesktop.data.repository.LyricsRepository
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.player.formatDuration
import com.ugnbt.spotlabdesktop.ui.model.toUiTrack

/**
 * The full "now playing" view — port of the Android client's
 * `ui/player/NowPlayingScreen.kt`: cover, transport, then either the queue
 * (drag to reorder) or synced lyrics, toggled by the mic button.
 */
@Composable
fun NowPlayingScreen(
    playback: PlaybackRepository,
    lyricsRepo: LyricsRepository,
    library: LibraryState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by playback.playback.collectAsState()
    val displayedCurrent by playback.displayedCurrent.collectAsState()
    val isLoadingTrack by playback.isLoadingTrack.collectAsState()
    val likedIds by library.likedIds.collectAsState()
    val pendingQueue by playback.pendingQueue.collectAsState()
    val lyrics by lyricsRepo.state.collectAsState()
    val lyricsOffset by lyricsRepo.offset.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    val queueDrag = remember { QueueDragState() }

    val current = displayedCurrent
    val position = rememberLivePosition(playback)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Fermer")
                }
                Text(
                    if (showLyrics) "Paroles" else "En cours de lecture",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (current != null) {
                    if (showLyrics) {
                        IconButton(onClick = lyricsRepo::resync) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Resynchroniser les paroles", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Paroles",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Rien en lecture", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            val liked = current.id in likedIds
            val onToggleLike: () -> Unit = { library.toggle(current.toUiTrack()) }
            val duration = current.duration.takeIf { it > 0 }

            if (showLyrics) {
                LyricsPanel(
                    state = lyrics,
                    positionSeconds = position,
                    offsetSeconds = lyricsOffset,
                    onSeek = playback::seek,
                    onNudge = lyricsRepo::nudgeOffset,
                    onResetOffset = lyricsRepo::resetOffset,
                    modifier = Modifier.weight(1f),
                )
                TrackHeadline(current, liked, onToggleLike, compact = true)
                PositionRow(position, duration, playback::seek)
                TransportRow(
                    isPlaying = state?.isPlaying == true,
                    shuffle = state?.shuffle == true,
                    repeat = state?.repeat ?: "off",
                    onToggleShuffle = playback::toggleShuffle,
                    onCycleRepeat = playback::cycleRepeat,
                    onPrevious = playback::skipPrevious,
                    onTogglePlay = playback::togglePlay,
                    onNext = playback::skipNext,
                    loading = isLoadingTrack,
                )
                return@Column
            }

            val queue = pendingQueue ?: state?.queue.orEmpty()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Cover(current)
                    TrackHeadline(current, liked, onToggleLike)
                    PositionRow(position, duration, playback::seek)
                    TransportRow(
                        isPlaying = state?.isPlaying == true,
                        shuffle = state?.shuffle == true,
                        repeat = state?.repeat ?: "off",
                        onToggleShuffle = playback::toggleShuffle,
                        onCycleRepeat = playback::cycleRepeat,
                        onPrevious = playback::skipPrevious,
                        onTogglePlay = playback::togglePlay,
                        onNext = playback::skipNext,
                        loading = isLoadingTrack,
                    )
                    QueueHeader(queue.size)
                    if (queue.isEmpty()) {
                        Text(
                            "Rien après ce titre.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                queueSection(
                    queue = queue,
                    drag = queueDrag,
                    onPlay = playback::playFromQueue,
                    onRemove = playback::removeFromQueue,
                    onReorder = playback::reorderQueue,
                )
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Cover(item: QueueItemDto) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .widthIn(max = 420.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(model = item.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TrackHeadline(item: QueueItemDto, liked: Boolean, onToggleLike: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Aimer",
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PositionRow(positionSeconds: Double, durationSeconds: Int?, onSeek: (Double) -> Unit, modifier: Modifier = Modifier) {
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val maxValue = (durationSeconds ?: 1).toFloat()
    val value = (scrubbing ?: positionSeconds.toFloat()).coerceIn(0f, maxValue)

    Column(modifier.padding(horizontal = 24.dp)) {
        ThinSlider(
            value = value,
            valueRange = 0f..maxValue,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = { scrubbing?.let { onSeek(it.toDouble()) }; scrubbing = null },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(formatDuration(value.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: String,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        IconButton(onClick = onToggleShuffle, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Lecture aléatoire",
                tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Titre précédent") }
            FilledIconButton(onClick = onTogglePlay, enabled = !loading, modifier = Modifier.padding(horizontal = 12.dp).size(64.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Titre suivant") }
        }
        IconButton(onClick = onCycleRepeat, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(
                if (repeat == "one") Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Répétition",
                tint = if (repeat != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
