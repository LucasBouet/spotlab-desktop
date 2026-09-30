package com.ugnbt.spotlabdesktop.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FriendActivityDto(
    val online: Boolean = false,
    val isPlaying: Boolean = false,
    val track: FriendTrackDto? = null,
)

@Serializable
data class FriendTrackDto(
    val title: String = "",
    val artist: String = "",
    val cover: String = "",
)

@Serializable
data class FriendDto(
    val friendshipId: String,
    val userId: String,
    val name: String? = null,
    val email: String = "",
    val activity: FriendActivityDto = FriendActivityDto(),
)

@Serializable
data class FriendRequestDto(
    val id: String,
    val name: String? = null,
    val email: String = "",
    val createdAt: String? = null,
)

/** The whole friend graph in one read: `GET /api/social`. */
@Serializable
data class SocialDataDto(
    val friends: List<FriendDto> = emptyList(),
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
)

@Serializable
data class FriendActivityUpdateDto(
    val userId: String,
    val activity: FriendActivityDto = FriendActivityDto(),
)

/** `GET /api/friends/activity` — presence only, cheap enough to poll. */
@Serializable
data class FriendActivitiesDto(
    val activities: List<FriendActivityUpdateDto> = emptyList(),
)

@Serializable
data class SocialMessageDto(val message: String = "")

/** Body of `POST /api/jam` — the single entry point for all five membership
 *  operations, selected by [op] (`invite`, `accept`, `decline`, `leave`, `stop`). */
@Serializable
data class JamOpDto(
    val op: String,
    val friendUserId: String? = null,
    val jamId: String? = null,
    val deviceId: String? = null,
)

@Serializable
data class JamOpResultDto(val ok: Boolean = false, val jamId: String? = null)
