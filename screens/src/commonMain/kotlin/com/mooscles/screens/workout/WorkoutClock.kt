package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.spokenDuration
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.Durations
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.shell_elapsed_under_minute
import com.mooscles.screens.resources.workout_clock_spoken
import com.mooscles.screens.shell.rememberElapsedSeconds
import org.jetbrains.compose.resources.stringResource

/** The test tag of the workout clock in the header. */
internal const val WorkoutClockTag = "workout-clock"

private const val SECONDS_PER_MINUTE = 60

/**
 * The time since [startedAt], in `num.sm`: "42:18", "1:02:18" from an hour on. It reads
 * [nowMillis] once a second, on the second the clock turns, and is the only composable of the
 * screen that recomposes then (`RecompositionProbe("clock")`). Read aloud in whole minutes
 * ("Workout time, 42 minutes"; "less than a minute" before the first), with no live region, so a
 * screen reader does not announce every second.
 */
@Composable
internal fun WorkoutClock(startedAt: Long, nowMillis: () -> Long, modifier: Modifier = Modifier) {
    RecompositionProbe("clock")
    val elapsed by rememberElapsedSeconds(startedAt, nowMillis)
    val minutes = (elapsed / SECONDS_PER_MINUTE).toInt()
    val spokenTime = if (minutes == 0) {
        stringResource(Res.string.shell_elapsed_under_minute)
    } else {
        spokenDuration(minutes * SECONDS_PER_MINUTE)
    }
    val spoken = stringResource(Res.string.workout_clock_spoken, spokenTime)
    MText(
        Durations.clock(elapsed),
        MTheme.type.numSm,
        modifier = modifier.testTag(WorkoutClockTag).semantics { contentDescription = spoken },
        softWrap = false,
        maxLines = 1,
    )
}
