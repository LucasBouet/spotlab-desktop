package com.ugnbt.spotlabdesktop.di

import com.ugnbt.spotlabdesktop.data.local.ClientCertStore
import com.ugnbt.spotlabdesktop.data.local.LyricsOffsetStore
import com.ugnbt.spotlabdesktop.data.local.SettingsStore
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.SpotlabHttp
import com.ugnbt.spotlabdesktop.data.remote.SpotlabJson
import com.ugnbt.spotlabdesktop.data.remote.SyncClient
import com.ugnbt.spotlabdesktop.data.repository.AuthRepository
import com.ugnbt.spotlabdesktop.data.repository.LibraryState
import com.ugnbt.spotlabdesktop.data.repository.LyricsRepository
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.data.repository.SessionState
import com.ugnbt.spotlabdesktop.player.PlaybackController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency graph, process-wide singleton — the desktop counterpart
 * to the Android client's `di/AppContainer.kt`. No Android Context: config
 * lives under `~/.config/spotlab`.
 */
class AppContainer {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val configDir: File = File(System.getProperty("user.home"), ".config/spotlab")

    val json = SpotlabJson

    val settings = SettingsStore(configDir)
    val clientCertStore = ClientCertStore(configDir)
    val lyricsOffsets = LyricsOffsetStore(configDir)
    val http = SpotlabHttp(settings::current, clientCertStore::loadKeyManagers)
    val api = SpotlabApi(http, json)
    val sync = SyncClient(http, json)

    val auth = AuthRepository(api, settings, appScope, defaultDeviceName())
    val playback = PlaybackRepository(
        api = api,
        sync = sync,
        settings = settings,
        scope = appScope,
        onSessionExpired = { auth.onUnauthorized() },
    )
    val playbackController = PlaybackController(playback, api, http, settings, configDir, appScope)
    val library = LibraryState(api, appScope)
    val lyrics = LyricsRepository(api, lyricsOffsets, appScope)

    private var started = false

    fun start() {
        if (started) return
        started = true

        api.onUnauthorized = { auth.onUnauthorized() }

        appScope.launch {
            settings.prime(defaultDeviceName())
            auth.bootstrap()
        }

        appScope.launch {
            auth.state.collect { state ->
                if (state is SessionState.SignedIn) {
                    playback.connect()
                    library.refresh()
                } else {
                    playback.disconnect()
                    library.clear()
                    lyrics.clear()
                }
            }
        }

        lyrics.follow(playback.playback)
    }

    fun shutdown() {
        playbackController.release()
    }

    private fun defaultDeviceName(): String =
        System.getProperty("user.name")?.takeIf { it.isNotBlank() }?.let { "Bureau de $it" } ?: "Bureau"
}
