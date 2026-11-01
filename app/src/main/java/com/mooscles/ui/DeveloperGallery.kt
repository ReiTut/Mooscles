package com.mooscles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.mooscles.designsystem.gallery.GalleryScreen
import com.mooscles.designsystem.gallery.rememberGalleryState
import com.mooscles.screens.exercise.figures.figuresGalleryPage

/**
 * The hidden entry to the design gallery: seven taps in a row on the installed version in
 * Settings, at most [MAX_GAP_MS] apart. The unlock lives in memory only. It writes no preference
 * and no saved state, so it lasts while the process lives and is gone after the process dies.
 * Main thread only.
 */
object DeveloperGallery {
    const val ROUTE = "gallery"
    const val TAPS = 7
    const val MAX_GAP_MS = 2_000L

    private var unlockedState by mutableStateOf(false)
    private var taps = 0
    private var lastTapMs = 0L

    /** Snapshot state: the Settings entry appears as soon as it turns true. */
    val unlocked: Boolean get() = unlockedState

    /** Counts a tap at [nowMs] (a monotonic clock). True on the seventh tap in a row. */
    fun registerVersionTap(nowMs: Long): Boolean {
        taps = if (taps > 0 && nowMs - lastTapMs in 0..MAX_GAP_MS) taps + 1 else 1
        lastTapMs = nowMs
        if (taps != TAPS) return false
        unlockedState = true
        return true
    }

    internal fun resetForTest() {
        unlockedState = false
        taps = 0
        lastTapMs = 0L
    }
}

/**
 * The `gallery` route. Leaving the gallery after switching its language must bring the next
 * screen back in the system language without resetting the navigation, so the language is
 * restored as soon as the destination changes (the gallery back button, the system back, a
 * footer tab or a request to open Settings), before the next screen composes. Restoring later,
 * when the gallery leaves composition, would rebuild the whole themed tree and with it the
 * NavController (see ProvideAppLocale).
 */
@Composable
internal fun DeveloperGalleryRoute(nav: NavController) {
    if (!DeveloperGallery.unlocked) {
        // A back stack restored after the process died can reopen this route without the unlock.
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val gallery = rememberGalleryState()
    DisposableEffect(nav, gallery) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route != DeveloperGallery.ROUTE) gallery.restoreLanguage()
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    // The system back behaves like the gallery's own: it closes the open page first.
    BackHandler { gallery.back { nav.popBackStack() } }
    // The figures page lives in :screens, next to the poses (plan decision 5).
    val extraPages = remember { listOf(figuresGalleryPage()) }
    GalleryScreen(onBack = { nav.popBackStack() }, state = gallery, extraPages = extraPages)
}
