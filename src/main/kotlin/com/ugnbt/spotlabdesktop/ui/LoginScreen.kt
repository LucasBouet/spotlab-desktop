package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.remote.userMessage
import com.ugnbt.spotlabdesktop.data.repository.AuthRepository
import com.ugnbt.spotlabdesktop.data.repository.SessionState
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    auth: AuthRepository,
    state: SessionState.SignedOut,
    modifier: Modifier = Modifier,
) {
    val config = state.config
    val siteName = config?.siteName ?: "Spotlab"
    val registrationEnabled = config?.registrationEnabled ?: false
    val activationEnabled = config?.activationEnabled ?: false
    val canCreateAccount = registrationEnabled || activationEnabled
    val activationRequired = activationEnabled && !registrationEnabled

    var registering by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var activationCode by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = email.isNotBlank() && password.length >= 8 &&
        (!registering || name.isNotBlank()) &&
        (!registering || !activationRequired || activationCode.isNotBlank())

    fun submit() {
        if (loading || !canSubmit) return
        loading = true
        error = null
        scope.launch {
            val result = when {
                !registering -> auth.signIn(email, password)
                activationCode.isNotBlank() -> auth.activate(activationCode, name, email, password)
                else -> auth.signUp(name, email, password)
            }
            loading = false
            error = result.exceptionOrNull()?.userMessage()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(siteName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (registering) "Créer un compte" else "Connexion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.message != null) {
            Spacer(Modifier.height(16.dp))
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { auth.retry() } }) { Text("Réessayer") }
        }

        Spacer(Modifier.height(24.dp))

        if (registering) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        if (registering && activationEnabled) {
            OutlinedTextField(
                value = activationCode,
                onValueChange = { activationCode = it; error = null },
                label = { Text(if (activationRequired) "Code d'activation" else "Code d'activation (optionnel)") },
                singleLine = true,
                modifier = Modifier.width(360.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("E-mail") },
            singleLine = true,
            modifier = Modifier.width(360.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = if (registering) {
                { Text("8 caractères minimum") }
            } else {
                null
            },
            modifier = Modifier.width(360.dp),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = ::submit, enabled = !loading && canSubmit, modifier = Modifier.width(360.dp)) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp).width(18.dp))
            } else {
                Text(if (registering) "Créer le compte" else "Se connecter")
            }
        }

        if (canCreateAccount) {
            TextButton(onClick = { registering = !registering; error = null }) {
                Text(if (registering) "J'ai déjà un compte" else "Créer un compte")
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { scope.launch { auth.forgetServer() } }) { Text("Changer de serveur") }
    }
}
