package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * A slim, Spotify-style slider — the stock Material3 [androidx.compose.material3.Slider]
 * has a thick "expressive" track by default with no simple way to thin it out
 * for a music player's seek/volume bars.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 4.dp,
    thumbSize: Dp = 12.dp,
) {
    var widthPx by remember { mutableStateOf(0f) }
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    fun fractionFromX(x: Float): Float = if (widthPx > 0f) (x / widthPx).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(thumbSize)
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        onValueChange(valueRange.start + fractionFromX(offset.x) * span)
                        onValueChangeFinished?.invoke()
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange(valueRange.start + fractionFromX(change.position.x) * span)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier.fillMaxWidth(fraction).height(trackHeight).clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            Modifier
                .offset { IntOffset((fraction * widthPx).toInt() - (thumbSize.roundToPx() / 2), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
