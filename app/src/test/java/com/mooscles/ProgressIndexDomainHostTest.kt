package com.mooscles

import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.AppData
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.LoadConvention
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import com.mooscles.data.writeTransaction
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.screens.progress.SeriesKey
import com.mooscles.screens.progress.SessionPoint
import com.mooscles.screens.progress.SetValue
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsQuery
import com.mooscles.screens.summary.SummaryStats
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.math.abs
import kotlin.math.ulp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The progress index (`:screens`) against the real domain (`WorkoutCalculations`), on the `UiState` the real
 * `AppViewModel` publishes from Room: the large history (208 workouts × 61 sets over 48 catalog exercises) and a few
 * workouts by hand with assisted sets, bodyweight without load, the Assistance convention and warm-ups (plan
 * decision 4: `WorkingSets` mirrors the domain's private set rules, and this is their proof).
 *
 * Accepted divergences (pre-flight P12), and only these: `WorkingSets` and the domain skip a completed set with
 * negative reps or a negative or non-finite load, which `WorkoutUi.volumeKg` still multiplies in; and `WorkingSets`
 * also reads an enum name (`ASSISTANCE`) as its convention, which the presentation never passes (it passes labels,
 * checked below). The last hand workout carries such sets (negative reps, a negative load; a non-finite load takes
 * the same filter), so the first divergence is shown and measured to the kilo; everything else must match exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ProgressIndexDomainHostTest {
    private lateinit var app: MoosclesApplication
    private val models = mutableListOf<AppViewModel>()

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("mooscles.db")
    }

    @After fun close() {
        models.forEach { it.viewModelScope.cancel() }
        app.appScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        app.database.close()
    }

    @Test fun indexAgreesWithTheDomainOnTheLargeHistory() {
        val (state, data) = seeded()
        val index = ProgressIndex.build(state.history, state.records)

        // The presentation passes convention labels, never enum names: the second divergence cannot happen here.
        val labels = LoadConvention.entries.map { it.label }.toSet()
        assertTrue(state.history.all { w -> w.exercises.all { it.exercise.convention in labels } })

        // Sessions: one per finished workout of each exercise and convention with a valid set, as the domain's
        // performance histories have them, with the domain's volume.
        val performances = WorkoutCalculations.performanceHistories(data)
        val keys = performances.keys.map { (id, convention) -> SeriesKey(id, convention.label) }.toSet()
        assertEquals(keys, state.history.flatMap { w -> w.exercises.map { SeriesKey(it.exercise.id, it.exercise.convention) } }
            .filter { index.sessions(it).isNotEmpty() }.toSet())
        performances.forEach { (key, sessions) ->
            val points = index.sessions(SeriesKey(key.first, key.second.label))
            val domain = sessions.groupBy { it.workout.id }.mapValues { (_, entries) -> entries.sumOf { it.volumeKg } }
            assertEquals("$key", domain.keys.sortedBy { id -> sessions.first { it.workout.id == id }.workout.startedAt },
                points.map(SessionPoint::workoutId))
            points.forEach { assertEquals("$key ${it.workoutId}", domain.getValue(it.workoutId), it.volumeKg, 0.0) }
        }

        // Volume: a workout's sessions add up to its WorkoutUi.volumeKg, except for the divergent sets, by exactly them.
        val divergent = state.history.single { it.name == DIVERGENT }
        val domainVolumes = performances.values.flatten().groupBy { it.workout.id }.mapValues { (_, p) -> p.sumOf { it.volumeKg } }
        var compared = 0
        state.history.forEach { workout ->
            val sessions = keys.mapNotNull { key -> index.sessions(key).firstOrNull { it.workoutId == workout.id } }
            val indexVolume = sessions.sumOf(SessionPoint::volumeKg)
            assertEquals(workout.name, domainVolumes[workout.id] ?: 0.0, indexVolume, 1e-9)
            val ignored = workout.exercises.filter { it.exercise.convention != LoadConvention.ASSISTANCE.label }
                .flatMap { it.sets }.filter { it.completed && !it.assistance && (it.reps <= 0 || it.weightKg < 0.0) }
                .sumOf { it.weightKg * it.reps }
            if (workout === divergent) assertTrue("the divergent workout has such sets", ignored != 0.0)
            else assertEquals(workout.name, 0.0, ignored, 0.0)
            assertEquals(workout.name, workout.volumeKg, indexVolume + ignored, 1e-9)
            compared++
        }
        assertEquals(LargeHistoryFixture.WORKOUTS + HAND_WORKOUTS, compared)

        // Load: the set SummaryStats.bestSet picks, when it holds a load above 0; Assistance has its own measure.
        state.history.forEach { workout ->
            workout.exercises.groupBy { SeriesKey(it.exercise.id, it.exercise.convention) }.forEach { (key, entries) ->
                if (key.convention == LoadConvention.ASSISTANCE.label) return@forEach
                val point = index.sessions(key).singleOrNull { it.workoutId == workout.id } ?: return@forEach
                val merged = WorkoutExerciseUi(entries.first().id, entries.first().exercise, entries.flatMap { it.sets })
                val best = SummaryStats.bestSet(merged)?.takeIf { it.weightKg > 0.0 }
                assertEquals("${workout.name} ${key.exerciseId}", best?.let { SetValue(it.weightKg, it.weightKg, it.reps) }, point.load)
            }
        }

        // Marks, both ways, against the records of the UiState (D6 and the Task 4 review).
        val all = state.history.flatMap { it.records } + state.records
        fun assisted(key: SeriesKey) = key.convention == LoadConvention.ASSISTANCE.label
        fun recordsOf(kind: RecordKind, key: SeriesKey, workoutId: String) = all.filter {
            it.kind == kind && !it.firstRecord && it.exercise.id == key.exerciseId &&
                it.exercise.convention == key.convention && it.workoutId == workoutId &&
                (kind == RecordKind.WEIGHT || it.assistance == assisted(key))
        }
        fun drew(record: RecordUi, set: SetValue?) = set != null && sameLoad(record.weightKg, set.weightKg) && record.reps == set.reps
        var marks = 0
        keys.forEach { key ->
            index.sessions(key).forEach { point ->
                val where = "${key.exerciseId} ${point.workoutId}"
                assertEquals(where, recordsOf(RecordKind.WEIGHT, key, point.workoutId).any { drew(it, point.load) }, point.weightRecord)
                assertEquals(where, recordsOf(RecordKind.REPS, key, point.workoutId).any { drew(it, point.reps) }, point.repsRecord)
                if (point.weightRecord) marks++
                if (point.repsRecord) marks++
            }
        }
        // The other way: every beaten record of a chart's group marks its session when its set is the plotted one,
        // and the only one that is not is the hand-made Assistance reps record at another load.
        val beaten = all.filter { !it.firstRecord }.filter {
            it.kind == RecordKind.WEIGHT || it.assistance == (it.exercise.convention == LoadConvention.ASSISTANCE.label)
        }.distinctBy { listOf(it.kind, it.exercise.id, it.exercise.convention, it.workoutId) }
        val unplotted = beaten.filter { record ->
            val key = SeriesKey(record.exercise.id, record.exercise.convention)
            val point = index.sessions(key).single { it.workoutId == record.workoutId }
            !drew(record, if (record.kind == RecordKind.WEIGHT) point.load else point.reps)
        }
        assertEquals(listOf("${ASSISTED_DIP}|${RecordKind.REPS}"), unplotted.map { "${it.exercise.id}|${it.kind}" })
        assertEquals(beaten.size - unplotted.size, marks)
        assertTrue("the history beats weight records", beaten.count { it.kind == RecordKind.WEIGHT } > 10)
        assertTrue("and reps records in both groups", beaten.filter { it.kind == RecordKind.REPS }.map { it.assistance }.toSet() == setOf(true, false))
    }

    @Test fun timelineCurrentMatchesTheRecordsTab() {
        val (state, _) = seeded()
        // Through the cache the screens use, built on Dispatchers.Default under the app's scope.
        val cache = ProgressIndexCache(app.appScope)
        val index = runBlocking { cache.get(state.history, state.records) }
        assertSame(index, cache.peek(state.history, state.records))

        assertTrue(state.records.size > 48)
        assertEquals(state.records.size, index.currentRecords.size)
        val zone = TimeZone.of("America/Sao_Paulo")
        assertEquals(state.records.size, RecordTimelines.query(index, RecordsQuery(currentOnly = true, zone = zone)).total)
        // Each current record is shown as itself: same exercise, convention, kind, assistance, workout and load.
        state.records.forEach { record ->
            assertTrue("$record", index.currentRecords.any {
                it.current && it.record.workoutId == record.workoutId && it.record.kind == record.kind &&
                    it.record.exercise.id == record.exercise.id && it.record.exercise.convention == record.exercise.convention &&
                    it.record.assistance == record.assistance && sameLoad(it.record.weightKg, record.weightKg)
            })
        }
    }

    /** Seeds the large history and the hand workouts through the DAO, then waits for the published UiState. */
    private fun seeded(): Pair<UiState, AppData> {
        val data = runBlocking(Dispatchers.IO) {
            app.preferences.autoRest(false)
            app.repository.initialize()
            val rows = LargeHistoryFixture.rows(LargeHistoryFixture.variedExercises(48))
            val hand = handWorkouts()
            app.database.writeTransaction {
                val dao = app.database.dao()
                dao.insertWorkouts(rows.workouts + hand.workouts)
                dao.insertWorkoutExercises(rows.entries + hand.entries)
                dao.insertSets(rows.sets + hand.sets)
            }
            app.repository.snapshot()
        }
        val model = AppViewModel(app).also { models += it }
        val expected = LargeHistoryFixture.WORKOUTS + HAND_WORKOUTS
        val deadline = System.nanoTime() + 60_000_000_000L
        while (model.contentUi.value.let { it.loading || it.history.size != expected } && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        val state = model.contentUi.value
        assertFalse("the history was never published: ${state.error}", state.loading)
        assertEquals(expected, state.history.size)
        assertNull(state.active)
        return state to data
    }

    /**
     * After the large history: bench with warm-ups, assisted sets and reps records in both groups; a pull-up
     * without load, then with; an Assistance machine whose beaten reps record is not its session's most reps
     * (the Task 4 review's case); and a workout whose completed sets have negative reps and a negative load.
     */
    private fun handWorkouts(): LargeHistoryFixture.Rows {
        val workouts = ArrayList<Workout>()
        val entries = ArrayList<WorkoutExercise>()
        val sets = ArrayList<WorkoutSet>()
        fun workout(name: String, day: Int, vararg exercises: Pair<String, List<WorkoutSet>>) {
            val start = 1_700_000_000_000L + day * 86_400_000L
            val workout = Workout(name = name, startedAt = start, endedAt = start + 3_600_000L, status = WorkoutStatus.FINISHED)
            workouts += workout
            exercises.forEachIndexed { position, (id, rows) ->
                val exercise = ExerciseCatalog.exercises.first { it.id == id }
                val entry = WorkoutExercise(workoutId = workout.id, exerciseId = id, name = exercise.name, muscle = exercise.muscle,
                    equipment = exercise.equipment, convention = exercise.convention, position = position)
                entries += entry
                sets += rows.mapIndexed { index, row -> row.copy(workoutExerciseId = entry.id, position = index) }
            }
        }
        fun set(weightKg: Double, reps: Int, warmup: Boolean = false, assistance: Boolean = false) =
            WorkoutSet(workoutExerciseId = "", reps = reps, weightKg = weightKg, completed = true, warmup = warmup, assistance = assistance)

        workout("Hand A", 210,
            BENCH to listOf(set(60.0, 10, warmup = true), set(100.0, 10), set(60.0, 10, assistance = true)),
            PULL_UP to listOf(set(0.0, 5, warmup = true), set(0.0, 8), set(0.0, 10)),
            ASSISTED_DIP to listOf(set(40.0, 5, warmup = true), set(30.0, 8), set(20.0, 6)),
        )
        workout("Hand B", 212,
            BENCH to listOf(set(60.0, 12, warmup = true), set(102.5, 3), set(100.0, 11), set(60.0, 12, assistance = true)),
            PULL_UP to listOf(set(0.0, 12), set(10.0, 5)),
            ASSISTED_DIP to listOf(set(30.0, 8), set(20.0, 7)),
        )
        workout("Hand C", 214,
            PULL_UP to listOf(set(12.5, 5), set(0.0, 10)),
            ASSISTED_DIP to listOf(set(25.0, 9)),
        )
        workout(DIVERGENT, 216,
            BENCH to listOf(set(60.0, 5), set(40.0, -3), set(-10.0, 5)),
        )
        return LargeHistoryFixture.Rows(workouts, entries, sets)
    }

    private fun sameLoad(first: Double, second: Double) = abs(first - second) <= maxOf(first.ulp, second.ulp) * 4

    private companion object {
        const val HAND_WORKOUTS = 4
        const val DIVERGENT = "Hand D, divergent inputs"
        const val BENCH = "catalog_barbell_bench_flat"
        const val PULL_UP = "catalog_bodyweight_pull_up"
        const val ASSISTED_DIP = "catalog_selectorized_assisted_dip"
    }
}
