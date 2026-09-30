package com.ugnbt.spotlabdesktop.player

/** One timed line of an LRC file. */
data class LyricLine(val timeSeconds: Double, val text: String)

/** Leading `[mm:ss.xx]` timestamps — ported verbatim from the Android client's
 *  `player/Lrc.kt`, identical to the server's own parser so every client
 *  highlights the same line at the same moment. */
private val TIMESTAMP = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?]""")

fun parseLrc(lrc: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    for (rawLine in lrc.split('\n')) {
        val stamps = TIMESTAMP.findAll(rawLine).toList()
        if (stamps.isEmpty()) continue
        val text = TIMESTAMP.replace(rawLine, "").trim()
        for (stamp in stamps) {
            lines += LyricLine(timeSeconds = stamp.seconds(), text = text)
        }
    }
    return lines.sortedBy { it.timeSeconds }
}

private fun MatchResult.seconds(): Double {
    val minutes = groupValues[1].toInt()
    val seconds = groupValues[2].toInt()
    val fraction = groupValues[3]
        .takeIf { it.isNotEmpty() }
        ?.padEnd(3, '0')
        ?.toInt()
        ?.div(1000.0)
        ?: 0.0
    return minutes * 60 + seconds + fraction
}

/** The line to highlight at [atSeconds]. [lines] must be sorted, which
 *  [parseLrc] guarantees. */
fun activeLyricIndex(lines: List<LyricLine>, atSeconds: Double): Int {
    var active = -1
    for (index in lines.indices) {
        if (lines[index].timeSeconds > atSeconds) break
        active = index
    }
    return active
}
