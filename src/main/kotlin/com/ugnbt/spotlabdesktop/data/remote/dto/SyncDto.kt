package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueueItemDto(
    val id: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val cover: String = "",
    val duration: Int = 0,
    val uid: String,
    val isManual: Boolean = false,
    val addedBy: AddedByDto? = null,
)

@Serializable
data class AddedByDto(val id: String = "", val name: String = "")

@Serializable
data class JamMemberDto(
    val userId: String,
    val name: String = "",
    val isHost: Boolean = false,
    val online: Boolean = false,
)

@Serializable
data class JamStateDto(
    val id: String,
    val hostId: String = "",
    val members: List<JamMemberDto> = emptyList(),
)

@Serializable
data class JamInviteDto(
    val jamId: String,
    val hostId: String = "",
    val hostName: String = "",
    val memberCount: Int = 0,
    val createdAt: String? = null,
)

/**
 * The state of playback, as owned by the server. Three fields drive every
 * client decision — see the Android client's identical DTO for the full
 * rationale (`data/remote/dto/SyncDto.kt` in Splotlab):
 *
 * - [activeDeviceIds]: play audio only if it contains our own device id.
 * - [revision]: monotonic per room; drop anything already applied.
 * - [room]: the user id in solo mode, the jam id in a jam.
 *
 * [positionSeconds] is an anchor, not a live value — see PlaybackPosition.
 */
@Serializable
data class PlaybackStateDto(
    val current: QueueItemDto? = null,
    val queue: List<QueueItemDto> = emptyList(),
    val history: List<QueueItemDto> = emptyList(),
    val contextTracks: List<QueueItemDto> = emptyList(),
    val activeContextId: String? = null,
    val shuffle: Boolean = false,
    /** "off", "all" or "one". */
    val repeat: String = "off",
    val isPlaying: Boolean = false,
    val positionSeconds: Double = 0.0,
    val positionUpdatedAt: String? = null,
    val activeDeviceIds: List<String> = emptyList(),
    val originDeviceId: String? = null,
    val revision: Long = 0,
    val room: String = "",
    val jam: JamStateDto? = null,
)

@Serializable
data class DeviceDto(
    val deviceId: String,
    val name: String = "",
    val platform: String = "",
    val online: Boolean = false,
    val lastSeenAt: String? = null,
)

@Serializable
data class DevicesResponseDto(val devices: List<DeviceDto> = emptyList())

@Serializable
data class DeviceResponseDto(val device: DeviceDto? = null)

/** Body of `POST /api/devices/register`. */
@Serializable
data class RegisterDeviceDto(
    val deviceId: String,
    val name: String,
    val platform: String,
)

/** The `snapshot` SSE event, sent once on connect. */
@Serializable
data class SyncSnapshotDto(
    val playback: PlaybackStateDto? = null,
    val devices: List<DeviceDto> = emptyList(),
    val jamInvites: List<JamInviteDto> = emptyList(),
)

/**
 * Everything a device may POST to `/api/sync/command`. The server runs queue
 * actions through the same reducer as every other client, so they can't diverge.
 */
@Serializable
sealed interface SyncAction {

    @Serializable
    @SerialName("TOGGLE_PLAY")
    data object TogglePlay : SyncAction

    @Serializable
    @SerialName("SET_PLAYING")
    data class SetPlaying(val isPlaying: Boolean) : SyncAction

    @Serializable
    @SerialName("SEEK")
    data class Seek(val positionSeconds: Double) : SyncAction

    @Serializable
    @SerialName("SET_ACTIVE_DEVICES")
    data class SetActiveDevices(val deviceIds: List<String>) : SyncAction

    @Serializable
    @SerialName("PLAY_TRACK")
    data class PlayTrack(val item: QueueItemDto) : SyncAction

    @Serializable
    @SerialName("PLAY_CONTEXT")
    data class PlayContext(
        val contextId: String,
        val items: List<QueueItemDto>,
        val startIndex: Int,
        val shuffleOverride: Boolean? = null,
    ) : SyncAction

    @Serializable
    @SerialName("SKIP_NEXT")
    data object SkipNext : SyncAction

    @Serializable
    @SerialName("SKIP_PREVIOUS")
    data object SkipPrevious : SyncAction

    /** Sent by the local player itself when a track finishes on its own —
     *  distinct from [SkipNext] (a manual "next" press) because Repeat
     *  "one" only replays on this one, never on a deliberate skip. */
    @Serializable
    @SerialName("TRACK_ENDED")
    data object TrackEnded : SyncAction

    @Serializable
    @SerialName("SET_REPEAT")
    data class SetRepeat(val repeat: String) : SyncAction

    @Serializable
    @SerialName("TOGGLE_SHUFFLE")
    data object ToggleShuffle : SyncAction

    @Serializable
    @SerialName("PLAY_FROM_QUEUE")
    data class PlayFromQueue(val uid: String) : SyncAction

    @Serializable
    @SerialName("QUEUE_PLAY_NEXT")
    data class QueuePlayNext(val item: QueueItemDto) : SyncAction

    @Serializable
    @SerialName("QUEUE_ADD_TO_END")
    data class QueueAddToEnd(val item: QueueItemDto) : SyncAction

    @Serializable
    @SerialName("REMOVE_FROM_QUEUE")
    data class RemoveFromQueue(val uid: String) : SyncAction

    @Serializable
    @SerialName("REORDER_QUEUE")
    data class ReorderQueue(val fromIndex: Int, val toIndex: Int) : SyncAction
}

/** Body of `POST /api/sync/command`. */
@Serializable
data class SyncCommandDto(val deviceId: String, val action: SyncAction)

@Serializable
data class SyncCommandResultDto(val ok: Boolean = false, val revision: Long = 0)
