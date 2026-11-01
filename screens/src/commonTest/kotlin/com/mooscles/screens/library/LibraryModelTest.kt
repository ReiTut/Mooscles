package com.mooscles.screens.library

import androidx.compose.runtime.saveable.SaverScope
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseSearch
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LibraryModelTest {
    private fun exercise(id: String, name: String = id, muscle: String = "Chest", equipment: String = "Barbell", custom: Boolean = false) =
        ExerciseUi(id, name, muscle, equipment, custom = custom)

    private fun catalog(stableId: String, storedName: String? = null): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, storedName ?: entry.name, entry.muscle.label, entry.equipment.label)
    }

    private fun workout(id: String, startedAt: Long, vararg exercises: ExerciseUi) =
        WorkoutUi(id, "Workout $id", startedAt, endedAt = startedAt + 1,
            exercises = exercises.mapIndexed { i, e -> WorkoutExerciseUi("$id-$i", e) })

    private val benchPress = exercise("bench", "Bench press", muscle = "Chest", equipment = "Barbell")
    private val flyMachine = exercise("fly", "Chest fly", muscle = "Chest", equipment = "Selectorized machine")
    private val legCurl = exercise("curl", "Leg curl", muscle = "Hamstrings", equipment = "Selectorized machine")
    private val rdl = exercise("rdl", "Romanian deadlift", muscle = "Hamstrings", equipment = "Dumbbell")
    private val row = exercise("row", "Cable row", muscle = "Back", equipment = "Cable")
    private val myPress = exercise("custom-press", "My chest press", muscle = "Chest", equipment = "Cable", custom = true)
    private val myCurl = exercise("custom-curl", "My hamstring curl", muscle = "Hamstrings", equipment = "Other", custom = true)
    private val all = listOf(benchPress, flyMachine, legCurl, rdl, row, myPress, myCurl)

    private fun LibraryModel.ids(query: String = "", filters: LibraryFilters = LibraryFilters()) =
        results(query, filters).map { it.id }.toSet()

    @Test
    fun musclesCombineWithOrAndDimensionsWithAnd() {
        val model = LibraryModel.build(all, portuguese = false)
        assertEquals(setOf("bench", "fly", "custom-press", "curl", "rdl", "custom-curl"),
            model.ids(filters = LibraryFilters(muscles = setOf("Chest", "Hamstrings"))))
        // A muscle and an equipment: both must hold.
        assertEquals(setOf("fly", "curl"),
            model.ids(filters = LibraryFilters(muscles = setOf("Chest", "Hamstrings"), equipment = setOf("Selectorized machine"))))
        assertEquals(setOf("custom-press"),
            model.ids(filters = LibraryFilters(muscles = setOf("Chest"), equipment = setOf("Cable"), mineOnly = true)))
        assertEquals(emptySet(), model.ids(filters = LibraryFilters(muscles = setOf("Back"), equipment = setOf("Barbell"))))
    }

    @Test
    fun equipmentCombinesWithOr() {
        val model = LibraryModel.build(all, portuguese = false)
        assertEquals(setOf("rdl", "row", "custom-press"), model.ids(filters = LibraryFilters(equipment = setOf("Dumbbell", "Cable"))))
        assertEquals(setOf("bench"), model.ids(filters = LibraryFilters(equipment = setOf("Barbell"))))
        // No filter keeps everything, and says so.
        assertFalse(LibraryFilters().active)
        assertTrue(LibraryFilters(equipment = setOf("Cable")).active)
        assertTrue(LibraryFilters(muscles = setOf("Back")).active)
        assertTrue(LibraryFilters(mineOnly = true).active)
        assertEquals(all.map { it.id }.toSet(), model.ids())
    }

    @Test
    fun mineOnlyKeepsCustomExercises() {
        val model = LibraryModel.build(all, portuguese = false)
        // In alphabetical order: "My chest press" before "My hamstring curl".
        assertEquals(listOf("custom-press", "custom-curl"), model.results("", LibraryFilters(mineOnly = true)).map { it.id })
        assertEquals(listOf("custom-curl"), model.results("", LibraryFilters(muscles = setOf("Hamstrings"), mineOnly = true)).map { it.id })
    }

    @Test
    fun queryAndFiltersCombine() {
        val model = LibraryModel.build(all, portuguese = false)
        assertEquals(setOf("curl", "custom-curl"), model.ids("curl"))
        assertEquals(setOf("custom-curl"), model.ids("curl", LibraryFilters(mineOnly = true)))
        assertEquals(setOf("curl"), model.ids("curl", LibraryFilters(equipment = setOf("Selectorized machine"))))
        // The query also reads muscle and equipment, in both languages.
        assertEquals(setOf("fly", "curl"), model.ids("machine"))
        assertEquals(setOf("fly"), model.ids("machine", LibraryFilters(muscles = setOf("Chest"))))
        assertEquals(setOf("rdl", "curl", "custom-curl"), model.ids("posterior"))
        assertEquals(emptySet(), model.ids("press", LibraryFilters(muscles = setOf("Back"))))
    }

    @Test
    fun resultsAreAlphabeticalByShownName() {
        val catalogEntries = listOf(
            // Stored under an older name: the shown name decides the place, not the stored one.
            catalog("barbell_bench_flat", storedName = "Zz old bench name"),
            catalog("dumbbell_curl_standing"),
            catalog("cable_triceps_pushdown_rope"),
            catalog("bodyweight_push_up"),
            catalog("barbell_back_squat_high_bar"),
        )
        val english = LibraryModel.build(catalogEntries, portuguese = false)
        assertEquals(
            listOf("Barbell bench press", "High-bar back squat", "Push-up", "Rope triceps pushdown", "Standing dumbbell curl"),
            english.exercises.map(english::shownName),
        )
        val portuguese = LibraryModel.build(catalogEntries, portuguese = true)
        assertEquals(
            listOf("Agachamento livre (barra alta)", "Flexão de braço", "Rosca direta com halteres", "Supino reto com barra",
                "Tríceps na polia com corda"),
            portuguese.exercises.map(portuguese::shownName),
        )
        assertEquals(portuguese.exercises, portuguese.results("", LibraryFilters()))
        assertTrue(portuguese.portuguese)
        assertFalse(english.portuguese)

        // Ported from ExercisePickerTest.allIsAlphabetical: case and accents do not move a name.
        val names = listOf("Supino reto", "agachamento livre", "Élevation", "Barbell row", "Remada curvada", "Abdominal", "barbell curl")
        val customs = names.mapIndexed { i, name -> exercise("x$i", name, muscle = "Core", equipment = "Other", custom = true) }
        val model = LibraryModel.build(customs, portuguese = true)
        assertEquals(
            listOf("Abdominal", "agachamento livre", "barbell curl", "Barbell row", "Élevation", "Remada curvada", "Supino reto"),
            model.results("", LibraryFilters()).map(model::shownName),
        )
        assertEquals(listOf("barbell curl", "Barbell row"), model.results("barbell", LibraryFilters()).map(model::shownName))
        // Equal shown names fall back to the name, then the id.
        val twins = listOf(exercise("b", "Row"), exercise("a", "Row"), exercise("c", "row"))
        assertEquals(listOf("a", "b", "c"), LibraryModel.build(twins, portuguese = false).exercises.map { it.id })
    }

    @Test
    fun searchFoldsAccents() {
        // Ported from ExercisePickerTest.searchFoldsAccentsLikeToday.
        val raise = exercise("custom-raise", "Elevação lateral", muscle = "Shoulders", equipment = "Dumbbell", custom = true)
        val bench = exercise("catalog_barbell_bench_press", "Barbell bench press")
        val fly = exercise("custom-fly", "Crucifixo máquina", muscle = "Chest", equipment = "Selectorized machine", custom = true)
        val exercises = listOf(raise, bench, fly)
        val model = LibraryModel.build(exercises, portuguese = false)
        val none = LibraryFilters()
        for (query in listOf("elevacao", "ELEVAÇÃO", "  Elevacao   LATERAL ", "lateral elevação", "shoulders")) {
            assertEquals(listOf(raise), model.results(query, none), query)
        }
        assertEquals(listOf(fly), model.results("crucifixo maquina", none))
        // Muscle and equipment are searchable, as before.
        assertEquals(listOf(bench, fly), model.results("chest", none))
        assertEquals(emptyList(), model.results("squat", none))
        // The index finds everything the old search found, in alphabetical order of the shown name.
        for (query in listOf("", "press", "dumbbell", "elev lat", "barbell chest", "zzz")) {
            val legacy = exercises.filter { ExerciseSearch.matches(it.id, it.name, it.muscle, it.equipment, query) }
            val results = model.results(query, none)
            assertTrue(results.containsAll(legacy), query)
            assertEquals(results.sortedBy { it.name.lowercase() }, results, query)
        }
    }

    @Test
    fun portugueseNamesAreFoundOnAnEnglishScreen() {
        val entries = listOf(
            catalog("barbell_bench_flat"), catalog("barbell_bench_incline"), catalog("dumbbell_bench_incline"),
            catalog("dumbbell_curl_standing"), catalog("selectorized_leg_extension"),
        )
        val english = LibraryModel.build(entries, portuguese = false)
        val incline = english.results("supino inclinado", LibraryFilters())
        assertEquals(listOf("catalog_barbell_bench_incline", "catalog_dumbbell_bench_incline"), incline.map { it.id })
        // Found by the Portuguese name, shown by the English one.
        assertEquals(listOf("Incline barbell bench press", "Incline dumbbell bench press"), incline.map(english::shownName))
        assertEquals(listOf("catalog_selectorized_leg_extension"), english.results("cadeira extensora", LibraryFilters()).map { it.id })
        // And the other way round: English names on a Portuguese screen.
        val portuguese = LibraryModel.build(entries, portuguese = true)
        assertEquals(listOf("Rosca direta com halteres"),
            portuguese.results("standing dumbbell curl", LibraryFilters()).map(portuguese::shownName))
    }

    @Test
    fun recentsUseTheCurrentEntryAndSkipMissing() {
        val current = List(10) { exercise("e$it", "Exercise $it") }
        val model = LibraryModel.build(current, portuguese = false)
        // The history holds snapshots (older names) and an exercise that is gone.
        val snapshot = { index: Int -> exercise("e$index", "Old name $index") }
        val gone = exercise("gone", "Deleted exercise")
        val history = listOf(
            workout("w1", startedAt = 100, snapshot(7), snapshot(8)),
            workout("w2", startedAt = 200, snapshot(3), gone, snapshot(4), snapshot(5), snapshot(6)),
            workout("w3", startedAt = 300, snapshot(0), snapshot(1), snapshot(0), snapshot(2)),
        )
        val recents = model.recents(history)
        // Newest workout first, each once, the gone one skipped and replaced by the next (the ninth entry).
        assertEquals(listOf("e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7"), recents.map { it.id })
        recents.forEach { recent -> assertSame(current.single { it.id == recent.id }, recent) }
        assertEquals(emptyList(), model.recents(emptyList()))
        assertEquals(emptyList(), model.recents(listOf(workout("w", 1, gone))))
    }

    @Test
    fun filtersSaverRoundTrips() {
        val scope = SaverScope { true }
        for (filters in listOf(
            LibraryFilters(),
            LibraryFilters(muscles = setOf("Chest", "Full body"), equipment = setOf("Smith machine"), mineOnly = true),
            LibraryFilters(equipment = setOf("Cable", "Plate-loaded machine")),
        )) {
            val saved = with(LibraryFilters.Saver) { scope.save(filters) }
            assertNotNull(saved)
            assertEquals(filters, LibraryFilters.Saver.restore(saved))
        }
    }

    @Test
    fun cacheKeepsTheModelForTheSameListAndLanguage() {
        val cache = LibraryModelCache()
        val list = listOf(benchPress, row)
        assertNull(cache.get(list, portuguese = false))
        val model = LibraryModel.build(list, portuguese = false)
        cache.put(list, portuguese = false, model)
        assertSame(model, cache.get(list, portuguese = false))
        // Another language, or an equal list that is another instance, is not the same model.
        assertNull(cache.get(list, portuguese = true))
        assertNull(cache.get(list.toList(), portuguese = false))
        // It keeps only the last pair.
        val other = listOf(row)
        val otherModel = LibraryModel.build(other, portuguese = true)
        cache.put(other, portuguese = true, otherModel)
        assertNull(cache.get(list, portuguese = false))
        assertSame(otherModel, cache.get(other, portuguese = true))
        assertNotSame(model, otherModel)
    }
}
