package com.mooscles.screens.exercise

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cue reader (spec D8, plan decision 20). Each test builds its own [CueCache], so the
 * process-wide cache of [ExerciseCues] never decides what a test sees (pre-flight F18).
 */
class ExerciseCuesTest {
    /** A fake file reader that counts its calls per path. */
    private class CountingReader(private val files: Map<String, String>) {
        private val lock = Mutex()
        val calls = mutableMapOf<String, Int>()

        suspend fun read(path: String): ByteArray {
            lock.withLock { calls[path] = (calls[path] ?: 0) + 1 }
            // Holds the read open so concurrent loads overlap it.
            delay(20)
            return (files[path] ?: error("no file $path")).encodeToByteArray()
        }
    }

    @Test
    fun parsesStableIdToCues() {
        val cues = ExerciseCues.parse(
            """{ "barbell_curl": ["Elbows stay at your sides.", "Lower under control."], "dumbbell_shrug": [] }""",
        )
        assertEquals(
            mapOf("barbell_curl" to listOf("Elbows stay at your sides.", "Lower under control."), "dumbbell_shrug" to emptyList()),
            cues,
        )
    }

    @Test
    fun loadsOncePerLanguage() = runTest {
        val en = ExerciseCues.path(portuguese = false)
        val pt = ExerciseCues.path(portuguese = true)
        val reader = CountingReader(mapOf(en to """{"a":["One."]}""", pt to """{"a":["Um."]}"""))
        val cache = CueCache(reader::read)
        val results = withContext(Dispatchers.Default) {
            coroutineScope {
                (0 until 10).map { i ->
                    val portuguese = i % 2 == 1
                    async { portuguese to cache.load(portuguese) }
                }.awaitAll()
            }
        }
        assertEquals(mapOf(en to 1, pt to 1), reader.calls.toMap())
        for ((portuguese, cues) in results) assertEquals(listOf(if (portuguese) "Um." else "One."), cues["a"])
        // Later loads come from memory, and the loaded map is visible without suspending.
        cache.load(portuguese = false)
        assertEquals(mapOf(en to 1, pt to 1), reader.calls.toMap())
        assertEquals(listOf("Um."), cache.loaded(portuguese = true)?.get("a"))
    }

    @Test
    fun unreadableFileGivesNoCues() = runTest {
        val en = ExerciseCues.path(portuguese = false)
        val pt = ExerciseCues.path(portuguese = true)
        // English is missing (the reader throws); Portuguese is not JSON of the expected shape.
        val reader = CountingReader(mapOf(pt to """["not", "an", "object"]"""))
        val cache = CueCache(reader::read)
        assertEquals(emptyMap(), cache.load(portuguese = false))
        assertEquals(emptyMap(), cache.load(portuguese = true))
        // A failed read is not retried in the same process.
        assertEquals(emptyMap(), cache.load(portuguese = false))
        assertEquals(mapOf(en to 1, pt to 1), reader.calls.toMap())
    }

    @Test
    fun catalogIdsLookUpByStableIdAndCustomOnesHaveNone() {
        val cues = mapOf("barbell_curl" to listOf("Elbows stay at your sides."))
        assertEquals(listOf("Elbows stay at your sides."), ExerciseCues.cuesFor(cues, "catalog_barbell_curl"))
        assertTrue(ExerciseCues.cuesFor(cues, "catalog_dumbbell_shrug").isEmpty())
        // A custom id never carries the catalog prefix, even when it spells a stable id.
        assertTrue(ExerciseCues.cuesFor(cues, "barbell_curl").isEmpty())
        assertTrue(ExerciseCues.cuesFor(cues, "3f2a9c1e-custom").isEmpty())
    }
}
