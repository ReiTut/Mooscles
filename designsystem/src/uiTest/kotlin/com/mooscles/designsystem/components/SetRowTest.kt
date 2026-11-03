package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.designsystem.theme.MTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.withTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesTargets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetRowTest {
    private fun state(
        status: SetStatus = SetStatus.Pending,
        type: SetType = SetType.Normal,
        previous: String? = "80 kg × 6",
        previousSpoken: String? = "80 kilograms by 6",
        load: String = "85",
        loadSpoken: String = "85 kilograms",
        reps: Int = 6,
        index: Int = 4,
        loadSuggested: Boolean = false,
        repsSuggested: Boolean = false,
        editing: Boolean = false,
    ) = SetRowState(
        index = index, total = 5, previous = previous, previousSpoken = previousSpoken,
        load = load, loadSpoken = loadSpoken, reps = reps, type = type, status = status,
        loadSuggested = loadSuggested, repsSuggested = repsSuggested, editing = editing,
    )

    @androidx.compose.runtime.Composable
    private fun Row(
        state: SetRowState,
        onComplete: (Boolean) -> Unit = {},
        onLoad: () -> Unit = {},
        onReps: () -> Unit = {},
        onType: () -> Unit = {},
        onRemove: () -> Unit = {},
        onEditRow: (() -> Unit)? = null,
        onCopyPrevious: (() -> Unit)? = null,
        recordHaptic: Boolean = true,
    ) = SetRow(
        state, onComplete, onLoad, onReps, onType, onRemove, modifier = Modifier.testTag("row"),
        onEditRow = onEditRow, onCopyPrevious = onCopyPrevious, recordHaptic = recordHaptic,
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun readsAsOneNodeWithTheSpecLabel() {
        runComposeUiTest {
            withTheme { ProvideAppLocale("en") { Row(state()) } }
            waitForIdle()
            onNodeWithTag("row").assertContentDescriptionEquals(
                "Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, 6 reps, not completed",
            )
        }
        runComposeUiTest {
            withTheme {
                ProvideAppLocale("pt-BR") {
                    Row(state(previousSpoken = "80 quilos por 6", loadSpoken = "85 quilos"))
                }
            }
            waitForIdle()
            onNodeWithTag("row").assertContentDescriptionEquals(
                "Série 4 de 5, anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída",
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun readsWithoutPrevious() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { Row(state(previous = null, previousSpoken = null, status = SetStatus.Completed)) } }
        waitForIdle()
        onNodeWithTag("row").assertContentDescriptionEquals("Set 4 of 5, load 85 kilograms, 6 reps, completed")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun exposesFiveCustomActionsThatCallBack() = runComposeUiTest {
        val calls = mutableListOf<String>()
        withTheme {
            ProvideAppLocale("en") {
                Row(
                    state(),
                    onComplete = { calls += "complete:$it" },
                    onLoad = { calls += "load" },
                    onReps = { calls += "reps" },
                    onType = { calls += "type" },
                    onRemove = { calls += "remove" },
                )
            }
        }
        waitForIdle()
        val actions = onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(
            listOf("Complete set", "Edit load", "Edit reps", "Change type", "Remove set"),
            actions.map { it.label },
        )
        runOnUiThread { actions.forEach { it.action() } }
        assertEquals(listOf("complete:true", "load", "reps", "type", "remove"), calls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun completeActionFollowsTheState() {
        for ((lang, expected) in listOf("en" to "Mark set as not done", "pt-BR" to "Marcar série como não feita")) {
            runComposeUiTest {
                val calls = mutableListOf<Boolean>()
                withTheme { ProvideAppLocale(lang) { Row(state(status = SetStatus.Completed), onComplete = { calls += it }) } }
                waitForIdle()
                val actions = onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
                assertEquals(expected, actions.first().label)
                runOnUiThread { actions.first().action() }
                assertEquals(listOf(false), calls)
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editableFieldsStayReachableForScreenReaders() = runComposeUiTest {
        var load by mutableStateOf("85")
        var reps by mutableStateOf("6")
        withTheme {
            ProvideAppLocale("en") {
                SetRow(
                    state(editing = true), {}, {}, {}, {}, {}, modifier = Modifier.testTag("row"),
                    loadField = { NumericField(load, { load = it }, NumericKind.Load, "Load") },
                    repsField = { NumericField(reps, { reps = it }, NumericKind.Reps, "Reps") },
                )
            }
        }
        waitForIdle()
        val fields = onAllNodes(hasSetTextAction())
        assertEquals(2, fields.fetchSemanticsNodes().size)
        assertEquals("85", fields[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        assertEquals("6", fields[1].fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text)
        fields[0].performTextReplacement("90")
        fields[1].performTextReplacement("8")
        waitForIdle()
        assertEquals("90", load)
        assertEquals("8", reps)
        val row = onNodeWithTag("row").fetchSemanticsNode().config
        assertEquals(listOf("Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, 6 reps, not completed"), row.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(5, row.getOrNull(SemanticsActions.CustomActions).orEmpty().size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recordBadgeFinishesItsEntranceInsideARow() {
        for (reduced in listOf(false, true)) {
            var settled: Rect? = null
            runComposeUiTest {
                withTheme(reducedMotion = reduced) { Row(state(status = SetStatus.CompletedRecord)) }
                waitForIdle()
                settled = onNodeWithTag(PrBadgeTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            }
            runComposeUiTest {
                var status by mutableStateOf(SetStatus.Pending)
                withTheme(reducedMotion = reduced) { Row(state(status = status, editing = status == SetStatus.Pending)) }
                waitForIdle()
                status = SetStatus.CompletedRecord
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                val bounds = onNodeWithTag(PrBadgeTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(settled!!.top, bounds.top, 0.5f, "reduced=$reduced top")
                assertEquals(settled!!.height, bounds.height, 0.5f, "reduced=$reduced height")
                assertEquals(settled!!.width, bounds.width, 0.5f, "reduced=$reduced width")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun splitsIntoTwoLinesAtOneAndAHalf() {
        for ((scale, split) in listOf(1f to false, 1.5f to true)) {
            runComposeUiTest {
                withTheme(fontScale = scale) { Row(state()) }
                waitForIdle()
                val previous = onNodeWithTag(SetRowPreviousTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val load = onNodeWithTag(SetRowLoadTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                if (split) {
                    assertTrue(previous.bottom <= load.top + 0.5f, "previous ${previous.bottom} not above load ${load.top}")
                } else {
                    assertEquals(previous.top, load.top, 0.5f)
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recordShowsTheBadgeAndFiresTheRecordHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Pending)
        withTheme(haptics = haptics) { ProvideAppLocale("en") { Row(state(status = status)) } }
        waitForIdle()
        onNodeWithTag(PrBadgeTag, useUnmergedTree = true).assertDoesNotExist()
        status = SetStatus.CompletedRecord
        waitForIdle()
        onNodeWithTag(PrBadgeTag, useUnmergedTree = true).assertExists()
        assertEquals(listOf(HapticEvent.PersonalRecord), haptics.events.toList())
        onNodeWithTag("row").assertContentDescriptionEquals(
            "Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, 6 reps, completed, personal record",
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recordReplacesTheCompleteHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Pending)
        withTheme(haptics = haptics) { Row(state(status = status)) }
        waitForIdle()
        assertEquals(emptyList(), haptics.events.toList())
        status = SetStatus.CompletedRecord
        waitForIdle()
        assertEquals(listOf(HapticEvent.PersonalRecord), haptics.events.toList())
        status = SetStatus.Pending
        waitForIdle()
        assertEquals(listOf(HapticEvent.PersonalRecord, HapticEvent.SetUnchecked), haptics.events.toList())
        status = SetStatus.Completed
        waitForIdle()
        assertEquals(listOf(HapticEvent.PersonalRecord, HapticEvent.SetUnchecked, HapticEvent.SetCompleted), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun typeIsALetterNotOnlyColor() {
        for ((tag, type, letter) in listOf(
            Triple("en", SetType.Warmup, "W"), Triple("pt-BR", SetType.Warmup, "A"),
            Triple("en", SetType.Drop, "D"), Triple("en", SetType.Failure, "F"),
        )) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(tag) { Row(state(type = type)) } }
                waitForIdle()
                onNodeWithTag(SetRowTypeTag, useUnmergedTree = true).assertTextEquals(letter)
            }
        }
        runComposeUiTest {
            withTheme { Row(state()) }
            waitForIdle()
            onNodeWithTag(SetRowTypeTag, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun numbersNeverEllipsizeAtTwoHundredPercentOn320dp() = assertNumbersWholeOn320dp()

    /** The iOS simulator's density: a dp is a pixel. Android still scales large text non-linearly. */
    @Test
    fun numbersNeverEllipsizeAtTwoHundredPercentOn320dpAtDensityOne() = assertNumbersWholeOn320dp(density = 1f)

    /** What the iOS simulator renders at 2x: density 1 and 28 sp drawn at 56 dp. */
    @Test
    fun numbersNeverEllipsizeAtLinearTwoHundredPercentOn320dp() =
        assertNumbersWholeOn320dp(density = 1f, fontScale = LINEAR_TWO_X_FONT_SCALE)

    @Test
    fun editableNumbersNeverClipAtLinearTwoHundredPercentOn320dp() =
        assertNumbersWholeOn320dp(density = 1f, fontScale = LINEAR_TWO_X_FONT_SCALE, fields = true)

    /** iOS's largest accessibility sizes are about 3x: the cells wrap onto more lines, never inside a value. */
    @Test
    fun numbersWrapBetweenCellsAtThreeHundredPercentOn320dp() {
        assertNumbersWholeOn320dp(density = 1f, fontScale = 3f)
        assertNumbersWholeOn320dp(density = 1f, fontScale = 3f, fields = true)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun assertNumbersWholeOn320dp(
        density: Float? = null,
        fontScale: Float = 2f,
        fields: Boolean = false,
        type: SetType = SetType.Normal,
    ) = runComposeUiTest {
        withTheme(fontScale = fontScale, density = density) {
            Box(Modifier.width(320.dp)) {
                ProvideAppLocale("en") {
                    val row = state(status = SetStatus.CompletedRecord, load = "187.5", previous = "187.5 kg × 12", type = type)
                    if (fields) {
                        SetRow(
                            row.copy(status = SetStatus.Pending, editing = true, reps = 12), {}, {}, {}, {}, {}, modifier = Modifier.testTag("row"),
                            loadField = { NumericField("187.5", {}, NumericKind.Load, "Load") },
                            repsField = { NumericField("12", {}, NumericKind.Reps, "Reps") },
                        )
                    } else {
                        Row(row)
                    }
                }
            }
        }
        waitForIdle()
        val dpPx = density ?: this.density.density
        val texts = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue(texts.size >= 3, "only ${texts.size} texts")
        for (node in texts) {
            val results = mutableListOf<TextLayoutResult>()
            runOnUiThread { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            for (layout in results) assertShownWhole(layout)
        }
        if (fields) {
            // A text field scrolls a value that is wider than it, so the whole-text check above
            // passes even when it is clipped: its drawn box must hold the text and its padding.
            val boxes = onAllNodes(hasTestTag(NumericFieldBoxTag), useUnmergedTree = true).fetchSemanticsNodes()
            val editable = onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals(2, boxes.size)
            assertEquals(2, editable.size)
            val padding = 8.dp.value * 2 * dpPx
            for ((box, field) in boxes.zip(editable)) {
                val results = mutableListOf<TextLayoutResult>()
                runOnUiThread { field.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
                val text = results.single()
                val needed = ceil(text.multiParagraph.intrinsics.maxIntrinsicWidth)
                assertTrue(
                    box.boundsInRoot.width >= needed + padding - 0.5f,
                    "\"${text.layoutInput.text}\" needs $needed px and $padding px of padding, its box is ${box.boundsInRoot.width}",
                )
            }
        }
        val widthPx = 320 * dpPx
        for (tag in listOf(SetRowPreviousTag, SetRowLoadTag, SetRowRepsTag, SetRowCheckTag)) {
            val bounds = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= -0.5f && bounds.right <= widthPx + 0.5f, "$tag spans ${bounds.left}..${bounds.right}, the row is $widthPx")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun checkIsAToggleOfItsOwnBesideTheRowSummary() {
        for ((lang, label, heading) in listOf(
            Triple("en", "Complete set 4", "Set 4 of 5"),
            Triple("pt-BR", "Concluir série 4", "Série 4 de 5"),
        )) {
            runComposeUiTest {
                var status by mutableStateOf(SetStatus.Pending)
                val calls = mutableListOf<Boolean>()
                withTheme {
                    ProvideAppLocale(lang) {
                        Row(state(status = status), onComplete = { calls += it; status = if (it) SetStatus.Completed else SetStatus.Pending })
                    }
                }
                waitForIdle()
                val check = onNodeWithTag(SetRowCheckTag)
                check.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
                check.assertContentDescriptionEquals(label)
                check.assertIsOff()
                check.performClick()
                waitForIdle()
                assertEquals(listOf(true), calls)
                onNodeWithTag(SetRowCheckTag).assertIsOn()
                // The row keeps its one summary (the check's label is not merged into it) and its five actions.
                val row = onNodeWithTag("row").fetchSemanticsNode().config
                val description = row.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                assertEquals(1, description.size, "$description")
                assertTrue(description.single().startsWith(heading), description.single())
                assertEquals(5, row.getOrNull(SemanticsActions.CustomActions).orEmpty().size)
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun screenReaderActivationOfTheCheckGivesTheTapHaptics() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Pending)
        withTheme(haptics = haptics) {
            Row(state(status = status), onComplete = { status = if (it) SetStatus.Completed else SetStatus.Pending })
        }
        waitForIdle()
        // A screen reader's double tap is the node's click action.
        onNodeWithTag(SetRowCheckTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(SetStatus.Completed, status)
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        onNodeWithTag(SetRowCheckTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(SetStatus.Pending, status)
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.SetUnchecked), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun setTypeIsSpoken() {
        val en = ", previous 80 kilograms by 6, load 85 kilograms, 6 reps, not completed"
        val pt = ", anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída"
        for ((lang, type, expected) in listOf(
            Triple("en", SetType.Warmup, "Set 4 of 5, warm-up$en"),
            Triple("en", SetType.Drop, "Set 4 of 5, drop set$en"),
            Triple("en", SetType.Failure, "Set 4 of 5, to failure$en"),
            Triple("pt-BR", SetType.Warmup, "Série 4 de 5, aquecimento$pt"),
            Triple("pt-BR", SetType.Drop, "Série 4 de 5, drop set$pt"),
            Triple("pt-BR", SetType.Failure, "Série 4 de 5, até a falha$pt"),
        )) {
            runComposeUiTest {
                val spoken = if (lang == "en") state(type = type) else state(type = type, previousSpoken = "80 quilos por 6", loadSpoken = "85 quilos")
                withTheme { ProvideAppLocale(lang) { Row(spoken) } }
                waitForIdle()
                onNodeWithTag("row").assertContentDescriptionEquals(expected)
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun previousIsSpokenWithItsReps() {
        for ((lang, load, expected) in listOf(
            Triple("en", "55 kilograms", "55 kilograms by 10"),
            Triple("pt-BR", "55 quilos", "55 quilos por 10"),
        )) {
            runComposeUiTest {
                var spoken = ""
                withTheme { ProvideAppLocale(lang) { spoken = spokenLoadByReps(load, 10) } }
                waitForIdle()
                assertEquals(expected, spoken)
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun previousStaysOnOneLineAtOneThirtyPercent() {
        val samples = listOf(
            "55 kg × 10" to SetStatus.Pending,
            "77.5 kg × 6" to SetStatus.Pending,
            "187.5 kg × 12" to SetStatus.CompletedRecord,
        )
        for (width in listOf(360, 320)) {
            for ((previous, status) in samples) {
                runComposeUiTest {
                    var needed = 0
                    withTheme(fontScale = 1.3f) {
                        val measurer = rememberTextMeasurer()
                        needed = measurer.measure(previous, MTheme.type.numSm, softWrap = false, maxLines = 1).size.width
                        Box(Modifier.width(width.dp)) { ProvideAppLocale("en") { Row(state(status = status, previous = previous, load = "187.5")) } }
                    }
                    waitForIdle()
                    // The cell is as wide as its text needs, so the value is not broken over two lines.
                    val cell = onNodeWithTag(SetRowPreviousTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue(cell.width >= needed - 0.5f, "$width dp: \"$previous\" needs $needed px, its cell is ${cell.width}")
                    val widthPx = with(density) { width.dp.toPx() }
                    for (tag in listOf(SetRowLoadTag, SetRowRepsTag, SetRowCheckTag)) {
                        val bounds = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                        assertTrue(bounds.right <= widthPx + 0.5f, "$width dp: $tag ends at ${bounds.right} beyond $widthPx")
                    }
                }
            }
        }
    }

    /** The text node of a number shown by the row (not the previous result), with the color it is drawn in. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.colorOf(text: String, tag: String): Color {
        val node = onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        runOnUiThread { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
        return results.single().layoutInput.style.color
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun suggestedValuesAreSecondaryAndSpoken() {
        for ((lang, spoken, expected) in listOf(
            Triple(
                "pt-BR", state(index = 2, previous = "80 kg × 8", previousSpoken = "80 quilos por 8", load = "82,5", loadSpoken = "82,5 quilos", reps = 8, loadSuggested = true, repsSuggested = true),
                "Série 2 de 5, anterior 80 quilos por 8, sugestão 82,5 quilos, 8 repetições, não concluída",
            ),
            Triple(
                "en", state(index = 2, previous = "80 kg × 8", previousSpoken = "80 kilograms by 8", load = "82.5", loadSpoken = "82.5 kilograms", reps = 8, loadSuggested = true, repsSuggested = true),
                "Set 2 of 5, previous 80 kilograms by 8, suggestion 82.5 kilograms, 8 reps, not completed",
            ),
        )) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(lang) { Row(spoken) } }
                waitForIdle()
                onNodeWithTag("row").assertContentDescriptionEquals(expected)
                val secondary = MoosclesColors.Light.textSecondary
                assertEquals(secondary, colorOf(spoken.load, SetRowLoadTag), "$lang load")
                assertEquals(secondary, colorOf("8", SetRowRepsTag), "$lang reps")
            }
        }
        // A saved value keeps the primary text color.
        runComposeUiTest {
            withTheme { Row(state()) }
            waitForIdle()
            assertEquals(MoosclesColors.Light.textPrimary, colorOf("85", SetRowLoadTag))
            assertEquals(MoosclesColors.Light.textPrimary, colorOf("6", SetRowRepsTag))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onlyRepsSuggestedSaysSuggestionBeforeReps() {
        for ((lang, spoken, expected) in listOf(
            Triple(
                "pt-BR", state(previousSpoken = "80 quilos por 6", loadSpoken = "85 quilos", reps = 8, repsSuggested = true),
                "Série 4 de 5, anterior 80 quilos por 6, carga 85 quilos, sugestão 8 repetições, não concluída",
            ),
            Triple(
                "en", state(reps = 8, repsSuggested = true),
                "Set 4 of 5, previous 80 kilograms by 6, load 85 kilograms, suggestion 8 reps, not completed",
            ),
            Triple(
                "en", state(previous = null, previousSpoken = null, reps = 8, repsSuggested = true),
                "Set 4 of 5, load 85 kilograms, suggestion 8 reps, not completed",
            ),
        )) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(lang) { Row(spoken) } }
                waitForIdle()
                onNodeWithTag("row").assertContentDescriptionEquals(expected)
                assertEquals(MoosclesColors.Light.textPrimary, colorOf("85", SetRowLoadTag))
                assertEquals(MoosclesColors.Light.textSecondary, colorOf("8", SetRowRepsTag))
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun assistedShowsASAndIsSpoken() {
        for ((lang, spoken, expected) in listOf(
            Triple("en", state(type = SetType.Assisted), "Set 4 of 5, assisted set, previous 80 kilograms by 6, load 85 kilograms, 6 reps, not completed"),
            Triple(
                "pt-BR", state(type = SetType.Assisted, previousSpoken = "80 quilos por 6", loadSpoken = "85 quilos"),
                "Série 4 de 5, série assistida, anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída",
            ),
        )) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(lang) { Row(spoken) } }
                waitForIdle()
                onNodeWithTag(SetRowTypeTag, useUnmergedTree = true).assertTextEquals("AS")
                onNodeWithTag("row").assertContentDescriptionEquals(expected)
            }
        }
        // With the wider "AS" pill, every text (the letters, previous, load and reps) stays whole
        // where text is largest: iOS's 2x on a 320 dp screen, with numbers and with fields.
        assertNumbersWholeOn320dp(density = 1f, fontScale = LINEAR_TWO_X_FONT_SCALE, type = SetType.Assisted)
        assertNumbersWholeOn320dp(density = 1f, fontScale = LINEAR_TWO_X_FONT_SCALE, fields = true, type = SetType.Assisted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rowClickIsEditSet() {
        for ((lang, label) in listOf("pt-BR" to "Editar série", "en" to "Edit set")) {
            runComposeUiTest {
                val calls = mutableListOf<String>()
                withTheme {
                    ProvideAppLocale(lang) {
                        Row(
                            state(),
                            onComplete = { calls += "complete" }, onLoad = { calls += "load" }, onReps = { calls += "reps" },
                            onEditRow = { calls += "editRow" },
                        )
                    }
                }
                waitForIdle()
                val click = onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)
                assertEquals(label, click?.label)
                onNodeWithTag("row").performSemanticsAction(SemanticsActions.OnClick)
                waitForIdle()
                assertEquals(listOf("editRow"), calls)
                // The five actions stay, and the check keeps its own click.
                assertEquals(5, onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().size)
                onNodeWithTag(SetRowCheckTag).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noRowClickWithoutCallback() = runComposeUiTest {
        withTheme { Row(state()) }
        waitForIdle()
        onNodeWithTag("row").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun previousIsATargetThatCopies() {
        for ((lang, label) in listOf("en" to "Copy previous", "pt-BR" to "Copiar anterior")) {
            for (previous in listOf("80 kg × 6", "5")) {
                runComposeUiTest {
                    var copies = 0
                    withTheme { ProvideAppLocale(lang) { Row(state(previous = previous), onCopyPrevious = { copies++ }) } }
                    waitForIdle()
                    val bounds = onNodeWithTag(SetRowPreviousTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val min = with(density) { MoosclesTargets().min.toPx() }
                    assertTrue(bounds.width >= min - 0.5f && bounds.height >= min - 0.5f, "\"$previous\": ${bounds.width} x ${bounds.height}, needs $min")
                    onNodeWithTag(SetRowPreviousTag, useUnmergedTree = true).performClick()
                    waitForIdle()
                    assertEquals(1, copies, "\"$previous\" tap")
                    val actions = onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
                    assertEquals(6, actions.size)
                    // Copy comes before the destructive "Remove set", which stays last.
                    assertEquals(label, actions[4].label)
                    assertEquals(if (lang == "en") "Remove set" else "Remover série", actions.last().label)
                    runOnUiThread { actions[4].action() }
                    assertEquals(2, copies, "\"$previous\" action")
                }
            }
        }
        // Without the callback, tapping the previous result does nothing and there is no action.
        runComposeUiTest {
            val calls = mutableListOf<String>()
            withTheme { Row(state(), onComplete = { calls += "complete" }, onLoad = { calls += "load" }, onType = { calls += "type" }) }
            waitForIdle()
            onNodeWithTag(SetRowPreviousTag, useUnmergedTree = true).performClick()
            waitForIdle()
            assertEquals(emptyList(), calls)
            assertEquals(5, onNodeWithTag("row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().size)
        }
    }

    /** Pre-flight F3: a record that stops holding goes away without a vibration. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun losingARecordDoesNotVibrate() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.CompletedRecord)
        withTheme(haptics = haptics) { Row(state(status = status)) }
        waitForIdle()
        status = SetStatus.Completed
        waitForIdle()
        onNodeWithTag(PrBadgeTag, useUnmergedTree = true).assertDoesNotExist()
        assertEquals(emptyList(), haptics.events.toList())
    }

    /** Pre-flight F3: editing is a flag, not a status, so entering or leaving it on a done set never vibrates. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editingNeverVibrates() {
        for (status in SetStatus.entries) {
            runComposeUiTest {
                val haptics = RecordingHaptics()
                var editing by mutableStateOf(false)
                withTheme(haptics = haptics) { Row(state(status = status, editing = editing)) }
                waitForIdle()
                editing = true
                waitForIdle()
                editing = false
                waitForIdle()
                assertEquals(emptyList(), haptics.events.toList(), "$status")
            }
        }
    }

    /** Pre-flight F3: in the workout the screen plays the record haptic from its record event, so the row only gives the complete one. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun withoutRecordHapticARecordGivesOnlyTheCompleteHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var status by mutableStateOf(SetStatus.Pending)
        withTheme(haptics = haptics) { Row(state(status = status), recordHaptic = false) }
        waitForIdle()
        status = SetStatus.CompletedRecord
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        status = SetStatus.Completed
        waitForIdle()
        status = SetStatus.CompletedRecord
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        onNodeWithTag(PrBadgeTag, useUnmergedTree = true).assertExists()
    }

    /** Review M2: a suggested load with saved reps names the load as the suggestion, not the reps. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onlyLoadSuggestedSaysSuggestedLoad() {
        for ((lang, spoken, expected) in listOf(
            Triple(
                "pt-BR", state(previousSpoken = "80 quilos por 6", load = "82,5", loadSpoken = "82,5 quilos", reps = 8, loadSuggested = true),
                "Série 4 de 5, anterior 80 quilos por 6, sugestão de carga 82,5 quilos, 8 repetições, não concluída",
            ),
            Triple(
                "en", state(load = "82.5", loadSpoken = "82.5 kilograms", reps = 8, loadSuggested = true),
                "Set 4 of 5, previous 80 kilograms by 6, suggested load 82.5 kilograms, 8 reps, not completed",
            ),
        )) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(lang) { Row(spoken) } }
                waitForIdle()
                onNodeWithTag("row").assertContentDescriptionEquals(expected)
                assertEquals(MoosclesColors.Light.textSecondary, colorOf(spoken.load, SetRowLoadTag))
                assertEquals(MoosclesColors.Light.textPrimary, colorOf("8", SetRowRepsTag))
            }
        }
    }
}
