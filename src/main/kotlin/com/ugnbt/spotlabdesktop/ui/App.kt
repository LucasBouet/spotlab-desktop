package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.data.repository.SessionState
import com.ugnbt.spotlabdesktop.di.AppContainer
import com.ugnbt.spotlabdesktop.ui.theme.SpotlabTheme

private enum class NavTab(val label: String) {
    Home("Accueil"),
    Search("Rechercher"),
    Playlists("Playlists"),
    Liked("Titres likés"),
    Settings("Réglages"),
}

@Composable
fun App(container: AppContainer) {
    SpotlabTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val session by container.auth.state.collectAsState()
            when (val state = session) {
                SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                SessionState.NoServer -> ServerSetupScreen(container.auth, container.clientCertStore)
                is SessionState.SignedOut -> LoginScreen(container.auth, state)
                is SessionState.SignedIn -> MainScreen(container)
            }
        }
    }
}

@Composable
private fun MainScreen(container: AppContainer) {
    var tab by remember { mutableStateOf(NavTab.Home) }
    var nowPlayingOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                NavRail(selected = tab, onSelect = { tab = it })
                Box(Modifier.weight(1f).fillMaxSize()) {
                    when (tab) {
                        NavTab.Home -> HomeScreen(container.api, container.playback)
                        NavTab.Search -> SearchScreen(container.api, container.playback, container.library)
                        NavTab.Playlists -> PlaylistsScreen(container.api, container.playback, container.library)
                        NavTab.Liked -> LikedScreen(container.api, container.playback, container.library)
                        NavTab.Settings -> SettingsScreen(container.auth, container.settings, container.clientCertStore, container.playback)
                    }
                }
            }
            PlayerBar(
                playback = container.playback,
                controller = container.playbackController,
                library = container.library,
                onExpand = { nowPlayingOpen = true },
            )
        }
        if (nowPlayingOpen) {
            NowPlayingScreen(
                playback = container.playback,
                lyricsRepo = container.lyrics,
                library = container.library,
                onBack = { nowPlayingOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A custom rail instead of Material3's stock [androidx.compose.material3.NavigationRail]
 *  — that component always packs its items against the top; this one centers
 *  them vertically in the available height, as requested. */
@Composable
private fun NavRail(selected: NavTab, onSelect: (NavTab) -> Unit) {
    Column(
        modifier = Modifier.fillMaxHeight().width(96.dp).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NavRailItem(Icons.Filled.Home, NavTab.Home.label, selected == NavTab.Home) { onSelect(NavTab.Home) }
        Spacer(Modifier.height(8.dp))
        NavRailItem(Icons.Filled.Search, NavTab.Search.label, selected == NavTab.Search) { onSelect(NavTab.Search) }
        Spacer(Modifier.height(8.dp))
        NavRailItem(Icons.Filled.QueueMusic, NavTab.Playlists.label, selected == NavTab.Playlists) { onSelect(NavTab.Playlists) }
        Spacer(Modifier.height(8.dp))
        NavRailItem(Icons.Filled.FavoriteBorder, NavTab.Liked.label, selected == NavTab.Liked) { onSelect(NavTab.Liked) }
        Spacer(Modifier.height(8.dp))
        NavRailItem(Icons.Filled.Settings, NavTab.Settings.label, selected == NavTab.Settings) { onSelect(NavTab.Settings) }
    }
}

@Composable
private fun NavRailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .height(32.dp).widthIn(min = 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
