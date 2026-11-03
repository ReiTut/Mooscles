package com.mooscles.screens.routines

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NBSP = "\u00A0"

/**
 * The planned set rows of the routine editor (spec "Editor de rotina", decisions 6 and 16): labels, the record icon,
 * the stepper on the first tap and the keyboard on the second, one write per valid edit, the set menu, removing with
 * undo, adding, and the limit.
 */
@OptIn(ExperimentalTestApi::class)
class RoutineEditorSetsTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell")
    private val row = ExerciseUi("row", "Remada curvada", "Back", "Barbell")
    private val fly = ExerciseUi("fly", "Crucifixo na polia", "Chest", "Cable")
    private val pullUp = ExerciseUi("pullup", "Barra fixa", "Back", "Bodyweight", convention = "Added weight")

    private fun set(id: String, reps: Int, kg: Double?, inherited: Boolean = true, warmup: Boolean = false) =
        RoutineSetUi(id, reps, kg, inherited = inherited && !warmup, warmup = warmup)

    /** A warm-up and two manual working sets. */
    private val e1 = PlannedExerciseUi(
        "e1", bench, sets = 3, restSeconds = 135,
        setTargets = listOf(set("e1w", 10, 40.0, warmup = true), set("e1a", 8, 80.0, inherited = false), set("e1b", 8, 80.0, inherited = false)),
    )

    /** Its working sets follow the record. */
    private val e2 = PlannedExerciseUi(
        "e2", row, sets = 3, restSeconds = 120,
        setTargets = listOf(set("e2w", 10, 30.0, warmup = true), set("e2a", 10, 60.0), set("e2b", 10, 60.0)),
    )

    /** One set, so the only one. */
    private val e3 = PlannedExerciseUi("e3", fly, sets = 1, restSeconds = 90, setTargets = listOf(set("e3a", 12, 25.0)))

    /** Nobody planned anything, and the exercise is logged with the body's weight. */
    private val e4 = PlannedExerciseUi("e4", pullUp, sets = 1, setTargets = listOf(set("e4a", 0, null, inherited = false)))

    /** A warm-up, a manual 8 x 80, a 10 x 60 that follows the record and a manual 6 x 90: three working sets. */
    private val e5 = PlannedExerciseUi(
        "e5", fly, sets = 4,
        setTargets = listOf(
            set("e5w", 12, 20.0, warmup = true), set("e5a", 8, 80.0, inherited = false),
            set("e5b", 10, 60.0), set("e5c", 6, 90.0, inherited = false),
        ),
    )

    /** A warm-up and a single working set. */
    private val e6 = PlannedExerciseUi(
        "e6", pullUp, sets = 2,
        setTargets = listOf(set("e6w", 10, null, warmup = true), set("e6a", 6, 10.0, inherited = false)),
    )

    private fun routineOf(vararg entries: PlannedExerciseUi) = RoutineUi("r", "Treino A", entries.toList(), folderId = "default")

    private fun stateOf(vararg entries: PlannedExerciseUi, pounds: Boolean = false) = mutableStateOf(
        UiState(
            loading = false, exercises = listOf(bench, row, fly, pullUp), routines = listOf(routineOf(*entries)),
            preferences = com.mooscles.ui.PreferencesUi(pounds = pounds),
        ),
    )

    /** The view model: records every call and echoes the ones a routine editor asks for into the state. */
    private class Fake(val state: MutableState<UiState>, private val recording: RecordingActions = RecordingActions()) :
        MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        /** False: a write is recorded but the state keeps its values. */
        var echo = true

        /** False: a removal is recorded but the set stays in the state (the echo has not come). */
        var applyRemove = true

        fun entries(): List<PlannedExerciseUi> = state.value.routines.single().exercises

        fun editEntries(change: (List<PlannedExerciseUi>) -> List<PlannedExerciseUi>) {
            state.value = state.value.copy(routines = state.value.routines.map { it.copy(exercises = change(it.exercises)) })
        }

        fun editSet(setId: String, change: (RoutineSetUi) -> RoutineSetUi) = editEntries { list ->
            list.map { e -> e.copy(setTargets = e.setTargets.map { if (it.id == setId) change(it) else it }) }
        }

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null, errorSubject = null)
        }

        override fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?) {
            recording.updateRoutineSet(setId, reps, weightKg)
            if (echo) editSet(setId) { it.copy(reps = reps, weightKg = weightKg, inherited = false) }
        }

        override fun setRoutineSetWarmup(setId: String, warmup: Boolean) {
            recording.setRoutineSetWarmup(setId, warmup)
            editSet(setId) { it.copy(warmup = warmup, inherited = false) }
        }

        override fun inheritRoutineSet(setId: String) {
            recording.inheritRoutineSet(setId)
            editSet(setId) { it.copy(inherited = true) }
        }

        override fun addRoutineSet(entryId: String) {
            recording.addRoutineSet(entryId)
            editEntries { list ->
                list.map { e ->
                    if (e.id != entryId || e.setTargets.size >= MAX_PLANNED_SETS) e else {
                        val last = e.setTargets.last()
                        e.copy(setTargets = e.setTargets + last.copy(id = "$entryId-n${e.setTargets.size}", warmup = false, inherited = false))
                    }
                }
            }
        }

        override fun removeRoutineSet(setId: String) {
            recording.removeRoutineSet(setId)
            if (applyRemove) editEntries { list -> list.map { e -> e.copy(setTargets = e.setTargets.filter { it.id != setId }) } }
        }

        override fun copyRoutineSetToAll(entryId: String, sourceSetId: String) {
            recording.copyRoutineSetToAll(entryId, sourceSetId)
            if (!echo) return
            editEntries { list ->
                list.map { e ->
                    val source = e.setTargets.firstOrNull { it.id == sourceSetId }
                    if (e.id != entryId || source == null) e else e.copy(setTargets = e.setTargets.map {
                        if (it.warmup || it.id == sourceSetId) it else it.copy(reps = source.reps, weightKg = source.weightKg, inherited = false)
                    })
                }
            }
        }

        override fun restoreRoutineSetTargets(entryId: String, exerciseId: String, previous: List<RoutineSetUi>) {
            recording.restoreRoutineSetTargets(entryId, exerciseId, previous)
            val byId = previous.associateBy { it.id }
            editEntries { list -> list.map { e -> if (e.id != entryId) e else e.copy(setTargets = e.setTargets.map { byId[it.id] ?: it }) } }
        }

        override fun restoreRoutineSet(entryId: String, position: Int, set: RoutineSetUi) {
            recording.restoreRoutineSet(entryId, position, set)
            val entry = entries().firstOrNull { it.id == entryId }
            when {
                entry == null -> state.value = state.value.copy(errorCode = UiError.SET_NOT_RESTORED)
                entry.setTargets.size >= MAX_PLANNED_SETS -> state.value = state.value.copy(errorCode = UiError.SET_LIMIT)
                else -> editEntries { list ->
                    list.map { e ->
                        if (e.id != entryId) e else e.copy(setTargets = e.setTargets.toMutableList().apply { add(position.coerceIn(0, size), set) })
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.show(
        state: MutableState<UiState> = stateOf(e1, e2, e3),
        fake: Fake = Fake(state),
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        keyboard: Boolean = false,
    ) {
        withScreen(locale = locale, fontScale = fontScale) {
            RoutineEditorRoute(
                routineId = "r",
                state = state.value,
                actions = fake,
                onBack = {},
                onGone = {},
                onOpenWorkout = {},
                onExerciseInfo = {},
                // The keyboard is 300 dp of injected insets on both platforms (spike S3).
                modifier = if (keyboard) Modifier.windowInsetsPadding(WindowInsets(bottom = 300.dp)) else Modifier,
            )
        }
        waitForIdle()
    }

    private fun ComposeUiTest.reveal(tag: String) {
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(tag))
        waitForIdle()
    }

    private fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction {
        reveal(RoutineEditorTags.set(id))
        return onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("routine-set-$id-$cell")), useUnmergedTree = true)
    }

    private fun ComposeUiTest.load(id: String) = field(id, "load")
    private fun ComposeUiTest.reps(id: String) = field(id, "reps")

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun ComposeUiTest.label(id: String): SemanticsNodeInteraction {
        reveal(RoutineEditorTags.set(id))
        return onNodeWithTag(RoutineEditorTags.setLabel(id))
    }

    private fun ComposeUiTest.pick(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openMenu(setId: String) {
        label(setId).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.toastWith(text: String) =
        onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("toast")), useUnmergedTree = true)

    private fun Fake.writes() = calls.filter { it.startsWith("updateRoutineSet:") }

    @Test
    fun labelsNumberWorkingSetsAndMarkWarmups() = runComposeUiTest {
        show()
        label("e1w").assertTextEquals("A").assertContentDescriptionEquals("Aquecimento 1")
        label("e1a").assertTextEquals("1").assertContentDescriptionEquals("Série 1")
        label("e1b").assertTextEquals("2").assertContentDescriptionEquals("Série 2")
        // The numbers count the working sets only.
        label("e2a").assertTextEquals("1")
        load("e1a").assertContentDescriptionEquals("Carga da série 1")
        load("e1w").assertContentDescriptionEquals("Carga do aquecimento 1")
        reps("e1w").assertContentDescriptionEquals("Repetições do aquecimento 1")
        assertEquals("80", load("e1a").editableText())
        assertEquals("8", reps("e1a").editableText())
    }

    @Test
    fun inheritedSetShowsTheRecordIconWithItsSpokenName() = runComposeUiTest {
        show()
        reveal(RoutineEditorTags.set("e2a"))
        onNode(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e2a"))), useUnmergedTree = true)
            .assertIsDisplayed()
        // A manual set and a warm-up (never inherited) have none.
        reveal(RoutineEditorTags.set("e1a"))
        onAllNodes(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e1a"))), useUnmergedTree = true)
            .assertCountEquals(0)
        reveal(RoutineEditorTags.set("e2w"))
        onAllNodes(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e2w"))), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun blankValuesShowNoLoadOrBodyweightByConvention() = runComposeUiTest {
        show(stateOf(e1, e4, e3.copy(setTargets = listOf(set("e3a", 0, null, inherited = false)))))
        // The placeholder is drawn in the field and named in the field's label, which a screen reader reads.
        assertEquals("", load("e4a").editableText())
        load("e4a").assertContentDescriptionEquals("Carga da série 1, Peso corporal")
        load("e3a").assertContentDescriptionEquals("Carga da série 1, Sem carga")
        assertEquals("", reps("e4a").editableText())
        // A set with a load names nothing.
        load("e1a").assertContentDescriptionEquals("Carga da série 1")
    }

    @Test
    fun firstTapShowsTheStepperSecondOpensTheKeyboard() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        load("e1a").performClick()
        waitForIdle()
        onNodeWithContentDescription("Editando carga da série 1").assertIsDisplayed()
        onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("80,0${NBSP}kg")
        onNodeWithText("passo de 2,5${NBSP}kg").assertIsDisplayed()
        load("e1a").assertIsNotFocused()
        assertEquals(emptyList(), fake.calls)
        load("e1a").performClick()
        waitForIdle()
        load("e1a").assertIsFocused()
        // The other field takes the keyboard at once, and its stepper follows.
        reps("e1a").performClick()
        waitForIdle()
        reps("e1a").assertIsFocused()
        onNodeWithContentDescription("Editando repetições da série 1").assertIsDisplayed()
        assertEquals(emptyList(), fake.calls)
    }

    @Test
    fun stepperWritesOncePerRun() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        load("e1a").performClick()
        waitForIdle()
        // A tap is one step and one write.
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("updateRoutineSet:e1a,8,82.5"), fake.calls)
        assertEquals("82,5", load("e1a").editableText())
        // A hold steps on and writes once, when it ends.
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        repeat(600) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("90,0${NBSP}kg")
        assertEquals(1, fake.writes().size, "a hold writes only when it ends")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(200) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(listOf("updateRoutineSet:e1a,8,82.5", "updateRoutineSet:e1a,8,90.0"), fake.writes())
    }

    @Test
    fun editedSetBecomesManual() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        reveal(RoutineEditorTags.set("e2a"))
        onNode(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e2a"))), useUnmergedTree = true).assertIsDisplayed()
        reps("e2a").performTextReplacement("12")
        waitForIdle()
        assertEquals(listOf("updateRoutineSet:e2a,12,60.0"), fake.writes())
        // The repository made it manual: the icon goes, and the sibling still follows.
        onAllNodes(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e2a"))), useUnmergedTree = true)
            .assertCountEquals(0)
        onNode(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e2b"))), useUnmergedTree = true).assertExists()
        // A cleared load is no load, written as null; cleared reps are 0.
        load("e2a").performTextReplacement("")
        reps("e2a").performTextReplacement("")
        waitForIdle()
        assertEquals(listOf("updateRoutineSet:e2a,12,60.0", "updateRoutineSet:e2a,12,null", "updateRoutineSet:e2a,0,null"), fake.writes())
    }

    @Test
    fun setMenuMarksWarmupAndBack() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        // Two taps: the label, then "Aquecimento".
        openMenu("e1a")
        onNodeWithText("Tipo da série").assertIsDisplayed()
        onNodeWithTag(PlannedSetMenuTags.NORMAL).assertIsSelected()
        onNodeWithTag(PlannedSetMenuTags.WARMUP).assertIsNotSelected()
        pick(PlannedSetMenuTags.WARMUP)
        assertEquals(listOf("setRoutineSetWarmup:e1a,true"), fake.calls)
        label("e1a").assertTextEquals("A")
        // And back.
        openMenu("e1a")
        onNodeWithTag(PlannedSetMenuTags.WARMUP).assertIsSelected()
        pick(PlannedSetMenuTags.NORMAL)
        assertEquals("setRoutineSetWarmup:e1a,false", fake.calls.last())
        label("e1a").assertTextEquals("1")
        // The one already chosen does nothing.
        openMenu("e1a")
        pick(PlannedSetMenuTags.NORMAL)
        assertEquals(2, fake.calls.size)
    }

    @Test
    fun followRecordOnlyOnManualWorkingSets() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        openMenu("e1a")
        onNodeWithTag(PlannedSetMenuTags.FOLLOW).assertIsDisplayed()
        // A warm-up never follows the record, and a set that already does has nothing to follow.
        onNodeWithTag(PlannedSetMenuTags.NORMAL).performClick()
        waitForIdle()
        openMenu("e1w")
        onAllNodesWithTag(PlannedSetMenuTags.FOLLOW).assertCountEquals(0)
        pick(PlannedSetMenuTags.WARMUP)
        openMenu("e2a")
        onAllNodesWithTag(PlannedSetMenuTags.FOLLOW).assertCountEquals(0)
        pick(PlannedSetMenuTags.NORMAL)
        assertEquals(emptyList(), fake.calls)
        // Following sends the one action.
        openMenu("e1b")
        pick(PlannedSetMenuTags.FOLLOW)
        assertEquals("inheritRoutineSet:e1b", fake.calls.last())
        reveal(RoutineEditorTags.set("e1b"))
        onNode(hasContentDescription("segue o recorde") and hasAnyAncestor(hasTestTag(RoutineEditorTags.set("e1b"))), useUnmergedTree = true).assertExists()
    }

    @Test
    fun removeShowsUndoAndRestoreCallsRestoreWithTheSnapshot() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        // The snapshot is the set as it stands: the manual 8 x 80 of "Série 2" (the third row), with its id.
        openMenu("e1b")
        pick(PlannedSetMenuTags.REMOVE)
        assertEquals(listOf("removeRoutineSet:e1b"), fake.calls)
        onAllNodesWithTag(RoutineEditorTags.set("e1b")).assertCountEquals(0)
        toastWith("Série 2 removida").assertIsDisplayed()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        assertEquals(
            "restoreRoutineSet:e1,2,${RoutineSetUi("e1b", 8, 80.0, inherited = false, warmup = false)}",
            fake.calls.last(),
        )
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.set("e1b")))
        onNodeWithTag(RoutineEditorTags.set("e1b")).assertExists()
    }

    @Test
    fun removeKeepsAPendingEditInTheSnapshot() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3)).apply { echo = false }
        show(fake.state, fake)
        // The write is in flight (no echo): the set removed is the set as written, manual.
        reps("e2b").performTextReplacement("15")
        waitForIdle()
        openMenu("e2b")
        pick(PlannedSetMenuTags.REMOVE)
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        assertEquals(
            "restoreRoutineSet:e2,2,${RoutineSetUi("e2b", 15, 60.0, inherited = false, warmup = false)}",
            fake.calls.last(),
        )
    }

    @Test
    fun undoAfterTheExerciseIsGoneShowsNotRestored() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        openMenu("e1b")
        pick(PlannedSetMenuTags.REMOVE)
        toastWith("Série 2 removida").assertIsDisplayed()
        // The exercise leaves the routine (removed from elsewhere) while the toast is up.
        fake.editEntries { list -> list.filter { it.id != "e1" } }
        waitForIdle()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        assertTrue(fake.calls.any { it.startsWith("restoreRoutineSet:e1,2,") }, fake.calls.toString())
        toastWith("A série não pôde voltar.").assertIsDisplayed()
    }

    @Test
    fun restoringTheFiftyFirstSetSaysTheLimit() = runComposeUiTest {
        val many = e1.copy(setTargets = (1..50).map { set("m$it", 8, 20.0, inherited = false) })
        val fake = Fake(stateOf(many))
        show(fake.state, fake)
        openMenu("m3")
        pick(PlannedSetMenuTags.REMOVE)
        toastWith("Série 3 removida").assertIsDisplayed()
        // Meanwhile another set fills the place: 50 again.
        fake.editEntries { list -> list.map { it.copy(setTargets = it.setTargets + set("m51", 8, 20.0, inherited = false)) } }
        waitForIdle()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        toastWith("Até 50 séries por exercício.").assertIsDisplayed()
    }

    @Test
    fun removingASetInEditDropsItsDraft() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3)).apply { echo = false; applyRemove = false }
        show(fake.state, fake)
        load("e1b").performClick()
        waitForIdle()
        reps("e1b").performTextReplacement("11")
        waitForIdle()
        assertEquals("11", reps("e1b").editableText())
        val writes = fake.writes().size
        openMenu("e1b")
        pick(PlannedSetMenuTags.REMOVE)
        // The row is still in the state (the removal has not come back), but its stepper is closed and its draft is
        // gone: it shows what the state holds, and nothing is written for it afterwards.
        onAllNodes(hasContentDescription("Editando carga da série 2")).assertCountEquals(0)
        assertEquals("8", reps("e1b").editableText())
        waitForIdle()
        assertEquals(writes, fake.writes().size)
    }

    @Test
    fun lastSetCannotBeRemovedAndSaysWhy() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        openMenu("e3a")
        onNodeWithTag(PlannedSetMenuTags.REMOVE).assertIsNotEnabled()
        onNodeWithTag(PlannedSetMenuTags.LAST_REASON).assertTextEquals("Uma rotina precisa de pelo menos uma série por exercício.")
        onNodeWithTag(PlannedSetMenuTags.REMOVE).performClick()
        waitForIdle()
        assertTrue(fake.calls.none { it.startsWith("removeRoutineSet") })
        // The others can.
        pick(PlannedSetMenuTags.NORMAL)
        openMenu("e1a")
        onNodeWithTag(PlannedSetMenuTags.REMOVE).assertIsEnabled()
        onAllNodesWithTag(PlannedSetMenuTags.LAST_REASON).assertCountEquals(0)
    }

    @Test
    fun addSetCopiesTheOneAbove() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        reveal(RoutineEditorTags.addSet("e1"))
        onNodeWithTag(RoutineEditorTags.addSet("e1")).assertTextEquals("Adicionar série")
        pick(RoutineEditorTags.addSet("e1"))
        assertEquals(listOf("addRoutineSet:e1"), fake.calls)
        // The copy is the repository's: the state shows a fourth set with the values above it.
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasTestTag(RoutineEditorTags.set("e1-n3")))
        assertEquals("80", load("e1-n3").editableText())
        label("e1-n3").assertTextEquals("3")
    }

    @Test
    fun fiftiethSetDisablesAddAndSaysWhy() = runComposeUiTest {
        val almost = e1.copy(setTargets = (1..49).map { set("m$it", 8, 20.0, inherited = false) })
        val fake = Fake(stateOf(almost))
        show(fake.state, fake)
        reveal(RoutineEditorTags.addSet("e1"))
        onNodeWithTag(RoutineEditorTags.addSet("e1")).assertIsEnabled()
        onAllNodesWithText("Até 50 séries por exercício.").assertCountEquals(0)
        pick(RoutineEditorTags.addSet("e1"))
        reveal(RoutineEditorTags.addSet("e1"))
        onNodeWithTag(RoutineEditorTags.addSet("e1")).assertIsNotEnabled()
        // The reason is under the button, in the card.
        onNodeWithTag(RoutineEditorTag).performScrollToNode(hasText("Até 50 séries por exercício."))
        waitForIdle()
        onNodeWithText("Até 50 séries por exercício.").assertIsDisplayed()
        onNodeWithTag(RoutineEditorTags.addSet("e1")).performClick()
        waitForIdle()
        assertEquals(1, fake.calls.size)
    }

    @Test
    fun addAndRemoveAreScopedToTheirExercise() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        pick(RoutineEditorTags.addSet("e2").also { reveal(it) })
        assertEquals(listOf("addRoutineSet:e2"), fake.calls)
        assertEquals(3, fake.entries().first { it.id == "e1" }.setTargets.size)
        assertEquals(4, fake.entries().first { it.id == "e2" }.setTargets.size)
        openMenu("e1a")
        pick(PlannedSetMenuTags.REMOVE)
        assertEquals(2, fake.entries().first { it.id == "e1" }.setTargets.size)
        assertEquals(4, fake.entries().first { it.id == "e2" }.setTargets.size)
        assertEquals(1, fake.entries().first { it.id == "e3" }.setTargets.size)
        // "Série 1 removida": the first working set of its exercise.
        toastWith("Série 1 removida").assertIsDisplayed()
    }

    @Test
    fun largeFontPutsLoadAndRepsOnTwoLinesWithNumbersWhole() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake, fontScale = LINEAR_TWO_X_FONT_SCALE)
        for (id in listOf("e1a", "e2a")) {
            reveal(RoutineEditorTags.set(id))
            val label = onNodeWithTag(RoutineEditorTags.setLabel(id)).fetchSemanticsNode().boundsInRoot
            val load = onNode(hasTestTag("routine-set-$id-load"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val reps = onNode(hasTestTag("routine-set-$id-reps"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // Two lines: the label on top, the numbers below it, whole.
            assertTrue(label.bottom <= load.top + 1f && label.bottom <= reps.top + 1f, "$id: label $label, load $load, reps $reps")
            for (cell in listOf("load", "reps")) {
                val field = field(id, cell).fetchSemanticsNode()
                val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                val read = field.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action
                assertTrue(read != null && read(results) && results.isNotEmpty(), "$id $cell: no text layout")
                assertShownWhole(results.first(), "$id $cell")
            }
        }
        // A stepper under a row at 200% is whole too.
        load("e1a").performClick()
        waitForIdle()
        onNodeWithContentDescription("Editando carga da série 1").assertIsDisplayed()
    }

    @Test
    fun longNamesAndLaterSetControlsStayReachableWithTheKeyboard() = runComposeUiTest {
        val long = e1.copy(
            exercise = ExerciseUi("long", "Supino inclinado com halteres e pegada neutra no banco regulável", "Chest", "Dumbbell"),
            setTargets = (1..14).map { set("l$it", 8, 20.0, inherited = false) },
        )
        val fake = Fake(stateOf(long, e2))
        show(fake.state, fake, keyboard = true)
        val list = { onNodeWithTag(RoutineEditorTag).fetchSemanticsNode().boundsInRoot }
        val root = { onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot }
        reps("l14").performClick()
        waitForIdle()
        reps("l14").performClick() // the keyboard
        waitForIdle()
        val stepper = onNodeWithContentDescription("Editando repetições da série 14")
        stepper.assertIsDisplayed()
        val keyboardTop = root().bottom - with(density) { 300.dp.toPx() }
        val node = stepper.fetchSemanticsNode()
        assertTrue(node.boundsInRoot.bottom <= keyboardTop + 1f, "stepper ${node.boundsInRoot} below the keyboard at $keyboardTop")
        val field = reps("l14").fetchSemanticsNode().boundsInRoot
        assertTrue(field.bottom <= keyboardTop + 1f && field.top >= list().top - 1f, "field $field not in view")
        // The start button stays above the keyboard as well.
        val start = onNodeWithTag(RoutineEditorTags.START).fetchSemanticsNode().boundsInRoot
        assertTrue(start.bottom <= keyboardTop + 1f, "start $start below the keyboard")
    }

    @Test
    fun onlyTheTypeItemsReportSelected() = runComposeUiTest {
        show()
        openMenu("e1a")
        // The two type items are a choice: selected or not. Remove and Follow are plain buttons that say nothing.
        onNodeWithTag(PlannedSetMenuTags.NORMAL).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        onNodeWithTag(PlannedSetMenuTags.WARMUP).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))
        onNodeWithTag(PlannedSetMenuTags.FOLLOW).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        onNodeWithTag(PlannedSetMenuTags.REMOVE).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        onNodeWithTag(PlannedSetMenuTags.COPY_ALL).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun warmupsAreNamedAmongWarmupsNotAmongTheWorkingSets() = runComposeUiTest {
        val two = e1.copy(setTargets = listOf(set("w1", 10, 40.0, warmup = true), set("w2", 8, 60.0, warmup = true), set("a", 8, 80.0, inherited = false)))
        val fake = Fake(stateOf(two, e2))
        show(fake.state, fake)
        label("w2").assertContentDescriptionEquals("Aquecimento 2")
        label("a").assertContentDescriptionEquals("Série 1")
        load("w2").assertContentDescriptionEquals("Carga do aquecimento 2")
        load("a").assertContentDescriptionEquals("Carga da série 1")
        load("w2").performClick()
        waitForIdle()
        onNodeWithContentDescription("Editando carga do aquecimento 2").assertIsDisplayed()
        reps("w2").performClick()
        waitForIdle()
        onNodeWithContentDescription("Editando repetições do aquecimento 2").assertIsDisplayed()
        openMenu("w2")
        pick(PlannedSetMenuTags.REMOVE)
        toastWith("Aquecimento 2 removido").assertIsDisplayed()
    }

    @Test
    fun undoAfterTheExerciseIsReplacedDoesNotRestore() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3))
        show(fake.state, fake)
        openMenu("e1b")
        pick(PlannedSetMenuTags.REMOVE)
        toastWith("Série 2 removida").assertIsDisplayed()
        // The entry keeps its id and its sets' ids but holds another exercise now.
        fake.editEntries { list -> list.map { if (it.id == "e1") it.copy(exercise = row) else it } }
        waitForIdle()
        onAllNodesWithText("Desfazer").assertCountEquals(0)
        assertTrue(fake.calls.none { it.startsWith("restoreRoutineSet") }, fake.calls.toString())
    }

    @Test
    fun replacingAnExerciseDropsItsDraftsAndNeverWritesTheOldLoad() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3)).apply { echo = false }
        show(fake.state, fake)
        load("e1a").performTextReplacement("20")
        waitForIdle()
        assertEquals("20", load("e1a").editableText())
        fake.editEntries { list ->
            list.map { e ->
                if (e.id != "e1") e else e.copy(exercise = pullUp, setTargets = e.setTargets.map { it.copy(weightKg = null, reps = 12, inherited = true) })
            }
        }
        waitForIdle()
        assertEquals("", load("e1a").editableText())
        assertEquals("12", reps("e1a").editableText())
        reps("e1a").performTextReplacement("13")
        waitForIdle()
        assertEquals("updateRoutineSet:e1a,13,null", fake.writes().last())
    }

    @Test
    fun aFailedWriteLetsTheSavedValueBackAfterTheError() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e2, e3)).apply { echo = false }
        show(fake.state, fake)
        reps("e1a").performTextReplacement("11")
        waitForIdle()
        assertEquals("11", reps("e1a").editableText())
        fake.state.value = fake.state.value.copy(errorCode = UiError.SAVE_FAILED)
        waitForIdle()
        // The row is still in edit (typing focused it): it keeps what was typed until the person leaves it.
        assertEquals("11", reps("e1a").editableText())
        reveal(RoutineEditorTags.header("e1"))
        onNodeWithTag(RoutineEditorTags.header("e1")).performClick()
        waitForIdle()
        assertEquals("8", reps("e1a").editableText())
        // Nothing is waited for any more: the next edit writes again.
        reps("e1a").performTextReplacement("11")
        waitForIdle()
        assertEquals(2, fake.writes().size)
    }

    // (2026-10-31, owner's request) "Copy to all sets".
    private fun Fake.copies() = calls.filter { it.startsWith("copyRoutineSetToAll:") }
    private fun Fake.targetRestores() = calls.filter { it.startsWith("restoreRoutineSetTargets:") }

    @Test
    fun copyToAllIsOfferedOnAWorkingSetWithOtherWorkingSets() = runComposeUiTest {
        show(stateOf(e1, e5))
        // A manual working set and one that follows the record, each with others in its exercise.
        openMenu("e1a")
        onNodeWithTag(PlannedSetMenuTags.COPY_ALL).assertIsDisplayed()
        onNode(hasText("Copiar para todas as séries") and hasAnyAncestor(hasTestTag(PlannedSetMenuTags.COPY_ALL)), useUnmergedTree = true).assertExists()
        pick(PlannedSetMenuTags.NORMAL)
        openMenu("e5b")
        onNodeWithTag(PlannedSetMenuTags.COPY_ALL).assertIsDisplayed()
    }

    @Test
    fun copyToAllIsHiddenOnAWarmupAndOnTheOnlyWorkingSet() = runComposeUiTest {
        val fake = Fake(stateOf(e1, e3, e6))
        show(fake.state, fake)
        openMenu("e1w")
        onAllNodesWithTag(PlannedSetMenuTags.COPY_ALL).assertCountEquals(0)
        pick(PlannedSetMenuTags.WARMUP)
        // The only set of its exercise, and the only working set beside a warm-up.
        openMenu("e3a")
        onAllNodesWithTag(PlannedSetMenuTags.COPY_ALL).assertCountEquals(0)
        pick(PlannedSetMenuTags.NORMAL)
        openMenu("e6a")
        onAllNodesWithTag(PlannedSetMenuTags.COPY_ALL).assertCountEquals(0)
        pick(PlannedSetMenuTags.NORMAL)
        assertTrue(fake.copies().isEmpty(), fake.calls.toString())
    }

    @Test
    fun copyToAllSendsOneActionAndSaysHowManySets() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake)
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        assertEquals(listOf("copyRoutineSetToAll:e5,e5a"), fake.copies())
        assertTrue(fake.writes().isEmpty(), fake.calls.toString())
        onAllNodesWithTag(PlannedSetMenuTags.LIST).assertCountEquals(0)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        // The echo: the working sets show the copy, the warm-up keeps its own.
        assertEquals("80", load("e5b").editableText())
        assertEquals("8", reps("e5c").editableText())
        assertEquals("12", reps("e5w").editableText())
        assertEquals(1, fake.copies().size)
    }

    @Test
    fun copyToastIsSingularForOneSet() = runComposeUiTest {
        val fake = Fake(stateOf(e1))
        show(fake.state, fake)
        openMenu("e1b")
        pick(PlannedSetMenuTags.COPY_ALL)
        assertEquals(listOf("copyRoutineSetToAll:e1,e1b"), fake.copies())
        toastWith("Copiado para 1 série").assertIsDisplayed()
    }

    @Test
    fun copyToastInEnglish() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake, locale = "en")
        openMenu("e5c")
        onNode(hasText("Copy to all sets") and hasAnyAncestor(hasTestTag(PlannedSetMenuTags.COPY_ALL)), useUnmergedTree = true).assertExists()
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copied to 2 sets").assertIsDisplayed()
        onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun undoCopySendsTheRestoreWithThePreviousSets() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake)
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        // One action with every changed set as it stood: the inherited 10 x 60 and the manual 6 x 90.
        assertEquals(
            listOf("restoreRoutineSetTargets:e5,fly,${RoutineSetUi("e5b", 10, 60.0, inherited = true)}|${RoutineSetUi("e5c", 6, 90.0, inherited = false)}"),
            fake.targetRestores(),
        )
        assertEquals("10", reps("e5b").editableText())
        assertEquals("90", load("e5c").editableText())
    }

    @Test
    fun copyDropsATargetsDraftAndUndoKeepsItsPendingWrite() = runComposeUiTest {
        val fake = Fake(stateOf(e5)).apply { echo = false }
        show(fake.state, fake)
        // A write of e5c is in flight (no echo): 15 reps.
        reps("e5c").performTextReplacement("15")
        waitForIdle()
        assertEquals("15", reps("e5c").editableText())
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        // The draft is gone: the row shows what the state holds, and the copy's echo replaces it.
        assertEquals("6", reps("e5c").editableText())
        fake.editEntries { list ->
            list.map { e -> e.copy(setTargets = e.setTargets.map { if (it.id == "e5b" || it.id == "e5c") it.copy(reps = 8, weightKg = 80.0, inherited = false) else it }) }
        }
        waitForIdle()
        assertEquals("8", reps("e5c").editableText())
        assertEquals("80", load("e5c").editableText())
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        // The undo puts back what the database held before the copy: the written 15, manual.
        assertEquals(
            listOf("restoreRoutineSetTargets:e5,fly,${RoutineSetUi("e5b", 10, 60.0, inherited = true)}|${RoutineSetUi("e5c", 15, 90.0, inherited = false)}"),
            fake.targetRestores(),
        )
        assertEquals(1, fake.writes().size)
    }

    @Test
    fun undoCopyAfterTheSetsChangedSaysNotRestored() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake)
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        // A set leaves the exercise (removed elsewhere) while the toast is up.
        fake.editEntries { list -> list.map { e -> e.copy(setTargets = e.setTargets.filter { it.id != "e5c" }) } }
        waitForIdle()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        assertTrue(fake.targetRestores().isEmpty(), fake.calls.toString())
        toastWith("A série não pôde voltar.").assertIsDisplayed()
    }

    @Test
    fun copyUndoIsWithdrawnWhenTheExerciseIsReplaced() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake)
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        // The entry keeps its id and its sets' ids but holds another exercise now: the offer goes, as a removal's does.
        fake.editEntries { list -> list.map { if (it.id == "e5") it.copy(exercise = row) else it } }
        waitForIdle()
        onAllNodesWithText("Desfazer").assertCountEquals(0)
        assertTrue(fake.targetRestores().isEmpty(), fake.calls.toString())
    }

    @Test
    fun copyUndoIsWithdrawnOnceACopiedSetIsEdited() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake)
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        // Editing a copied set: an undo now would wipe that edit out, so it is no longer offered.
        reps("e5c").performTextReplacement("9")
        waitForIdle()
        assertEquals("updateRoutineSet:e5c,9,80.0", fake.writes().last())
        onAllNodesWithText("Desfazer").assertCountEquals(0)
        // The same for a copied set that follows the record again.
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        openMenu("e5b")
        pick(PlannedSetMenuTags.FOLLOW)
        onAllNodesWithText("Desfazer").assertCountEquals(0)
        // Editing the source keeps the offer: the undo never touches it.
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        reps("e5a").performTextReplacement("7")
        waitForIdle()
        onNodeWithText("Desfazer").assertIsDisplayed()
        assertTrue(fake.targetRestores().isEmpty(), fake.calls.toString())
    }

    @Test
    fun aDoubleTapOnCopyCopiesOnceAndKeepsTheSnapshot() = runComposeUiTest {
        val fake = Fake(stateOf(e5)).apply { echo = false }
        show(fake.state, fake)
        reps("e5c").performTextReplacement("15")
        waitForIdle()
        openMenu("e5a")
        // Both taps land before the sheet leaves.
        onNodeWithTag(PlannedSetMenuTags.COPY_ALL).performTouchInput {
            click()
            advanceEventTime(50)
            click()
        }
        waitForIdle()
        assertEquals(listOf("copyRoutineSetToAll:e5,e5a"), fake.copies())
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        // The snapshot is the first tap's, with the write in flight.
        assertEquals(
            listOf("restoreRoutineSetTargets:e5,fly,${RoutineSetUi("e5b", 10, 60.0, inherited = true)}|${RoutineSetUi("e5c", 15, 90.0, inherited = false)}"),
            fake.targetRestores(),
        )
    }

    @Test
    fun undoCopyAfterASetIsAddedOrBecomesAWarmupSaysNotRestored() = runComposeUiTest {
        val fake = Fake(stateOf(e5))
        show(fake.state, fake)
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 2 séries").assertIsDisplayed()
        // A set is added elsewhere while the toast is up.
        fake.editEntries { list -> list.map { e -> e.copy(setTargets = e.setTargets + set("e5d", 8, 80.0, inherited = false)) } }
        waitForIdle()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        toastWith("A série não pôde voltar.").assertIsDisplayed()
        // A copied set becomes a warm-up elsewhere.
        openMenu("e5a")
        pick(PlannedSetMenuTags.COPY_ALL)
        toastWith("Copiado para 3 séries").assertIsDisplayed()
        fake.editSet("e5b") { it.copy(warmup = true, inherited = false) }
        waitForIdle()
        onNodeWithText("Desfazer").performClick()
        waitForIdle()
        toastWith("A série não pôde voltar.").assertIsDisplayed()
        assertTrue(fake.targetRestores().isEmpty(), fake.calls.toString())
    }
}
