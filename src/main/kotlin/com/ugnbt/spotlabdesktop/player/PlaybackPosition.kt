package com.ugnbt.spotlabdesktop.player

import com.ugnbt.spotlabdesktop.data.remote.dto.PlaybackStateDto
import java.time.Instant

/**
 * `positionSeconds` is an *anchor*, not a live position: the server only moves it
 * on a real transport command, and re-broadcasts every 5 s to correct drift. The
 * UI and the player both extrapolate through this one function so they can never
 * disagree about where playback actually is. Ported verbatim from the Android
 * client (`player/PlaybackPosition.kt`) — it has no platform dependency.
 */
object PlaybackPosition {

    fun positionAt(
        state: PlaybackStateDto?,
        nowMs: Long = System.currentTimeMillis(),
    ): Double {
        if (state == null) return 0.0
        val duration = state.current?.duration?.takeIf { it > 0 }?.toDouble()
        val anchor = state.positionSeconds.coerceAtLeast(0.0)
        if (!state.isPlaying) return duration?.let { anchor.coerceAtMost(it) } ?: anchor

        val anchoredAt = state.positionUpdatedAt?.let(::epochMilliOrNull) ?: return anchor
        val elapsed = ((nowMs - anchoredAt) / 1000.0).coerceAtLeast(0.0)
        val live = anchor + elapsed
        return duration?.let { live.coerceIn(0.0, it) } ?: live
    }

    private fun epochMilliOrNull(raw: String): Long? =
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
}

/** `3:07`, or `--:--` while nothing is loaded. */
fun formatDuration(seconds: Number?): String {
    val total = seconds?.toInt() ?: return "--:--"
    if (total < 0) return "--:--"
    return "%d:%02d".format(total / 60, total % 60)
}
