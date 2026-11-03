package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_duration_and
import com.mooscles.designsystem.resources.ds_duration_list
import com.mooscles.designsystem.resources.ds_hours
import com.mooscles.designsystem.resources.ds_minutes
import com.mooscles.designsystem.resources.ds_seconds
import com.mooscles.designsystem.resources.ds_timer_announce
import com.mooscles.designsystem.resources.ds_timer_done
import com.mooscles.designsystem.resources.ds_timer_minus15
import com.mooscles.designsystem.resources.ds_timer_minus15_short
import com.mooscles.designsystem.resources.ds_timer_open
import com.mooscles.designsystem.resources.ds_timer_open_done
import com.mooscles.designsystem.resources.ds_timer_paused
import com.mooscles.designsystem.resources.ds_timer_plus15
import com.mooscles.designsystem.resources.ds_timer_plus15_short
import com.mooscles.designsystem.resources.ds_timer_skip
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

const val TimerDockTag = "timer-dock"
const val TimerTimeTag = "timer-time"
const val TimerMinusTag = "timer-minus15"
const val TimerPlusTag = "timer-plus15"
const val TimerSkipTag = "timer-skip"
const val TimerAnnounceTag = "timer-announce"
const val TimerPausedIconTag = "timer-paused-icon"
const val TimerOpenTag = "timer-open"

/** From this font scale on, the dock always stacks its buttons under the time (it also stacks when they do not fit beside it). */
private const val STACK_FONT_SCALE = 1.5f

/** A tick is the clock moving down by up to this many seconds; anything else is an adjustment. */
private const val TICK_SECONDS = 2

/** The widest time the dock shows, so the layout is chosen from a value that does not change every second. */
private const val DOCK_WIDEST_TIME = "00:00"

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/**
 * A duration as it is read aloud: "1 minute and 24 seconds", "42 minutes", "1 hour and 5 minutes"
 * (pt-BR "1 minuto e 24 segundos", "1 hora e 5 minutos"). Parts that are zero are left out, and a
 * zero duration reads "0 seconds" (zero stays plural). Ruling F5: public, so the screens speak the
 * workout clock and the in-progress bar with the same words as the timer; pass whole minutes
 * (`minutes * 60`) for a reading without seconds.
 */
@Composable
fun spokenDuration(totalSeconds: Int): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val parts = buildList {
        if (hours > 0) add(pluralCount(Res.plurals.ds_hours, hours))
        if (minutes > 0) add(pluralCount(Res.plurals.ds_minutes, minutes))
        if (seconds > 0 || (hours == 0 && minutes == 0)) add(pluralCount(Res.plurals.ds_seconds, seconds))
    }
    return when (parts.size) {
        1 -> parts[0]
        2 -> stringResource(Res.string.ds_duration_and, parts[0], parts[1])
        else -> stringResource(Res.string.ds_duration_list, parts[0], parts[1], parts[2])
    }
}

/** Remembers what was last announced so that only a start, an adjustment or the end changes it. */
private class Announcement {
    private var phase: TimerPhase? = null
    private var remaining = Int.MIN_VALUE
    var text = ""
        private set

    /** Counts the announcements made, so the view can tell a new one from a recomposition. */
    var serial = 0
        private set

    fun update(visual: RestTimerVisual, fresh: String): String {
        val ticked = phase == TimerPhase.Running && visual.phase == TimerPhase.Running &&
            remaining - visual.remainingSeconds in 0..TICK_SECONDS
        val unchanged = phase == visual.phase && (visual.phase != TimerPhase.Running || remaining == visual.remainingSeconds)
        if (!ticked && !unchanged) {
            text = fresh
            serial++
        }
        phase = visual.phase
        remaining = visual.remainingSeconds
        return text
    }
}

/**
 * The live-region text of a timer. It is set when the timer starts, when ±15 s or a skip moves the
 * clock, and when it ends, never on an ordinary tick, so a screen reader is not read to every second.
 * It is empty the rest of the time (cleared after `timing.timerAnnounceHold`), so the announcer is
 * not a stop for a screen reader and cannot be read stale.
 */
@Composable
internal fun rememberTimerAnnouncement(visual: RestTimerVisual): String {
    val holder = remember { Announcement() }
    val fresh = when (visual.phase) {
        TimerPhase.Done -> stringResource(Res.string.ds_timer_done)
        TimerPhase.Paused -> stringResource(Res.string.ds_timer_paused)
        TimerPhase.Running -> stringResource(Res.string.ds_timer_announce, spokenDuration(visual.remainingSeconds))
    }
    val text = holder.update(visual, fresh)
    val serial = holder.serial
    var clearedSerial by remember { mutableIntStateOf(-1) }
    val hold = MTheme.timing.timerAnnounceHold
    LaunchedEffect(serial) {
        delay(hold)
        clearedSerial = serial
    }
    return if (serial == clearedSerial) "" else text
}

