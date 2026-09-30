package com.ugnbt.spotlabdesktop.data.repository

import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.ui.model.UiTrack
import com.ugnbt.spotlabdesktop.ui.model.toMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Just the liked-track ids, shared across every screen so a heart toggled in
 *  the player or in a search result stays in sync everywhere else. */
class LibraryState(private val api: SpotlabApi, private val scope: CoroutineScope) {

    private val _likedIds = MutableStateFlow<Set<Long>>(emptySet())
    val likedIds: StateFlow<Set<Long>> = _likedIds.asStateFlow()

    fun refresh() {
        scope.launch {
            runCatching { api.likedTrackIds() }.onSuccess { _likedIds.value = it }
        }
    }

    fun clear() {
        _likedIds.value = emptySet()
    }

    fun toggle(track: UiTrack) {
        val liked = track.id in _likedIds.value
        // Optimistic: the heart should flip the instant it's tapped.
        _likedIds.update { if (liked) it - track.id else it + track.id }
        scope.launch {
            val result = if (liked) runCatching { api.unlike(track.id) } else runCatching { api.like(track.id, track.toMetadata()) }
            result.onFailure {
                _likedIds.update { ids -> if (liked) ids + track.id else ids - track.id }
            }
        }
    }
}
