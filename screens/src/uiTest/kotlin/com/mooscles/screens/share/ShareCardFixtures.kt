package com.mooscles.screens.share

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.screens.withScreen

/** What the card needs from a composition: the design system's styles and a measurer. */
internal class ShareCardInputs(val styles: ShareCardStyles, val measurer: TextMeasurer)

/** Resolves the fonts in a composition (in the light or dark app theme), then leaves it: drawing runs outside. */
@OptIn(ExperimentalTestApi::class)
internal fun captureShareCardInputs(dark: Boolean = true): ShareCardInputs {
    var inputs: ShareCardInputs? = null
    runComposeUiTest {
        withScreen(dark = dark) {
            inputs = ShareCardInputs(rememberShareCardStyles(), rememberTextMeasurer())
        }
        waitForIdle()
    }
    return checkNotNull(inputs)
}

private val fixtureLabels = ShareCardLabels(
    duration = "Duration", sets = "Sets", volume = "Volume", records = "Records",
    exercises = "Exercises", bestSet = "Best set", noCompletedSets = "No completed sets", recordBadge = "PR",
    setCountOne = "%1\$d set", setCountOther = "%1\$d sets",
)

/**
 * The "long-workout" preview of the former renderer test (48 exercises, a record every fifth),
 * which that renderer split into [TODAY_LONG_WORKOUT_PAGES] pages of at most 4096 px.
 */
internal fun longWorkoutCard(): ShareCardData {
    val rows = List(48) { index ->
        ShareCardRow("Exercise ${index + 1} · Controlled working sets", 3,
            if (index % 4 == 0) "Bodyweight × 12" else "${20 + index}\u00A0kg × 8", index % 5 == 0)
    }
    return ShareCardData(
        workoutName = "Full-body training",
        dateText = "Thu, Oct 22, 2026 · 20:55–21:39",
        durationText = "44:00",
        setsText = "144",
        volumeText = "12,450\u00A0kg",
        records = rows.filter { it.hasRecord }.map { ShareRecordRow(it.name, it.bestSet) },
        rows = rows,
        workoutNumber = 74,
        labels = fixtureLabels,
    )
}

/** Pages of [longWorkoutCard] in the 4096 px renderer this card replaced (measured at 3b848b0). */
internal const val TODAY_LONG_WORKOUT_PAGES = 3
