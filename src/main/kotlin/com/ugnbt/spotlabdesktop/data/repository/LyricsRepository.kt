package com.ugnbt.spotlabdesktop.data.repository

import com.ugnbt.spotlabdesktop.data.local.LyricsOffsetStore
import com.ugnbt.spotlabdesktop.data.remote.ApiException
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.LyricsDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaybackStateDto
import com.ugnbt.spotlabdesktop.data.remote.dto.QueueItemDto
import com.ugnbt.spotlabdesktop.player.LyricLine
import com.ugnbt.spotlabdesktop.player.parseLrc
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the lyrics panel has to draw. */
sealed interface LyricsState {
    data object None : LyricsState
    data object Loading : LyricsState
    data class Synced(val lines: List<LyricLine>) : LyricsState
    data class Plain(val text: String) : LyricsState
    data class Unavailable(val reason: String) : LyricsState
}

internal fun clampLyricsOffset(seconds: Double): Double {
    val bounded = seconds.coerceIn(-MAX_OFFSET_SECONDS, MAX_OFFSET_SECONDS)
    return (bounded * 10).roundToLong() / 10.0
}

/**
 * Lyrics for whatever the server says is playing, plus the user's timing
 * correction — ported near-verbatim from the Android client, which has zero
 * platform dependency beyond [LyricsOffsetStore].
 */
class LyricsRepository(
    private val api: SpotlabApi,
    private val offsets: LyricsOffsetStore,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<LyricsState>(LyricsState.None)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private val _offset = MutableStateFlow(0.0)
    val offset: StateFlow<Double> = _offset.asStateFlow()

    private val cache = mutableMapOf<Long, LyricsState>()

    private var currentTrackId: Long? = null
    private var currentItem: QueueItemDto? = null

    fun follow(playback: StateFlow<PlaybackStateDto?>) {
        scope.launch {
            playback
                .map { it?.current }
                .distinctUntilChangedBy { it?.id }
                .collectLatest(::load)
        }
    }

    fun clear() {
        cache.clear()
        currentTrackId = null
        _state.value = LyricsState.None
        _offset.value = 0.0
    }

    fun nudgeOffset(direction: Int) {
        applyOffset(_offset.value + direction * OFFSET_STEP_SECONDS)
    }

    fun resetOffset() = applyOffset(0.0)

    private fun applyOffset(seconds: Double) {
        val trackId = currentTrackId ?: return
        val clamped = clampLyricsOffset(seconds)
        _offset.value = clamped
        scope.launch { offsets.setOffset(trackId, clamped) }
    }

    fun resync() {
        val item = currentItem ?: return
        scope.launch { refetchAndCache(item) }
    }

    private suspend fun load(item: QueueItemDto?) {
        currentTrackId = item?.id
        currentItem = item
        if (item == null) {
            _state.value = LyricsState.None
            _offset.value = 0.0
            return
        }

        _offset.value = offsets.offsetFor(item.id)

        cache[item.id]?.let { known ->
            _state.value = known
            return
        }
        if (item.title.isBlank() || item.artist.isBlank()) {
            _state.value = LyricsState.Unavailable(NO_METADATA)
            return
        }

        refetchAndCache(item)
    }

    private suspend fun refetchAndCache(item: QueueItemDto) {
        _state.value = LyricsState.Loading
        val resolved = fetch(item)
        if (resolved == null) {
            _state.value = LyricsState.Unavailable(FETCH_FAILED)
            return
        }
        cache[item.id] = resolved
        _state.value = resolved
    }

    private suspend fun fetch(item: QueueItemDto): LyricsState? = try {
        api.lyrics(
            track = item.title,
            artist = item.artist,
            album = item.album,
            durationSeconds = item.duration,
        ).toState()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        val status = (failure as? ApiException)?.status
        if (status == 404 || status == 400) LyricsState.Unavailable(NOT_FOUND) else null
    }

    private fun LyricsDto.toState(): LyricsState = when {
        instrumental -> LyricsState.Unavailable(INSTRUMENTAL)
        !syncedLyrics.isNullOrBlank() -> parseLrc(syncedLyrics).let { lines ->
            if (lines.isNotEmpty()) {
                LyricsState.Synced(lines)
            } else {
                plainLyrics?.takeIf { it.isNotBlank() }?.let(LyricsState::Plain)
                    ?: LyricsState.Unavailable(NOT_FOUND)
            }
        }
        !plainLyrics.isNullOrBlank() -> LyricsState.Plain(plainLyrics)
        else -> LyricsState.Unavailable(NOT_FOUND)
    }
}

internal const val OFFSET_STEP_SECONDS = 0.5
private const val MAX_OFFSET_SECONDS = 20.0

private const val NOT_FOUND = "Aucune parole trouvée pour ce titre."
private const val INSTRUMENTAL = "Titre instrumental."
private const val NO_METADATA = "Titre sans artiste : impossible de chercher les paroles."
private const val FETCH_FAILED = "Paroles indisponibles pour le moment."