/**
 * The 1 dp node that carries the polite live region: it adds nothing to the layout. Between
 * announcements [text] is empty and the node has no description, so a screen reader skips it.
 */
@Composable
internal fun TimerAnnouncer(text: String) {
    Box(
        Modifier
            .size(MTheme.sizes.border)
            .testTag(TimerAnnounceTag)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text.isNotEmpty()) contentDescription = text
            },
    )
}

/** The status line of a timer: the end, the pause, or its name. */
@Composable
internal fun timerStatus(visual: RestTimerVisual): String = when (visual.phase) {
    TimerPhase.Done -> stringResource(Res.string.ds_timer_done)
    TimerPhase.Paused -> stringResource(Res.string.ds_timer_paused)
    TimerPhase.Running -> visual.label
}

/** The side padding of a ±15 s label: `space.2`, so "−15 s" (about 40 dp) stays within the 56 dp target. */
internal val TimerAdjustPadding: Dp
    @Composable get() = MTheme.space.s2

/**
 * Gives the `RestFinished` haptic when [done] turns true while this timer view is shown, not when
 * the view enters the composition already done (switching between the dock and the expanded timer,
 * or coming back to a screen, during `timing.timerDoneHold`).
 */
@Composable
internal fun RestFinishedHaptic(done: Boolean) {
    val haptics = MTheme.haptics
    var wasDone by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !wasDone) haptics.perform(HapticEvent.RestFinished)
        wasDone = done
    }
}

/**
 * The ±15 s button, 56 dp at least (a disc of `timer.disc` in the expanded timer). [description] is
 * its label. Not [enabled] (+15 s at the 5:00 ceiling), it draws like a disabled `MButton`.
 */
@Composable
internal fun TimerAdjustButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MTheme.radius.md),
    size: Dp = MTheme.targets.workout,
    enabled: Boolean = true,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .sizeIn(minWidth = size, minHeight = size)
            .mPressScale(source, enabled)
            .semantics { contentDescription = description }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .mFocusRing(shape, source)
            .background(if (enabled) colors.bgRaised else colors.bgInset, shape)
            .then(if (enabled) Modifier.border(MTheme.sizes.border, colors.borderStrong, shape) else Modifier)
            .padding(horizontal = TimerAdjustPadding),
        contentAlignment = Alignment.Center,
    ) {
        MText(text, MTheme.type.labelLg, color = if (enabled) Color.Unspecified else colors.textDisabled)
    }
}

/**
 * The part of a timer that opens the expanded one: with [onOpen] one button named "Open rest
 * timer, 1 minute and 24 seconds remaining"; in pt-BR "…, tempo restante: 1 minuto e 24
 * segundos", which agrees with any number), without it plain content. The name stands for the
 * texts merged into it; they are merged, not cleared, so they stay in the tree for the tests.
 * Paused or done, the name alone would hide what the merged status line says, so the button also
 * reads that state ("Paused", "Rest complete"); done, the name drops the time left.
 * A press scales the body like the other workout controls (`motion.pressScale`).
 */
@Composable
internal fun Modifier.timerOpener(visual: RestTimerVisual, onOpen: (() -> Unit)?): Modifier {
    val tagged = testTag(TimerOpenTag)
    if (onOpen == null) return tagged
    val label = if (visual.phase == TimerPhase.Done) {
        stringResource(Res.string.ds_timer_open_done)
    } else {
        stringResource(Res.string.ds_timer_open, spokenDuration(visual.remainingSeconds))
    }
    val state = if (visual.phase == TimerPhase.Running) null else timerStatus(visual)
    val source = remember { MutableInteractionSource() }
    return tagged
        .mPressScale(source, enabled = true)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            if (state != null) stateDescription = state
        }
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onOpen)
        .mFocusRing(RoundedCornerShape(MTheme.radius.md), source)
}

/**
 * The rest timer docked above the bottom bar: a 48 dp ring, the name, the time (`num.dock`,
 * in `text.secondary` and with a pause icon and a `text.tertiary` arc while paused,
 * tabular, one line, never cut), then -15, +15 and skip, each 56 dp. The buttons stack under the
 * time from a font scale of 1.5, or sooner when the measured width of the widest time plus the
 * buttons does not fit the dock. When the phase turns [TimerPhase.Done] it gives the `RestFinished`
 * haptic once and says "Rest complete"; how long that stays up is the caller's (3 s,
 * `timing.timerDoneHold`). A hidden polite live region speaks the start, each adjustment and the
 * end, not every second. The component is presentational and owns no clock: the caller passes a new
 * [visual] each second, which recomposes the dock (the ring sweep is read while drawing).
 * With [onOpen], tapping the body (ring, name and time) opens the expanded timer; without [canAdd]
 * (the 5:00 ceiling) +15 s is disabled. The dock is one traversal group.
 */
