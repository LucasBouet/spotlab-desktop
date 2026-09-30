package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

/** `GET /api/config` — the only unauthenticated endpoint besides `/api/activate`. */
@Serializable
data class ServerConfigDto(
    val siteName: String = "Spotlab",
    val registrationEnabled: Boolean = false,
    val activationEnabled: Boolean = false,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String = "",
    val name: String? = null,
    val role: String = "USER",
    val isAdmin: Boolean = false,
    val createdAt: String? = null,
)

/** `POST /api/auth/{login,register,refresh}` */
@Serializable
data class AuthResponseDto(
    val token: String,
    val expiresAt: String? = null,
    val user: UserDto,
)

/** `GET /api/auth/me` — the boot call, doubles as the token validity check. */
@Serializable
data class MeResponseDto(
    val user: UserDto,
    val siteName: String = "Spotlab",
    val expiresAt: String? = null,
)

/** Every failure from the API, with a message already written in French. */
@Serializable
data class ApiErrorDto(val error: String = "")
