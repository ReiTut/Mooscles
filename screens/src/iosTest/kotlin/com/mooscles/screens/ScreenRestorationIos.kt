package com.mooscles.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi

/**
 * The iOS simulator's [ScreenRestoration]. Compose Multiplatform's `StateRestorationTester`
 * throws `NotImplementedError` there, from `platformEncodeDecode`
 * (`StateRestorationTester.skiko.kt:21`), so this one keeps its own [SaveableStateRegistry]: it
 * saves the content's registry, takes the content out of composition and composes it again over
 * a registry restored from that saved map, without a platform encode.
 *
 * The content leaves and comes back at the same place (as Compose's tester does), not under a
 * new `key`: a key would change the composite key hashes `rememberSaveable` saves under, and
 * nothing would be restored.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.screenRestoration(): ScreenRestoration = IosScreenRestoration(this)

@OptIn(ExperimentalTestApi::class)
private class IosScreenRestoration(private val test: ComposeUiTest) : ScreenRestoration {
    private var registry = SaveableStateRegistry(null) { true }
    private var shown by mutableStateOf(true)

    /** The registry the content sees: the current one, replaced by a restored one at each recreation. */
    private val delegating = object : SaveableStateRegistry {
        override fun canBeSaved(value: Any): Boolean = registry.canBeSaved(value)
        override fun consumeRestored(key: String): Any? = registry.consumeRestored(key)
        override fun registerProvider(key: String, valueProvider: () -> Any?): SaveableStateRegistry.Entry =
            registry.registerProvider(key, valueProvider)
        override fun performSave(): Map<String, List<Any?>> = registry.performSave()
    }

    override fun setContent(content: @Composable () -> Unit) {
        test.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides delegating) {
                if (shown) content()
            }
        }
    }

    override fun emulateSaveAndRestore() {
        test.runOnIdle {
            val saved = registry.performSave()
            shown = false
            registry = SaveableStateRegistry(saved) { true }
        }
        test.waitForIdle()
        test.runOnIdle { shown = true }
        test.waitForIdle()
    }
}
