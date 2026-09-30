package com.ugnbt.spotlabdesktop

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ugnbt.spotlabdesktop.di.AppContainer
import com.ugnbt.spotlabdesktop.ui.App

fun main() = application {
    val container = remember { AppContainer().also { it.start() } }

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
