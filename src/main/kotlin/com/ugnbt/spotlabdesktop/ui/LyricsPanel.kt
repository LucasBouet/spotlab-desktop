package com.ugnbt.spotlabdesktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.repository.LyricsState
import com.ugnbt.spotlabdesktop.player.LyricLine
import com.ugnbt.spotlabdesktop.player.activeLyricIndex
import java.util.Locale

/**
 * Synced lyrics scrolling against the extrapolated playback position — ported
 * from the Android client's `ui/player/LyricsPanel.kt`, pure Compose.
 */
@Composable
fun LyricsPanel(
    state: LyricsState,
    positionSeconds: Double,
    offsetSeconds: Double,
    onSeek: (Double) -> Unit,
    onNudge: (Int) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LyricsState.None, LyricsState.Loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }

        is LyricsState.Unavailable -> Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }

        is LyricsState.Plain -> Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                state.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is LyricsState.Synced -> SyncedLyrics(
            lines = state.lines,
            positionSeconds = positionSeconds,
            offsetSeconds = offsetSeconds,
            onSeek = onSeek,
            onNudge = onNudge,
            onResetOffset = onResetOffset,
            modifier = modifier,
        )
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionSeconds: Double,
    offsetSeconds: Double,
    onSeek: (Double) -> Unit,
    onNudge: (Int) -> Unit,
    onResetOffset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex = activeLyricIndex(lines, positionSeconds - offsetSeconds)
    val listState = rememberLazyListState()
    var lastDragAtMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start || interaction is DragInteraction.Stop) {
                lastDragAtMs = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - lastDragAtMs < HANDS_OFF_MS) return@LaunchedEffect
        val layout = listState.layoutInfo
        val lineHeight = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex }?.size ?: 0
        listState.animateScrollToItem(index = activeIndex, scrollOffset = -((layout.viewportSize.height - lineHeight) / 2))
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 140.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { index, line ->
                LyricRow(line = line, active = index == activeIndex, onClick = { onSeek(line.timeSeconds + offsetSeconds) })
            }
        }

        OffsetControls(
            offsetSeconds = offsetSeconds,
            onNudge = onNudge,
            onReset = onResetOffset,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1f else 0.94f, label = "lyric-scale")
    Text(
        text = line.text.ifBlank { "♪" },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).scale(scale).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun OffsetControls(offsetSeconds: Double, onNudge: (Int) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            NudgeButton(label = "−", description = "Avancer les paroles") { onNudge(-1) }
            Text(
                text = formatLyricsOffset(offsetSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = if (offsetSeconds == 0.0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp).clickable(enabled = offsetSeconds != 0.0, onClick = onReset).padding(vertical = 10.dp),
            )
            NudgeButton(label = "+", description = "Retarder les paroles") { onNudge(1) }
        }
    }
}

@Composable
private fun NudgeButton(label: String, description: String, onClick: () -> Unit) {
    Box(modifier = Modifier.size(40.dp).clickable(onClickLabel = description, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatLyricsOffset(seconds: Double): String {
    val sign = if (seconds > 0) "+" else ""
    return sign + "%.1f s".format(Locale.ROOT, seconds).replace('.', ',')
}

private const val HANDS_OFF_MS = 5_000L
