package com.mooscles.screens.share

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.Exercise
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.domain.WeightUnits
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The cases of the former `WorkoutShareCardTest` (:app), with the values the equivalence run
 * against `WorkoutShareCards.from` produced, written as literals. Loads use the design system's
 * no-break space before the unit.
 */
class ShareCardDataTest {
    private val utc = TimeZone.UTC
    private fun workout(id: String = "selected", start: Long = 1000, end: Long? = start + 123000,
                        status: WorkoutStatus = WorkoutStatus.FINISHED) =
        Workout(id, "Saved workout $id", start, end, status)

    private fun exercise(id: String = "entry", variation: String = "bench", workout: String = "selected",
                         position: Int = 0, convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        WorkoutExercise(id, workout, variation, "Saved $variation", MuscleGroup.CHEST,
            Equipment.BARBELL, convention, position)

    private fun set(entry: WorkoutExercise, kg: Double, reps: Int, position: Int = 0,
                    completed: Boolean = true, warmup: Boolean = false, assistance: Boolean = false) =
        WorkoutSet("${entry.id}-$position-$kg-$reps", entry.id, position, reps, kg, completed, warmup, assistance)

    private fun data(entry: WorkoutExercise, vararg sets: WorkoutSet) =
        AppData(workouts = listOf(workout()), workoutExercises = listOf(entry), sets = sets.toList())

    private fun card(data: AppData, workoutId: String = "selected", pounds: Boolean = false,
                     locale: NumberLocale = NumberLocale.EN, zone: TimeZone = utc,
                     texts: ShareCardTexts = EnglishTexts) =
        ShareCards.from(data, workoutId, pounds, locale, zone, texts)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime(y, mo, d, h, mi).toInstant(utc).toEpochMilliseconds()

    @Test fun bestLoadedSetKeepsTheActualHighestWeightAndRepsTogether() {
        val entry = exercise()
        val result = card(data(entry, set(entry, 20.0, 20), set(entry, 22.0, 6), set(entry, 22.0, 8)))
        assertEquals("22\u00A0kg × 8", result.rows.single().bestSet)
        assertEquals(3, result.rows.single().completedSets)
        assertEquals("708\u00A0kg", result.volumeText)
        assertEquals("2:03", result.durationText)
        assertEquals("3", result.setsText)
    }

    @Test fun warmupAssistanceUnfinishedAndInvalidSetsCannotBecomeBestWorkingSet() {
        val entry = exercise()
        val result = card(data(entry, set(entry, 22.0, 6), set(entry, 100.0, 3, warmup = true),
            set(entry, 110.0, 3, assistance = true), set(entry, 120.0, 3, completed = false),
            set(entry, -1.0, 3), set(entry, Double.NaN, 3), set(entry, 150.0, 0)))
        assertEquals("22\u00A0kg × 6", result.rows.single().bestSet)
        assertEquals(3, result.rows.single().completedSets)
        assertEquals("432\u00A0kg", result.volumeText)
    }

    @Test fun bodyweightChoosesHighestCompletedEligibleRepsAndPreservesZeroLoad() {
        val entry = exercise(convention = LoadConvention.BODYWEIGHT)
        val result = card(data(entry, set(entry, 0.0, 8), set(entry, 0.0, 12),
            set(entry, 0.0, 30, warmup = true), set(entry, 0.0, 40, completed = false)))
        assertEquals("Bodyweight × 12", result.rows.single().bestSet)
        assertEquals("0\u00A0kg", result.volumeText)
        assertTrue(result.rows.single().hasRecord)
        assertEquals(listOf(ShareRecordRow("Saved bench", "Bodyweight × 12")), result.records)
    }

    @Test fun loadedBodyweightWorkingSetsDisplayActualAddedWeightAndNotBodyMass() {
        val entry = exercise(convention = LoadConvention.BODYWEIGHT)
        val result = card(data(entry, set(entry, 0.0, 12), set(entry, 5.0, 6)))
        assertEquals("5\u00A0kg × 6", result.rows.single().bestSet)
        assertEquals("30\u00A0kg", result.volumeText)
    }

    @Test fun poundsConvertWeightAndVolumeOnceWithoutMultiplyingPerHandLoads() {
        val entry = exercise(convention = LoadConvention.PER_HAND)
        val result = card(data(entry, set(entry, WeightUnits.lbToKg(22.0), 6)), pounds = true)
        assertEquals("22\u00A0lb × 6", result.rows.single().bestSet)
        assertEquals("132\u00A0lb", result.volumeText)
        assertEquals(listOf(ShareRecordRow("Saved bench", "22\u00A0lb × 6")), result.records)
    }

    @Test fun binaryUnitRoundTripNoiseDoesNotDefeatTheHigherRepetitionTieBreak() {
        val entry = exercise()
        val result = card(data(entry, set(entry, 22.0.nextUp(), 6), set(entry, 22.0, 9)))
        assertEquals("22\u00A0kg × 9", result.rows.single().bestSet)
    }

    @Test fun everyCompletedExerciseIsRetainedInSavedOrderWithLongNamesIntact() {
        val first = exercise(id = "first", variation = "first", position = 0)
            .copy(name = "An exceptionally long exercise name with equipment and variation details that must remain complete")
        val second = exercise(id = "second", variation = "second", position = 1)
        val third = exercise(id = "third", variation = "third", position = 2)
        val noSets = exercise(id = "unfinished", variation = "unfinished", position = 3)
        val result = card(AppData(workouts = listOf(workout()),
            workoutExercises = listOf(third, noSets, second, first),
            sets = listOf(set(third, 30.0, 2), set(first, 20.0, 10), set(second, 10.0, 8),
                set(noSets, 100.0, 10, completed = false))))
        assertEquals(listOf(first.name, second.name, third.name), result.rows.map { it.name })
        assertEquals(3, result.rows.size)
    }

    @Test fun nonworkingOnlyRowsRemainTruthfulInsteadOfInventingABestSet() {
        val warmup = exercise(id = "warmup", position = 0)
        val assistance = exercise(id = "assistance", position = 1, convention = LoadConvention.ASSISTANCE)
        val mixed = exercise(id = "mixed", position = 2)
        val source = AppData(workouts = listOf(workout()), workoutExercises = listOf(warmup, assistance, mixed),
            sets = listOf(set(warmup, 40.0, 5, warmup = true), set(assistance, 50.0, 8),
                set(mixed, 40.0, 5, warmup = true), set(mixed, 50.0, 8, assistance = true)))
        val result = card(source)
        assertEquals(listOf("Warm-up only", "Assistance only", "No working set"), result.rows.map { it.bestSet })
        assertEquals(listOf(1, 1, 2), result.rows.map { it.completedSets })
        val portuguese = card(source, locale = NumberLocale.PT_BR, texts = PortugueseTexts)
        assertEquals(listOf("Só aquecimento", "Só assistência", "Sem série de trabalho"), portuguese.rows.map { it.bestSet })
    }

    @Test fun firstAndBeatenWeightAndBodyweightRepetitionRecordsCountEachVariationOnce() {
        val bench = exercise()
        val duplicate = exercise(id = "duplicate", position = 2)
        val pullup = exercise(id = "pullup", variation = "pullup", position = 1, convention = LoadConvention.BODYWEIGHT)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(bench, pullup, duplicate),
            sets = listOf(set(bench, 22.0, 6), set(duplicate, 22.0, 8), set(pullup, 0.0, 12))))
        assertEquals(2, result.records.size)
        assertEquals(listOf(false, true, true), result.rows.map { it.hasRecord })
        assertEquals(listOf(ShareRecordRow("Saved bench", "22\u00A0kg × 8"), ShareRecordRow("Saved pullup", "Bodyweight × 12")),
            result.records)
    }

    @Test fun duplicateVariationMarksOnlyTheRowContainingTheRecordSettingSet() {
        val losing = exercise(id = "losing", position = 0)
        val winning = exercise(id = "winning", position = 1)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(losing, winning),
            sets = listOf(set(losing, 22.0, 6), set(winning, 22.0, 8))))
        assertEquals(listOf(false, true), result.rows.map { it.hasRecord })
        assertEquals(1, result.records.size)
    }

    @Test fun warmupDuplicateCannotInheritAWorkingSetRecordBadge() {
        val warmup = exercise(id = "warmup", position = 0)
        val winning = exercise(id = "winning", position = 1)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(warmup, winning),
            sets = listOf(set(warmup, 22.0, 8, warmup = true), set(winning, 22.0, 8))))
        assertEquals(listOf(false, true), result.rows.map { it.hasRecord })
        assertEquals(1, result.records.size)
    }

    @Test fun bodyweightDuplicateRepetitionBadgeBelongsToTheMatchingCompletedSet() {
        val losing = exercise(id = "losing", position = 0, convention = LoadConvention.BODYWEIGHT)
        val winning = exercise(id = "winning", position = 1, convention = LoadConvention.BODYWEIGHT)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(losing, winning),
            sets = listOf(set(losing, 0.0, 8), set(winning, 0.0, 12))))
        assertEquals(listOf(false, true), result.rows.map { it.hasRecord })
        assertEquals(1, result.records.size)
    }

    @Test fun nonRecordExerciseIsUnmarkedWhileIndependentImprovedVariationsAreMarked() {
        val oldBench = exercise(id = "oldbench", workout = "old")
        val oldPullup = exercise(id = "oldpullup", variation = "pullup", workout = "old", convention = LoadConvention.BODYWEIGHT)
        val bench = exercise()
        val pullup = exercise(id = "pullup", variation = "pullup", position = 1, convention = LoadConvention.BODYWEIGHT)
        val result = card(AppData(workouts = listOf(workout("old", 0), workout()),
            workoutExercises = listOf(oldBench, oldPullup, bench, pullup),
            sets = listOf(set(oldBench, 22.0, 8), set(oldPullup, 0.0, 8),
                set(bench, 22.0, 6), set(pullup, 0.0, 12))))
        assertEquals(listOf(ShareRecordRow("Saved pullup", "Bodyweight × 12")), result.records)
        assertFalse(result.rows[0].hasRecord)
        assertTrue(result.rows[1].hasRecord)
    }

    /**
     * A repetitions record on a lighter set (50 kg × 12) while the best set (60 kg × 8) is no
     * record: the row is not marked, so its brass "60 kg × 8" never reads as a record; the record
     * is in the card's records.
     */
    @Test fun aRecordOnAnotherSetDoesNotMarkTheBestSet() {
        val old = exercise(id = "old", workout = "old")
        val bench = exercise()
        val result = card(AppData(workouts = listOf(workout("old", 0), workout()),
            workoutExercises = listOf(old, bench),
            sets = listOf(set(old, 60.0, 8), set(old, 50.0, 8, position = 1),
                set(bench, 60.0, 8), set(bench, 50.0, 12, position = 1))))
        assertEquals("60 kg × 8", result.rows.single().bestSet)
        assertFalse(result.rows.single().hasRecord)
        assertEquals(listOf(ShareRecordRow("Saved bench", "50 kg × 12")), result.records)
    }

    @Test fun workoutNumberIsTheSelectedChronologicalOrdinalAndExcludesActiveWorkouts() {
        val target = workout("selected", 2000)
        val result = card(AppData(workouts = listOf(workout("last", 3000), target,
            workout("active", 500, null, WorkoutStatus.ACTIVE), workout("first", 1000))))
        assertEquals(2, result.workoutNumber)
        assertEquals(target.name, result.workoutName)
    }

    @Test fun equalTimestampsUseDeterministicFinishedChronologyNotInputOrder() {
        val result = card(AppData(workouts = listOf(workout("z", 1000, 3000),
            workout("selected", 1000, 2000), workout("a", 1000, 2000))))
        assertEquals(2, result.workoutNumber)
    }

    @Test fun cardUsesTheSelectedSavedSnapshotAndIgnoresCatalogChangesAndActiveSession() {
        val saved = exercise().copy(name = "Original saved bench")
        val active = exercise(id = "activeentry", workout = "active")
        val result = card(AppData(workouts = listOf(workout(), workout("active", 5000, null, WorkoutStatus.ACTIVE)),
            workoutExercises = listOf(saved, active), sets = listOf(set(saved, 22.0, 6), set(active, 999.0, 99))))
        assertEquals("Original saved bench", result.rows.single().name)
        assertEquals("22\u00A0kg × 6", result.rows.single().bestSet)
        assertEquals("132\u00A0kg", result.volumeText)
        assertEquals("2:03", result.durationText)
        assertEquals("1", result.setsText)
    }

    @Test fun localeAndZoneApplyToDatesAndNumbers() {
        val entry = exercise()
        val source = AppData(
            workouts = listOf(Workout("selected", "Upper body A", at(2024, 10, 29, 18, 2), at(2024, 10, 29, 18, 44), WorkoutStatus.FINISHED)),
            workoutExercises = listOf(entry), sets = listOf(set(entry, 22.5, 6)))
        val en = card(source)
        val pt = card(source, locale = NumberLocale.PT_BR, texts = PortugueseTexts)
        val shifted = card(source, zone = TimeZone.of("America/Sao_Paulo"))
        assertEquals("22.5\u00A0kg × 6", en.rows.single().bestSet)
        assertEquals("22,5\u00A0kg × 6", pt.rows.single().bestSet)
        assertEquals("Tue, Oct 29, 2024 · 18:02–18:44", en.dateText)
        assertEquals("ter., 29 de out. de 2024 · 18:02–18:44", pt.dateText)
        assertEquals("Tue, Oct 29, 2024 · 15:02–15:44", shifted.dateText)
        assertNotEquals(en.dateText, shifted.dateText)
        assertEquals(EnglishTexts.labels, en.labels)
        assertEquals(PortugueseTexts.labels, pt.labels)
    }

    @Test fun volumeAndSetsAreGroupedInTheLanguageOfTheDevice() {
        val entry = exercise()
        val source = data(entry, *Array(25) { set(entry, 102.5, 10, position = it) })
        assertEquals("25,625\u00A0kg", card(source).volumeText)
        assertEquals("25.625\u00A0kg", card(source, locale = NumberLocale.PT_BR, texts = PortugueseTexts).volumeText)
        assertEquals("25", card(source).setsText)
    }

    @Test fun durationSupportsHoursAndAnAbsentEndTimeCannotGrow() {
        val long = card(AppData(workouts = listOf(workout(end = 1000 + 3_661_000))))
        assertEquals("1:01:01", long.durationText)
        val incompleteOldSnapshot = card(AppData(workouts = listOf(workout(end = null))))
        assertEquals("0:00", incompleteOldSnapshot.durationText)
        assertEquals("Thu, Jan 1, 1970 · 00:00–00:00", incompleteOldSnapshot.dateText)
    }

    @Test fun missingAndActiveWorkoutsFailWithAUsefulSharingMessage() {
        val missing = assertFailsWith<IllegalArgumentException> { card(AppData()) }
        assertTrue(missing.message!!.contains("no longer available"))
        val active = assertFailsWith<IllegalArgumentException> {
            card(AppData(workouts = listOf(workout(status = WorkoutStatus.ACTIVE))))
        }
        assertEquals("Finish this workout before sharing it.", active.message)
    }

    @Test fun assistedRepetitionRecordNamesTheAssistance() {
        val entry = exercise(convention = LoadConvention.ASSISTANCE)
        val result = card(data(entry, set(entry, 20.0, 8)))
        assertEquals("Assistance only", result.rows.single().bestSet)
        assertEquals(listOf(ShareRecordRow("Saved bench", "20\u00A0kg assistance × 8")), result.records)
    }

    /** The card is a lasting picture: its date says the year, in both languages, at the turn of a year too. */
    @Test fun dateKeepsTheYear() {
        val source = AppData(workouts = listOf(Workout("selected", "New year", at(2025, 12, 31, 23, 30),
            at(2026, 1, 1, 0, 40), WorkoutStatus.FINISHED)))
        assertEquals("Wed, Dec 31, 2025 · 23:30–00:40", card(source).dateText)
        assertEquals("qua., 31 de dez. de 2025 · 23:30–00:40",
            card(source, locale = NumberLocale.PT_BR, texts = PortugueseTexts).dateText)
        assertEquals("Thu, Jan 1, 2026 · 00:30–01:40", card(source, zone = TimeZone.of("Europe/Berlin")).dateText)
    }

    /** Plan decision 11: with the catalog in Portuguese, rows and records show the catalog's pt-BR name. */
    @Test fun portugueseNamesWhenAsked() {
        val bench = WorkoutExercise("entry", "selected", "catalog_barbell_bench_flat", "Barbell bench press",
            MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD, 0)
        val typed = exercise(id = "typed", variation = "custom_row", position = 1)
        val source = AppData(
            workouts = listOf(workout()),
            exercises = listOf(Exercise("custom_row", "Saved custom_row", MuscleGroup.BACK, Equipment.CABLE, custom = true)),
            workoutExercises = listOf(bench, typed),
            sets = listOf(set(bench, 80.0, 5), set(typed, 40.0, 10)),
        )
        val english = card(source)
        assertEquals(listOf("Barbell bench press", "Saved custom_row"), english.rows.map { it.name })
        assertEquals(listOf("Barbell bench press", "Saved custom_row"), english.records.map { it.name })
        val portuguese = card(source, locale = NumberLocale.PT_BR, texts = PortugueseTexts.copy(catalogPortuguese = true))
        assertEquals(listOf("Supino reto com barra", "Saved custom_row"), portuguese.rows.map { it.name })
        assertEquals(listOf("Supino reto com barra", "Saved custom_row"), portuguese.records.map { it.name })
        // The default stays English: a caller that does not ask gets the stored catalog name.
        assertEquals("Barbell bench press", card(source, texts = PortugueseTexts).rows.first().name)
    }

    @Test fun templatesReplaceOnlyNumberedPlaceholders() {
        assertEquals("b a 3", fillTemplate("%2\$s %1\$s %3\$d", "a", "b", 3))
        assertEquals("100% a", fillTemplate("100% %1\$s", "a"))
        val missing = assertFailsWith<IllegalArgumentException> { fillTemplate("%1\$s %2\$s", "a") }
        assertTrue(missing.message!!.contains("%2\$s"))
    }
}

