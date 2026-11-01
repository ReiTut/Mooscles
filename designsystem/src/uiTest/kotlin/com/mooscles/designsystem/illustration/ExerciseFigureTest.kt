package com.mooscles.designsystem.illustration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseMediaFigureTag
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_figure_show_end
import com.mooscles.designsystem.resources.ds_figure_show_start
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class)
class ExerciseFigureTest {
    private val hold = MoosclesTiming().illustrationFrame
    private val fade = MoosclesMotion().base.toLong()

    /** From the start of the alternation (the end frame shown), to the middle of the start frame's hold. */
    private val toStart = hold + fade + hold / 2

    /** One frame and its fade: from the middle of one hold to the middle of the next. */
    private val toNext = hold + fade

    private val start = FigurePose(
        hipX = 50f, hipY = 50f, trunk = 0f, head = 0f,
        upperArmNear = 180f, forearmNear = 180f, upperArmFar = 180f, forearmFar = 180f,
        thighNear = 180f, shinNear = 180f, footNear = 90f, thighFar = 180f, shinFar = 180f, footFar = 90f,
        floorY = 91f,
        props = listOf(FigureProp.Dumbbell(FigureJoint.HAND_NEAR), FigureProp.Floor(91f)),
    )
    private val end = start.copy(forearmNear = 20f)
    private val highlights = setOf(FigureSegment.UPPER_ARM_FRONT)

    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private fun ComposeUiTest.frame(): String =
        onNodeWithTag(AnimatedFigureTag).fetchSemanticsNode().config[FigureFrameKey]

    private fun ComposeUiTest.shownAlpha(): Float =
        onNodeWithTag(AnimatedFigureTag).fetchSemanticsNode().config[FigureFrameAlpha]()

