package com.ugnbt.spotlabdesktop.data.repository

import com.ugnbt.spotlabdesktop.data.local.SettingsStore
import com.ugnbt.spotlabdesktop.data.remote.ApiException
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.ServerConfigDto
import com.ugnbt.spotlabdesktop.data.remote.dto.UserDto
import com.ugnbt.spotlabdesktop.data.remote.normalizeServerUrl
import com.ugnbt.spotlabdesktop.data.remote.userMessage
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState

    /** First launch, or the user cleared the server: ask for an address. */
    data object NoServer : SessionState

    /** [config] is null when the server couldn't be reached; [message] says why. */
    data class SignedOut(
        val config: ServerConfigDto? = null,
        val message: String? = null,
    ) : SessionState

    data class SignedIn(val user: UserDto, val siteName: String) : SessionState
}

/**
 * Whether a token whose life ends at [expiresAt] should be traded for a new one.
 */
internal fun shouldRenewToken(expiresAt: String?, now: Instant, window: Duration): Boolean {
    val deadline = expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    return !now.plus(window).isBefore(deadline)
}

/**
 * Owns the session: which server we talk to, whether we hold a valid token, and
 * this install's registration with the sync engine.
 *
 * Unlike the Android client, there is no mandatory "enroll a certificate
 * first" gate — a LAN server needs none at all, and the certificate is only
 * ever attached opportunistically by SpotlabHttp when one has been enrolled
 * (see ClientCertStore). Enrollment lives in Settings instead, for whoever
 * points this client at the public mTLS-protected domain.
 */
class AuthRepository(
    private val api: SpotlabApi,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val fallbackDeviceName: String,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    suspend fun bootstrap() {
        val stored = settings.current
        when {
            !stored.hasServer -> _state.value = SessionState.NoServer
            !stored.hasSession -> _state.value = SessionState.SignedOut(probeConfig())
            else -> restoreSession()
        }
    }

    suspend fun retry() = bootstrap()

    private suspend fun restoreSession() {
        try {
            val me = api.me()
            renewTokenIfExpiringSoon(me.expiresAt)
            registerDevice()
            _state.value = SessionState.SignedIn(me.user, me.siteName)
        } catch (failure: ApiException) {
            if (failure.status == 401) settings.clearSession()
            _state.value = SessionState.SignedOut(
                config = probeConfig(),
                message = if (failure.status == 401) null else failure.message,
            )
        } catch (offline: IOException) {
            _state.value = SessionState.SignedOut(message = offline.userMessage())
        }
    }

    suspend fun useServer(rawUrl: String): Result<ServerConfigDto> {
        val normalized = normalizeServerUrl(rawUrl)
            ?: return Result.failure(ApiException(0, "Adresse du serveur invalide."))
        return runCatching {
            val config = api.probeConfig(normalized)
            settings.setBaseUrl(normalized)
            settings.clearSession()
            _state.value = SessionState.SignedOut(config)
            config
        }
    }

    suspend fun forgetServer() {
        runCatching { api.logout() }
        settings.clearSession()
        settings.setBaseUrl("")
        _state.value = SessionState.NoServer
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val response = api.login(email.trim(), password)
        settings.setSession(response.token, response.expiresAt)
        finishSignIn(response.user)
    }

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> = runCatching {
        val response = api.register(name.trim(), email.trim(), password)
        settings.setSession(response.token, response.expiresAt)
        finishSignIn(response.user)
    }

    suspend fun activate(code: String, name: String, email: String, password: String): Result<Unit> =
        runCatching {
            val response = api.activate(code.trim(), name.trim(), email.trim(), password)
            settings.setSession(response.token, response.expiresAt)
            finishSignIn(response.user)
        }

    private suspend fun finishSignIn(user: UserDto) {
        registerDevice()
        val siteName = runCatching { api.me().siteName }.getOrNull() ?: "Spotlab"
        _state.value = SessionState.SignedIn(user, siteName)
    }

    suspend fun signOut() {
        runCatching { api.logout() }
        settings.clearSession()
        _state.value = SessionState.SignedOut(probeConfig())
    }

    /** Called from the HTTP layer when an authenticated call comes back 401. */
    fun onUnauthorized() {
        scope.launch {
            if (_state.value is SessionState.SignedIn) {
                settings.clearSession()
                _state.value = SessionState.SignedOut(
                    config = probeConfig(),
                    message = "Session expirée. Reconnectez-vous.",
                )
            }
        }
    }

    private suspend fun renewTokenIfExpiringSoon(expiresAt: String?) {
        if (!shouldRenewToken(expiresAt, Instant.now(), RENEW_WINDOW)) return
        runCatching { api.refresh() }
            .onSuccess { settings.setSession(it.token, it.expiresAt) }
    }

    /** Re-registered on every start: the server only sets `name` on creation. */
    suspend fun registerDevice() {
        val stored = settings.current
        if (stored.deviceId.isBlank()) return
        runCatching {
            api.registerDevice(
                deviceId = stored.deviceId,
                name = stored.deviceName.ifBlank { fallbackDeviceName },
                platform = "Bureau",
            )
        }
    }

    private suspend fun probeConfig(): ServerConfigDto? = runCatching { api.config() }.getOrNull()

    private companion object {
        /** Renew once the token has less than this left. */
        val RENEW_WINDOW: Duration = Duration.ofDays(5)
    }
}