/** The English texts of `strings_share.xml` and `strings_dates.xml`, as `rememberShareCardTexts` resolves them. */
internal val EnglishTexts = ShareCardTexts(
    months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    dateFormat = "%1\$s, %3\$s %2\$d, %4\$d · %5\$s–%6\$s",
    bodyweightSet = "Bodyweight × %1\$d",
    assistedSet = "%1\$s assistance × %2\$d",
    warmupOnly = "Warm-up only",
    assistanceOnly = "Assistance only",
    noWorkingSet = "No working set",
    labels = ShareCardLabels(
        duration = "Duration", sets = "Sets", volume = "Volume", records = "Records",
        exercises = "Exercises", bestSet = "Best set", noCompletedSets = "No completed sets", recordBadge = "PR",
        setCountOne = "%1\$d set", setCountOther = "%1\$d sets",
    ),
)

/** The pt-BR texts. */
internal val PortugueseTexts = ShareCardTexts(
    months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"),
    weekdays = listOf("seg.", "ter.", "qua.", "qui.", "sex.", "sáb.", "dom."),
    dateFormat = "%1\$s, %2\$d de %3\$s. de %4\$d · %5\$s–%6\$s",
    bodyweightSet = "Peso corporal × %1\$d",
    assistedSet = "%1\$s de assistência × %2\$d",
    warmupOnly = "Só aquecimento",
    assistanceOnly = "Só assistência",
    noWorkingSet = "Sem série de trabalho",
    labels = ShareCardLabels(
        duration = "Duração", sets = "Séries", volume = "Volume", records = "Recordes",
        exercises = "Exercícios", bestSet = "Melhor série", noCompletedSets = "Nenhuma série concluída", recordBadge = "PR",
        setCountOne = "%1\$d série", setCountOther = "%1\$d séries",
    ),
)