@Composable
fun RestTimerDock(
    visual: RestTimerVisual,
    onMinus15: () -> Unit,
    onPlus15: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    canAdd: Boolean = true,
) {
    val colors = MTheme.colors
    val haptics = MTheme.haptics
    val density = LocalDensity.current
    val progress = rememberRingProgress(visual)
    val announcement = rememberTimerAnnouncement(visual)
    val status = timerStatus(visual)
    val done = visual.phase == TimerPhase.Done
    val paused = visual.phase == TimerPhase.Paused
    RestFinishedHaptic(done)
    val minus = { haptics.perform(HapticEvent.StepperStep); onMinus15() }
    val plus = { haptics.perform(HapticEvent.StepperStep); onPlus15() }
    val minusLabel = stringResource(Res.string.ds_timer_minus15)
    val plusLabel = stringResource(Res.string.ds_timer_plus15)
    val minusShort = stringResource(Res.string.ds_timer_minus15_short)
    val plusShort = stringResource(Res.string.ds_timer_plus15_short)
    val skipLabel = stringResource(Res.string.ds_timer_skip)
    val shape = RoundedCornerShape(MTheme.radius.lg)

    val measurer = rememberTextMeasurer()
    val direction = LocalLayoutDirection.current
    val timeStyle = MTheme.type.numDock
    val buttonStyle = MTheme.type.labelLg
    val padding = MTheme.space.s3
    val adjustPadding = TimerAdjustPadding
    val ring = MTheme.sizes.dockRing
    val target = MTheme.targets.workout
    val skipSize = MTheme.sizes.iconButton56
    val gap = MTheme.targets.gap
    // The inline row in whole pixels, rounded the way the layout rounds each piece: outer padding,
    // ring, two row gaps, the widest time, both ±15 s buttons, skip and the two gaps between them.
    val neededPx = with(density) {
        fun width(text: String, style: androidx.compose.ui.text.TextStyle) =
            measurer.measure(text, style, softWrap = false, maxLines = 1, layoutDirection = direction, density = density).size.width
        val time = width(DOCK_WIDEST_TIME, timeStyle)
        val label = maxOf(width(minusShort, buttonStyle), width(plusShort, buttonStyle))
        val adjust = maxOf(target.roundToPx(), label + adjustPadding.roundToPx() * 2)
        padding.roundToPx() * 4 + ring.roundToPx() + time + adjust * 2 + skipSize.roundToPx() + gap.roundToPx() * 2
    }

    // One traversal group, as the compact timer and the workout bar: a screen reader reads the
    // body, then -15 s, +15 s and skip, together, wherever the layout puts the buttons.
    BoxWithConstraints(modifier.testTag(TimerDockTag).semantics { isTraversalGroup = true }) {
        val fits = !constraints.hasBoundedWidth || constraints.maxWidth >= neededPx
        val stacked = density.fontScale >= STACK_FONT_SCALE || !fits
        val buttons: @Composable (Modifier) -> Unit = { each ->
            TimerAdjustButton(minusShort, minusLabel, minus, each.testTag(TimerMinusTag))
            TimerAdjustButton(plusShort, plusLabel, plus, each.testTag(TimerPlusTag), enabled = canAdd)
        }
        Column(
            Modifier.fillMaxWidth().background(colors.bgRaised, shape).padding(padding),
            verticalArrangement = Arrangement.spacedBy(padding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(padding)) {
                // The body, with the row's gap, so the inline width measured above still holds.
                Row(
                    Modifier.weight(1f).timerOpener(visual, onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(padding),
                ) {
                    RestTimerRing({ progress.value }, ring, MTheme.sizes.dockRingStroke, ticks = false, phase = visual.phase) {
                        MIcon(
                            when (visual.phase) {
                                TimerPhase.Done -> MIcons.CircleCheck
                                TimerPhase.Paused -> MIcons.Pause
                                TimerPhase.Running -> MIcons.Timer
                            },
                            contentDescription = null,
                            modifier = if (paused) Modifier.testTag(TimerPausedIconTag) else Modifier,
                            size = MTheme.sizes.icon20,
                            tint = if (done) colors.timerProgress else colors.textSecondary,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        MText(status, MTheme.type.labelMd, color = colors.textSecondary)
                        MText(
                            MoosclesNumbers.clock(visual.remainingSeconds),
                            timeStyle,
                            modifier = Modifier.testTag(TimerTimeTag),
                            color = if (paused) colors.textSecondary else colors.textPrimary,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                }
                if (!stacked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                        buttons(Modifier)
                        MIconButton(
                            MIcons.SkipForward, skipLabel, onSkip,
                            modifier = Modifier.testTag(TimerSkipTag),
                            size = skipSize,
                        )
                    }
                }
            }
            if (stacked) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    buttons(Modifier.fillMaxWidth())
                    MButton(
                        skipLabel, onSkip,
                        modifier = Modifier.fillMaxWidth().testTag(TimerSkipTag),
                        variant = MButtonVariant.Tonal,
                        size = MButtonSize.L,
                        icon = MIcons.SkipForward,
                    )
                }
            }
        }
        TimerAnnouncer(announcement)
    }
}
