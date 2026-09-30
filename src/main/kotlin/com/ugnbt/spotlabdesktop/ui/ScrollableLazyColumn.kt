package com.ugnbt.spotlabdesktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A [LazyColumn] with a draggable vertical scrollbar — desktop users expect
 * one on any list long enough to scroll (a big playlist, the liked-tracks
 * library), and there's no built-in indicator otherwise. A drop-in
 * replacement: same [content] signature as `LazyColumn` itself.
 */
@Composable
fun ScrollableLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier = modifier) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), content = content)
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(state),
        )
    }
}
