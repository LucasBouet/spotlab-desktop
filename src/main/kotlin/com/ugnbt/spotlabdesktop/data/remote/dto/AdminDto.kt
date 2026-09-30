package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String = "",
    val name: String? = null,
    val role: String = "USER",
    val createdAt: String? = null,
)

@Serializable
data class ListUsersResponseDto(val users: List<AdminUserDto> = emptyList())

@Serializable
data class AppSettingDefinitionDto(
    val key: String,
    val label: String,
    val description: String = "",
    val type: String = "string",
    val default: String = "",
)

@Serializable
data class SettingsResponseDto(
    val settings: Map<String, String> = emptyMap(),
    val definitions: List<AppSettingDefinitionDto> = emptyList(),
)
