package com.mooscles.screens.exercise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mooscles.data.ExerciseCatalog
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Short execution cues of the catalog exercises (spec D8): our own text, up to four per exercise,
 * in `files/exercise_cues/en.json` and `pt-BR.json`, each an object from `stableId` (the catalog id
 * without `catalog_`) to its cues. Read once per language and per process, off the main thread,
 * and kept in memory (plan decision 20).
 */
internal object ExerciseCues {
    /** The file of one language, as [Res.readBytes] takes it. */
    fun path(portuguese: Boolean): String = "files/exercise_cues/${if (portuguese) "pt-BR" else "en"}.json"

    /** Parses one cue file. Throws on anything that is not an object of string arrays. */
    fun parse(json: String): Map<String, List<String>> =
        Json.parseToJsonElement(json).jsonObject.mapValues { (id, cues) ->
            cues.jsonArray.map { cue ->
                val text = cue.jsonPrimitive
                require(text.isString) { "cue of $id is not a string" }
                text.content
            }
        }

    /** The cues of [exerciseId]; none for a custom exercise (its id never has the catalog prefix). */
    fun cuesFor(cues: Map<String, List<String>>, exerciseId: String): List<String> =
        if (exerciseId.startsWith(ExerciseCatalog.ID_PREFIX)) cues[exerciseId.removePrefix(ExerciseCatalog.ID_PREFIX)].orEmpty() else emptyList()

    @OptIn(ExperimentalResourceApi::class)
    private val process = CueCache { Res.readBytes(it) }

    /** The cues of one language, read and parsed on [Dispatchers.Default] the first time. */
    suspend fun load(portuguese: Boolean): Map<String, List<String>> =
        process.loaded(portuguese) ?: withContext(Dispatchers.Default) { process.load(portuguese) }

    /** The cues of one language when they are already in memory, without suspending. */
    fun loaded(portuguese: Boolean): Map<String, List<String>>? = process.loaded(portuguese)
}

/**
 * The cue cache of one reader: one read per language, even under concurrent loads, guarded by a
 * [Mutex]. A file that cannot be read or parsed gives no cues and is not read again. [ExerciseCues]
 * keeps the process instance; tests build their own with a fake [read] (pre-flight F18).
 */
internal class CueCache(private val read: suspend (String) -> ByteArray) {
    private val mutex = Mutex()

    @Volatile
    private var english: Map<String, List<String>>? = null

    @Volatile
    private var portuguese: Map<String, List<String>>? = null

    fun loaded(portuguese: Boolean): Map<String, List<String>>? = if (portuguese) this.portuguese else english

    suspend fun load(portuguese: Boolean): Map<String, List<String>> =
        loaded(portuguese) ?: mutex.withLock {
            loaded(portuguese) ?: readOrNone(portuguese).also {
                if (portuguese) this.portuguese = it else english = it
            }
        }

    private suspend fun readOrNone(portuguese: Boolean): Map<String, List<String>> =
        try {
            ExerciseCues.parse(read(ExerciseCues.path(portuguese)).decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A missing or broken bundled file hides the section (spec D8: no cues, no section).
            emptyMap()
        }
}

/**
 * The cues of [exerciseId] (the full id, `catalog_<stableId>` for the catalog) in the catalog
 * language of the screen: `null` while the file loads, empty without cues and for custom exercises.
 */
@Composable
internal fun rememberExerciseCues(exerciseId: String): List<String>? {
    // The same language as the names on screen ([catalogInPortuguese]).
    val portuguese = catalogInPortuguese()
    val cues = remember(exerciseId, portuguese) {
        mutableStateOf(ExerciseCues.loaded(portuguese)?.let { ExerciseCues.cuesFor(it, exerciseId) })
    }
    LaunchedEffect(exerciseId, portuguese) {
        if (cues.value == null) cues.value = ExerciseCues.cuesFor(ExerciseCues.load(portuguese), exerciseId)
    }
    return cues.value
}
