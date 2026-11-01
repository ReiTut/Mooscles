package com.mooscles.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.mooscles.data.ExerciseSearchIndex
import com.mooscles.data.SearchEntry
import com.mooscles.data.SearchFolding
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.format.displayName
import com.mooscles.screens.workout.ExercisePicker
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * What the library lists (spec "Biblioteca"), for one exercise list and one screen language, built
 * once ([build], off the main thread) and then read per keystroke: the bilingual search of the new
 * screens ([ExerciseSearchIndex], plan decision 3), the filters, the alphabetical order by the
 * shown name and "Recently used" (plan decision 19, from the in-memory state).
 */
@Immutable
class LibraryModel private constructor(
    /** Every exercise, in alphabetical order of the shown name (folded, then the name, then the id). */
    val exercises: List<ExerciseUi>,
    /** Whether the shown names are the catalog's Portuguese ones ([catalogInPortuguese]). */
    val portuguese: Boolean,
    private val byId: Map<String, ExerciseUi>,
    private val index: ExerciseSearchIndex,
) {
    /** The name [exercise] shows on this screen, as `exerciseName` gives it. */
    fun shownName(exercise: ExerciseUi): String = displayName(exercise, portuguese)

    /**
     * The exercises that match [query] (any language, accents, case and word order folded; muscle,
     * equipment and aliases count) and pass [filters], in the order of [exercises]. A blank query
     * keeps them all. Only the query is folded here.
     */
    fun results(query: String, filters: LibraryFilters): List<ExerciseUi> {
        val matching = if (query.isBlank()) exercises else index.matching(query).mapNotNull(byId::get)
        return if (filters.active) matching.filter(filters::accepts) else matching
    }

    /**
     * "Recently used": up to [ExercisePicker.RECENT_LIMIT] exercises of the latest workouts in
     * [history], newest first and each once, as this list has them now; the ones it no longer has
     * are left out before counting ([ExercisePicker.currentRecents], the same as the picker).
     */
    fun recents(history: List<WorkoutUi>): List<ExerciseUi> = ExercisePicker.currentRecents(history, byId)

    companion object {
        /** Folds every shown name once, sorts, and builds the search index in that order. */
        fun build(exercises: List<ExerciseUi>, portuguese: Boolean): LibraryModel {
            val sorted = exercises
                .map { exercise ->
                    val name = displayName(exercise, portuguese)
                    SortKey(SearchFolding.fold(name), name, exercise)
                }
                .sortedWith(compareBy<SortKey> { it.folded }.thenBy { it.name }.thenBy { it.exercise.id })
                .map(SortKey::exercise)
            val index = ExerciseSearchIndex.build(sorted.map { SearchEntry(it.id, it.name, it.muscle, it.equipment) })
            return LibraryModel(sorted, portuguese, sorted.associateBy(ExerciseUi::id), index)
        }
    }

    private class SortKey(val folded: String, val name: String, val exercise: ExerciseUi)
}

/**
 * The last [LibraryModel] built, for its list instance and language: the repository keeps the
 * identity of an unchanged list, so going back to the library reuses the model in the same frame
 * instead of building it again. One pair only. [rememberLibraryModel] uses the process instance;
 * tests build their own.
 */
internal class LibraryModelCache {
    private class Entry(val exercises: List<ExerciseUi>, val portuguese: Boolean, val model: LibraryModel)

    @Volatile
    private var last: Entry? = null

    /** The model built for this very list instance ([exercises] `===`) and [portuguese], if it is the last one. */
    fun get(exercises: List<ExerciseUi>, portuguese: Boolean): LibraryModel? =
        last?.takeIf { it.exercises === exercises && it.portuguese == portuguese }?.model

    fun put(exercises: List<ExerciseUi>, portuguese: Boolean, model: LibraryModel) {
        last = Entry(exercises, portuguese, model)
    }

    companion object {
        val process = LibraryModelCache()
    }
}

/**
 * The [LibraryModel] of [exercises] in the catalog language [portuguese] (the screen's,
 * `catalogInPortuguese()`): `null` until the first build, which runs on [Dispatchers.Default]; at
 * once when the process cache already holds this list and language. When the list or the language
 * changes, the previous model stays until the new one is ready, so the list never falls back to the
 * skeleton; but a model of the other language is dropped at once (the skeleton shows until the new
 * one is built), so a name is never shown in a language the screen around it is not in.
 *
 * Its seams (pre-flight F19): tests pass their own [cache] and a [build] they can hold, so "before
 * the index is ready" is a state they set, not a race.
 */
@Composable
internal fun rememberLibraryModel(
    exercises: List<ExerciseUi>,
    portuguese: Boolean,
    cache: LibraryModelCache = LibraryModelCache.process,
    build: suspend (List<ExerciseUi>, Boolean) -> LibraryModel = ::buildOffTheMainThread,
): LibraryModel? {
    val currentBuild by rememberUpdatedState(build)
    var latest by remember(cache) { mutableStateOf<LibraryModel?>(null) }
    val cached = cache.get(exercises, portuguese)
    LaunchedEffect(exercises, portuguese, cache) {
        latest = cache.get(exercises, portuguese)
            ?: currentBuild(exercises, portuguese).also { cache.put(exercises, portuguese, it) }
    }
    return cached ?: latest?.takeIf { it.portuguese == portuguese }
}

/** The default build of [rememberLibraryModel]: [LibraryModel.build] on [Dispatchers.Default]. */
internal suspend fun buildOffTheMainThread(exercises: List<ExerciseUi>, portuguese: Boolean): LibraryModel =
    withContext(Dispatchers.Default) { LibraryModel.build(exercises, portuguese) }
