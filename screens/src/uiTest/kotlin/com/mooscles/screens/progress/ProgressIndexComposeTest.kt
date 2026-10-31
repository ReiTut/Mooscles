package com.mooscles.screens.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [rememberProgressIndex] and [rememberProgressQuery] in a composition (Task 7 review carry): null until the build and
 * the query end off the main thread, the previous result kept while a new history builds, one computation per query,
 * and a failing build that never reaches the composition.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalAtomicApi::class)
class ProgressIndexComposeTest {
    private val bench = ExerciseUi("bench", "Bench", "Chest", "Barbell", "Total load")
    private val key = SeriesKey(bench.id, bench.convention)

    private fun history(sessions: Int): List<WorkoutUi> = (1..sessions).map { n ->
        WorkoutUi(
            "w$n", "W$n", n * 86_400_000L, n * 86_400_000L + 3_600_000,
            exercises = listOf(WorkoutExerciseUi("e$n", bench, listOf(SetUi("s$n", 5, 80.0 + n, completed = true)))),
        )
    }

    /** The number of sessions of the bench, as a query; [computed] counts its computations. */
    private data class Sessions(val key: SeriesKey, val computed: AtomicInt, val fail: Boolean = false) : ProgressQuery<String> {
        override fun compute(index: ProgressIndex): String {
            computed.incrementAndFetch()
            check(!fail) { "query failed on purpose" }
            return "sessions:${index.sessions(key).size}"
        }
    }

    /** A cache whose builds wait for [gate] (when set) and throw while [failing]; [builds] counts them. */
    private class HeldCache {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val builds = AtomicInt(0)

        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        @Volatile
        var failing = false

        val cache = ProgressIndexCache(scope, build = { history, records ->
            builds.incrementAndFetch()
            gate?.let { runBlocking { it.await() } }
            check(!failing) { "build failed on purpose" }
            ProgressIndex.build(history, records)
        })

        fun close() {
            gate?.complete(Unit)
            scope.cancel()
        }
    }

    private fun ComposeUiTest.show(history: MutableState<List<WorkoutUi>>, query: Sessions, cache: ProgressIndexCache?) =
        show(history, mutableStateOf(query), cache)

    private fun <V : Any> ProgressLoad<V>.text(ready: (V) -> String): String = when (this) {
        ProgressLoad.Loading -> "loading"
        ProgressLoad.Failed -> "failed"
        is ProgressLoad.Ready -> ready(value)
    }

