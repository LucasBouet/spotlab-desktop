package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ugnbt.spotlabdesktop.data.remote.SpotlabApi
import com.ugnbt.spotlabdesktop.data.remote.dto.ListeningStatsDto
import com.ugnbt.spotlabdesktop.data.remote.dto.StatEntryDto

@Composable
fun StatsScreen(api: SpotlabApi, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf<ListeningStatsDto?>(null) }
    LaunchedEffect(Unit) { runCatching { stats = api.stats() } }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Statistiques", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        val current = stats ?: return@Column
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatTotal("Titres écoutés", current.totalPlays.toString())
            StatTotal("Temps d'écoute", formatListeningTime(current.totalSeconds))
        }
        Spacer(Modifier.height(24.dp))

        StatSection("Titres les plus écoutés", current.topTracks)
        StatSection("Albums les plus écoutés", current.topAlbums)
        StatSection("Genres préférés", current.topGenres)
    }
}

@Composable
private fun StatTotal(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatSection(title: String, entries: List<StatEntryDto>) {
    if (entries.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    entries.forEachIndexed { index, entry ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (entry.cover != null) {
                AsyncImage(
                    model = entry.cover,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.padding(end = 10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.bodyMedium)
                entry.sublabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("${entry.count}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(20.dp))
}

private fun formatListeningTime(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
}