    private fun ComposeUiTest.clickLabel(): String? =
        onNodeWithTag(AnimatedFigureTag).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    @Test
    fun alternatesEveryIllustrationFrame() = runComposeUiTest {
        // Stopped before the first frame: with the clock running, the test policy stops infinite animations.
        mainClock.autoAdvance = false
        withTheme { AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp)) }
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        assertEquals(1f, shownAlpha())
        mainClock.advanceTimeBy(toStart)
        assertEquals("start", frame())
        assertEquals(1f, shownAlpha())
        mainClock.advanceTimeBy(toNext)
        assertEquals("end", frame())
        mainClock.advanceTimeBy(toNext)
        assertEquals("start", frame())
        mainClock.autoAdvance = true
    }

    /** Also the proof that the alternation lets a test with the clock running go idle (F7). */
    @Test
    fun autoAlternationHasNoClickAction() = runComposeUiTest {
        withTheme { AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp)) }
        waitForIdle()
        onNodeWithTag(AnimatedFigureTag).assert(!hasClickAction())
        // Spec "Acessibilidade": the figure is an image with a label.
        onNodeWithTag(AnimatedFigureTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
        assertEquals("end", frame())
    }

    @Test
    fun reducedMotionShowsTheEndAndATapToggles() = runComposeUiTest {
        withTheme(reducedMotion = true) { AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp)) }
        waitForIdle()
        assertEquals("end", frame())
        assertEquals(getString(Res.string.ds_figure_show_start), clickLabel())
        onNodeWithTag(AnimatedFigureTag).performClick()
        waitForIdle()
        assertEquals("start", frame())
        assertEquals(1f, shownAlpha())
        assertEquals(getString(Res.string.ds_figure_show_end), clickLabel())
        onNodeWithTag(AnimatedFigureTag).performClick()
        waitForIdle()
        assertEquals("end", frame())
        assertEquals(getString(Res.string.ds_figure_show_start), clickLabel())
    }

    @Test
    fun reducedMotionMidFadeSettlesOnTheEnd() = runComposeUiTest {
        val reduced = mutableStateOf(false)
        mainClock.autoAdvance = false
        setContent {
            MoosclesDesignTheme(dark = false, reducedMotion = reduced.value) {
                AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp))
            }
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(hold + fade * 2 / 5)
        val mid = shownAlpha()
        assertTrue(mid > 0f && mid < 1f, "mid-fade, the shown frame is translucent: $mid")
        reduced.value = true
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        assertEquals(1f, shownAlpha())
        mainClock.advanceTimeBy(toStart)
        assertEquals("end", frame(), "still with reduced motion")
        assertEquals(getString(Res.string.ds_figure_show_start), clickLabel())
        // Without reduced motion the alternation starts over from the end frame.
        reduced.value = false
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        assertEquals(null, clickLabel())
        mainClock.advanceTimeBy(toStart)
        assertEquals("start", frame())
        mainClock.autoAdvance = true
    }

    @Test
    fun backgroundMidFadeSettlesAndResumes() = runComposeUiTest {
        val foreground = mutableStateOf(true)
        mainClock.autoAdvance = false
        withTheme {
            AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp), foreground = foreground.value)
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(hold + fade * 2 / 5)
        val mid = shownAlpha()
        assertTrue(mid > 0f && mid < 1f, "mid-fade, the shown frame is translucent: $mid")
        foreground.value = false
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        assertEquals(1f, shownAlpha())
        mainClock.advanceTimeBy(toStart)
        assertEquals("end", frame(), "still in the background")
        assertEquals(1f, shownAlpha())
        // In the background there is nobody to tap: no action.
        assertEquals(null, clickLabel())
        foreground.value = true
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        mainClock.advanceTimeBy(toStart)
        assertEquals("start", frame())
        mainClock.advanceTimeBy(toNext)
        assertEquals("end", frame())
        mainClock.autoAdvance = true
    }

    /**
     * The scrolling itself runs with the clock on (F7: never scroll with it stopped). With the
     * clock on, the test policy stops a running alternation where it is, so a figure that did not
     * pause would stay on the start frame; a paused one shows the end frame, whole.
     */
    @Test
    fun pausesWhileScrolledOff() = runComposeUiTest {
        mainClock.autoAdvance = false
        withTheme {
            Column(Modifier.size(200.dp, 300.dp).verticalScroll(rememberScrollState())) {
                AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp))
                Spacer(Modifier.height(2000.dp))
                BasicText("bottom", Modifier.testTag("bottom"))
            }
        }
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(toStart)
        assertEquals("start", frame(), "on screen it alternates")
        mainClock.autoAdvance = true
        onNodeWithTag("bottom").performScrollTo()
        waitForIdle()
        onNodeWithTag(AnimatedFigureTag).assertIsNotDisplayed()
        assertEquals("end", frame())
        assertEquals(1f, shownAlpha())
        mainClock.autoAdvance = false
        repeat(6) { step ->
            mainClock.advanceTimeBy(hold / 2)
            assertEquals("end", frame(), "off screen, step $step")
            assertEquals(1f, shownAlpha())
        }
        mainClock.autoAdvance = true
    }

    /**
     * Battery (4B Task 5): while a frame holds, the figure asks for no frames at all; only the fade
     * does. Read on the composition's frame clock (the recomposer's [BroadcastFrameClock], the one
     * every `withFrameNanos` of the composition waits on): after each frame the test advances, is
     * anything still waiting for the next one? On the device a waiting awaiter is a
     * `Choreographer#doFrame` 60 times a second, with nothing to draw.
     */
    @Test
    fun holdRequestsNoFrames() = runComposeUiTest {
        var clock: BroadcastFrameClock? = null
        mainClock.autoAdvance = false
        withTheme {
            val scope = rememberCoroutineScope()
            SideEffect { clock = scope.coroutineContext[MonotonicFrameClock] as? BroadcastFrameClock }
            AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp))
        }
        mainClock.advanceTimeByFrame()
        val frames = assertNotNull(clock, "the composition waits on the recomposer's frame clock")

        // The frames advanced over [span] after which something in the composition waited for another.
        fun requested(span: Long): Int {
            var count = 0
            val until = mainClock.currentTime + span
            while (mainClock.currentTime < until) {
                mainClock.advanceTimeByFrame()
                if (frames.hasAwaiters) count++
            }
            return count
        }

        assertEquals(0, requested(hold - fade), "the end frame holds without asking for frames")
        assertEquals("end", frame())
        assertEquals(1f, shownAlpha())
        mainClock.advanceTimeBy(fade)
        // Positive control: the fade asks for a frame on (nearly) every frame.
        val fading = requested(fade)
        assertTrue(fading >= fade / FRAME_MILLIS / 2, "the fade asked for frames on $fading frames")
        mainClock.advanceTimeBy(fade)
        assertEquals(0, requested(hold - 2 * fade), "the start frame holds without asking for frames")
        assertEquals("start", frame())
        assertEquals(1f, shownAlpha())
        mainClock.autoAdvance = true
    }

    @Test
    fun onlyTheFigureRecomposesOnEachFrame() = runComposeUiTest {
        var sibling = 0
        val scopes = mutableMapOf<String, Int>()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalFigureLayerProbe provides { scope -> scopes[scope] = (scopes[scope] ?: 0) + 1 }) {
                MoosclesDesignTheme(dark = false, reducedMotion = false) {
                    SideEffect { sibling++ }
                    Column {
                        BasicText("Sibling")
                        AnimatedExerciseFigure(start, end, highlights, "Curl", Modifier.size(160.dp))
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val first = scopes.toMap()
        assertEquals(1, first[FigureScopeLayer], "the layer composed once: $first")
        // Two whole cycles: four frame changes, over a few hundred animation frames.
        mainClock.advanceTimeBy(2 * (hold + fade) * 2)
        assertEquals("end", frame())
        assertEquals(1, sibling, "the content around the figure never recomposes")
        assertEquals(first[FigureScopeOuter], scopes[FigureScopeOuter], "the label and the tap never recompose: $scopes")
        // Positive control: the frame scopes recompose on each frame change, and only then (not on every animation frame).
        for (scope in listOf(FigureScopeAlternation, FigureScopeLayer)) {
            val changes = (scopes[scope] ?: 0) - (first[scope] ?: 0)
            assertTrue(changes in 4..6, "$scope recomposed $changes times over four frame changes")
        }
        mainClock.autoAdvance = true
    }

    @Test
    fun labelNamesTheExercise() {
        runComposeUiTest {
            withTheme { AnimatedExerciseFigure(start, end, highlights, "Barbell bench press", Modifier.size(160.dp)) }
            waitForIdle()
            onNodeWithTag(AnimatedFigureTag).assert(hasContentDescription("Illustration: start and end of Barbell bench press"))
        }
        runComposeUiTest {
            withTheme {
                ProvideAppLocale("pt-BR") {
                    AnimatedExerciseFigure(start, end, highlights, "Supino reto com barra", Modifier.size(160.dp))
                }
            }
            waitForIdle()
            onNodeWithTag(AnimatedFigureTag).assert(hasContentDescription("Ilustração de início e fim: Supino reto com barra"))
        }
    }

    @Test
    fun thumbnailShowsTheFigureOrTheEquipmentIcon() = runComposeUiTest {
        val media = mutableStateOf<ExerciseMedia>(ExerciseMedia.Figure(end, highlights))
        withTheme { ExerciseThumbnail(media.value, MIcons.Dumbbell, 56.dp, Modifier.testTag("thumbnail")) }
        waitForIdle()
        onNodeWithTag(ExerciseMediaFigureTag).assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        onNodeWithTag(ExerciseEquipmentIconTag).assertDoesNotExist()
        // Decorative: the name beside it says what it is.
        onNodeWithTag("thumbnail").assert(!hasContentDescription("", substring = true))
        media.value = ExerciseMedia.None
        waitForIdle()
        onNodeWithTag(ExerciseMediaFigureTag).assertDoesNotExist()
        onNodeWithTag(ExerciseEquipmentIconTag).assertExists()
        onNodeWithTag("thumbnail").assertWidthIsEqualTo(56.dp)
    }
}

/** The test clock's frame time. */
private const val FRAME_MILLIS = 16L