    private fun ComposeUiTest.show(
        history: MutableState<List<WorkoutUi>>,
        query: MutableState<Sessions>,
        cache: ProgressIndexCache?,
        loading: MutableState<Boolean> = mutableStateOf(false),
    ) {
        val records = emptyList<RecordUi>()
        withScreen {
            CompositionLocalProvider(LocalProgressIndexCache provides cache) {
                val index = rememberProgressIndex(history.value, records, loading.value)
                val value = rememberProgressQuery(index, query.value)
                // Read where the composition reads them: the nodes show what the composables returned.
                MText(index.text { "index:${it.sessions(key).size}" }, MTheme.type.bodyMd, Modifier.testTag(INDEX))
                MText(value.text { it }, MTheme.type.bodyMd, Modifier.testTag(QUERY))
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.text(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun ComposeUiTest.waitForText(tag: String, expected: String) {
        waitUntil(timeoutMillis = 5_000) { text(tag) == expected }
    }

    @Test
    fun indexAndQueryAreNullUntilTheBuildEnds() = runComposeUiTest {
        val held = HeldCache().apply { gate = CompletableDeferred() }
        try {
            val computed = AtomicInt(0)
            show(mutableStateOf(history(3)), Sessions(key, computed), held.cache)
            assertEquals("loading", text(INDEX))
            assertEquals("loading", text(QUERY))
            assertEquals(0, computed.load())
            held.gate?.complete(Unit)
            waitForText(INDEX, "index:3")
            waitForText(QUERY, "sessions:3")
            waitForIdle()
            assertEquals(1, held.builds.load())
            assertEquals(1, computed.load())
        } finally {
            held.close()
        }
    }

    /**
     * Final review of phase 5A (CR-6): the screens' guard lives in [rememberProgressIndex]. While the state loads, its
     * lists are a restore's empty ones: loading, and the cache is not asked to build them.
     */
    @Test
    fun whileTheStateLoadsTheCacheBuildsNothing() = runComposeUiTest {
        val held = HeldCache()
        try {
            val computed = AtomicInt(0)
            val loading = mutableStateOf(true)
            show(mutableStateOf(history(3)), mutableStateOf(Sessions(key, computed)), held.cache, loading)
            waitForIdle()
            assertEquals("loading", text(INDEX))
            assertEquals("loading", text(QUERY))
            assertEquals(0, held.builds.load())

            loading.value = false
            waitForText(INDEX, "index:3")
            waitForText(QUERY, "sessions:3")
            assertEquals(1, held.builds.load())
        } finally {
            held.close()
        }
    }

    @Test
    fun withoutAProvidedCacheTheCompositionKeepsItsOwn() = runComposeUiTest {
        val computed = AtomicInt(0)
        show(mutableStateOf(history(2)), Sessions(key, computed), cache = null)
        waitForText(QUERY, "sessions:2")
        assertEquals("index:2", text(INDEX))
        assertEquals(1, computed.load())
    }

    @Test
    fun aNewHistoryKeepsThePreviousResultUntilItsIndexIsReadyThenQueriesAgain() = runComposeUiTest {
        val held = HeldCache()
        try {
            val computed = AtomicInt(0)
            val history = mutableStateOf(history(2))
            show(history, Sessions(key, computed), held.cache)
            waitForText(QUERY, "sessions:2")

            held.gate = CompletableDeferred()
            history.value = history(3)
            waitForIdle()
            // Still the previous history's index and result, never null in between.
            assertEquals("index:2", text(INDEX))
            assertEquals("sessions:2", text(QUERY))

            held.gate?.complete(Unit)
            waitForText(INDEX, "index:3")
            waitForText(QUERY, "sessions:3")
            assertEquals(2, held.builds.load())
            assertEquals(2, computed.load())
        } finally {
            held.close()
        }
    }

    @Test
    fun aFailingBuildIsFailedThenTheNextHistoryIsReady() = runComposeUiTest {
        val held = HeldCache().apply { failing = true }
        try {
            val computed = AtomicInt(0)
            val history = mutableStateOf(history(2))
            show(history, Sessions(key, computed), held.cache)
            waitUntil(timeoutMillis = 5_000) { held.builds.load() == 1 }
            // Nothing to show and nothing to wait for: failed, never loading for good.
            waitForText(INDEX, "failed")
            assertEquals("failed", text(QUERY))

            held.failing = false
            history.value = history(4)
            waitForText(INDEX, "index:4")
            waitForText(QUERY, "sessions:4")
            assertEquals(1, computed.load())
        } finally {
            held.close()
        }
    }

    @Test
    fun aFailingBuildKeepsThePreviousIndex() = runComposeUiTest {
        val held = HeldCache()
        try {
            val history = mutableStateOf(history(2))
            show(history, Sessions(key, AtomicInt(0)), held.cache)
            waitForText(QUERY, "sessions:2")
            held.failing = true
            history.value = history(3)
            waitUntil(timeoutMillis = 5_000) { held.builds.load() == 2 }
            waitForIdle()
            // The previous index is still valid data: kept, not failed.
            assertEquals("index:2", text(INDEX))
            assertEquals("sessions:2", text(QUERY))
        } finally {
            held.close()
        }
    }

    @Test
    fun aFailingQueryIsFailedNotThePreviousResult() = runComposeUiTest {
        val computed = AtomicInt(0)
        val query = mutableStateOf(Sessions(key, computed))
        show(mutableStateOf(history(2)), query, cache = null)
        waitForText(QUERY, "sessions:2")
        query.value = Sessions(key, computed, fail = true)
        // The previous result belongs to another query: showing it would disagree with the screen for good.
        waitForText(QUERY, "failed")
        assertEquals("index:2", text(INDEX))
        query.value = Sessions(key, computed)
        waitForText(QUERY, "sessions:2")
    }

    private companion object {
        const val INDEX = "probe-index"
        const val QUERY = "probe-query"
    }
}
