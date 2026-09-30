package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.AdminUserDto
import com.ugnbt.spotlabdesktop.data.remote.dto.SettingsResponseDto
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(api: SpotlabApi, currentUserId: String, modifier: Modifier = Modifier) {
    var users by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }
    var settings by remember { mutableStateOf<SettingsResponseDto?>(null) }
    var siteName by remember { mutableStateOf("") }
    var registrationEnabled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refreshUsers() {
        runCatching { users = api.adminUsers() }
    }

    LaunchedEffect(Unit) {
        refreshUsers()
        runCatching { api.adminSettings() }.onSuccess {
            settings = it
            siteName = it.settings["site_name"].orEmpty()
            registrationEnabled = it.settings["registration_enabled"] == "true"
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Administration", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("Réglages du site", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = siteName,
            onValueChange = { siteName = it },
            label = { Text("Nom du site") },
            singleLine = true,
            modifier = Modifier.width(320.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = registrationEnabled, onCheckedChange = { registrationEnabled = it })
            Spacer(Modifier.width(8.dp))
            Text("Inscription ouverte")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                runCatching { api.adminUpdateSettings(siteName = siteName, registrationEnabled = registrationEnabled) }
            }
        }) { Text("Enregistrer") }

        Spacer(Modifier.height(24.dp))
        Text("Utilisateurs · ${users.size}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(users, key = { it.id }) { user ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(user.name?.takeIf { it.isNotBlank() } ?: user.email, style = MaterialTheme.typography.bodyMedium)
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val isSelf = user.id == currentUserId
                    TextButton(
                        enabled = !isSelf,
                        onClick = {
                            val nextRole = if (user.role == "ADMIN") "USER" else "ADMIN"
                            scope.launch {
                                runCatching { api.adminSetUserRole(user.id, nextRole) }
                                refreshUsers()
                            }
                        },
                    ) { Text(user.role) }
                    IconButton(
                        enabled = !isSelf,
                        onClick = {
                            scope.launch {
                                runCatching { api.adminDeleteUser(user.id) }
                                refreshUsers()
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
