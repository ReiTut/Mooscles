package com.mooscles.screens.exercise

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import com.mooscles.designsystem.tokens.MoosclesTiming
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import com.mooscles.data.Equipment
import com.mooscles.data.MuscleGroup
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertTextNotCut
import com.mooscles.screens.exercise.photo.PhotoRowTags
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The photo of the custom exercise form (decision 12) and a form that stays open and busy until its save is echoed
 * (pre-flight F10), over a [FakeExercisePhotos].
 */
@OptIn(ExperimentalTestApi::class)
class CustomExerciseFormPhotoTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Plate-loaded machine", "Total load", custom = true)

    private class Outcome(val actions: RecordingActions, val photos: FakeExercisePhotos, val state: MutableState<UiState>) {
        val saved = mutableListOf<String>()
        var cancelled = 0
        val creations: List<String> get() = actions.calls.filter { it.startsWith("createCustomExercise:") }
        val id: String get() = creations.last().removePrefix("createCustomExercise:").substringBefore(",")
    }

    private fun ComposeUiTest.showForm(
        initial: ExerciseUi? = null,
        photos: FakeExercisePhotos = FakeExercisePhotos(),
        state: MutableState<UiState> = mutableStateOf(UiState(loading = false, exercises = listOf(mine))),
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        size: DpSize? = null,
        restoration: ScreenRestoration? = null,
        provide: Boolean = true,
    ): Outcome {
        val outcome = Outcome(RecordingActions(), photos, state)
        // Reduced motion: the busy button's spinner would never let the test go idle.
        withScreen(locale = locale, fontScale = fontScale, size = size, restoration = restoration, reducedMotion = true) {
            val form: @androidx.compose.runtime.Composable () -> Unit = {
                CustomExerciseForm(
                    initial = initial,
                    prefillName = "",
                    state = state.value,
                    actions = outcome.actions,
                    onSaved = {
                        outcome.photos.store.note("saved:$it")
                        outcome.saved += it
                    },
                    onCancel = { outcome.cancelled++ },
                    pageSize = size,
                )
            }
            if (provide) photos.Provide(form) else form()
        }
        waitForIdle()
        return outcome
    }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.fillAndSave(name: String = "Remada articulada") {
        onNodeWithTag(CustomExerciseFormTags.NAME).performTextReplacement(name)
        tap(CustomExerciseFormTags.muscle(MuscleGroup.BACK.label))
        tap(CustomExerciseFormTags.equipment(Equipment.PLATE_LOADED_MACHINE.label))
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.exists(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.pick() {
        tap(PhotoRowTags.GALLERY)
        waitUntilAtLeastOneExists(hasTestTag(PhotoRowTags.PREVIEW), timeoutMillis = 5_000)
    }

    private val busy = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)

    private fun ComposeUiTest.assertBusy(expected: Boolean) {
        val save = onNodeWithTag(CustomExerciseFormTags.SAVE)
        if (expected) save.assert(busy) else save.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    private fun ExerciseUi.arrived(state: MutableState<UiState>) {
        state.value = state.value.copy(exercises = state.value.exercises + this)
    }

    @Test
    fun galleryPickShowsThePreview() = runComposeUiTest {
        val outcome = showForm()
        assertTrue(exists(PhotoRowTags.EMPTY))
        assertTrue(!exists(PhotoRowTags.REMOVE))
        pick()
        assertTrue(exists(PhotoRowTags.REMOVE))
        assertTrue(!exists(PhotoRowTags.EMPTY))
        assertEquals(1, outcome.photos.capture.galleryLaunches)
        assertEquals(1, outcome.photos.store.log.count { it.startsWith("stage:") && it.endsWith(":content://gallery/1") })
        // A person who backs out of the picker changes nothing.
        outcome.photos.capture.galleryUri = null
        tap(PhotoRowTags.GALLERY)
        assertEquals(1, outcome.photos.store.log.count { it.startsWith("stage:") })
        assertTrue(exists(PhotoRowTags.PREVIEW))
    }

    @Test
    fun cameraHiddenWithoutACameraApp() {
        runComposeUiTest {
            showForm(photos = FakeExercisePhotos(cameraAvailable = false))
            assertTrue(exists(PhotoRowTags.GALLERY))
            assertTrue(!exists(PhotoRowTags.CAMERA))
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            val outcome = showForm(photos = FakeExercisePhotos(cameraAvailable = true))
            tap(PhotoRowTags.CAMERA)
            waitUntil(timeoutMillis = 5_000) { exists(PhotoRowTags.PREVIEW) }
            assertEquals(1, outcome.photos.store.log.count { it.endsWith(":content://camera/1") })
        }
        LocalAppLocale.restore()
        // Without a provider there is no "Photo" row at all.
        runComposeUiTest {
            showForm(provide = false)
            assertTrue(!exists(PhotoRowTags.GALLERY) && !exists(PhotoRowTags.EMPTY))
        }
    }

    @Test
    fun saveStaysOpenAndBusyUntilTheExerciseArrives() = runComposeUiTest {
        val outcome = showForm()
        fillAndSave()
        assertEquals(1, outcome.creations.size)
        // The state has not brought the exercise: busy, open, nothing reported.
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertBusy(true)
        assertEquals(emptyList(), outcome.saved)
        // More taps while busy send nothing.
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        assertEquals(1, outcome.creations.size)
        // Unrelated changes end nothing.
        outcome.state.value = outcome.state.value.copy(history = emptyList())
        waitForIdle()
        assertEquals(emptyList(), outcome.saved)
        ExerciseUi(outcome.id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf(outcome.id), outcome.saved)
    }

    @Test
    fun failedCreateKeepsTheFormOpenWithThePhoto() = runComposeUiTest {
        val outcome = showForm()
        pick()
        fillAndSave()
        assertBusy(true)
        val id = outcome.id
        // The create fails: the count of failures about the new id grows (and the error is about it).
        outcome.state.value = outcome.state.value.copy(
            error = "raw", errorCode = UiError.SAVE_FAILED, errorSubject = id, subjectFailures = mapOf(id to 1),
        )
        waitForIdle()
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertBusy(false)
        assertEquals(emptyList(), outcome.saved)
        assertTrue(exists(PhotoRowTags.PREVIEW), "the photo stays")
        assertTrue(!outcome.photos.store.log.any { it.startsWith("discard:") })
        // The error shows over the form, and is handed back.
        onNode(hasText("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.", substring = true) and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true)
            .assertIsDisplayed()
        assertTrue("clearError:" in outcome.actions.calls.dropWhile { !it.startsWith("createCustomExercise:") }, "${outcome.actions.calls}")
        // Save again: a second send, with the baseline of one failure; the next failure releases it again.
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        assertEquals(2, outcome.creations.size)
        assertBusy(true)
        outcome.state.value = outcome.state.value.copy(subjectFailures = mapOf(id to 2))
        waitForIdle()
        assertBusy(false)
        // And finally it works: the photo moves under the id, then the host hears.
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf(id), outcome.saved)
    }

    @Test
    fun unrelatedErrorDoesNotStopTheWait() = runComposeUiTest {
        val outcome = showForm()
        fillAndSave()
        val id = outcome.id
        // Something else fails: another subject, and a second error that replaces the first as the shown one.
        outcome.state.value = outcome.state.value.copy(
            error = "raw", errorCode = UiError.SAVE_FAILED, errorSubject = "another-routine", subjectFailures = mapOf("another-routine" to 1),
        )
        waitForIdle()
        outcome.state.value = outcome.state.value.copy(
            errorCode = UiError.GENERIC, errorSubject = null, subjectFailures = mapOf("another-routine" to 2),
        )
        waitForIdle()
        assertBusy(true)
        assertEquals(emptyList(), outcome.saved)
        assertTrue("clearError:" !in outcome.actions.calls.dropWhile { !it.startsWith("createCustomExercise:") }, "the form leaves errors that are not its own")
        ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf(id), outcome.saved)
    }

    @Test
    fun photoIsCommittedUnderTheNewIdBeforeSaved() = runComposeUiTest {
        val outcome = showForm()
        pick()
        fillAndSave()
        val id = outcome.id
        // Not before the exercise exists.
        assertTrue(outcome.photos.store.log.none { it.startsWith("commit:") })
        ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        val order = outcome.photos.store.log.filter { it.startsWith("commit:") || it.startsWith("saved:") }
        assertEquals(listOf("commit:$id:$id", "saved:$id"), order)
        assertTrue(outcome.photos.store.hasPhoto(id))
    }

    @Test
    fun photoFailureToastsAndKeepsTheExercise() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.commitWorks = false }
        val outcome = showForm(photos = photos)
        pick()
        fillAndSave()
        ExerciseUi(outcome.id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        // The exercise is kept (the form reports it saved) and the failure is noted for the detail to show.
        assertEquals(listOf(outcome.id), outcome.saved)
        assertEquals(setOf(outcome.id), photos.photos.failedCommits.value)
        assertTrue(!photos.store.hasPhoto(outcome.id))
    }

    @Test
    fun stagingFailureToastsInTheForm() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.stageWorks = false }
        showForm(photos = photos)
        tap(PhotoRowTags.GALLERY)
        waitForIdle()
        onNode(hasText("Não foi possível salvar a foto.") and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true)
            .assertIsDisplayed()
        assertTrue(exists(PhotoRowTags.EMPTY))
    }

    @Test
    fun cancelDiscardsTheDraft() = runComposeUiTest {
        val outcome = showForm()
        pick()
        onNodeWithTag(CustomExerciseFormTags.CANCEL).performClick()
        waitUntil(timeoutMillis = 5_000) { outcome.photos.store.log.any { it.startsWith("discard:") } }
        assertEquals(1, outcome.cancelled)
        assertTrue(outcome.photos.store.log.none { it.startsWith("commit:") })
    }

    @Test
    fun backDiscardsTheDraft() = runComposeUiTest {
        val outcome = showForm()
        pick()
        onNodeWithTag(CustomExerciseFormTags.ROOT).performKeyInput { pressKey(Key.Escape) }
        waitUntil(timeoutMillis = 5_000) { outcome.photos.store.log.any { it.startsWith("discard:") } }
        assertEquals(1, outcome.cancelled)
    }

    @Test
    fun photoDraftSurvivesRecreation() = runComposeUiTest {
        val restoration = screenRestoration()
        val outcome = showForm(locale = null, restoration = restoration)
        pick()
        restoration.emulateSaveAndRestore()
        waitForIdle()
        // The draft is read back from the store, with its remove button; it was not staged again.
        waitUntil(timeoutMillis = 5_000) { exists(PhotoRowTags.PREVIEW) }
        assertTrue(exists(PhotoRowTags.REMOVE))
        assertEquals(1, outcome.photos.store.log.count { it.startsWith("stage:") })
        // And the draft goes with the save under the same key.
        fillAndSave()
        ExerciseUi(outcome.id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertTrue(outcome.photos.store.hasPhoto(outcome.id))
    }

    @Test
    fun recreationDropsTheWaitAndKeepsTheDraft() = runComposeUiTest {
        val restoration = screenRestoration()
        val outcome = showForm(locale = null, restoration = restoration)
        pick()
        fillAndSave()
        assertBusy(true)
        restoration.emulateSaveAndRestore()
        waitForIdle()
        // A restored wait could never end after process death: the form is open and free, with its photo.
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertBusy(false)
        assertTrue(exists(PhotoRowTags.PREVIEW))
    }

    @Test
    fun removeOnEditAppliesOnlyOnSave() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        val outcome = showForm(initial = mine, photos = photos)
        waitUntil(timeoutMillis = 5_000) { exists(PhotoRowTags.PREVIEW) }
        tap(PhotoRowTags.REMOVE)
        assertTrue(exists(PhotoRowTags.EMPTY) && !exists(PhotoRowTags.PREVIEW))
        // Still stored until the save.
        assertTrue(photos.store.hasPhoto(mine.id))
        assertTrue(photos.store.log.none { it.startsWith("remove:") })
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf(mine.id), outcome.saved)
        assertTrue(!photos.store.hasPhoto(mine.id))
        assertEquals(listOf("remove:${mine.id}"), photos.store.log.filter { it.startsWith("remove:") })
    }

    @Test
    fun removeOnEditThenCancelKeepsThePhoto() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        val outcome = showForm(initial = mine, photos = photos)
        waitUntil(timeoutMillis = 5_000) { exists(PhotoRowTags.PREVIEW) }
        tap(PhotoRowTags.REMOVE)
        onNodeWithTag(CustomExerciseFormTags.CANCEL).performClick()
        waitForIdle()
        assertEquals(1, outcome.cancelled)
        assertTrue(photos.store.hasPhoto(mine.id))
        assertTrue(photos.store.log.none { it.startsWith("remove:") })
    }

    @Test
    fun editWithANewPhotoCommitsItUnderTheSameId() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        val outcome = showForm(initial = mine, photos = photos)
        pick()
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf("commit:${mine.id}:${mine.id}", "saved:${mine.id}"), photos.store.log.filter { it.startsWith("commit:") || it.startsWith("saved:") })
    }

    private fun ComposeUiTest.editName(text: String) {
        onNodeWithTag(CustomExerciseFormTags.NAME).performTextReplacement(text)
        waitForIdle()
    }

    private fun ExerciseUi.edited(name: String) = copy(name = name)

    @Test
    fun editStaysBusyUntilTheStateShowsTheNewValues() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        val outcome = showForm(initial = mine, photos = photos)
        waitUntilAtLeastOneExists(hasTestTag(PhotoRowTags.PREVIEW), timeoutMillis = 5_000)
        editName("Supino articulado do Zé II")
        tap(PhotoRowTags.REMOVE)
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        assertEquals(1, outcome.actions.calls.count { it.startsWith("saveCustomExercise:") })
        assertBusy(true)
        assertEquals(emptyList(), outcome.saved)
        // Nothing is removed before the echo.
        assertTrue(photos.store.log.none { it.startsWith("remove:") })
        // A state that still has the old name is no echo.
        outcome.state.value = outcome.state.value.copy(history = emptyList())
        waitForIdle()
        assertEquals(emptyList(), outcome.saved)
        outcome.state.value = outcome.state.value.copy(exercises = listOf(mine.edited("Supino articulado do Zé II")))
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf("remove:${mine.id}", "saved:${mine.id}"), photos.store.log.filter { it.startsWith("remove:") || it.startsWith("saved:") })
    }

    @Test
    fun failedEditKeepsTheFormAndThePhoto() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        val outcome = showForm(initial = mine, photos = photos)
        waitUntilAtLeastOneExists(hasTestTag(PhotoRowTags.PREVIEW), timeoutMillis = 5_000)
        pick()
        editName("Supino articulado do Zé II")
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        assertBusy(true)
        // On the UI thread, like the host's own writes: from the test thread the write races a real layout pass
        // and the next test frame fails with "performMeasureAndLayout called during measure layout".
        runOnUiThread {
            outcome.state.value = outcome.state.value.copy(
                error = "raw", errorCode = UiError.SAVE_FAILED, errorSubject = mine.id, subjectFailures = mapOf(mine.id to 1),
            )
        }
        waitForIdle()
        // The form stays, free, with the draft and the saved photo untouched.
        onNodeWithTag(CustomExerciseFormTags.ROOT).assertIsDisplayed()
        assertBusy(false)
        assertEquals(emptyList(), outcome.saved)
        assertTrue(exists(PhotoRowTags.PREVIEW))
        assertTrue(photos.store.hasPhoto(mine.id) && photos.store.log.none { it.startsWith("commit:") || it.startsWith("remove:") })
        // Saved again, and this time echoed: the draft is committed, then the host hears.
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        runOnUiThread { outcome.state.value = outcome.state.value.copy(exercises = listOf(mine.edited("Supino articulado do Zé II"))) }
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertEquals(listOf("commit:${mine.id}:${mine.id}", "saved:${mine.id}"), photos.store.log.filter { it.startsWith("commit:") || it.startsWith("saved:") })
    }

    @Test
    fun lostCommandFreesTheFormAfterTheCapWithItsToast() = runComposeUiTest {
        val outcome = showForm()
        fillAndSave()
        assertBusy(true)
        mainClock.advanceTimeBy(MoosclesTiming().saveEchoWait + 100)
        waitForIdle()
        assertBusy(false)
        assertEquals(emptyList(), outcome.saved)
        onNode(hasText("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.") and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun cancelWhileSavingDoesNothing() = runComposeUiTest {
        val outcome = showForm()
        pick()
        fillAndSave()
        assertBusy(true)
        onNodeWithTag(CustomExerciseFormTags.CANCEL).performClick()
        onNodeWithTag(CustomExerciseFormTags.ROOT).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(0, outcome.cancelled)
        assertTrue(outcome.photos.store.log.none { it.startsWith("discard:") })
        // The echo still commits the photo.
        ExerciseUi(outcome.id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertTrue(outcome.photos.store.hasPhoto(outcome.id))
        assertEquals(0, outcome.cancelled)
    }

    @Test
    fun resaveAfterTheEchoSendsNothing() = runComposeUiTest {
        val restoration = screenRestoration()
        val outcome = showForm(locale = null, restoration = restoration)
        pick()
        fillAndSave()
        val id = outcome.id
        // Recreated while waiting: the wait is dropped, and the echo arrives meanwhile.
        restoration.emulateSaveAndRestore()
        waitForIdle()
        ExerciseUi(id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitForIdle()
        assertBusy(false)
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        // One creation only, the photo under the id, and no error for a duplicate.
        assertEquals(1, outcome.creations.size)
        assertEquals(listOf(id), outcome.saved)
        assertTrue(outcome.photos.store.hasPhoto(id))
    }

    @Test
    fun saveWaitsWhileAChosenPhotoIsPrepared() = runComposeUiTest {
        val photos = FakeExercisePhotos()
        val gate = CompletableDeferred<Unit>().also { photos.store.stageGate = it }
        val outcome = showForm(photos = photos)
        onNodeWithTag(CustomExerciseFormTags.NAME).performTextReplacement("Remada articulada")
        tap(CustomExerciseFormTags.muscle(MuscleGroup.BACK.label))
        tap(CustomExerciseFormTags.equipment(Equipment.PLATE_LOADED_MACHINE.label))
        tap(PhotoRowTags.GALLERY)
        // Still preparing: the row says so, and Save does nothing (and looks busy).
        assertTrue(exists(PhotoRowTags.PREPARING))
        assertBusy(true)
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        assertEquals(0, outcome.creations.size)
        gate.complete(Unit)
        waitUntilAtLeastOneExists(hasTestTag(PhotoRowTags.PREVIEW), timeoutMillis = 5_000)
        assertTrue(!exists(PhotoRowTags.PREPARING))
        assertBusy(false)
        onNodeWithTag(CustomExerciseFormTags.SAVE).performClick()
        waitForIdle()
        assertEquals(1, outcome.creations.size)
        ExerciseUi(outcome.id, "Remada articulada", "Back", "Plate-loaded machine", custom = true).arrived(outcome.state)
        waitUntil(timeoutMillis = 5_000) { outcome.saved.isNotEmpty() }
        assertTrue(photos.store.hasPhoto(outcome.id), "the photo is committed under the new id")
    }

    @Test
    fun failedStagingReleasesTheForm() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.stageWorks = false }
        val gate = CompletableDeferred<Unit>().also { photos.store.stageGate = it }
        showForm(photos = photos)
        tap(PhotoRowTags.GALLERY)
        assertTrue(exists(PhotoRowTags.PREPARING))
        gate.complete(Unit)
        waitForIdle()
        assertTrue(!exists(PhotoRowTags.PREPARING) && exists(PhotoRowTags.EMPTY))
        assertBusy(false)
        onNode(hasText("Não foi possível salvar a foto.") and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun photoRowWholeAtTwoX() {
        for (scale in listOf(2f, LINEAR_TWO_X_FONT_SCALE)) {
            runComposeUiTest {
                val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
                showForm(initial = mine, photos = photos, fontScale = scale, size = DpSize(320.dp, 640.dp))
                waitUntil(timeoutMillis = 5_000) { exists(PhotoRowTags.PREVIEW) }
                onNodeWithTag(PhotoRowTags.REMOVE).performScrollTo()
                val page = onNodeWithTag(CustomExerciseFormTags.ROOT).getUnclippedBoundsInRoot()
                for (tag in listOf(PhotoRowTags.GALLERY, PhotoRowTags.CAMERA, PhotoRowTags.REMOVE)) {
                    val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                    assertTrue(bounds.left >= page.left && bounds.right <= page.right, "$tag at $scale: $bounds in $page")
                }
                val wanted = setOf("Escolher da galeria", "Tirar foto", "Remover foto")
                val seen = mutableSetOf<String>()
                for (node in onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(CustomExerciseFormTags.ROOT)), useUnmergedTree = true).fetchSemanticsNodes()) {
                    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                    if (text !in wanted) continue
                    val results = mutableListOf<TextLayoutResult>()
                    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
                    if (!action(results) || results.isEmpty()) continue
                    assertEquals(scale, results.first().layoutInput.density.fontScale, "\"$text\"")
                    assertTextNotCut(results.first(), "\"$text\" at $scale")
                    seen += text
                }
                assertEquals(wanted, seen, "at $scale")
            }
            LocalAppLocale.restore()
        }
    }
}
