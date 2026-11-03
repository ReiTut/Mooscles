package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

const val TimerCompactTag = "timer-compact"
const val TimerCompactRingTag = "timer-compact-ring"

/**
 * The compact rest timer (spec D15): one `sizes.rowMin` (56 dp) row with the small ring
 * (`sizes.ringBar`; the pause icon and a `text.tertiary` arc while paused), the time in `num.dock`
 * (tabular, one line, never cut) and skip (56 dp), without ±15 s, which stay in the expanded timer.
 * It stands in for the dock above the keyboard and at large text. With [onOpen], tapping the ring
 * and time opens the expanded timer. Same phases, `RestFinished` haptic and polite live region as
 * [RestTimerDock]; presentational too.
 */
@Composable
fun RestTimerCompact(
    visual: RestTimerVisual,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    val colors = MTheme.colors
    val space = MTheme.space
    val sizes = MTheme.sizes
    val progress = rememberRingProgress(visual)
    val announcement = rememberTimerAnnouncement(visual)
    val done = visual.phase == TimerPhase.Done
    val paused = visual.phase == TimerPhase.Paused
    RestFinishedHaptic(done)
    val shape = RoundedCornerShape(MTheme.radius.lg)

    Box(modifier.testTag(TimerCompactTag)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = sizes.rowMin)
                .background(colors.bgRaised, shape)
                .semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = sizes.rowMin)
                    .timerOpener(visual, onOpen)
                    .padding(start = space.s3, end = space.s2),
                horizontalArrangement = Arrangement.spacedBy(space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestTimerRing(
                    { progress.value }, sizes.ringBar, sizes.ringBarStroke, ticks = false, phase = visual.phase,
                    modifier = Modifier.testTag(TimerCompactRingTag),
                ) {
                    MIcon(
                        when (visual.phase) {
                            TimerPhase.Done -> MIcons.CircleCheck
                            TimerPhase.Paused -> MIcons.Pause
                            TimerPhase.Running -> MIcons.Timer
                        },
                        contentDescription = null,
                        modifier = if (paused) Modifier.testTag(TimerPausedIconTag) else Modifier,
                        size = sizes.icon16,
                        tint = if (done) colors.timerProgress else colors.textSecondary,
                    )
                }
                MText(
                    MoosclesNumbers.clock(visual.remainingSeconds),
                    MTheme.type.numDock,
                    modifier = Modifier.testTag(TimerTimeTag),
                    color = if (paused) colors.textSecondary else colors.textPrimary,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            MIconButton(
                MIcons.SkipForward, stringResource(Res.string.ds_timer_skip), onSkip,
                modifier = Modifier.testTag(TimerSkipTag),
                size = sizes.iconButton56,
            )
        }
        TimerAnnouncer(announcement)
    }
}
