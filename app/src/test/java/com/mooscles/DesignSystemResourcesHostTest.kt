package com.mooscles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_licenses_title
import com.mooscles.designsystem.tokens.interFamily
import com.mooscles.designsystem.tokens.numberFamily
import com.mooscles.screens.format.conventionCaption
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The :designsystem Compose Multiplatform resources (strings, fonts and files) load in the
 * :app host tests under Robolectric. Later tasks render design system screens here, so a
 * missing resource must fail this class first. The :screens strings, packed the same way, too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class DesignSystemResourcesHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun stringResourceResolvesUnderRobolectric() {
        compose.setContent { Text(stringResource(Res.string.ds_licenses_title)) }
        compose.onNodeWithText("Open-source licenses").assertExists()
    }

    // A text only the :screens resources hold (an unknown label would fall back to itself, so the
    // caption cannot come from anywhere else); its Res class is internal to that module.
    @Test
    fun screensStringResourceResolvesUnderRobolectric() {
        compose.setContent { Text(conventionCaption("Per hand").orEmpty()) }
        compose.onNodeWithText("Per hand: log one dumbbell's load.").assertExists()
    }

    @Test
    fun fontsLoadUnderRobolectric() {
        compose.setContent {
            Column {
                Text("Inter sample", style = TextStyle(fontFamily = interFamily()))
                Text("1234567890", style = TextStyle(fontFamily = numberFamily()))
            }
        }
        compose.onNodeWithText("Inter sample").assertExists()
        compose.onNodeWithText("1234567890").assertExists()
    }

    @OptIn(ExperimentalResourceApi::class)
    @Test
    fun licenseFilesAreBundled() = runTest {
        val inter = Res.readBytes("files/licenses/inter_ofl.txt").decodeToString()
        val barlow = Res.readBytes("files/licenses/barlow_condensed_ofl.txt").decodeToString()
        val lucide = Res.readBytes("files/licenses/lucide_isc.txt").decodeToString()
        assertTrue("inter_ofl.txt is empty", inter.isNotBlank())
        assertTrue("barlow_condensed_ofl.txt is empty", barlow.isNotBlank())
        assertTrue("lucide_isc.txt is empty", lucide.isNotBlank())
        assertTrue("inter_ofl.txt is not the OFL", "SIL OPEN FONT LICENSE" in inter)
        assertTrue("barlow_condensed_ofl.txt is not the OFL", "SIL OPEN FONT LICENSE" in barlow)
        // Phase 4A: the body map (MIT) and the own media (CC0).
        val muscleMap = Res.readBytes("files/licenses/musclemap_mit.txt").decodeToString()
        val media = Res.readBytes("files/licenses/media-CC0.md").decodeToString()
        assertTrue("musclemap_mit.txt is not MuscleMap's MIT License", "MIT License" in muscleMap && "Jsplice / MuscleMap" in muscleMap)
        assertTrue("media-CC0.md is not CC0 1.0", "CC0 1.0 Universal" in media && "Statement of Purpose" in media)
    }
}
