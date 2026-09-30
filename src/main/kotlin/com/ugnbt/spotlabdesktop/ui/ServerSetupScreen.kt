package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.local.ClientCertStore
import com.ugnbt.spotlabdesktop.data.remote.userMessage
import com.ugnbt.spotlabdesktop.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun ServerSetupScreen(auth: AuthRepository, certStore: ClientCertStore, modifier: Modifier = Modifier) {
    var url by remember { mutableStateOf("") }
    var enrollCode by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var certEnrolled by remember { mutableStateOf(certStore.hasEnrolledCert()) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (loading || url.isBlank()) return
        // Enroll first: a server behind nginx's mTLS gate refuses the very
        // first probe below (400) without a client certificate already
        // attached, so this has to happen before useServer, not in Settings
        // (which only exists once signed in — too late for this server).
        if (enrollCode.isNotBlank()) {
            val result = runCatching { certStore.enroll(enrollCode) }
            if (result.isFailure) {
                error = result.exceptionOrNull()?.message ?: "Code d'enrôlement invalide."
                return
            }
            certEnrolled = true
        }
        loading = true
        error = null
        scope.launch {
            val result = auth.useServer(url)
            loading = false
            error = result.exceptionOrNull()?.userMessage()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Spotlab", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Indiquez l'adresse de votre serveur.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Adresse du serveur") },
            placeholder = { Text("192.168.1.20:3000") },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.width(360.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "http:// est ajouté si vous l'omettez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        if (certEnrolled) {
            Text(
                "Certificat mTLS enrôlé sur cet appareil.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedTextField(
                value = enrollCode,
                onValueChange = { enrollCode = it; error = null },
                label = { Text("Code d'enrôlement mTLS (si le serveur en demande un)") },
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Nécessaire pour un serveur derrière nginx en mTLS (ex. spotlab.ugnbt.com), " +
                    "généré côté serveur par « mtls-ca -issue ». Laissez vide sur un LAN sans mTLS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = ::submit, enabled = !loading && url.isNotBlank(), modifier = Modifier.width(360.dp)) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
            } else {
                Text("Se connecter")
            }
        }
    }
}
