package com.ugnbt.spotlabdesktop

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import com.ugnbt.spotlabdesktop.di.AppContainer
import com.ugnbt.spotlabdesktop.ui.App
import java.io.File

/** 200MB, explicit — same reasoning as the Android client: Coil's own
 *  default disk cache size ("2% of free disk space") is unpredictable,
 *  which is exactly what a "vider le cache images" button in Settings needs
 *  to not be. */
const val IMAGE_CACHE_MAX_BYTES = 200L * 1024 * 1024

fun main() = application {
    val container = remember { AppContainer().also { it.start() } }
    remember {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(container.configDir, "image_cache"))
                        .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                        .build()
                }
                .build()
        }
    }

    Window(
        onCloseRequest = {
            container.shutdown()
            exitApplication()
        },
        title = "Spotlab",
        state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
    ) {
        App(container)
    }
}
