package com.mooscles.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A hook the host can provide to set up the platform window of every [MOverlayWindow] as it opens. It runs
 * inside the window's own composition, so the host reads the window from there (on Android, the view's
 * `DialogWindowProvider`) and, say, gives the window's system bars the app theme's appearance, which the
 * window would otherwise take from the system. The default does nothing.
 */
val LocalOverlayWindowSetup = staticCompositionLocalOf<@Composable () -> Unit> { {} }

/**
 * The window every overlay opens ([MDialog], [MBottomSheet], and the screens' full-screen pages: the
 * custom exercise form, the expanded rest timer): a common `Dialog` (a window on Android, a layer on
 * iOS) that Back dismisses through [onDismissRequest], with no outside tap (the overlay draws and
 * handles its own scrim or covers the screen) and the whole screen's width.
 *
 * Its [content] takes the host's density and font scale. On Android the window would take its own
 * from the configuration, so a host that scales its text on purpose (the gallery's text-size
 * control, a test at 2x) would not reach the content. Elsewhere in the app the two are the same.
 */
@Composable
fun MOverlayWindow(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val hostDensity = LocalDensity.current
    val setup = LocalOverlayWindowSetup.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        setup()
        CompositionLocalProvider(LocalDensity provides hostDensity, content = content)
    }
}
