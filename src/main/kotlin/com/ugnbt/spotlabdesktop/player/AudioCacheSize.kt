package com.ugnbt.spotlabdesktop.player

/** Presets for the audio disk cache's size cap — same steps as the Android
 *  client's equivalent, see [com.ugnbt.spotlabdesktop.ui.SettingsScreen]. */
enum class AudioCacheSize(val bytes: Long, val label: String) {
    MB250(250L * 1024 * 1024, "250 Mo"),
    MB500(500L * 1024 * 1024, "500 Mo"),
    GB1(1024L * 1024 * 1024, "1 Go"),
    GB2(2L * 1024 * 1024 * 1024, "2 Go"),
    ;

    companion object {
        val Default = GB1
    }
}
