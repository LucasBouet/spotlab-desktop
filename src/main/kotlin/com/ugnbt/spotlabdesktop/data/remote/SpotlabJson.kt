package com.ugnbt.spotlabdesktop.data.remote

import kotlinx.serialization.json.Json

/**
 * - `ignoreUnknownKeys`: the server may add fields; crashing on them would be
 *   worse than ignoring them.
 * - `encodeDefaults`: `uid` and `isManual` must be sent even when they equal
 *   their defaults, or the server can't address a queue entry.
 * - `explicitNulls = false`: `addedBy` has to be *absent* during solo playback,
 *   not null.
 */
val SpotlabJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
