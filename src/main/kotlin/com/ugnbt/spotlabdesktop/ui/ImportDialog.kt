package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.PlaylistImportEvent
import com.ugnbt.spotlabdesktop.data.remote.userMessage
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val DESTINATION_PLAYLIST = "playlist"
private const val DESTINATION_LIKED = "liked"

/** Port of the Android client's `PlaylistImportScreen`, as a dialog instead
 *  of a full screen — this desktop app has no navigation stack to push onto. */
@Composable
fun ImportPlaylistDialog(api: SpotlabApi, onDismiss: () -> Unit, onImported: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf(DESTINATION_PLAYLIST) }
    var name by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var fetched by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PlaylistImportEvent.Done?>(null) }
    val scope = rememberCoroutineScope()

    fun startImport() {
        if (importing || link.isBlank()) return
        importing = true
        error = null
        fetched = 0
        total = 0
        scope.launch {
            api.importDeezerPlaylist(link.trim(), destination, name.trim().ifBlank { null })
                .catch { failure -> importing = false; error = failure.userMessage() }
                .collect { event ->
                    when (event) {
                        is PlaylistImportEvent.Progress -> { fetched = event.fetched; total = event.total }
                        is PlaylistImportEvent.Done -> { importing = false; result = event }
                        is PlaylistImportEvent.Failed -> { importing = false; error = event.message }
                    }
                }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.width(420.dp)) {
            val done = result
            if (done != null) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (done.destination == DESTINATION_LIKED) {
                            "${done.trackCount} titre(s) ajouté(s) aux titres likés"
                        } else {
                            "${done.trackCount} titre(s) importé(s)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onImported, modifier = Modifier.fillMaxWidth()) { Text("Terminé") }
                }
            } else {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Importer depuis Deezer", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; error = null },
                        label = { Text("Lien ou identifiant de playlist Deezer") },
                        singleLine = true,
                        enabled = !importing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Destination", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        DestinationOption("Nouvelle playlist", destination == DESTINATION_PLAYLIST, !importing) { destination = DESTINATION_PLAYLIST }
                        DestinationOption("Titres likés", destination == DESTINATION_LIKED, !importing) { destination = DESTINATION_LIKED }
                    }
                    if (destination == DESTINATION_PLAYLIST) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nom (optionnel)") },
                            singleLine = true,
                            enabled = !importing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (importing) {
                        Spacer(Modifier.height(16.dp))
                        val progress = total.takeIf { it > 0 }?.let { fetched.toFloat() / it }
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("$fetched / $total titres", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss, enabled = !importing) { Text("Annuler") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = ::startImport, enabled = !importing && link.isNotBlank()) {
                            if (importing) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            } else {
                                Text("Importer")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
