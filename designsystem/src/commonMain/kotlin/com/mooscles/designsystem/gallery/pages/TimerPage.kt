package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.RestTimerCompact
import com.mooscles.designsystem.components.RestTimerDock
import com.mooscles.designsystem.components.RestTimerExpanded
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_rest
import com.mooscles.designsystem.resources.gallery_finish_rest
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.resources.gallery_sample_exercise
import com.mooscles.designsystem.resources.gallery_sample_exercise_next
import com.mooscles.designsystem.resources.gallery_section_timer
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val TOTAL = 120
private const val STEP = 15
private const val SAMPLE_REMAINING = 84
/** The 5:00 ceiling of the rest: at it, +15 s is disabled. */
private const val CEILING = 300

/**
 * The dock running (its body is the button that opens the expanded timer), paused, at the 5:00 ceiling (+15 s
 * disabled) and done; the compact timer running, paused and done; and the expanded timer, whose ±15
 * s, pause and restart work.
 */
@Composable
fun TimerPage() {
    val label = stringResource(Res.string.ds_timer_rest)
    val next = stringResource(Res.string.gallery_sample_exercise_next)
    val title = "$label · ${stringResource(Res.string.gallery_sample_exercise)}"
    var finished by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(SAMPLE_REMAINING) }
    var paused by remember { mutableStateOf(false) }
    fun visual(phase: TimerPhase, seconds: Int) = RestTimerVisual(seconds, TOTAL, phase, label)
    val minus = { remaining = (remaining - STEP).coerceAtLeast(0) }
    val plus = { remaining = (remaining + STEP).coerceAtMost(CEILING) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_timer))
        RestTimerDock(visual(TimerPhase.Running, remaining), minus, plus, {}, onOpen = {}, canAdd = remaining < CEILING)
        RestTimerDock(visual(TimerPhase.Paused, SAMPLE_REMAINING), {}, {}, {})
        RestTimerDock(visual(TimerPhase.Running, CEILING), {}, {}, {}, canAdd = false)
        if (finished) {
            RestTimerDock(visual(TimerPhase.Done, 0), {}, {}, {})
        } else {
            MButton(stringResource(Res.string.gallery_finish_rest), { finished = true }, variant = MButtonVariant.Secondary)
        }
        RestTimerCompact(visual(TimerPhase.Running, remaining), {}, onOpen = {})
        RestTimerCompact(visual(TimerPhase.Paused, SAMPLE_REMAINING), {})
        RestTimerCompact(visual(TimerPhase.Done, 0), {})
        RestTimerExpanded(
            visual(if (paused) TimerPhase.Paused else TimerPhase.Running, remaining), next,
            minus, plus, {},
            title = title,
            totalText = MoosclesNumbers.clock(TOTAL),
            onPauseResume = { paused = !paused },
            onRestart = { remaining = TOTAL },
            onClose = {},
            modifier = Modifier.padding(top = MTheme.space.s4),
            canAdd = remaining < CEILING,
        )
    }
}
