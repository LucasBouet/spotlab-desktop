package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.dto.QueueItemDto
import com.ugnbt.spotlabdesktop.data.repository.moved
import com.ugnbt.spotlabdesktop.player.formatDuration
import kotlin.math.roundToInt

/**
 * Where a row picked up at [fromIndex] lands after being dragged [dragPx] —
 * ported verbatim from the Android client's `ui/player/QueueSection.kt`, a
 * pure function with no platform dependency.
 */
internal fun dropTargetIndex(fromIndex: Int, dragPx: Float, rowHeightPx: Float, size: Int): Int {
    if (size <= 1 || rowHeightPx <= 0f) return fromIndex
    return (fromIndex + (dragPx / rowHeightPx).roundToInt()).coerceIn(0, size - 1)
}

/** The drag in progress, hoisted out of the rows so it survives their
 *  recomposition. */
@Stable
class QueueDragState {
    var fromIndex by mutableIntStateOf(NONE)
    var dragPx by mutableFloatStateOf(0f)
    var rowHeightPx by mutableFloatStateOf(0f)

    val isDragging: Boolean get() = fromIndex != NONE

    fun begin(index: Int) {
        fromIndex = index
        dragPx = 0f
    }

    fun dragBy(deltaPx: Float) {
        dragPx += deltaPx
    }

    fun reset() {
        fromIndex = NONE
        dragPx = 0f
    }

    fun targetIndex(size: Int): Int = dropTargetIndex(fromIndex, dragPx, rowHeightPx, size)

    private companion object {
        const val NONE = -1
    }
}

/** The queue: a grip to reorder by dragging, the row itself to play from
 *  there, a cross to drop it — same interaction as the Android client. */
fun LazyListScope.queueSection(
    queue: List<QueueItemDto>,
    drag: QueueDragState,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val target = if (drag.isDragging) drag.targetIndex(queue.size) else -1
    val shown = if (drag.isDragging) queue.moved(drag.fromIndex, target) else queue

    itemsIndexed(shown, key = { _, item -> item.uid }) { index, item ->
        val dragged = drag.isDragging && index == target
        QueueRow(
            item = item,
            offsetPx = if (dragged) drag.dragPx - (target - drag.fromIndex) * drag.rowHeightPx else 0f,
            dragged = dragged,
            onPlay = { onPlay(item.uid) },
            onRemove = { onRemove(item.uid) },
            onMeasured = { height -> if (drag.rowHeightPx == 0f) drag.rowHeightPx = height },
            onDragStart = {
                queue.indexOfFirst { it.uid == item.uid }.takeIf { it >= 0 }?.let(drag::begin)
            },
            onDrag = drag::dragBy,
            onDragEnd = {
                if (drag.isDragging) {
                    val from = drag.fromIndex
                    val to = drag.targetIndex(queue.size)
                    drag.reset()
                    if (from != to) onReorder(from, to)
                }
            },
            onDragCancel = drag::reset,
        )
    }
}

@Composable
private fun QueueRow(
    item: QueueItemDto,
    offsetPx: Float,
    dragged: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragged) 1f else 0f)
            .offset { IntOffset(0, offsetPx.roundToInt()) }
            .background(if (dragged) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .onSizeChanged { onMeasured(it.height.toFloat()) }
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DragHandle(key = item.uid, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd, onDragCancel = onDragCancel)
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onPlay),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    item.title.ifBlank { "Titre inconnu" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatDuration(item.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Retirer de la file", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QueueHeader(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = if (count == 0) "File d'attente" else "Prochains titres · $count",
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The grip. A dedicated handle rather than a drag-anywhere row, because the
 *  row's own click is "play from here" and the two gestures would conflict. */
@Composable
private fun DragHandle(
    key: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, delta -> change.consume(); onDrag(delta.y) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            }
            .size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragHandle, contentDescription = "Réordonner", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
