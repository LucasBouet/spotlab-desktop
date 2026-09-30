package com.ugnbt.spotlabdesktop.player

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.factory.MediaPlayerFactory

/**
 * Wraps VLCJ (JVM bindings for libVLC). Chosen over `javafx.scene.media`
 * because the stream needs a custom `Authorization` header and seeking, both
 * of which libVLC handles natively and JavaFX's media codecs don't reliably
 * — see the plan's §4. Requires VLC installed on the machine (`libvlc`).
 *
 * Deliberately dumb, same as the Android client's PlaybackService: it never
 * decides what plays, it only renders whatever URL it's told to and reports
 * buffering/position back up — [com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository]
 * owns the actual decision.
 */
class DesktopPlayer {

    fun interface BufferingListener {
        fun onBufferingChanged(buffering: Boolean)
    }

    fun interface EndedListener {
        fun onEnded()
    }

    var bufferingListener: BufferingListener? = null

    /** Fired when libVLC reaches the end of the current media on its own —
     *  never for [stop] or starting a new [play]. */
    var endedListener: EndedListener? = null

    // Audio only, no player UI of our own to embed a video surface into —
    // without `--no-video` libVLC still opens its own top-level window the
    // moment a track has (or looks like it has) a video track.
    private val factory = MediaPlayerFactory("--no-video", "--intf", "dummy")
    private val player: MediaPlayer = factory.mediaPlayers().newMediaPlayer()

    private var currentUrl: String? = null

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                bufferingListener?.onBufferingChanged(newCache < 100f)
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                bufferingListener?.onBufferingChanged(false)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                endedListener?.onEnded()
            }
        })
    }

    /** Starts a new URL from the top. No-ops if it's already the current one. */
    fun play(url: String, headers: Map<String, String>) {
        if (url == currentUrl && player.status().isPlaying) return
        currentUrl = url
        val authHeader = headers["Authorization"]
        // vlcj passes extra libvlc "media options" as `:option=value` strings;
        // http-header-fields lets us attach the bearer token to the request
        // libVLC itself makes, since it — not OkHttp — fetches this URL.
        val options = buildList {
            add(":no-video")
            if (authHeader != null) add(":http-header-fields=Authorization: $authHeader")
        }
        player.media().play(url, *options.toTypedArray())
    }

    fun pause() {
        player.controls().setPause(true)
    }

    fun resume() {
        player.controls().setPause(false)
    }

    /** [positionSeconds] as libVLC wants it: a 0f–1f fraction of total duration. */
    fun seekTo(positionSeconds: Double, durationSeconds: Double) {
        if (durationSeconds <= 0) return
        player.controls().setPosition((positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f))
    }

    fun setVolume(percent: Int) {
        player.audio().setVolume(percent.coerceIn(0, 100))
    }

    fun currentPositionMs(): Long = player.status().time()

    fun isPlaying(): Boolean = player.status().isPlaying

    fun stop() {
        currentUrl = null
        player.controls().stop()
    }

    fun release() {
        player.release()
        factory.release()
    }
}
