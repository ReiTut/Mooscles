package com.mooscles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.mooscles.ui.MoosclesTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mooscles.backup.ImportSummary
import com.mooscles.ui.BackupUi
import com.mooscles.ui.DataActions
import com.mooscles.ui.DataSection
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
class DataSectionHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsBothButtonsAndPrivacyNote() {
        show(BackupUi())
        compose.onNodeWithText("Your data").assertIsDisplayed()
        compose.onNodeWithTag("data-export").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("data-import").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Export data").assertIsDisplayed()
        compose.onNodeWithText("Import data").assertIsDisplayed()
        compose.onNodeWithText("Backups contain your full workout history without encryption. Keep the file somewhere private.").assertIsDisplayed()
    }

    @Test
    fun exportShowsThePhotosNote() {
        show(BackupUi())
        compose.onNodeWithText("Photos of custom exercises are not included in the file.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("data-export-photos-note").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun importDisabledDuringActiveWorkoutWithReason() {
        show(BackupUi(), workoutActive = true)
        compose.onNodeWithTag("data-import").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("data-export").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Finish or discard the active workout to import").assertIsDisplayed()
    }

    @Test
    fun busyDisablesBothButtonsAndShowsProgress() {
        show(BackupUi(busy = true))
        compose.onNodeWithTag("data-export").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("data-import").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("data-progress").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun summaryDialogShowsCountsAndConfirmCallsAction() {
        val actions = CapturedDataActions()
        show(BackupUi(pendingImport = ImportSummary(180, 28, 4, 0, 3, false, nothingToAdd = false)), actions)
        compose.onNodeWithText("Import backup?").assertIsDisplayed()
        compose.onNodeWithText("Add 180 workouts, 4 routines and 3 custom exercises. 28 workouts are already on this phone and will be kept as they are.").assertIsDisplayed()
        compose.onNodeWithText("Import").performClick()
        compose.runOnIdle { assertEquals(listOf("confirm"), actions.calls) }
    }

    @Test
    fun dismissingSummaryDialogCancelsImport() {
        val actions = CapturedDataActions()
        show(BackupUi(pendingImport = ImportSummary(1, 0, 0, 0, 0, false, nothingToAdd = false)), actions)
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(listOf("cancel"), actions.calls) }
    }

    @Test
    fun singularCountsUseSingularNouns() {
        show(BackupUi(pendingImport = ImportSummary(1, 1, 1, 0, 1, true, nothingToAdd = false)))
        compose.onNodeWithText(
            "Add 1 workout, 1 routine and 1 custom exercise. 1 workout is already on this phone and will be kept as it is. Your units, theme and rest settings will also be restored.",
        ).assertIsDisplayed()
    }

    @Test
    fun messageIsShownUntilCleared() {
        val actions = CapturedDataActions()
        val state = mutableStateOf(BackupUi(message = "Exported 2 workouts, 1 routine and 0 custom exercises"))
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { DataSection(state.value, false, actions) } } }
        compose.onNodeWithText("Exported 2 workouts, 1 routine and 0 custom exercises").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("data-message-dismiss").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("clear"), actions.calls) }
        compose.runOnIdle { state.value = BackupUi() }
        compose.onNodeWithText("Exported 2 workouts, 1 routine and 0 custom exercises").assertDoesNotExist()
    }

    @Test
    fun buttonsStayVisibleAtLargeFontScaleOnNarrowWidth() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 2f),
            ) {
                MoosclesTheme("light") {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        DataSection(BackupUi(), false, CapturedDataActions())
                    }
                }
            }
        }
        compose.onNodeWithTag("data-export").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("data-import").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dialogIsGoneWhileImportRuns() {
        show(BackupUi(busy = true, working = "Importing…", pendingImport = ImportSummary(1, 0, 0, 0, 0, false, nothingToAdd = false)))
        compose.onNodeWithText("Import backup?").assertDoesNotExist()
        compose.onNodeWithText("Importing…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("data-progress").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun progressLabelNamesEachOperation() {
        val state = mutableStateOf(BackupUi(busy = true, working = "Exporting…"))
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { DataSection(state.value, false, CapturedDataActions()) } } }
        listOf("Exporting…", "Checking backup…", "Importing…").forEach { label ->
            compose.runOnIdle { state.value = BackupUi(busy = true, working = label) }
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun nothingNewDialogOffersOnlyOk() {
        val actions = CapturedDataActions()
        show(BackupUi(pendingImport = ImportSummary(0, 5, 0, 2, 0, false, nothingToAdd = true)), actions)
        compose.onNodeWithText("Everything in this backup is already on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Import").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(listOf("cancel"), actions.calls) }
    }

    @Test
    fun preferencesOnlyBackupIsNotNothingNew() {
        show(BackupUi(pendingImport = ImportSummary(0, 5, 0, 0, 0, true, nothingToAdd = false)))
        compose.onNodeWithText("Import").assertIsDisplayed()
    }

    @Test
    fun errorAndSuccessMessagesAreDistinguishedByIcon() {
        show(BackupUi(message = "This file is not a Mooscles backup.", messageIsError = true))
        compose.onNodeWithTag("data-message-error", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("data-message-success", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun successMessageShowsCheck() {
        show(BackupUi(message = "Exported 1 workout, 0 routines and 0 custom exercises"))
        compose.onNodeWithTag("data-message-success", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("data-message-error", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun activeWorkoutExplanationMeetsContrastInDarkTheme() {
        var ratio = 0.0
        compose.setContent {
            com.mooscles.ui.MoosclesTheme("dark") {
                val fg = MaterialTheme.colorScheme.onSurfaceVariant
                val bg = androidx.compose.material3.CardDefaults.cardColors().containerColor
                ratio = contrast(fg, bg)
            }
        }
        compose.runOnIdle { org.junit.Assert.assertTrue("ratio $ratio", ratio >= 4.5) }
    }

    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val la = a.luminance().toDouble(); val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun show(state: BackupUi, actions: DataActions = CapturedDataActions(), workoutActive: Boolean = false) {
        compose.setContent { MoosclesTheme("light") { Column(Modifier.verticalScroll(rememberScrollState())) { DataSection(state, workoutActive, actions) } } }
    }

    private class CapturedDataActions : DataActions {
        val calls = mutableListOf<String>()
        override fun exportData(uri: String) { calls += "export:$uri" }
        override fun prepareImport(uri: String) { calls += "prepare:$uri" }
        override fun confirmImport() { calls += "confirm" }
        override fun cancelImport() { calls += "cancel" }
        override fun clearBackupMessage() { calls += "clear" }
    }
}
