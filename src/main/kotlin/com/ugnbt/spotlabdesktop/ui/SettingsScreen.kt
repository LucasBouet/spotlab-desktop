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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.ugnbt.spotlabdesktop.data.local.ClientCertStore
import com.ugnbt.spotlabdesktop.data.local.SettingsStore
import com.ugnbt.spotlabdesktop.data.remote.dto.DeviceDto
import com.ugnbt.spotlabdesktop.data.repository.AuthRepository
import com.ugnbt.spotlabdesktop.data.repository.PlaybackRepository
import com.ugnbt.spotlabdesktop.ui.theme.SpotlabOnline
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    auth: AuthRepository,
    settings: SettingsStore,
    certStore: ClientCertStore,
    playback: PlaybackRepository,
    modifier: Modifier = Modifier,
) {
    val current = settings.current
    var deviceName by remember { mutableStateOf(current.deviceName) }
    var enrollCode by remember { mutableStateOf("") }
    var enrollError by remember { mutableStateOf<String?>(null) }
    var enrollOk by remember { mutableStateOf(certStore.hasEnrolledCert()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { playback.refreshDevices() }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text("Réglages", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))

        Text("Serveur", style = MaterialTheme.typography.titleMedium)
        Text(current.baseUrl.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { scope.launch { auth.forgetServer() } }) { Text("Changer de serveur") }

        Spacer(Modifier.height(24.dp))
        Text("Appareil", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Nom de cet appareil") },
            singleLine = true,
            modifier = Modifier.width(320.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                settings.setDeviceName(deviceName)
                auth.registerDevice()
                playback.refreshDevices()
            }
        }) { Text("Enregistrer") }

        Spacer(Modifier.height(24.dp))
        Text("Appareils connectés", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val devices by playback.devices.collectAsState()
        devices.forEach { device ->
            DeviceRow(
                device = device,
                isThisDevice = device.deviceId == current.deviceId,
                onRename = { name -> playback.renameDevice(device.deviceId, name) },
                onForget = { playback.forgetDevice(device.deviceId) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Certificat mTLS", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nécessaire uniquement pour un serveur derrière nginx avec mTLS " +
                "(le domaine public) — inutile sur une adresse locale. Collez le " +
                "code généré par « cmd/mtls-ca -issue » sur le serveur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (enrollOk) {
            Text("Certificat enrôlé.", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { certStore.clear(); enrollOk = false }) { Text("Oublier le certificat") }
        } else {
            OutlinedTextField(
                value = enrollCode,
                onValueChange = { enrollCode = it; enrollError = null },
                label = { Text("Code d'enrôlement") },
                singleLine = true,
                isError = enrollError != null,
                modifier = Modifier.fillMaxWidth().width(480.dp),
            )
            if (enrollError != null) {
                Text(enrollError.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val result = runCatching { certStore.enroll(enrollCode) }
                result.onSuccess { enrollOk = true; enrollError = null }
                    .onFailure { enrollError = it.message ?: "Code invalide." }
            }) { Text("Valider") }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { scope.launch { auth.signOut() } }) { Text("Se déconnecter") }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceDto,
    isThisDevice: Boolean,
    onRename: (String) -> Unit,
    onForget: () -> Unit,
) {
    var editing by remember(device.deviceId) { mutableStateOf(false) }
    var name by remember(device.deviceId) { mutableStateOf(device.name) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (device.online) SpotlabOnline else MaterialTheme.colorScheme.outline))

        if (editing) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )
            IconButton(onClick = { onRename(name); editing = false }) {
                Icon(Icons.Filled.Edit, contentDescription = "Valider")
            }
        } else {
            Column(Modifier.weight(1f)) {
                Text(device.name + if (isThisDevice) " (cet appareil)" else "", style = MaterialTheme.typography.bodyMedium)
                Text(device.platform, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Renommer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Filled.Close, contentDescription = "Oublier", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
