package com.mooscles.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A screen tall enough for every row of the tests' lists to be composed at once. */
internal val TALL: Dp = 2400.dp

/**
 * [content] [height] tall when given, from the window's top down (a centered taller frame would put
 * the top bar and the filters above the window, where nothing is displayed or touched).
 */
@Composable
internal fun TopAnchored(height: Dp?, content: @Composable () -> Unit) {
    if (height == null) {
        content()
        return
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .requiredHeight(height),
        ) { content() }
    }
}
