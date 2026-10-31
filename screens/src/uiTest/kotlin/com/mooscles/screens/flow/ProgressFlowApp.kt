package com.mooscles.screens.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.mooscles.designsystem.charts.LineChartTag
import com.mooscles.screens.isPlaced
import com.mooscles.screens.progress.ExerciseProgressTag
import com.mooscles.screens.progress.ProgressTags
import com.mooscles.screens.progress.RecordEvent
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsTags
import com.mooscles.ui.RecordUi
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.mooscles.data.WorkoutRepository
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.progress.ExerciseProgressRoute
import com.mooscles.screens.progress.LocalProgressIndexCache
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.screens.records.RecordsRoute
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs

/** The screens of [ProgressFlowApp], as the strings its `screen` holds. */
internal object ProgressScreens {
    /** The Train tab, with nothing in it: where the tap budgets start, as the baseline did. */
    const val TRAIN = ShellTabs.TRAIN
    const val RECORDS = ShellTabs.RECORDS
    private const val PROGRESS = "progress:"
    private const val INFO = "info:"

    /** The progress of [exerciseId] on [metric] (a `ProgressMetric.route`, or none: the convention's first). */
    fun progress(exerciseId: String, metric: String?) = "$PROGRESS$exerciseId:${metric.orEmpty()}"
    fun info(exerciseId: String) = "$INFO$exerciseId"

    fun isProgress(screen: String) = screen.startsWith(PROGRESS)
    fun isInfo(screen: String) = screen.startsWith(INFO)

    /** The exercise of a progress or detail screen (an id has no colon). */
    fun exerciseOf(screen: String): String = screen.substringAfter(':').substringBefore(':')

    /** The metric of a progress screen, or null for none. */
    fun metricOf(screen: String): String? = screen.substringAfterLast(':').ifEmpty { null }
}

/**
 * The progress path as `:app` hosts it (`MoosclesApp`), without its NavHost: the Records tab, an exercise's progress
 * and its detail, switched by [screen] (a [ProgressScreens] value) over the view model's `contentUi`, with the shell's
 * bottom (the tab bar) under them and the callbacks the app passes: a record opens the progress on its metric, "About
 * the exercise" opens the detail, and the detail's "View progress" opens the progress again. The Train tab is an
 * empty page. One [ProgressIndexCache] for every screen, as the app keeps one for the process. Clear of the system
 * bars and the keyboard, as the app's window is.
 */
@Composable
internal fun ProgressFlowApp(viewModel: FlowViewModel, screen: MutableState<String>) {
    val state by viewModel.contentUi.collectAsState()
    val timer = viewModel.timerUi.collectAsState()
    val scope = rememberCoroutineScope()
    val cache = remember(scope) { ProgressIndexCache(scope) }
    CompositionLocalProvider(LocalProgressIndexCache provides cache) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val current = screen.value
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    current == ProgressScreens.RECORDS -> RecordsRoute(
                        state, viewModel,
                        onOpenProgress = { id, metric -> screen.value = ProgressScreens.progress(id, metric.route) },
                        onOpenWorkout = {},
                        onOpenSettings = {},
                    )
                    ProgressScreens.isProgress(current) -> {
                        val id = ProgressScreens.exerciseOf(current)
                        ExerciseProgressRoute(
                            exerciseId = id,
                            metric = ProgressScreens.metricOf(current),
                            state = state,
                            actions = viewModel,
                            onBack = { screen.value = ProgressScreens.RECORDS },
                            onAbout = { screen.value = ProgressScreens.info(id) },
                            onOpenWorkout = {},
                        )
                    }
                    ProgressScreens.isInfo(current) -> {
                        val id = ProgressScreens.exerciseOf(current)
                        ExerciseInfoRoute(
                            exerciseId = id,
                            state = state,
                            actions = viewModel,
                            onBack = { screen.value = ProgressScreens.progress(id, null) },
                            onOpenHistory = { screen.value = ProgressScreens.progress(id, null) },
                            onOpenRoutine = {},
                            onOpenWorkout = {},
                        )
                    }
                    else -> Box(Modifier.fillMaxSize())
                }
            }
            AppShellBottom(
                state = state,
                timer = timer,
                selectedTab = if (current == ProgressScreens.TRAIN) ShellTabs.TRAIN else ShellTabs.RECORDS,
                onSelectTab = { screen.value = if (it == ShellTabs.RECORDS) ProgressScreens.RECORDS else ProgressScreens.TRAIN },
                onReselectTab = { screen.value = if (it == ShellTabs.RECORDS) ProgressScreens.RECORDS else ProgressScreens.TRAIN },
                onOpenWorkout = {},
                actions = viewModel,
                imeInsets = WindowInsets(0, 0, 0, 0),
            )
        }
    }
}

