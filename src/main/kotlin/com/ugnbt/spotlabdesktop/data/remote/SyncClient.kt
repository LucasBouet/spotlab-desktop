package com.ugnbt.spotlabdesktop.data.remote

import com.ugnbt.spotlabdesktop.data.remote.dto.DeviceDto
import com.ugnbt.spotlabdesktop.data.remote.dto.JamInviteDto
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaybackStateDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SyncSnapshotDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * The `GET /api/sync/stream` event stream. Holding it open is also what marks
 * this device online for the presence features.
 */
class SyncClient(private val http: SpotlabHttp, private val json: Json) {

    sealed interface Event {
        data object Opened : Event
        data class Snapshot(val payload: SyncSnapshotDto) : Event
        data class Playback(val state: PlaybackStateDto) : Event
        data class Devices(val devices: List<DeviceDto>) : Event
        data class Invites(val invites: List<JamInviteDto>) : Event
        data object Ping : Event

        /** [status] is the HTTP status when the stream was refused outright. */
        data class Closed(val cause: Throwable? = null, val status: Int? = null) : Event
    }

    fun events(deviceId: String): Flow<Event> = callbackFlow {
        val request = Request.Builder()
            .url(http.url("api/sync/stream", mapOf("deviceId" to deviceId)))
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                trySend(Event.Opened)
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                val event = when (type) {
                    "snapshot" -> decodeOrNull<SyncSnapshotDto>(data)?.let(Event::Snapshot)
                    "playback" -> decodeOrNull<PlaybackStateDto>(data)?.let(Event::Playback)
                    "devices" -> decodeOrNull<List<DeviceDto>>(data)?.let(Event::Devices)
                    "jam-invites" -> decodeOrNull<List<JamInviteDto>>(data)?.let(Event::Invites)
                    "ping" -> Event.Ping
                    else -> null
                }
                if (event != null) trySend(event)
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(Event.Closed())
                this@callbackFlow.close()
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                trySend(Event.Closed(t, response?.code))
                this@callbackFlow.close()
            }
        }

        val source = EventSources.createFactory(http.streamingClient)
            .newEventSource(request, listener)

        awaitClose { source.cancel() }
    }

    private inline fun <reified T> decodeOrNull(data: String): T? =
        runCatching { json.decodeFromString<T>(data) }.getOrNull()
}
