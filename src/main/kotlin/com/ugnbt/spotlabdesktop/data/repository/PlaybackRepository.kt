package com.ugnbt.spotlabdesktop.data.repository

import com.ugnbt.spotlabdesktop.data.local.SettingsStore
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.SyncClient
import com.ugnbt.spotlabdesktop.data.remote.dto.DeviceDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlayEventDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaybackStateDto
import com.ugnbt.spotlabdesktop.data.remote.dto.QueueItemDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SyncAction
import com.ugnbt.spotlabdesktop.data.remote.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ported from the Android client's PlaybackRepository — same reducer contract. */
internal fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex) return this
    if (fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun Flow<Boolean>.offlineAfterGrace(graceMs: Long): Flow<Boolean> = channelFlow {
    collectLatest { isConnected ->
        if (isConnected) {
            send(false)
        } else {
            delay(graceMs)
            send(true)
        }
    }
}

internal fun nextOutputDevices(
    active: List<String>,
    owned: Set<String>,
    target: String,
): List<String> {
    val mine = active.filter { it in owned }.distinct()
    return if (target in mine) mine - target else mine + target
}

internal fun shouldApplyPlayback(
    room: String?,
    appliedRevision: Long,
    state: PlaybackStateDto,
): Boolean = state.room != room || state.revision > appliedRevision

/**
 * Mirrors the server's canonical playback state and sends commands to change
 * it. Nothing here decides what plays: the client asks, the server decides
 * and broadcasts — [DesktopPlayer] only ever follows [playback].
 *
 * Trimmed from the Android client: no jam/social bits (out of scope for the
 * desktop v1), everything else is the same contract.
 */
class PlaybackRepository(
    private val api: SpotlabApi,
    private val sync: SyncClient,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val onSessionExpired: () -> Unit = {},
) {

    private val _playback = MutableStateFlow<PlaybackStateDto?>(null)
    val playback: StateFlow<PlaybackStateDto?> = _playback.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceDto>>(emptyList())
    val devices: StateFlow<List<DeviceDto>> = _devices.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    val offline: StateFlow<Boolean> = connected
        .offlineAfterGrace(OFFLINE_GRACE_MS)
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _pendingQueue = MutableStateFlow<List<QueueItemDto>?>(null)
    val pendingQueue: StateFlow<List<QueueItemDto>?> = _pendingQueue.asStateFlow()

    private val _pendingTrack = MutableStateFlow<QueueItemDto?>(null)

    val displayedCurrent: StateFlow<QueueItemDto?> = combine(_playback, _pendingTrack) { state, pending ->
        pending ?: state?.current
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val isLoadingTrack: StateFlow<Boolean> = combine(_playback, _pendingTrack) { state, pending ->
        pending != null && pending.id != state?.current?.id
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val loadingTrackId: StateFlow<Long?> = combine(displayedCurrent, isLoadingTrack) { current, loading ->
        current?.id.takeIf { loading }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val deviceId: String get() = settings.current.deviceId

    val isLocalOutput: StateFlow<Boolean> = playback
        .map { state -> state?.activeDeviceIds?.contains(deviceId) == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _localBuffering = MutableStateFlow(false)
    val localBuffering: StateFlow<Boolean> = _localBuffering.asStateFlow()

    fun reportLocalBuffering(buffering: Boolean) {
        _localBuffering.value = buffering
    }

    private var streamJob: Job? = null

    @Volatile
    private var lastEventAt = 0L

    private var appliedRevision = -1L
    private var room: String? = null

    @Volatile
    private var sessionExpired = false

    fun connect() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch { streamForever() }
    }

    fun reconnectNow() {
        val previous = streamJob
        streamJob = scope.launch {
            previous?.cancelAndJoin()
            streamForever()
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        _connected.value = false
        _playback.value = null
        _devices.value = emptyList()
        appliedRevision = -1L
        room = null
        sessionExpired = false
        _pendingQueue.value = null
    }

    private suspend fun streamForever() {
        var backoffMs = INITIAL_BACKOFF_MS
        while (currentCoroutineContext().isActive && !sessionExpired) {
            val id = settings.current.deviceId
            if (id.isBlank()) {
                delay(INITIAL_BACKOFF_MS)
                continue
            }
            val startedAt = System.currentTimeMillis()
            try {
                streamOnce(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Any stream failure has the same answer: reconnect.
            }
            _connected.value = false
            backoffMs = if (System.currentTimeMillis() - startedAt > STABLE_CONNECTION_MS) {
                INITIAL_BACKOFF_MS
            } else {
                (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
            delay(backoffMs)
        }
    }

    private suspend fun streamOnce(id: String) = withContext(Dispatchers.IO) {
        lastEventAt = System.currentTimeMillis()
        val collector = launch { runCatching { sync.events(id).collect(::handle) } }
        val watchdog = launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                if (System.currentTimeMillis() - lastEventAt > STALE_AFTER_MS) {
                    collector.cancel()
                    break
                }
            }
        }
        collector.join()
        watchdog.cancel()
    }

    private fun handle(event: SyncClient.Event) {
        lastEventAt = System.currentTimeMillis()
        when (event) {
            SyncClient.Event.Opened -> _connected.value = true
            is SyncClient.Event.Snapshot -> {
                _connected.value = true
                event.payload.playback?.let(::applyPlayback)
                _devices.value = event.payload.devices
            }
            is SyncClient.Event.Playback -> applyPlayback(event.state)
            is SyncClient.Event.Devices -> _devices.value = event.devices
            SyncClient.Event.Ping -> Unit
            is SyncClient.Event.Closed -> {
                _connected.value = false
                if (event.status == 401) {
                    sessionExpired = true
                    onSessionExpired()
                }
            }
        }
    }

    private fun applyPlayback(state: PlaybackStateDto) {
        if (!shouldApplyPlayback(room, appliedRevision, state)) return
        room = state.room
        appliedRevision = state.revision
        _playback.value = state
        _pendingQueue.value = null
        _pendingTrack.value = null
    }

    // --------------------------------------------------------- commands

    fun send(
        action: SyncAction,
        confirmation: String? = null,
        onRefused: () -> Unit = {},
    ) {
        scope.launch {
            val id = settings.current.deviceId
            if (id.isBlank()) return@launch
            runCatching { api.sendCommand(id, action) }
                .onSuccess { confirmation?.let(_notices::tryEmit) }
                .onFailure {
                    onRefused()
                    _errors.tryEmit(it.userMessage())
                }
        }
    }

    fun togglePlay() = send(SyncAction.TogglePlay)

    fun setPlaying(isPlaying: Boolean) = send(SyncAction.SetPlaying(isPlaying))

    fun seek(positionSeconds: Double) = send(SyncAction.Seek(positionSeconds))

    fun skipNext() = send(SyncAction.SkipNext)

    fun skipPrevious() = send(SyncAction.SkipPrevious)

    fun toggleShuffle() = send(SyncAction.ToggleShuffle)

    /** Called by [com.ugnbt.spotlabdesktop.player.PlaybackController] when
     *  the local player reaches the end of a track on its own — never for a
     *  manual skip, which stays [skipNext]. */
    fun trackEnded() = send(SyncAction.TrackEnded)

    fun setRepeat(mode: String) = send(SyncAction.SetRepeat(mode))

    /** off -> all -> one -> off, the same single-button cycle every other
     *  player uses. */
    fun cycleRepeat() {
        val next = when (_playback.value?.repeat) {
            "off" -> "all"
            "all" -> "one"
            else -> "off"
        }
        setRepeat(next)
    }

    fun playTrack(item: QueueItemDto) {
        _pendingTrack.value = item
        send(SyncAction.PlayTrack(item), onRefused = { _pendingTrack.value = null })
    }

    fun playContext(
        contextId: String,
        items: List<QueueItemDto>,
        startIndex: Int,
        shuffle: Boolean? = null,
    ) {
        items.getOrNull(startIndex)?.let { _pendingTrack.value = it }
        send(
            action = SyncAction.PlayContext(contextId, items, startIndex, shuffle),
            onRefused = { _pendingTrack.value = null },
        )
    }

    fun playFromQueue(uid: String) {
        _playback.value?.queue?.firstOrNull { it.uid == uid }?.let { _pendingTrack.value = it }
        send(SyncAction.PlayFromQueue(uid), onRefused = { _pendingTrack.value = null })
    }

    fun queuePlayNext(item: QueueItemDto) = send(
        action = SyncAction.QueuePlayNext(item),
        confirmation = "« ${item.title} » sera lu ensuite.",
    )

    fun queueAddToEnd(item: QueueItemDto) = send(
        action = SyncAction.QueueAddToEnd(item),
        confirmation = "« ${item.title} » ajouté à la file d'attente.",
    )

    fun removeFromQueue(uid: String) = send(SyncAction.RemoveFromQueue(uid))

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val queue = _playback.value?.queue ?: return
        if (fromIndex == toIndex) return
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return
        _pendingQueue.value = queue.moved(fromIndex, toIndex)
        send(
            action = SyncAction.ReorderQueue(fromIndex, toIndex),
            onRefused = { _pendingQueue.value = null },
        )
    }

    fun setActiveDevices(deviceIds: List<String>) =
        send(SyncAction.SetActiveDevices(deviceIds))

    fun playHere() = setActiveDevices(listOf(deviceId))

    fun toggleOutputDevice(targetDeviceId: String) = setActiveDevices(
        nextOutputDevices(
            active = _playback.value?.activeDeviceIds.orEmpty(),
            owned = _devices.value.mapTo(mutableSetOf()) { it.deviceId },
            target = targetDeviceId,
        ),
    )

    fun reportSystemFailure(message: String) {
        _errors.tryEmit(message)
    }

    suspend fun refreshDevices() {
        runCatching { api.devices() }.onSuccess { _devices.value = it }
    }

    fun renameDevice(deviceId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            runCatching { api.renameDevice(deviceId, trimmed) }
                .onSuccess {
                    if (deviceId == settings.current.deviceId) settings.setDeviceName(trimmed)
                    _notices.tryEmit("Appareil renommé « $trimmed ».")
                    refreshDevices()
                }
                .onFailure { _errors.tryEmit(it.userMessage()) }
        }
    }

    fun forgetDevice(deviceId: String) {
        scope.launch {
            runCatching { api.forgetDevice(deviceId) }
                .onSuccess {
                    _notices.tryEmit("Appareil oublié.")
                    refreshDevices()
                }
                .onFailure { _errors.tryEmit(it.userMessage()) }
        }
    }

    fun logPlay(item: QueueItemDto) {
        scope.launch {
            runCatching {
                api.logPlay(
                    PlayEventDto(
                        deezerTrackId = item.id,
                        title = item.title,
                        artistName = item.artist,
                        albumTitle = item.album,
                        albumCover = item.cover,
                        duration = item.duration,
                    ),
                )
            }
        }
    }

    fun prefetch(trackId: Long) {
        scope.launch { runCatching { api.prefetch(trackId) } }
    }

    private companion object {
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val STABLE_CONNECTION_MS = 30_000L
        const val WATCHDOG_TICK_MS = 5_000L
        const val OFFLINE_GRACE_MS = 6_000L
        const val STALE_AFTER_MS = 45_000L
    }
}