/** What [seedBenchProgress] wrote: the bench's six workouts (oldest first) and the squat's two, if any. */
internal class BenchSeed(val benchWorkouts: List<String>, val squatWorkouts: List<String>)

/**
 * Six finished workouts of the bench press, four days apart and ending yesterday, the load going up: 60, 65, 70, 75
 * and 80 kg × 5 (both sets), then 85 kg × 5 and 80 kg × 8, the newest workout's load record (beating 80 kg) and its
 * reps record (8 at 80 kg, beating 5). With [squat], the last two workouts start with the back squat (100, then 105 kg
 * × 5), so the newest workout's first record is the squat's and a search is needed to bring the bench's to the top;
 * without it, the bench's load record is the newest of all, the first row of the timeline.
 */
internal suspend fun seedBenchProgress(repository: WorkoutRepository, squat: Boolean): BenchSeed {
    repository.initialize()
    val now = currentTimeMillis()
    val loads = listOf(60.0, 65.0, 70.0, 75.0, 80.0, 85.0)
    val bench = ArrayList<String>()
    val squats = ArrayList<String>()
    for ((i, kg) in loads.withIndex()) {
        val start = now - (loads.size - i) * 4 * DAY_MS
        val workout = repository.startEmpty("Push ${i + 1}", now = start)
        val withSquat = squat && i >= loads.size - 2
        if (withSquat) {
            val entry = repository.addWorkoutExercise(workout, BACK_SQUAT)
            repository.workoutSets(workout).filter { it.workoutExerciseId == entry }.forEach {
                repository.updateSet(it.copy(reps = 5, weightKg = if (i == loads.lastIndex) 105.0 else 100.0, completed = true))
            }
            squats += workout
        }
        val entry = repository.addWorkoutExercise(workout, BENCH_PRESS)
        val sets = repository.workoutSets(workout).filter { it.workoutExerciseId == entry }.sortedBy { it.position }
        // The last workout: the heavier set, then more reps at the previous load.
        val values = if (i == loads.lastIndex) listOf(85.0 to 5, 80.0 to 8) else listOf(kg to 5, kg to 5)
        sets.zip(values).forEach { (set, value) -> repository.updateSet(set.copy(weightKg = value.first, reps = value.second, completed = true)) }
        check(repository.finishWorkout(workout, now = start + HOUR_MS))
        bench += workout
    }
    return BenchSeed(bench, squats)
}

// --- Finding and acting (ProgressFlowTest, TapBudgetTest) ------------------------------------------

/** The timeline's row of [record], as a current event (the key does not hold the label). */
internal fun recordRowTag(record: RecordUi): String = RecordsTags.row(RecordTimelines.key(RecordEvent(record, "", 0, current = true)))

private val recordRow = SemanticsMatcher("a record row") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RecordsTags.ROW_PREFIX) == true
}

/** The tag of the timeline's first row on screen, or null without one. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.topRecordRow(): String? =
    onAllNodes(recordRow and isPlaced).fetchSemanticsNodes()
        .filter { it.boundsInRoot.height > 0f }
        .minByOrNull { it.boundsInRoot.top }
        ?.config?.get(SemanticsProperties.TestTag)

/** Scrolls the progress screen's list to [tag]; scrolling is not a tap. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollProgressTo(tag: String) {
    onNodeWithTag(ExerciseProgressTag).performScrollToNode(hasTestTag(tag))
    waitForIdle()
}

/** What the progress chart says to a screen reader, or "" while there is none. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.progressChartSummary(): String =
    onAllNodes(hasTestTag(LineChartTag)).fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()

/**
 * Waits for the progress chart of [metric] (its name in the screen's language) to be drawn, scrolling to it: one image
 * node ([Role.Image]) whose summary starts with the name and says more than it.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.awaitProgressChart(metric: String) {
    await("the $metric chart is drawn") {
        runCatching { scrollProgressTo(ProgressTags.CHART) }.isSuccess && progressChartSummary().startsWith(metric)
    }
    assertEquals(Role.Image, onNodeWithTag(LineChartTag).fetchSemanticsNode().config[SemanticsProperties.Role])
    val summary = progressChartSummary()
    assertTrue(summary.length > metric.length, "the chart's summary says more than its name: '$summary'")
}

internal const val BENCH_PRESS = "catalog_barbell_bench_flat"
internal const val BACK_SQUAT = "catalog_barbell_back_squat_high_bar"
private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS
