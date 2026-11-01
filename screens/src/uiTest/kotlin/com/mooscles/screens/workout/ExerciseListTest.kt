package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.abs
import com.mooscles.screens.assertShownWhole
import com.mooscles.designsystem.components.setRowColumnTag
import com.mooscles.designsystem.components.SetRowPreviousTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.ExerciseCardCaptionTag
import com.mooscles.designsystem.components.ExerciseCardDoneTag
import com.mooscles.designsystem.components.ExerciseCardProgressTag
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.ExerciseMediaFigureTag
import com.mooscles.designsystem.components.ExerciseMediaPlaceholderTag
import com.mooscles.designsystem.components.ExerciseHeaderToggleTag
import com.mooscles.designsystem.components.ExerciseMenuTag
import com.mooscles.designsystem.components.PrBadgeTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.data.ExerciseCatalog
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The exercise cards of the active workout as lazy list items (spec "Lista", "Card do
 * exercício", D9): the header, the column header, the rows and the footer, the advance to the
 * next exercise and the exercise menu. The list is laid out as the screen lays it out, with the
 * advance and the outside-tap scope beside it.
 */
@OptIn(ExperimentalTestApi::class)
class ExerciseListTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")
    private val curl = ExerciseUi("curl", "Rosca alternada", "Biceps", "Dumbbell", convention = "Per hand")
    private val pullUp = ExerciseUi("apu", "Barra assistida", "Back", "Selectorized machine", convention = "Assistance")

    /**
     * Records every call and, like the database emission, writes `updateSet` into [exercises]
     * (only with [echo]) and drops the entry of `removeWorkoutExercise`.
     */
    private class Actions(
        val exercises: MutableState<List<WorkoutExerciseUi>>,
        val echo: Boolean = true,
        val recording: RecordingActions = RecordingActions(),
    ) : MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) {
            recording.updateSet(setId, reps, weightKg, completed, warmup, assistance)
            if (!echo) return
            exercises.value = exercises.value.map { entry ->
                entry.copy(
                    sets = entry.sets.map {
                        if (it.id == setId) it.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance) else it
                    },
                )
            }
        }

        override fun removeWorkoutExercise(entryId: String) {
            recording.removeWorkoutExercise(entryId)
            exercises.value = exercises.value.filter { it.id != entryId }
        }
    }

    private class Shown {
        lateinit var screen: ActiveWorkoutScreenState
        lateinit var list: LazyListState

        /** What the cards asked of the screen through their callbacks. */
        val callbacks = mutableListOf<String>()
    }

    /**
     * The cards of [exercises] in a lazy list with no spacing of its own, the advance beside it,
     * the outside-tap scope around it and the "How to log" sheet the screen opens.
     */
    private fun ComposeUiTest.showList(
        exercises: MutableState<List<WorkoutExerciseUi>>,
        actions: MoosclesActions,
        locale: String? = "pt-BR",
        reducedMotion: Boolean = false,
        pounds: Boolean = false,
        size: DpSize = DpSize(412.dp, 800.dp),
        fontScale: Float = 1f,
        probe: ((String) -> Unit)? = null,
    ): Shown {
        val shown = Shown()
        withScreen(locale = locale, reducedMotion = reducedMotion, size = size, fontScale = fontScale, probe = probe) {
            val screen = rememberActiveWorkoutScreenState()
            val list = rememberLazyListState()
            shown.screen = screen
            shown.list = list
            var howToLog by remember { mutableStateOf<WorkoutExerciseUi?>(null) }
            val callbacks = remember(screen) {
                ExerciseCallbacks(
                    onRestChip = { shown.callbacks += "rest:${it.id}" },
                    onSwap = { shown.callbacks += "swap:${it.id}" },
                    onAbout = { shown.callbacks += "about:$it" },
                    onHistory = { shown.callbacks += "history:$it" },
                    onHowToLog = { howToLog = it },
                    onChangeType = { set, _ -> screen.openTypeSheet(set.id) },
                    onRemoveSet = { screen.removeSet(it, actions) },
                )
            }
            val current = exercises.value
            Box(Modifier.fillMaxSize().endsSetEditingOnOutsideTap(screen)) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag(LIST),
                    state = list,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    exerciseCards(current, pounds, screen, actions, callbacks)
                }
            }
            CardAdvance(current, screen, list)
            howToLog?.let { HowToLogSheet(it) { howToLog = null } }
        }
        waitForIdle()
        return shown
    }

    private fun sets(prefix: String, count: Int, done: Int = 0) =
        (1..count).map { SetUi("$prefix$it", reps = 6, weightKg = 80.0, completed = it <= done) }

    private fun row(id: String) = hasTestTag(workoutSetRowTag(id))

    private fun ComposeUiTest.header(entryId: String): SemanticsNodeInteraction = onNodeWithTag(exerciseHeaderTag(entryId))

    private fun ComposeUiTest.inHeader(entryId: String, tag: String): SemanticsNodeInteraction =
        onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId))), useUnmergedTree = true)

    private fun ComposeUiTest.toggle(entryId: String) {
        inHeader(entryId, ExerciseHeaderToggleTag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openMenu(entryId: String) {
        inHeader(entryId, ExerciseMenuTag).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.check(id: String): SemanticsNodeInteraction = onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(row(id)))

    private fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(row(id)), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.editableText(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun ComposeUiTest.rowDescription(id: String): String =
        onNodeWithTag(workoutSetRowTag(id), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString(" ")

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag))
        waitForIdle()
    }

    /** Whether the rows (and footer) of [entryId] are in the list: the card is open. */
    private fun ComposeUiTest.isOpen(entryId: String): Boolean =
        onAllNodesWithTag(exerciseColumnsTag(entryId)).fetchSemanticsNodes().isNotEmpty()

    /**
     * Checks the column titles of [entryId] against its row [setId] and returns whether they are
     * shown. Shown, the row is one line and each title is whole (one line, as wide as it needs)
     * over its cell: the set's before the previous cell, the previous one at its start, the load and
     * the reps centered. Hidden, the item is 0 tall. Over a two-line row they must be hidden.
     */
    private fun ComposeUiTest.columnsShownAndAligned(entryId: String, setId: String): Boolean {
        waitForIdle()
        fun cell(tag: String): Rect = onNode(hasTestTag(tag) and hasAnyAncestor(row(setId)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val previous = cell(SetRowPreviousTag)
        val load = cell(SetRowLoadTag)
        val reps = cell(SetRowRepsTag)
        val oneLine = load.top < previous.bottom
        val columns = onNodeWithTag(exerciseColumnsTag(entryId), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        if (columns.height == 0f) {
            return false
        }
        assertTrue(oneLine, "titles over a two-line row: previous $previous, load $load")
        val titles = (0..3).map {
            onNode(hasTestTag(setRowColumnTag(it)) and hasAnyAncestor(hasTestTag(exerciseColumnsTag(entryId))), useUnmergedTree = true).fetchSemanticsNode()
        }
        for (node in titles) {
            val results = mutableListOf<TextLayoutResult>()
            val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            assertTrue(action != null && action(results) && results.isNotEmpty(), "a column title has no layout")
            assertShownWhole(results.first(), "column title")
        }
        val (set, prev, unit, repsTitle) = titles.map { it.boundsInRoot }
        assertTrue(set.right <= previous.left, "the set title stays over the index cell: $set, $previous")
        assertTrue(abs(prev.left - previous.left) < 1f, "previous starts with its cell: $prev, $previous")
        assertTrue(abs(unit.center.x - load.center.x) < 1f, "load centered: $unit, $load")
        assertTrue(abs(repsTitle.center.x - reps.center.x) < 1f, "reps centered: $repsTitle, $reps")
        return true
    }

    @Test
    fun largeExerciseComposesOnlyVisibleRows() = runComposeUiTest {
        val rows = HashSet<String>()
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 100))))
        val shown = showList(exercises, Actions(exercises), probe = { if (it.startsWith("row-")) rows += it })
        onNode(row("s1")).assertExists()
        onNode(row("s100")).assertDoesNotExist()
        assertTrue(rows.size in 1..20, "100 rows must not compose 100 rows: ${rows.size} composed")
        val seen = rows.size

        scrollTo(workoutSetRowTag("s100"))
        onNode(row("s100")).assertIsDisplayed()
        onNode(row("s1")).assertDoesNotExist()
        assertTrue(shown.list.firstVisibleItemIndex > 80, "the list really scrolled: ${shown.list.firstVisibleItemIndex}")
        assertTrue(rows.size > seen, "the rows scrolled to are composed and reported")
    }

    /** Review Focus 3: the same exercise twice keeps its own values, record and previous pairs. */
    @Test
    fun repeatedInstancesKeepTheirOwnValues() = runComposeUiTest {
        val previous = listOf(PreviousSetUi(40.0, 10, true, false), PreviousSetUi(80.0, 5, false, false), PreviousSetUi(82.5, 5, false, false))
        val record = SetRecordUi(RecordKind.WEIGHT, previousKg = 85.0)
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi(
                    "e1", bench,
                    // A warm-up first here and not in e2: each instance pairs by its own sets.
                    sets = listOf(SetUi("a0", reps = 10, weightKg = 40.0, warmup = true), SetUi("a1", reps = 5, weightKg = 90.0), SetUi("a2", reps = 5, weightKg = 90.0)),
                    previousSets = previous,
                ),
                WorkoutExerciseUi(
                    "e2", bench,
                    sets = listOf(SetUi("b1", reps = 5, weightKg = 90.0, completed = true, record = record), SetUi("b2", reps = 5, weightKg = 90.0)),
                    previousSets = previous,
                ),
            ),
        )
        val actions = Actions(exercises)
        showList(exercises, actions)
        // Both open by hand, so the advance leaves them open when the order changes.
        toggle("e2")
        toggle("e1")
        toggle("e1")

        // Each instance pairs the previous session with its own sets.
        assertTrue("40 quilos por 10" in rowDescription("a0"), rowDescription("a0"))
        assertTrue("80 quilos por 5" in rowDescription("a1"), rowDescription("a1"))
        assertTrue("82,5 quilos por 5" in rowDescription("a2"), rowDescription("a2"))
        scrollTo(workoutSetRowTag("b2"))
        assertTrue("80 quilos por 5" in rowDescription("b1"), rowDescription("b1"))
        assertTrue("82,5 quilos por 5" in rowDescription("b2"), rowDescription("b2"))
        // The record is the one the domain chose, not the equal set of the other instance.
        onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("b1")), useUnmergedTree = true).assertExists()
        scrollTo(workoutSetRowTag("a1"))
        assertEquals(0, onAllNodes(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("a1")), useUnmergedTree = true).fetchSemanticsNodes().size)

        field("a1", SetRowRepsTag).performTextReplacement("12")
        waitForIdle()
        check("a1").performClick()
        waitForIdle()
        assertEquals(SetUi("a1", reps = 12, weightKg = 90.0, completed = true), exercises.value[0].sets[1])
        assertEquals(listOf(SetUi("b1", reps = 5, weightKg = 90.0, completed = true, record = record), SetUi("b2", reps = 5, weightKg = 90.0)), exercises.value[1].sets)

        // Reordered (as moving it does), each instance keeps its own rows.
        exercises.value = exercises.value.reversed()
        waitForIdle()
        scrollTo(workoutSetRowTag("b2"))
        assertEquals("5", field("b2", SetRowRepsTag).editableText())
        onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("b1")), useUnmergedTree = true).assertExists()
        scrollTo(workoutSetRowTag("a1"))
        assertEquals("12", field("a1", SetRowRepsTag).editableText())
        assertEquals("90", field("a1", SetRowLoadTag).editableText())
        assertEquals(0, onAllNodes(hasTestTag(PrBadgeTag) and hasAnyAncestor(row("a1")), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun metaLineIsLocalized() {
        for ((locale, texts) in listOf(
            "pt-BR" to listOf("Barra · Peito · série 4 de 5", "Halter · Bíceps", "0 de 3 séries"),
            "en" to listOf("Barbell · Chest · set 4 of 5", "Dumbbell · Biceps", "0 of 3 sets"),
        )) runComposeUiTest {
            val exercises = mutableStateOf(
                listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 5, done = 3)), WorkoutExerciseUi("e2", curl, sets = sets("c", 3))),
            )
            showList(exercises, Actions(exercises), locale = locale)
            onNode(hasText(texts[0]) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).assertIsDisplayed()
            // Collapsed: the meta without the progress, and the count of sets done under it.
            onNode(hasText(texts[1]) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e2"))), useUnmergedTree = true).assertIsDisplayed()
            assertEquals(texts[2], inHeader("e2", ExerciseCardProgressTag).text())
            assertEquals(0, onAllNodes(hasTestTag(ExerciseCardProgressTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun supportLineShowsRestChipAndPreviousDate() = runComposeUiTest {
        val zone = TimeZone.currentSystemDefault()
        val october21 = LocalDateTime(2026, 10, 21, 12, 0).toInstant(zone).toEpochMilliseconds()
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 2), restSeconds = 120, previousDate = october21),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 2), restSeconds = 90),
            ),
        )
        val shown = showList(exercises, Actions(exercises))
        onNode(hasText("Descanso 2:00") and hasAnyAncestor(hasTestTag(exerciseRestTag("e1"))), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Anterior: 21 out") and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(exerciseRestTag("e1")).performClick()
        waitForIdle()
        assertEquals(listOf("rest:e1"), shown.callbacks)

        toggle("e2")
        onNode(hasText("Descanso 1:30") and hasAnyAncestor(hasTestTag(exerciseRestTag("e2"))), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Primeira vez") and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e2"))), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun conventionCaptionOnlyForNonTotalLoad() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        showList(exercises, Actions(exercises))
        toggle("e2")
        assertEquals(0, onAllNodes(hasTestTag(ExerciseCardCaptionTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag("e1"))), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("Por mão: registre a carga de um halter.", inHeader("e2", ExerciseCardCaptionTag).text())
    }

    @Test
    fun columnHeaderIsHiddenFromScreenReaders() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 2))))
        showList(exercises, Actions(exercises))
        assertTrue(columnsShownAndAligned("e1", "s1"), "412 dp at 1x: one-line rows, with titles")
        // The merged tree is what a screen reader walks: a cleared node's children are not in it.
        val inside = SemanticsMatcher("text") { it.config.getOrNull(SemanticsProperties.Text) != null } and
            hasAnyAncestor(hasTestTag(exerciseColumnsTag("e1")))
        assertEquals(0, onAllNodes(inside).fetchSemanticsNodes().size, "no column title reaches a screen reader")
        for (title in listOf("SÉRIE", "ANTERIOR", "REPS", "KG")) {
            assertEquals(0, onAllNodes(hasText(title)).fetchSemanticsNodes().size, title)
        }
        // Drawn, though: the titles are there below the cleared node.
        assertEquals(4, onAllNodes(inside, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    /** Sets with a previous result of [previousKg] × [previousReps], as a returning exercise has. */
    private fun withPrevious(prefix: String, count: Int, previousKg: Double = 82.5, previousReps: Int = 10) = WorkoutExerciseUi(
        "e1", bench, sets = sets(prefix, count),
        previousSets = List(count) { PreviousSetUi(previousKg, previousReps, false, false) },
    )

    @Test
    fun columnTitlesAt412FollowTheRow() {
        // The row has 340 dp at 412 dp and the fixed cells take 280. A first-time exercise ("—" as
        // previous) is one line, and so is the usual returning one: the previous result is shown
        // without its unit ("82,5 × 10", the title says "kg"), as on the board. Only a much wider
        // previous result ("1002,75 × 100") makes the row two lines, and then the titles go.
        for ((exercise, shown) in listOf(
            WorkoutExerciseUi("e1", bench, sets = sets("s", 2)) to true,
            withPrevious("s", 2) to true,
            withPrevious("s", 2, previousKg = 1002.75, previousReps = 100) to false,
        )) runComposeUiTest {
            val exercises = mutableStateOf(listOf(exercise))
            showList(exercises, Actions(exercises), size = DpSize(412.dp, 800.dp))
            assertEquals(shown, columnsShownAndAligned("e1", "s1"), "previous sets: ${exercise.previousSets}")
        }
    }

    @Test
    fun columnTitlesAt320AreHiddenOrWhole() {
        for (locale in listOf("pt-BR", "en")) runComposeUiTest {
            val exercises = mutableStateOf(listOf(withPrevious("s", 2)))
            showList(exercises, Actions(exercises), locale = locale, size = DpSize(320.dp, 800.dp))
            // Either answer is right, as long as it matches the row: titles only over one line.
            columnsShownAndAligned("e1", "s1")
        }
    }

    @Test
    fun columnTitlesHideWhenRowsAreTwoLines() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(withPrevious("s", 2)))
        showList(exercises, Actions(exercises), size = DpSize(412.dp, 800.dp), fontScale = 1.5f)
        assertFalse(columnsShownAndAligned("e1", "s1"), "at 1.5x the rows are two lines, so no titles")
    }

    @Test
    fun columnUnitFollowsUnitAndConvention() {
        for (locale in listOf("pt-BR", "en")) runComposeUiTest {
            val units = mutableListOf<String>()
            withScreen(locale = locale) {
                for (convention in listOf(bench.convention, curl.convention, pullUp.convention)) {
                    for (pounds in listOf(false, true)) units += columnUnit(convention, pounds)
                }
            }
            waitForIdle()
            assertEquals(listOf("kg", "lb", "kg", "lb", "kg assist.", "lb assist."), units.take(6), locale)
        }
    }

    @Test
    fun completingTheLastPendingCollapsesAndOpensTheNext() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 8, done = 7)),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 6)),
                WorkoutExerciseUi("e3", pullUp, sets = sets("p", 2)),
            ),
        )
        val shown = showList(exercises, Actions(exercises), size = DpSize(412.dp, 640.dp))
        assertTrue(isOpen("e1"))
        assertFalse(isOpen("e2"))
        scrollTo(workoutSetRowTag("s8"))

        mainClock.autoAdvance = false
        check("s8").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MoosclesTiming().cardAdvance - 100)
        // The check is seen first: the card waits before it collapses.
        assertTrue(isOpen("e1"), "e1 stays open during timing.cardAdvance")
        assertFalse(isOpen("e2"))

        mainClock.advanceTimeBy(200)
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
        assertFalse(isOpen("e1"))
        assertTrue(isOpen("e2"))
        assertFalse(isOpen("e3"))
        // The list scrolled to the header of the next exercise, right after the collapsed e1.
        assertEquals(1, shown.list.firstVisibleItemIndex)
        assertEquals(0, shown.list.firstVisibleItemScrollOffset)
        scrollTo(exerciseHeaderTag("e1"))
        assertEquals("8 de 8 séries", inHeader("e1", ExerciseCardProgressTag).text())
        inHeader("e1", ExerciseCardDoneTag).assertExists()
    }

    @Test
    fun reducedMotionWaitsThenSwitchesWithoutAnimation() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 8, done = 7)),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 6)),
                // Below e2, so the list can bring e2's header to the top.
                WorkoutExerciseUi("e3", pullUp, sets = sets("p", 2)),
            ),
        )
        val shown = showList(exercises, Actions(exercises), reducedMotion = true, size = DpSize(412.dp, 640.dp))
        scrollTo(workoutSetRowTag("s8"))

        mainClock.autoAdvance = false
        check("s8").performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(MoosclesTiming().cardAdvance - 100)
        // The wait is time to see the check, not motion: it stays.
        assertTrue(isOpen("e1"), "e1 stays open during timing.cardAdvance")
        assertFalse(isOpen("e2"))

        // Then the switch and the jump take a couple of frames, with no animation to wait for.
        mainClock.advanceTimeBy(200)
        repeat(3) { mainClock.advanceTimeByFrame() }
        assertFalse(isOpen("e1"))
        assertTrue(isOpen("e2"))
        assertEquals(1, shown.list.firstVisibleItemIndex)
        assertEquals(0, shown.list.firstVisibleItemScrollOffset)
        mainClock.autoAdvance = true
    }

    @Test
    fun manualToggleStaysPut() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(
                WorkoutExerciseUi("e1", bench, sets = sets("s", 1)),
                WorkoutExerciseUi("e2", curl, sets = sets("c", 2)),
                WorkoutExerciseUi("e3", pullUp, sets = sets("p", 1)),
            ),
        )
        val shown = showList(exercises, Actions(exercises))
        // The person opens e3, and opens and closes e2 again.
        toggle("e3")
        toggle("e2")
        toggle("e2")
        assertTrue(isOpen("e1"))
        assertFalse(isOpen("e2"))
        assertTrue(isOpen("e3"))

        check("s1").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(MoosclesTiming().cardAdvance + 100)
        waitForIdle()
        // e1 collapses on its own; e2, closed by hand, stays closed; e3, opened by hand, stays open.
        assertFalse(isOpen("e1"))
        assertFalse(isOpen("e2"))
        assertTrue(isOpen("e3"))
        assertEquals(0, shown.list.firstVisibleItemIndex, "nothing opened, so the list does not move")

        // A tap on a header the advance closed opens it again.
        toggle("e1")
        assertTrue(isOpen("e1"))
    }

    @Test
    fun menuHasTheSevenItems() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        showList(exercises, Actions(exercises))
        openMenu("e1")
        val labels = listOf(
            "Sobre o exercício", "Progresso do exercício", "Trocar exercício", "Mover para cima", "Mover para baixo", "Como registrar", "Remover exercício",
        )
        val tops = ExerciseMenuTags.all.mapIndexed { i, tag ->
            val item = onNodeWithTag(tag)
            item.assertIsDisplayed()
            assertEquals(labels[i], onNode(hasText(labels[i]) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).text())
            item.fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops, "in this order")
        // The first exercise cannot move up; the others can move.
        onNodeWithTag(ExerciseMenuTags.MOVE_UP).assertIsNotEnabled()
        onNodeWithTag(ExerciseMenuTags.MOVE_DOWN).assertIsEnabled()

        // "How to log" opens the full explanation of the convention.
        onNodeWithTag(ExerciseMenuTags.HOW_TO_LOG).performClick()
        waitForIdle()
        onNodeWithTag(ExerciseMenuTags.HOW_TO_LOG).assertDoesNotExist()
        onNodeWithText("Como registrar").assertIsDisplayed()
        onNodeWithText("Carga total").assertIsDisplayed()
        onNodeWithText("Registre a carga externa total; volume = carga registrada × repetições.").assertIsDisplayed()
    }

    @Test
    fun removeExerciseConfirms() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        val actions = Actions(exercises)
        showList(exercises, actions)
        val text = "Remover Supino reto? As 3 séries dele saem deste treino."

        // One set: the singular.
        openMenu("e2")
        onNodeWithTag(ExerciseMenuTags.REMOVE).performClick()
        waitForIdle()
        onNodeWithText("Remover Rosca alternada? A 1 série dele sai deste treino.").assertIsDisplayed()
        onNodeWithText("Continuar treinando").performClick()
        waitForIdle()

        // "Keep training" leaves the exercise in the workout.
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.REMOVE).performClick()
        waitForIdle()
        onNodeWithText(text).assertIsDisplayed()
        onNodeWithText("Continuar treinando").performClick()
        waitForIdle()
        onNodeWithText(text).assertDoesNotExist()
        assertEquals(emptyList(), actions.calls)

        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.REMOVE).performClick()
        waitForIdle()
        onNodeWithText("Remover").performClick()
        waitForIdle()
        assertEquals(listOf("removeWorkoutExercise:e1"), actions.calls)
        header("e1").assertDoesNotExist()
        onNodeWithText(text).assertDoesNotExist()
    }

    @Test
    fun swapWithCompletedSetsConfirms() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3, done = 1))))
        val actions = Actions(exercises)
        val shown = showList(exercises, actions)
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
        waitForIdle()
        onNodeWithText("Trocar Supino reto? As séries registradas dele saem deste treino.").assertIsDisplayed()
        assertEquals(emptyList(), shown.callbacks, "the picker waits for the confirmation")
        onNodeWithText("Trocar").performClick()
        waitForIdle()
        assertEquals(listOf("swap:e1"), shown.callbacks)
        assertEquals(emptyList(), actions.calls, "the picker writes the swap, not the menu")
    }

    @Test
    fun swapAsksWhileACheckIsNotSavedYet() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3))))
        // No echo: the check is drawn done, the database still says pending.
        val actions = Actions(exercises, echo = false)
        val shown = showList(exercises, actions)
        check("s1").performClick()
        waitForIdle()
        assertTrue(shown.screen.completedOf(exercises.value[0].sets[0]))
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
        waitForIdle()
        onNodeWithText("Trocar Supino reto? As séries registradas dele saem deste treino.").assertIsDisplayed()
        assertEquals(emptyList(), shown.callbacks)
    }

    @Test
    fun swapWithoutCompletedSetsOpensThePickerDirectly() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 3))))
        val shown = showList(exercises, Actions(exercises))
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.SWAP).performClick()
        waitForIdle()
        assertEquals(listOf("swap:e1"), shown.callbacks)
        onNodeWithText("Trocar Supino reto?", substring = true).assertDoesNotExist()
        onNodeWithTag(ExerciseMenuTags.SWAP).assertDoesNotExist()
    }

    @Test
    fun moveUpAndDownCallMove() = runComposeUiTest {
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        val actions = Actions(exercises)
        showList(exercises, actions)
        openMenu("e2")
        onNodeWithTag(ExerciseMenuTags.MOVE_DOWN).assertIsNotEnabled()
        onNodeWithTag(ExerciseMenuTags.MOVE_UP).performClick()
        waitForIdle()
        assertEquals(listOf("moveWorkoutExercise:e2,-1"), actions.calls)
        onNodeWithTag(ExerciseMenuTags.MOVE_UP).assertDoesNotExist()

        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.MOVE_DOWN).performClick()
        waitForIdle()
        assertEquals(listOf("moveWorkoutExercise:e2,-1", "moveWorkoutExercise:e1,1"), actions.calls)
    }

    @Test
    fun cardShowsTheStillFigureOrTheEquipmentIcon() = runComposeUiTest {
        // Pre-flight F2 (spec D5): a catalog exercise with a figure shows its still frame B in the
        // card's 64 dp tile, with the equipment's small icon beside the meta; one without, the icon.
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_barbell_bench_flat" }
        val pilot = ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
        val exercises = mutableStateOf(
            listOf(WorkoutExerciseUi("e1", pilot, sets = sets("s", 1)), WorkoutExerciseUi("e2", curl, sets = sets("c", 1))),
        )
        showList(exercises, Actions(exercises))
        inHeader("e1", ExerciseMediaFigureTag).assertIsDisplayed().assertWidthIsEqualTo(64.dp)
        inHeader("e1", ExerciseMediaPlaceholderTag).assertDoesNotExist()
        inHeader("e1", ExerciseEquipmentIconTag).assertIsDisplayed().assertWidthIsEqualTo(16.dp)
        inHeader("e2", ExerciseMediaFigureTag).assertDoesNotExist()
        inHeader("e2", ExerciseEquipmentIconTag).assertIsDisplayed().assertWidthIsEqualTo(28.dp)
    }

    @Test
    fun historyCallsBackWithTheExerciseId() = runComposeUiTest {
        val exercises = mutableStateOf(listOf(WorkoutExerciseUi("e1", bench, sets = sets("s", 1))))
        val shown = showList(exercises, Actions(exercises))
        openMenu("e1")
        onNodeWithTag(ExerciseMenuTags.HISTORY).performClick()
        waitForIdle()
        assertEquals(listOf("history:bench"), shown.callbacks)
        onNodeWithTag(ExerciseMenuTags.HISTORY).assertDoesNotExist()
    }

    private companion object {
        const val LIST = "exercise-list"
    }
}
