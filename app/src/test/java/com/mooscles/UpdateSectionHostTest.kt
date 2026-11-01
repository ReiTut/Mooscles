package com.mooscles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mooscles.ui.MoosclesTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mooscles.ui.UpdateActions
import com.mooscles.ui.UpdatePhase
import com.mooscles.ui.UpdateSection
import com.mooscles.ui.UpdateUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UpdateSectionHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun preferencesDefaultToAutomaticWifiAndStableWithManualCheckAvailable() {
        val actions = CapturedUpdateActions()
        show(UpdateUi(installedVersionName = "1.1.0", installedVersionCode = 5), actions)
        compose.onNodeWithText("Installed 1.1.0 · build 5").assertIsDisplayed()
        compose.onNodeWithTag("update-automatic").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("update-wifi-only").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("update-prereleases").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("update-check").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("automatic:false", "wifi:false", "prereleases:true", "check"), actions.calls) }
    }

    @Test
    fun downloadShowsProgressAndCancellationThenRetryAfterFailure() {
        val actions = CapturedUpdateActions()
        val state = mutableStateOf(UpdateUi(
            installedVersionName = "1.1.0", installedVersionCode = 5,
            availableVersionName = "1.2.0", availableVersionCode = 6,
            phase = UpdatePhase.DOWNLOADING, downloadedBytes = 1_048_576, totalBytes = 2_097_152,
            releaseNotes = "Improved workout tracking.",
        ))
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { UpdateSection(state.value, actions) } } }
        compose.onNodeWithTag("update-progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-cancel").performScrollTo().performClick()
        compose.onNodeWithTag("update-check").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.FAILED, message = "Download interrupted. Try again when connected.") }
        compose.onNodeWithTag("update-retry").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("cancel", "download"), actions.calls) }
    }

    @Test
    fun workoutDeferralDoesNotExposeInstallAndAndroidApprovalIsExplicit() {
        val actions = CapturedUpdateActions()
        val state = mutableStateOf(UpdateUi(
            installedVersionName = "1.1.0", installedVersionCode = 5,
            availableVersionName = "1.2.0", availableVersionCode = 6, phase = UpdatePhase.DEFERRED,
        ))
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { UpdateSection(state.value, actions) } } }
        compose.onNodeWithText("Update ready · installation deferred").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-install").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.PERMISSION_REQUIRED) }
        compose.onNodeWithText("Allow from this source").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.APPROVAL_REQUIRED) }
        compose.onNodeWithText("Review installation").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.SUCCEEDED, installedVersionName = "1.2.0", installedVersionCode = 6) }
        compose.onNodeWithText("Update successful").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("install", "install"), actions.calls) }
    }

    @Test
    fun failedOrCancelledInstallRetriesVerifiedApkWithoutAnotherDownload() {
        val actions = CapturedUpdateActions()
        val state = mutableStateOf(UpdateUi(
            installedVersionName = "1.1.0", installedVersionCode = 5,
            availableVersionName = "1.2.0", availableVersionCode = 6,
            phase = UpdatePhase.FAILED, verifiedApkReady = true,
        ))
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { UpdateSection(state.value, actions) } } }
        compose.onNodeWithText("Retry installation").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(phase = UpdatePhase.CANCELLED) }
        compose.onNodeWithText("Retry installation").performScrollTo().performClick()
        compose.runOnIdle { state.value = state.value.copy(verifiedApkReady = false) }
        compose.onNodeWithText("Download again").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("install", "install", "download"), actions.calls) }
    }

    private fun show(state: UpdateUi, actions: UpdateActions) {
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { UpdateSection(state, actions) } } }
    }

    private class CapturedUpdateActions : UpdateActions {
        val calls = mutableListOf<String>()
        override fun checkForUpdates() { calls += "check" }
        override fun setAutomaticUpdates(enabled: Boolean) { calls += "automatic:$enabled" }
        override fun setUpdateWifiOnly(enabled: Boolean) { calls += "wifi:$enabled" }
        override fun setIncludePrereleases(enabled: Boolean) { calls += "prereleases:$enabled" }
        override fun downloadUpdate() { calls += "download" }
        override fun cancelUpdate() { calls += "cancel" }
        override fun installUpdate() { calls += "install" }
    }
}
