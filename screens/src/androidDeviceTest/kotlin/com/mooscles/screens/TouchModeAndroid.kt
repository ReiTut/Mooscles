package com.mooscles.screens

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Puts the emulator back in touch mode after a test that asked for keyboard mode
 * (`InputModeManager.requestInputMode(Keyboard)`). Android's touch mode is the display's, not the
 * test's: left off, it would hand focus around in the tests that run after (a cleared focus goes
 * back to the first focusable), and common code has no way to turn it back on.
 */
fun restoreTouchMode() {
    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
}
