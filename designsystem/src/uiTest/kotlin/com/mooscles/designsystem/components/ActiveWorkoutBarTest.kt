package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ActiveWorkoutBarTest {
    /** Long enough to overflow 412 dp at 1.0 on every platform (iOS fonts are narrower than Android's). */
    private val longName = "Upper body strength and conditioning, heavy week: bench press, weighted pull-ups, overhead press, barbell rows and face pulls to finish"

    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /** The state a screen builds: 42:18 elapsed (read as whole minutes), 1:24 of a 2:00 rest. */
    @Composable
    private fun state(name: String = "Workout A", rest: Boolean = true, sets: String = "9 of 15 sets") = ActiveWorkoutBarState(
        workoutName = name,
        elapsedText = "42:18",
        elapsedSpoken = spokenDuration(42 * 60),
        setsText = sets,
        rest = if (rest) RestTimerVisual(84, 120, TimerPhase.Running, "Rest") else null,
        restSpoken = if (rest) spokenDuration(84) else null,
    )

    private fun ComposeUiTest.bar(
        name: String = "Workout A",
        rest: Boolean = true,
        sets: String = "9 of 15 sets",
        locale: String = "en",
        width: Dp = 412.dp,
        fontScale: Float = 1f,
        density: Float? = null,
        onOpen: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) {
        withTheme(fontScale = fontScale, density = density) {
            ProvideAppLocale(locale) {
                Box(Modifier.width(width)) { ActiveWorkoutBar(state(name, rest, sets), onOpen, onSkip) }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result for $tag")
        return results.first()
    }

    @Test
    fun isAButtonWithTheSpokenSummary() {
        val cases = listOf(
            Triple("en", "Workout A", "Workout in progress, Workout A, 42 minutes. Rest, 1 minute and 24 seconds"),
            Triple("pt-BR", "Treino A", "Treino em andamento, Treino A, 42 minutos. Descanso, 1 minuto e 24 segundos"),
        )
        for ((locale, name, spoken) in cases) {
            runComposeUiTest {
                bar(name = name, locale = locale)
                onNodeWithTag(WorkoutBarTag)
                    .assert(hasClickAction())
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                    .assertContentDescriptionEquals(spoken)
                    // The texts inside are not read a second time.
                    .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            }
        }
        // Without a rest it stops after the elapsed time.
        runComposeUiTest {
            bar(rest = false)
            onNodeWithTag(WorkoutBarTag).assertContentDescriptionEquals("Workout in progress, Workout A, 42 minutes")
        }
    }

    /** Ruling F5: the design system's spoken duration is public and reads hours ("1 hora e 5 minutos"). */
    @Test
    fun spokenDurationReadsHoursMinutesAndSeconds() {
        val seconds = listOf(42 * 60, 3_900, 3_661, 84, 0)
        val expected = mapOf(
            "en" to listOf("42 minutes", "1 hour and 5 minutes", "1 hour, 1 minute and 1 second", "1 minute and 24 seconds", "0 seconds"),
            "pt-BR" to listOf("42 minutos", "1 hora e 5 minutos", "1 hora, 1 minuto e 1 segundo", "1 minuto e 24 segundos", "0 segundos"),
        )
        for ((locale, texts) in expected) {
            runComposeUiTest {
                val read = mutableListOf<String>()
                withTheme { ProvideAppLocale(locale) { read.clear(); seconds.forEach { read += spokenDuration(it) } } }
                waitForIdle()
                assertEquals(texts, read, locale)
            }
        }
    }

    @Test
    fun withoutRestShowsSetsAndNoSkip() = runComposeUiTest {
        bar(name = "Treino A", rest = false, sets = "9 de 15 séries", locale = "pt-BR")
        onNodeWithText("9 de 15 séries", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(WorkoutBarSkipTag).assertDoesNotExist()
        onNodeWithTag(WorkoutBarRestTag, useUnmergedTree = true).assertDoesNotExist()
        assertShownWhole(layoutOf(WorkoutBarSetsTag))
    }

    @Test
    fun skipIsFiftySixAndCallsSkip() = runComposeUiTest {
        var opened = 0
        var skipped = 0
        bar(onOpen = { opened++ }, onSkip = { skipped++ })
        // With a rest the time of the rest replaces the sets.
        onNodeWithTag(WorkoutBarSetsTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("1:24", useUnmergedTree = true).assertIsDisplayed()
        val targets = MoosclesTargets()
        val minPx = with(density) { targets.workout.toPx() } - 0.5f
        val touch = onNodeWithTag(WorkoutBarSkipTag).assertContentDescriptionEquals("Skip rest").fetchSemanticsNode().touchBoundsInRoot
        assertTrue(touch.width >= minPx && touch.height >= minPx, "skip is ${touch.width} x ${touch.height} px")
        val bar = onNodeWithTag(WorkoutBarTag).getUnclippedBoundsInRoot()
        assertTrue(bar.bottom - bar.top >= targets.workout - 0.5.dp, "the bar is ${bar.bottom - bar.top} tall")
        onNodeWithTag(WorkoutBarSkipTag).performClick()
        waitForIdle()
        assertEquals(1, skipped)
        assertEquals(0, opened)
        onNodeWithTag(WorkoutBarTag).performClick()
        waitForIdle()
        assertEquals(1, opened)
        assertEquals(1, skipped)
    }

    @Test
    fun hasNoLiveRegion() {
        for (rest in listOf(true, false)) {
            runComposeUiTest {
                bar(rest = rest)
                onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true).assertCountEquals(0)
            }
        }
    }

    @Test
    fun twoLinesAtTwoHundredKeepsSkip() {
        // One line at 100%: the name and the time share a line.
        runComposeUiTest {
            bar()
            val name = onNodeWithTag(WorkoutBarNameTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val time = onNodeWithTag(WorkoutBarTimeTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(name.bottom > time.top && time.bottom > name.top, "one line: name $name, time $time")
        }
        for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            runComposeUiTest {
                bar(width = 320.dp, fontScale = scale, density = density)
                val name = onNodeWithTag(WorkoutBarNameTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val time = onNodeWithTag(WorkoutBarTimeTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(name.bottom <= time.top + 0.5.dp, "at $scale the time is under the name: name $name, time $time")
                val skip = onNodeWithTag(WorkoutBarSkipTag).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue(skip.right <= 320.dp + 0.5.dp, "at $scale skip ends at ${skip.right}")
                assertShownWhole(layoutOf(WorkoutBarTimeTag), "time at $scale")
                assertShownWhole(layoutOf(WorkoutBarRestTag), "rest at $scale")
            }
        }
    }

    @Test
    fun timeIsWholeWhileTheNameEllipsizes() {
        val scales = listOf(1f to null, 1.3f to null, 2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)
        for (width in listOf(320.dp, 412.dp)) {
            for ((scale, density) in scales) {
                for (rest in listOf(true, false)) {
                    runComposeUiTest {
                        bar(name = longName, rest = rest, width = width, fontScale = scale, density = density)
                        val where = "$width at $scale, rest=$rest"
                        val name = layoutOf(WorkoutBarNameTag)
                        assertEquals(1, name.lineCount, where)
                        // isLineEllipsized is false on iOS for this node (its layout result comes
                        // from a relayout at the parent's width), so check what it needs and how it ends.
                        assertEquals(TextOverflow.Ellipsis, name.layoutInput.overflow, where)
                        val needed = name.multiParagraph.intrinsics.maxIntrinsicWidth
                        assertTrue(needed > name.size.width, "$where: the long name needs $needed px, has ${name.size.width}")
                        assertShownWhole(layoutOf(WorkoutBarTimeTag), "time $where")
                        assertShownWhole(layoutOf(if (rest) WorkoutBarRestTag else WorkoutBarSetsTag), "$where")
                    }
                }
            }
        }
    }
}
