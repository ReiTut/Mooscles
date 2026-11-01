package com.mooscles.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester

/**
 * The emulator's [ScreenRestoration]: Compose's own [StateRestorationTester], which also checks
 * that every saved value fits in an Android `Bundle`.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.screenRestoration(): ScreenRestoration {
    val tester = StateRestorationTester(this)
    return object : ScreenRestoration {
        override fun setContent(content: @Composable () -> Unit) = tester.setContent(content)

        override fun emulateSaveAndRestore() = tester.emulateSaveAndRestore()
    }
}
