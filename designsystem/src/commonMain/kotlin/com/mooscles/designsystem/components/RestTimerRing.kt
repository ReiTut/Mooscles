package com.mooscles.designsystem.components

import androidx.compose.runtime.State
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the rest timer is: counting, held, or finished. */
enum class TimerPhase { Running, Paused, Done }

/**
 * What the timer components draw. They are presentational: the owner keeps the countdown and
 * passes it in. [label] is the already localized name of the timer ("Rest").
 */
data class RestTimerVisual(
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val phase: TimerPhase,
    val label: String,
)

private const val MS_PER_SECOND = 1000L

/**
 * The share of the ring still to go, from 1 (just started) to 0. It is linear in the time left.
 * With [reduced] it steps once a second: the remaining time is rounded up to the whole second,
 * which is the second the clock shows, so 59.4 s of 120 s draws exactly 60/120.
 */
fun ringProgress(remainingMs: Long, totalMs: Long, reduced: Boolean): Float {
    if (totalMs <= 0L) return 0f
    val left = remainingMs.coerceIn(0L, totalMs)
    val shown = if (reduced) minOf(((left + MS_PER_SECOND - 1) / MS_PER_SECOND) * MS_PER_SECOND, totalMs) else left
    return shown.toFloat() / totalMs.toFloat()
}

/** The angles, in degrees clockwise from 12 o'clock, of [count] marks evenly around the rim. */
fun tickAngles(count: Int = 60): List<Float> =
    if (count <= 0) emptyList() else List(count) { it * 360f / count }

/**
 * The color of the progress arc: `timer.progress` while the timer runs or has finished, and
 * `text.tertiary` while it is paused, so a held timer does not look like a running one. The paused
 * arc stays at 3:1 or more against the dock (`bg.raised`: 5.96 light, 5.63 dark) and against the
 * track (4.28 and 4.22); `border.strong` would be 2.53 against the dark track. The pause icon and
 * the "Paused" label say it without color too.
 */
internal fun ringBarColor(phase: TimerPhase, colors: MoosclesColors): Color =
    if (phase == TimerPhase.Paused) colors.textTertiary else colors.timerProgress

private const val TURN = 360f
private const val QUARTER_TURN = 90f

/**
 * The plate-shaped ring: a track, the progress arc (`timer.track`, `timer.progress`) and, with
 * [ticks], the rim marks of a weight plate in `border.subtle`; the arc is drawn in [ringBarColor] for
 * [phase]. [progress] is the share still to go;
 * it is read only while drawing, so a tick redraws the ring and recomposes nothing around it.
 * [content] sits in the middle.
 */
@Composable
internal fun RestTimerRing(
    progress: () -> Float,
    diameter: Dp,
    stroke: Dp,
    ticks: Boolean,
    phase: TimerPhase,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val colors = MTheme.colors
    val track = colors.timerTrack
    val bar = ringBarColor(phase, colors)
    val rim = colors.borderSubtle
    val sizes = MTheme.sizes
    val tickLength = sizes.tickLength
    val tickWidth = sizes.tickWidth
    val tickCount = sizes.tickCount
    val gap = MTheme.space.s1
    val angles = remember(tickCount) { tickAngles(tickCount) }
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val strokePx = stroke.toPx()
            val edge = if (ticks) tickLength.toPx() + gap.toPx() else 0f
            val outer = size.minDimension / 2f
            val radius = outer - edge - strokePx / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arc = Size(radius * 2f, radius * 2f)
            drawArc(track, 0f, TURN, false, topLeft, arc, style = Stroke(strokePx))
            val sweep = progress().coerceIn(0f, 1f) * TURN
            if (sweep > 0f) {
                drawArc(bar, -QUARTER_TURN, sweep, false, topLeft, arc, style = Stroke(strokePx, cap = StrokeCap.Round))
            }
            if (ticks) {
                val lengthPx = tickLength.toPx()
                for (angle in angles) {
                    val radians = (angle - QUARTER_TURN) * PI.toFloat() / 180f
                    val direction = Offset(cos(radians), sin(radians))
                    drawLine(rim, center + direction * (outer - lengthPx), center + direction * outer, tickWidth.toPx())
                }
            }
        }
        content()
    }
}

/**
 * The ring progress as a state swept linearly over each second (the timer only reports whole
 * seconds); reduced motion jumps straight to the stepped value. A finished timer draws the ring full, with the check.
 */
@Composable
internal fun rememberRingProgress(visual: RestTimerVisual): State<Float> {
    val reduced = MTheme.reducedMotion
    val target = when (visual.phase) {
        TimerPhase.Done -> 1f
        else -> ringProgress(visual.remainingSeconds * MS_PER_SECOND, visual.totalSeconds * MS_PER_SECOND, reduced)
    }
    // Steady sweep: each reported second is crossed in one second, linearly. Reduced motion and the
    // end jump straight to the value.
    val spec = if (reduced || visual.phase == TimerPhase.Done) {
        tween<Float>(MTheme.motion.instant)
    } else {
        tween(MS_PER_SECOND.toInt(), easing = LinearEasing)
    }
    return animateFloatAsState(target, spec, label = "ring")
}
