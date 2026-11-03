package com.mooscles.screens

import androidx.compose.runtime.Composable

/**
 * Recreates a test's content from its saved state, as a rotation or process death would: what
 * `rememberSaveable` kept comes back, everything else starts over. Get one with
 * `ComposeUiTest.screenRestoration()`, declared once per platform test source set (the emulator
 * wraps Compose's `StateRestorationTester`; the iOS simulator, where that tester is not
 * implemented, has its own), and pass it to [withScreen].
 */
interface ScreenRestoration {
    /** Sets [content] as the test's content, once, so it can be recreated. */
    fun setContent(content: @Composable () -> Unit)

    /** Saves the state of the content, takes the content away and composes it again from that state. */
    fun emulateSaveAndRestore()
}
