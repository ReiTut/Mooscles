package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.mooscles.designsystem.components.ActiveWorkoutBar
import com.mooscles.designsystem.components.ActiveWorkoutBarState
import com.mooscles.designsystem.components.MTab
import com.mooscles.designsystem.components.MTabBar
import com.mooscles.designsystem.components.RestTimerVisual
import com.mooscles.designsystem.components.TimerPhase
import com.mooscles.designsystem.components.spokenDuration
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_timer_rest
import com.mooscles.designsystem.resources.gallery_sample_long_workout
import com.mooscles.designsystem.resources.gallery_sample_sets_done
import com.mooscles.designsystem.resources.gallery_sample_workout
import com.mooscles.designsystem.resources.gallery_section_shell
import com.mooscles.designsystem.resources.gallery_tab_history
import com.mooscles.designsystem.resources.gallery_tab_library
import com.mooscles.designsystem.resources.gallery_tab_records
import com.mooscles.designsystem.resources.gallery_tab_train
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val ELAPSED_SECONDS = 42 * 60 + 18
private const val REST_TOTAL = 120
private const val REST_REMAINING = 84
private const val SECONDS_PER_MINUTE = 60
private const val TWO_HUNDRED_PERCENT = 2f

/**
 * The shell of the app: the tab bar at the page's text size and at 200% (where only the selected
 * tab keeps its label), then the in-progress workout bar without a rest, with one (its skip
 * button ends the sample rest) and with a long name.
 */
@Composable
fun ShellPage() {
    val tabs = listOf(
        MTab("routines", stringResource(Res.string.gallery_tab_train), MIcons.Dumbbell, MIcons.DumbbellFilled),
        MTab("library", stringResource(Res.string.gallery_tab_library), MIcons.LayoutGrid, MIcons.LayoutGridFilled),
        MTab("history", stringResource(Res.string.gallery_tab_history), MIcons.History, MIcons.HistoryFilled),
        MTab("records", stringResource(Res.string.gallery_tab_records), MIcons.PlateRecord, MIcons.PlateRecordFilled),
    )
    var selected by remember { mutableStateOf("routines") }
    var resting by remember { mutableStateOf(true) }
    val restLabel = stringResource(Res.string.ds_timer_rest)
    val name = stringResource(Res.string.gallery_sample_workout)
    val sets = stringResource(Res.string.gallery_sample_sets_done)
    val elapsedSpoken = spokenDuration(ELAPSED_SECONDS / SECONDS_PER_MINUTE * SECONDS_PER_MINUTE)
    val restSpoken = spokenDuration(REST_REMAINING)
    fun bar(workout: String, rest: Boolean) = ActiveWorkoutBarState(
        workoutName = workout,
        elapsedText = MoosclesNumbers.clock(ELAPSED_SECONDS),
        elapsedSpoken = elapsedSpoken,
        setsText = sets,
        rest = if (rest) RestTimerVisual(REST_REMAINING, REST_TOTAL, TimerPhase.Running, restLabel) else null,
        restSpoken = if (rest) restSpoken else null,
    )
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_shell))
        Sample("MTabBar") {
            MTabBar(tabs, selected, onSelect = { selected = it }, onReselect = {})
        }
        Sample("MTabBar, fontScale = 2") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, TWO_HUNDRED_PERCENT)) {
                MTabBar(tabs, selected, onSelect = { selected = it }, onReselect = {})
            }
        }
        Sample("ActiveWorkoutBar, rest = null") {
            ActiveWorkoutBar(bar(name, rest = false), onOpen = {}, onSkipRest = {})
        }
        Sample("ActiveWorkoutBar, rest = Running") {
            ActiveWorkoutBar(bar(name, rest = resting), onOpen = {}, onSkipRest = { resting = false })
        }
        Sample("ActiveWorkoutBar, long name") {
            ActiveWorkoutBar(bar(stringResource(Res.string.gallery_sample_long_workout), rest = true), onOpen = {}, onSkipRest = {})
        }
    }
}
