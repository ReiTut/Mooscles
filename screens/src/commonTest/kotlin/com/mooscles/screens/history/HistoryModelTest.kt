package com.mooscles.screens.history

import com.mooscles.data.MuscleRegion
import com.mooscles.data.ExerciseNames
import com.mooscles.data.SearchFolding
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class HistoryModelTest {
    private val zone = TimeZone.UTC
    private val today = LocalDate(2026, 11, 4)
    private val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell")
    private val row = ExerciseUi("custom-row", "Remada do João", "Back", "Cable", custom = true)
    private fun entry(exercise: ExerciseUi = bench, vararg sets: SetUi) =
        WorkoutExerciseUi("entry-${exercise.id}", exercise, sets.toList())
    private fun set(id: String = "s", reps: Int = 8, kg: Double = 20.0, completed: Boolean = true,
        warmup: Boolean = false, assisted: Boolean = false) = SetUi(id, reps, kg, completed, warmup, assisted)
    private fun workout(id: String, day: String = "2026-11-04", routine: String? = null,
        name: String = "Workout $id", entries: List<WorkoutExerciseUi> = listOf(entry(bench, set()))) =
        LocalDate.parse(day).atStartOfDayIn(zone).toEpochMilliseconds().let {
            WorkoutUi(id, name, it, it + 3_600_000, entries, sourceRoutineId = routine)
        }
    private fun index(vararg workouts: WorkoutUi) = ProgressIndex.build(workouts.toList(), emptyList())
    private fun query(index: ProgressIndex, search: String = "", routines: Set<String> = emptySet(),
        others: Boolean = false, known: Set<String> = emptySet(), exercise: String? = null,
        beaten: Boolean = false, day: LocalDate? = null) = HistoryQuery(search, routines, others,
            known, exercise, beaten, day, zone).compute(index)
    private fun overall(index: ProgressIndex, period: HistoryPeriod = HistoryPeriod.THREE_MONTHS,
        date: LocalDate = today, weekStart: DayOfWeek = DayOfWeek.SUNDAY, timeZone: TimeZone = zone) =
        OverallProgressQuery(period, date, timeZone, weekStart).compute(index)

    @Test fun emptyHistoryHasEmptyFiltersCalendarAndProgress() {
        val index = index()
        assertTrue(query(index).workouts.isEmpty())
        assertEquals(null, query(index).earliestDay)
        assertEquals(0, HistoryCalendarQuery(2026, 11, zone).compute(index).totalWorkouts)
        val progress = overall(index)
        assertEquals(0, progress.workouts)
        assertEquals(0, progress.sets)
        assertEquals(0.0, progress.volumeKg)
        assertEquals(13, progress.weeks)
    }

    @Test fun searchFindsCatalogNamesInBothLanguagesAndFoldsAccents() {
        val index = index(workout("bench"), workout("row", entries = listOf(entry(row, set()))))
        assertEquals(listOf("bench"), query(index, search = "barbell bench").workouts.map { it.id })
        assertEquals(listOf("bench"), query(index, search = "supino barra").workouts.map { it.id })
        assertEquals(listOf("row"), query(index, search = "remada joao").workouts.map { it.id })
    }

    @Test fun cachedNamePiecesExactlyMatchTheOriginalWholeStringFold() {
        val definitions = listOf(
            bench.copy(name = "  Legácy—Supino / Cafe\u0301  "),
            row.copy(name = "João’s \u0301Remada 👩🏽‍🚀 / Łódź ß ۱۲"),
            bench.copy(name = "CUSTOM — M\u0301áquina", custom = true),
            bench.copy(id = "catalog_no_longer_available", name = "  Desconhecido\tＡＢＣ…  "),
            row.copy(id = "empty-name", name = "\u0301💪—\n\uD800"),
        )
        val names = listOf("  Upper—A / Cafe\u0301  ", "\u0301💪—\uD800", "", "Straße/İSTANBUL\t2026")
        val workouts = names.mapIndexed { n, name ->
            workout("fold-$n", name = name, entries = (definitions + definitions.map { it.copy() })
                .mapIndexed { e, exercise -> entry(exercise, set("fold-$n-$e")).copy(id = "fold-$n-entry-$e") })
        }
        val indexed = index(*workouts.toTypedArray()).historyData.items.associateBy { it.workout.id }
        for (workout in workouts) {
            // Frozen pre-cache algorithm: fold one raw concatenation, including repeated stored/EN/PT names.
            val original = StringBuilder(workout.name)
            workout.exercises.forEach { entry ->
                val exercise = entry.exercise
                original.append(' ').append(exercise.name)
                    .append(' ').append(ExerciseNames.display(exercise.id, exercise.name, exercise.custom, false))
                    .append(' ').append(ExerciseNames.display(exercise.id, exercise.name, exercise.custom, true))
            }
            assertEquals(SearchFolding.fold(original.toString()), indexed.getValue(workout.id).searchText)
        }
    }

    @Test fun exerciseMetadataUsesTheWholeSnapshotAndDoesNotOutliveItsIndex() {
        val original = ExerciseUi("shared-custom-id", "Original press", "Chest", "Barbell", "Total load", custom = true)
        val renamed = original.copy(name = "Renamed pull", muscle = "Back", equipment = "Machine", convention = "Assistance")
        val fullBody = original.copy(name = "Whole-body complex", muscle = "Full body", equipment = "Bodyweight")
        val before = index(workout("original", name = "Session", entries = listOf(entry(original, set()))),
            workout("renamed", name = "Session", entries = listOf(entry(renamed, set(kg = 50.0, reps = 10)))),
            workout("full", name = "Session", entries = listOf(entry(fullBody, set()))))
        val items = before.historyData.items.associateBy { it.workout.id }
        assertEquals(listOf("original"), query(before, search = "original press").workouts.map { it.id })
        assertEquals(listOf("renamed"), query(before, search = "renamed pull").workouts.map { it.id })
        assertEquals(mapOf(MuscleRegion.CHEST to 1.0), items.getValue("original").muscleSets)
        assertEquals(mapOf(MuscleRegion.LATS to 1.0), items.getValue("renamed").muscleSets)
        assertEquals(160.0, items.getValue("original").volumeKg)
        assertEquals(0.0, items.getValue("renamed").volumeKg)
        assertTrue(items.getValue("full").muscleSets.isEmpty())
        assertEquals(1.0, items.getValue("full").fullBodySets)

        val updated = original.copy(name = "Updated raise", muscle = "Shoulders", equipment = "Dumbbell")
        val after = index(workout("original", name = "Session", entries = listOf(entry(updated, set(kg = 22.123456789)))))
        assertEquals(listOf("original"), query(after, search = "updated raise").workouts.map { it.id })
        assertTrue(query(after, search = "original press").workouts.isEmpty())
        assertEquals(mapOf(MuscleRegion.SHOULDERS to 1.0), after.historyData.items.single().muscleSets)
        assertEquals(22.123456789 * 8, after.historyData.items.single().volumeKg)
        assertEquals(listOf("original"), query(before, search = "original press").workouts.map { it.id })
        assertEquals(mapOf(MuscleRegion.CHEST to 1.0), items.getValue("original").muscleSets)
    }

    @Test fun allSearchWordsMustBelongToOneWorkoutButMaySpanItsNameAndExercises() {
        val index = index(workout("a", name = "Monday upper"),
            workout("b", name = "Thursday", entries = listOf(entry(row, set()))))
        assertEquals(listOf("a"), query(index, search = "monday bench").workouts.map { it.id })
        assertTrue(query(index, search = "monday remada").workouts.isEmpty())
    }

    @Test fun selectedRoutinesAreOrAndOtherWorkoutsIncludesDeletedRoutines() {
        val index = index(workout("a", routine = "a"), workout("b", routine = "b"),
            workout("deleted", routine = "gone"), workout("free"))
        assertEquals(setOf("a", "b"), query(index, routines = setOf("a", "b"), known = setOf("a", "b"))
            .workouts.map { it.id }.toSet())
        assertEquals(setOf("deleted", "free"), query(index, others = true, known = setOf("a", "b"))
            .workouts.map { it.id }.toSet())
        assertEquals(setOf("a", "deleted", "free"), query(index, routines = setOf("a"), others = true,
            known = setOf("a", "b")).workouts.map { it.id }.toSet())
    }

    @Test fun categoriesCombineWithAndAndCalendarDayCanBeCleared() {
        val index = index(workout("a", routine = "a"), workout("b", "2026-11-03", "a"),
            workout("c", routine = "a", entries = listOf(entry(row, set()))))
        assertEquals(listOf("a"), query(index, routines = setOf("a"), exercise = bench.id, day = today)
            .workouts.map { it.id })
        assertEquals(setOf("a", "b"), query(index, routines = setOf("a"), exercise = bench.id)
            .workouts.map { it.id }.toSet())
    }

    @Test fun beatenFilterExcludesFirstRecordsWithoutChangingInclusivePrTotals() {
        val first = workout("first").let { it.copy(records = listOf(RecordUi(bench, 20.0, 8))) }
        val beaten = workout("beaten").let { it.copy(records = listOf(RecordUi(bench, 22.0, 6, previousKg = 20.0))) }
        val index = index(first, beaten)
        assertEquals(listOf("beaten"), query(index, beaten = true).workouts.map { it.id })
        assertEquals(1, index.historyData.items.first { it.workout.id == "first" }.prCount)
        assertEquals(0, index.historyData.items.first { it.workout.id == "first" }.beatenPrCount)
    }

    @Test fun monthGroupingAndTieOrderAreDeterministic() {
        val result = query(index(workout("a"), workout("z"), workout("old", "2026-10-03")))
        assertEquals(listOf("z", "a", "old"), result.workouts.map { it.id })
        assertEquals(listOf(LocalDate(2026, 11, 1), LocalDate(2026, 10, 1)), result.months.map { it.month })
        assertEquals(LocalDate(2026, 10, 3), result.earliestDay)
    }

    @Test fun calendarRetainsAllSessionsOnOneDayAndDoesNotUseOnlyFilteredRows() {
        val index = index(workout("a"), workout("b"), workout("oct", "2026-10-31"))
        val calendar = HistoryCalendarQuery(2026, 11, zone).compute(index)
        assertEquals(2, calendar.totalWorkouts)
        assertEquals(listOf("b", "a"), calendar.dayWorkouts[4])
        assertEquals(listOf("Workout b", "Workout a"), calendar.dayWorkoutNames[4])
        assertEquals(LocalDate(2026, 10, 31), calendar.earliestDay)
        assertTrue(query(index, search = "none").workouts.isEmpty())
        assertEquals(2, HistoryCalendarQuery(2026, 11, zone).compute(index).totalWorkouts)
    }

    @Test fun localStartDayControlsCalendarEvenWhenWorkoutEndsNextDay() {
        val local = TimeZone.of("America/Sao_Paulo")
        val start = LocalDate(2026, 11, 4).atStartOfDayIn(zone).toEpochMilliseconds()
        val workout = workout("night").copy(startedAt = start, endedAt = start + 10_800_000)
        val index = index(workout)
        assertEquals(listOf("night"), HistoryCalendarQuery(2026, 11, local).compute(index).dayWorkouts[3])
        assertEquals(listOf("night"), HistoryQuery(day = LocalDate(2026, 11, 3), zone = local)
            .compute(index).workouts.map { it.id })
    }

    @Test fun filterOptionsComeFromWholeHistoryEvenWhenResultsAreEmpty() {
        val result = query(index(workout("a", routine = "gone"), workout("b", entries = listOf(entry(row, set())))), search = "none")
        assertEquals(setOf(bench.id, row.id), result.allExerciseIds)
        assertEquals(setOf("gone"), result.sourceRoutineIds)
    }

    @Test fun countsAndVolumeUseValidCompletedSetsWarmupsIncludedButAssistedVolumeExcluded() {
        val entries = listOf(entry(bench, set("valid"), set("warm", kg = 10.0, warmup = true),
            set("assisted", kg = 50.0, assisted = true), set("incomplete", completed = false),
            set("zero", reps = 0), set("negative", kg = -1.0), set("nan", kg = Double.NaN)))
        val progress = overall(index(workout("a", entries = entries)))
        assertEquals(3, progress.sets)
        assertEquals(240.0, progress.volumeKg)
        assertEquals(1, progress.workouts)
    }

    @Test fun assistanceConventionNeverAddsLoggedVolumeButCountsSets() {
        val assisted = bench.copy(convention = "ASSISTANCE")
        val progress = overall(index(workout("a", entries = listOf(entry(assisted, set(kg = 40.0))))))
        assertEquals(1, progress.sets)
        assertEquals(0.0, progress.volumeKg)
    }

    @Test fun fourWeeksAlignToRegionAndIncludeEmptyWeeksAndPartialCurrentWeek() {
        val index = index(workout("today"), workout("first", "2026-10-11"), workout("old", "2026-10-10"))
        val progress = overall(index, HistoryPeriod.FOUR_WEEKS)
        assertEquals(LocalDate(2026, 10, 11), progress.rangeStart)
        assertEquals(today, progress.rangeEnd)
        assertEquals(4, progress.weeks)
        assertEquals(4, progress.buckets.size)
        assertEquals(listOf(1, 0, 0, 1), progress.buckets.map { it.workouts })
        assertEquals(2, progress.weeksWithWorkouts)
        assertEquals(0.5, progress.workoutsPerWeek)
        assertTrue(progress.buckets.last().partial)
        assertFalse(progress.buckets.first().partial)
    }

    @Test fun mondayAndSaturdayWeekStartsProduceDifferentCorrectBoundaries() {
        val index = index(workout("a"))
        assertEquals(LocalDate(2026, 10, 12), overall(index, HistoryPeriod.FOUR_WEEKS, weekStart = DayOfWeek.MONDAY).rangeStart)
        assertEquals(LocalDate(2026, 10, 10), overall(index, HistoryPeriod.FOUR_WEEKS, weekStart = DayOfWeek.SATURDAY).rangeStart)
    }

    @Test fun threeAndSixMonthChipsAreExactlyThirteenAndTwentySixWeeks() {
        val index = index(workout("a"))
        assertEquals(13, overall(index, HistoryPeriod.THREE_MONTHS).buckets.size)
        assertEquals(26, overall(index, HistoryPeriod.SIX_MONTHS).buckets.size)
    }

    @Test fun yearUsesTwelveCalendarMonthsAndCurrentMonthIsPartial() {
        val progress = overall(index(workout("a")), HistoryPeriod.ONE_YEAR)
        assertEquals(LocalDate(2025, 12, 1), progress.rangeStart)
        assertEquals(12, progress.buckets.size)
        assertTrue(progress.buckets.all { it.kind == HistoryBucketKind.MONTH })
        assertTrue(progress.buckets.last().partial)
    }

    @Test fun allPeriodIncludesFutureImportedWorkoutsAndUsesQuarterlyBucketsPastThirtySixMonths() {
        val progress = overall(index(workout("old", "2022-06-15"), workout("future", "2027-02-01")), HistoryPeriod.ALL)
        assertEquals(LocalDate(2022, 6, 1), progress.rangeStart)
        assertEquals(LocalDate(2027, 2, 1), progress.rangeEnd)
        assertEquals(2, progress.workouts)
        assertTrue(progress.buckets.all { it.kind == HistoryBucketKind.QUARTER })
        assertEquals(LocalDate(2022, 4, 1), progress.buckets.first().start)
    }

    @Test fun exactlyThirtySixMonthsStillUsesMonthlyBuckets() {
        val progress = overall(index(workout("first", "2023-12-15")), HistoryPeriod.ALL)
        assertEquals(36, progress.buckets.size)
        assertTrue(progress.buckets.all { it.kind == HistoryBucketKind.MONTH })
    }

    @Test fun bucketVolumeIsSumOfSessionsNotTheExerciseChartsBestSession() {
        val progress = overall(index(workout("a"), workout("b", "2026-11-03")))
        assertEquals(320.0, progress.buckets.last().volumeKg)
        assertEquals(2, progress.buckets.last().workouts)
    }

    @Test fun muscleWeightsExcludeWarmupsButIncludeAssistedWorkingSets() {
        // A known catalog row supplies secondary biceps; custom rows only use their primary group.
        val barbellRow = ExerciseUi("catalog_dumbbell_row_bent_over", "Row", "Back", "Dumbbell")
        val index = index(workout("a", entries = listOf(entry(barbellRow, set(), set("warm", warmup = true),
            set("assisted", assisted = true)))))
        val progress = overall(index)
        val anatomy = com.mooscles.data.ExerciseAnatomy.forExercise(barbellRow.id, barbellRow.muscle)!!
        anatomy.primary.forEach { region -> assertEquals(2.0, progress.muscles.first { it.region == region }.sets) }
        anatomy.secondary.forEach { region -> assertEquals(1.0, progress.muscles.first { it.region == region }.sets) }
        assertEquals(2.0 / 13, progress.muscles.first().perWeek)
    }

    @Test fun customMuscleAndFullBodyHaveSeparatePreservedCounts() {
        val full = ExerciseUi("custom-full", "Complex", "Full body", "Bodyweight", custom = true)
        val progress = overall(index(workout("a", entries = listOf(entry(row, set()), entry(full, set("f"))))))
        assertEquals(listOf(MuscleRegion.LATS), progress.muscles.map { it.region })
        assertEquals(1.0, progress.fullBodySets)
    }

    @Test fun mostPerformedCountsSessionsNotEntriesOrSetsAndBreaksTiesByRecentSession() {
        val progress = overall(index(workout("a", entries = listOf(entry(bench, set(), set("two")),
            entry(bench, set("repeated")))), workout("b", "2026-11-03", entries = listOf(entry(row, set())))))
        assertEquals(listOf(bench.id, row.id), progress.mostPerformed.map { it.exercise.id })
        assertEquals(listOf(1, 1), progress.mostPerformed.map { it.sessions })
    }

    @Test fun topExercisesAreLimitedToFiveAndRequireAValidCompletedSet() {
        val entries = (0..6).map { entry(row.copy(id = "e$it"), set("s$it")) } +
            entry(row.copy(id = "unfinished"), set("u", completed = false))
        val progress = overall(index(workout("a", entries = entries)))
        assertEquals(5, progress.mostPerformed.size)
        assertFalse(progress.mostPerformed.any { it.exercise.id == "unfinished" })
    }

    @Test fun startDayHandlesEuropeanDstWithoutFixedDayMillis() {
        val berlin = TimeZone.of("Europe/Berlin")
        val a = LocalDate(2026, 3, 29).atStartOfDayIn(berlin).toEpochMilliseconds()
        val b = LocalDate(2026, 3, 30).atStartOfDayIn(berlin).toEpochMilliseconds()
        assertNotEquals(86_400_000L, b - a)
        val index = index(workout("a").copy(startedAt = a), workout("b").copy(startedAt = b))
        val result = OverallProgressQuery(HistoryPeriod.FOUR_WEEKS, LocalDate(2026, 3, 30), berlin, DayOfWeek.MONDAY).compute(index)
        assertEquals(listOf(0, 0, 1, 1), result.buckets.map { it.workouts })
    }

    @Test fun missingMidnightAtBrazilDstStillBelongsToItsLocalCalendarDay() {
        val brazil = TimeZone.of("America/Sao_Paulo")
        val start = LocalDate(2018, 11, 4).atStartOfDayIn(brazil).toEpochMilliseconds()
        val index = index(workout("dst").copy(startedAt = start))
        val month = HistoryCalendarQuery(2018, 11, brazil).compute(index)
        assertEquals(listOf("dst"), month.dayWorkouts[4])
        val progress = OverallProgressQuery(HistoryPeriod.FOUR_WEEKS, LocalDate(2018, 11, 4), brazil,
            DayOfWeek.SUNDAY).compute(index)
        assertEquals(1, progress.buckets.last().workouts)
    }

    @Test fun queriesKeepTheOriginalSnapshotAndExactKgWithoutRoundingOrUnitConversion() {
        val exact = 22.123456789
        val original = workout("exact", entries = listOf(entry(bench, set(reps = 6, kg = exact))))
        val index = index(original)
        val result = query(index)
        assertTrue(result.workouts.single() === original)
        assertEquals(exact, result.workouts.single().exercises.single().sets.single().weightKg)
        assertEquals(exact * 6, overall(index).volumeKg)
    }

    @Test fun changedHistoryGetsNewIndexedValuesWithoutMutatingPreviousIndex() {
        val before = index(workout("a"))
        val after = index(workout("a", entries = listOf(entry(bench, set(kg = 22.0, reps = 6)))))
        assertEquals(160.0, overall(before).volumeKg)
        assertEquals(132.0, overall(after).volumeKg)
        assertEquals(160.0, overall(before).volumeKg)
    }
}
