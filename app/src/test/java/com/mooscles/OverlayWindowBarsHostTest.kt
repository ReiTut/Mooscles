package com.mooscles

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.mooscles.designsystem.components.MOverlayWindow
import com.mooscles.ui.MoosclesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * An overlay's window (the dialogs, the sheets, the exercise form) takes its system bars' appearance from the
 * app's theme, not from the system: under a light app the status and navigation icons are dark.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class OverlayWindowBarsHostTest {
    @get:Rule val compose = createComposeRule()

    private val choice = mutableStateOf("light")
    private var overlay: View? = null

    private fun bars(): Pair<Boolean, Boolean> {
        val view = overlay
        assertNotNull("the overlay window opened", view)
        val window = (view!!.parent as DialogWindowProvider).window
        val controller = WindowCompat.getInsetsController(window, view)
        return controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars
    }

    @Test fun overlayWindowFollowsTheAppTheme() {
        compose.setContent {
            MoosclesTheme(choice.value) { MOverlayWindow(onDismissRequest = {}) { overlay = LocalView.current } }
        }
        compose.waitForIdle()
        assertEquals("light app: dark icons on both bars", true to true, bars())
        choice.value = "dark"
        compose.waitForIdle()
        assertEquals("dark app: light icons on both bars", false to false, bars())
    }
}
