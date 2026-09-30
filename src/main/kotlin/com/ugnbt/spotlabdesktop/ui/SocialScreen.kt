package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.FriendDto
import com.ugnbt.spotlabdesktop.data.remote.dto.FriendRequestDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SocialDataDto
import com.ugnbt.spotlabdesktop.data.remote.userMessage
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.ui.theme.SpotlabOnline
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun FriendDto.displayName(): String = name?.takeIf { it.isNotBlank() } ?: email

@Composable
fun SocialScreen(api: SpotlabApi, playback: PlaybackRepository, currentUserId: String, modifier: Modifier = Modifier) {
    var social by remember { mutableStateOf(SocialDataDto()) }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val jamInvites by playback.jamInvites.collectAsState()
    val jam by playback.jam.collectAsState()
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        runCatching { social = api.social() }
    }

    // Presence has no push event of its own — poll while the screen is open,
    // same contract as the Android/web clients.
    LaunchedEffect(Unit) {
        refresh()
        while (isActive) {
            delay(10_000)
            refresh()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Amis", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        val activeJam = jam
        if (activeJam != null) {
            val amHost = activeJam.members.firstOrNull { it.userId == currentUserId }?.isHost == true
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Jam en cours — ${activeJam.members.size} participant(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = playback::leaveJam) { Text("Quitter") }
                    if (amHost) {
                        TextButton(onClick = playback::stopJam) { Text("Arrêter pour tous") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        jamInvites.forEach { invite ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${invite.hostName.ifBlank { "Un ami" }} vous invite à une jam", modifier = Modifier.weight(1f))
                    TextButton(onClick = { playback.acceptJamInvite(invite.jamId) }) { Text("Rejoindre") }
                    TextButton(onClick = { playback.declineJamInvite(invite.jamId) }) { Text("Ignorer") }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Ajouter un ami par e-mail") },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val trimmed = email.trim()
                if (trimmed.isBlank()) return@Button
                scope.launch {
                    runCatching { api.sendFriendRequest(trimmed) }
                        .onSuccess { email = ""; refresh() }
                        .onFailure { error = it.userMessage() }
                }
            }) { Icon(Icons.Filled.GroupAdd, contentDescription = "Ajouter") }
        }
        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (social.incoming.isNotEmpty()) {
            SectionLabel("Demandes reçues")
            social.incoming.forEach { request ->
                RequestRow(request) {
                    Row {
                        IconButton(onClick = { scope.launch { runCatching { api.friendRequest(request.id, "accept") }; refresh() } }) {
                            Icon(Icons.Filled.Check, contentDescription = "Accepter", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { scope.launch { runCatching { api.friendRequest(request.id, "decline") }; refresh() } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Refuser")
                        }
                    }
                }
            }
        }

        if (social.outgoing.isNotEmpty()) {
            SectionLabel("Demandes envoyées")
            social.outgoing.forEach { request ->
                RequestRow(request) {
                    TextButton(onClick = { scope.launch { runCatching { api.friendRequest(request.id, "cancel") }; refresh() } }) { Text("Annuler") }
                }
            }
        }

        SectionLabel("Amis · ${social.friends.size}")
        LazyColumn {
            items(social.friends, key = { it.friendshipId }) { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (friend.activity.online) SpotlabOnline else MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(friend.displayName(), style = MaterialTheme.typography.bodyMedium)
                        val playing = friend.activity.track
                        if (friend.activity.isPlaying && playing != null) {
                            Text(
                                "${playing.title} · ${playing.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = {
                        scope.launch { playback.inviteToJam(friend.userId, friend.displayName()) }
                    }) { Text("Inviter en jam") }
                    IconButton(onClick = { scope.launch { runCatching { api.removeFriend(friend.friendshipId) }; refresh() } }) {
                        Icon(Icons.Filled.Close, contentDescription = "Retirer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RequestRow(request: FriendRequestDto, actions: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(request.name?.takeIf { it.isNotBlank() } ?: request.email, modifier = Modifier.weight(1f))
        actions()
    }
}
