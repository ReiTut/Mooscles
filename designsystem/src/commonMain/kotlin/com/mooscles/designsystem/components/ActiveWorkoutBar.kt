package com.mooscles.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.resources.ds_workout_bar_rest_spoken
import com.mooscles.designsystem.resources.ds_workout_bar_spoken
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.tabular
import org.jetbrains.compose.resources.stringResource

const val WorkoutBarTag = "workout-bar"
const val WorkoutBarNameTag = "workout-bar-name"
const val WorkoutBarTimeTag = "workout-bar-time"
const val WorkoutBarSetsTag = "workout-bar-sets"
const val WorkoutBarRestTag = "workout-bar-rest"
const val WorkoutBarSkipTag = "workout-bar-skip"

/** From this font scale on the bar always takes two lines (it also does when one line does not fit). */
private const val TWO_LINE_FONT_SCALE = 1.5f

/** The widest rest time the bar shows (rest is 5:00 at most), so the layout does not change every second. */
private const val WIDEST_REST = "00:00"

/**
 * What the in-progress workout bar shows, already localized by the caller: the workout's name,
 * the elapsed time as shown ("42:18") and as read ("42 minutes", see [spokenDuration]), the sets
 * ("9 of 15 sets"), and the running rest with its reading ("1 minute and 24 seconds"), or null.
 */
data class ActiveWorkoutBarState(
    val workoutName: String,
    val elapsedText: String,
    val elapsedSpoken: String,
    val setsText: String,
    val rest: RestTimerVisual?,
    val restSpoken: String?,
)

/**
 * The bar of the workout in progress, fixed above the tab bar (spec D8): at least 56 dp, `bg.raised`
 * with `radius.lg` on top and `elevation.2`. It shows the name (`title.sm`, one line, which may end
 * in an ellipsis because it is a name, not a number), the elapsed time and the sets; while a rest
 * runs, a 32 dp ring and the rest time take the place of the sets and a 56 dp skip button appears.
 * At a font scale of 1.5 or more, or when one line does not fit (measured), the name goes on top
 * and the times under it; the skip button is never hidden. Numbers are never cut.
 *
 * Tapping the bar calls [onOpen]: it is one button, tagged [WorkoutBarTag], named "Workout in
 * progress, Workout A, 42 minutes. Rest, 1 minute and 24 seconds". It has no live region, so it is
 * not announced every second. The bar and its skip button are a traversal group of their own.
 */
@Composable
fun ActiveWorkoutBar(
    state: ActiveWorkoutBarState,
    onOpen: () -> Unit,
    onSkipRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MTheme.colors
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val rest = state.rest
    val summary = stringResource(Res.string.ds_workout_bar_spoken, state.workoutName, state.elapsedSpoken)
    val spoken = if (rest != null && state.restSpoken != null) {
        stringResource(Res.string.ds_workout_bar_rest_spoken, summary, state.restSpoken)
    } else {
        summary
    }
    val skipLabel = stringResource(Res.string.ds_timer_skip)
    val shape = topRoundedShape()
    val source = remember { MutableInteractionSource() }
    val timeStyle = MTheme.type.numSm
    val setsStyle = MTheme.type.labelMd.tabular()
    val horizontal = MTheme.space.s4
    val gap = MTheme.space.s3
    val ringGap = MTheme.space.s2
    val ring = MTheme.sizes.ringBar
    val skipSize = MTheme.sizes.iconButton56

    // The times of one line, in whole pixels: elapsed, a gap, then the rest (ring, gap, widest time) or the sets.
    val trailingPx = with(density) {
        fun width(text: String, style: TextStyle) =
            measurer.measure(text, style, softWrap = false, maxLines = 1, layoutDirection = direction, density = density).size.width
        val secondary = if (rest != null) ring.roundToPx() + ringGap.roundToPx() + width(WIDEST_REST, timeStyle) else width(state.setsText, setsStyle)
        width(state.elapsedText, timeStyle) + gap.roundToPx() + secondary
    }

    BoxWithConstraints(modifier) {
        // The content width of one line, less the gap before the times.
        val available = with(density) {
            constraints.maxWidth - horizontal.roundToPx() * 2 - gap.roundToPx() - (if (rest != null) skipSize.roundToPx() else 0)
        }
        // One line keeps at least half of it for the name.
        val oneLineFits = !constraints.hasBoundedWidth || trailingPx <= available / 2
        val twoLines = density.fontScale >= TWO_LINE_FONT_SCALE || !oneLineFits

        val name: @Composable (Modifier) -> Unit = { each ->
            MText(
                state.workoutName,
                MTheme.type.titleSm,
                modifier = each.testTag(WorkoutBarNameTag),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val elapsed: @Composable () -> Unit = {
            MText(state.elapsedText, timeStyle, modifier = Modifier.testTag(WorkoutBarTimeTag), softWrap = false, maxLines = 1)
        }
        val secondary: @Composable () -> Unit = {
            if (rest != null) {
                RestPart(rest, ringGap)
            } else {
                MText(
                    state.setsText,
                    setsStyle,
                    modifier = Modifier.testTag(WorkoutBarSetsTag),
                    color = colors.textSecondary,
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MTheme.targets.workout)
                .fixedBarSurface(shape)
                .semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = MTheme.targets.workout)
                    .testTag(WorkoutBarTag)
                    .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onOpen)
                    // One name for the whole bar; the texts inside are not read again, and nothing is live.
                    .clearAndSetSemantics { contentDescription = spoken }
                    .mFocusRing(shape, source)
                    .padding(horizontal = horizontal, vertical = MTheme.space.s2),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (twoLines) {
                    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
                        name(Modifier.fillMaxWidth())
                        // The times wrap under each other rather than be cut when even this line is too narrow.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            elapsed()
                            secondary()
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        name(Modifier.weight(1f))
                        elapsed()
                        secondary()
                    }
                }
            }
            if (rest != null) {
                MIconButton(
                    MIcons.SkipForward, skipLabel, onSkipRest,
                    modifier = Modifier.testTag(WorkoutBarSkipTag),
                    size = skipSize,
                )
            }
        }
    }
}

/** The running rest in the bar: the 32 dp ring (pause icon and a `text.tertiary` arc while paused) and its time. */
@Composable
private fun RestPart(rest: RestTimerVisual, gap: Dp) {
    val colors = MTheme.colors
    val progress = rememberRingProgress(rest)
    val done = rest.phase == TimerPhase.Done
    val paused = rest.phase == TimerPhase.Paused
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        RestTimerRing({ progress.value }, MTheme.sizes.ringBar, MTheme.sizes.ringBarStroke, ticks = false, phase = rest.phase) {
            MIcon(
                when (rest.phase) {
                    TimerPhase.Done -> MIcons.CircleCheck
                    TimerPhase.Paused -> MIcons.Pause
                    TimerPhase.Running -> MIcons.Timer
                },
                contentDescription = null,
                size = MTheme.sizes.icon16,
                tint = if (done) colors.timerProgress else colors.textSecondary,
            )
        }
        MText(
            MoosclesNumbers.clock(rest.remainingSeconds),
            MTheme.type.numSm,
            modifier = Modifier.testTag(WorkoutBarRestTag),
            color = if (paused) colors.textSecondary else colors.textPrimary,
            softWrap = false,
            maxLines = 1,
        )
    }
}
