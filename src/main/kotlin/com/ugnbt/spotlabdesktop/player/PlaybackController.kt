package com.ugnbt.spotlabdesktop.player

import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.SpotlabHttp
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaybackStateDto
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The desktop counterpart to the Android client's `PlaybackService`: drives
 * [DesktopPlayer] off whatever [PlaybackRepository] says the server wants,
 * and never the other way round. No MediaSession/notification equivalent
 * needed here — this is a plain window app, not a background service.
 */
class PlaybackController(
    private val playback: PlaybackRepository,
    private val api: SpotlabApi,
    private val http: SpotlabHttp,
    scope: CoroutineScope,
) {
    val player = DesktopPlayer()

    /** See [StreamProxy]'s doc comment: VLC needs a plain local URL, mTLS and
     *  the bearer token attach through [http]'s OkHttpClient instead. */
    private val streamProxy = StreamProxy(client = { http.client }, upstreamUrl = api::streamUrl)

    private var lastTrackId: Long? = null

    /**
     * Set on libVLC's own `finished` callback (a native thread, hence
     * `@Volatile`), consumed by the next [apply]. Repeat "one" replays the
     * *same* track id, so `current.id != lastTrackId` alone can't detect
     * that a restart is needed — this flag is the precise signal instead of
     * an `isPlaying()` heuristic, which would also misfire on an ordinary
     * pause→resume (paused-but-not-yet-resumed looks identical to "just
     * finished" from the outside).
     */
    @Volatile
    private var pendingRestart = false

    init {
        player.bufferingListener = DesktopPlayer.BufferingListener { buffering ->
            playback.reportLocalBuffering(buffering)
        }
        player.endedListener = DesktopPlayer.EndedListener {
            pendingRestart = true
            playback.trackEnded()
        }
        scope.launch {
            playback.playback.collect(::apply)
        }
    }

    private fun apply(state: PlaybackStateDto?) {
        if (state == null || state.current == null || !state.activeDeviceIds.contains(playback.deviceId)) {
            if (lastTrackId != null) {
                player.stop()
                lastTrackId = null
            }
            return
        }

        val current = state.current
        val durationSeconds = current.duration.toDouble()

        if (current.id != lastTrackId || pendingRestart) {
            pendingRestart = false
            lastTrackId = current.id
            player.play(streamProxy.localUrl(current.id), emptyMap())
            player.seekTo(state.positionSeconds, durationSeconds)
        }

        if (state.isPlaying) player.resume() else player.pause()

        // Correct drift after a real seek/skip command, or after the server's
        // periodic re-broadcast — same tolerance the Android client's UI uses.
        val expected = PlaybackPosition.positionAt(state)
        val actual = player.currentPositionMs() / 1000.0
        if (abs(actual - expected) > 2.0) {
            player.seekTo(expected, durationSeconds)
        }
    }

    fun release() {
        player.release()
        streamProxy.stop()
    }
}
