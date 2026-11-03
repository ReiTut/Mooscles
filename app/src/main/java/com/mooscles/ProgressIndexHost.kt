package com.mooscles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.mooscles.screens.progress.LocalProgressIndexCache

/**
 * Gives the screens the application's one progress index ([MoosclesApplication.progressIndex], plan 5A decision 9).
 * Without this, each screen keeps an index of its own composition, built again every time it opens.
 */
@Composable
internal fun ProvideProgressIndex(app: MoosclesApplication, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalProgressIndexCache provides app.progressIndex, content = content)
}
