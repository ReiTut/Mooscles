package com.mooscles.screens.progress

import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsQuery
import com.mooscles.screens.records.RecordsTimeline
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi
import kotlin.concurrent.atomics.AtomicInt
import kotlin.coroutines.CoroutineContext
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressIndexCacheTest {
    private val zone = TimeZone.of("America/Sao_Paulo")

    /** Small histories of their own, each a new list instance. */
    private fun history(name: String): List<WorkoutUi> =
        listOf(workout(name, DAY, entry(bench, set(100.0, 5)), entry(pullUp, set(0.0, 8))))

    private val records: List<RecordUi> = emptyList()

    /** A `build` that counts, and remembers which history each index was built from. */
    private class CountingBuild {
        val built = mutableListOf<List<WorkoutUi>>()
        val from = mutableMapOf<ProgressIndex, List<WorkoutUi>>()

        val build: (List<WorkoutUi>, List<RecordUi>) -> ProgressIndex = { history, records ->
            built += history
            ProgressIndex.build(history, records).also { from[it] = history }
        }
    }

    private fun TestScope.cache(counting: CountingBuild, context: CoroutineDispatcher = StandardTestDispatcher(testScheduler)) =
        ProgressIndexCache(backgroundScope, context, build = counting.build)

    /** Holds what is dispatched to it until [runAll]: a build that has started, from the cache's view, and not ended. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runAll() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    @Test
    fun sameIdentityBuildsOnce() = runTest {
        val counting = CountingBuild()
        val cache = cache(counting)
        val history = history("a")

        assertNull(cache.peek(history, records))
        val first = cache.get(history, records)
        val second = cache.get(history, records)

        assertSame(first, second)
        assertSame(first, cache.peek(history, records))
        assertEquals(1, counting.built.size)
    }

    @Test
    fun concurrentGetsShareOneBuild() = runTest {
        val counting = CountingBuild()
        val cache = cache(counting)
        val history = history("a")

        // Two collectors (and a third) ask before the build has run: they all wait for the same one.
        val waiting = List(3) { async { cache.get(history, records) } }
        runCurrent()
        assertEquals(1, counting.built.size)
        val indexes = waiting.awaitAll()

        assertEquals(1, counting.built.size)
        indexes.forEach { assertSame(indexes.first(), it) }
        assertSame(indexes.first(), cache.peek(history, records))
    }

    @Test
    fun newIdentityBuildsAgainAndPeekReturnsOnlyTheExactOne() = runTest {
        val counting = CountingBuild()
        val cache = cache(counting)
        val history = history("a")
        val first = cache.get(history, records)
        val equal = history.toList()
        val otherRecords = ArrayList<RecordUi>()

        // An equal list in another instance is a new history: the presentation made it again (kg to lb, an import).
        assertEquals(history, equal)
        assertNull(cache.peek(equal, records))
        assertNull(cache.peek(history, otherRecords))
        val second = cache.get(equal, records)

        assertEquals(2, counting.built.size)
        assertTrue(first !== second)
        assertSame(second, cache.peek(equal, records))
        assertNull(cache.peek(history, records), "only the latest history is served")
    }

    @Test
    fun aNewHistoryWhileTheBuildIsPendingCancelsTheStaleBuild() = runTest {
        val counting = CountingBuild()
        // Builds are held, so the first one is still pending when the history changes.
        val builds = HeldDispatcher()
        val cache = cache(counting, builds)
        val old = history("old")
        val new = history("new")

        val oldCollector = launch { cache.get(old, records) }
        runCurrent()
        // The screen moves to the new history and asks for it...
        val newCollector = async { cache.get(new, records) }
        runCurrent()
        // ...and the effect of the old one is cancelled while its build is still queued.
        oldCollector.cancel()
        runCurrent()
        builds.runAll()
        runCurrent()

        assertEquals(listOf(new), counting.built, "the stale build never ran")
        val index = newCollector.await()
        assertSame(index, cache.peek(new, records))
        assertNull(cache.peek(old, records))
    }

    @Test
    fun aCollectorStillWaitingForTheOldHistoryGetsItsOwnIndex() = runTest {
        val counting = CountingBuild()
        val builds = HeldDispatcher()
        val cache = cache(counting, builds)
        val old = history("old")
        val new = history("new")

        val oldCollector = async { cache.get(old, records) }
        runCurrent()
        val newCollector = async { cache.get(new, records) }
        runCurrent()
        // A third collector of the old history joins the build still awaited instead of starting another.
        val lateOldCollector = async { cache.get(old, records) }
        runCurrent()
        builds.runAll()
        runCurrent()

        val oldIndex = oldCollector.await()
        val newIndex = newCollector.await()
        assertSame(old, counting.from[oldIndex])
        assertSame(new, counting.from[newIndex])
        assertSame(oldIndex, lateOldCollector.await())
        assertEquals(listOf(old, new), counting.built)
        assertSame(newIndex, cache.peek(new, records))
        assertNull(cache.peek(old, records), "an older build never replaces the latest")
    }

    @Test
    fun getsFromManyThreadsShareOneBuild() = runTest {
        // Real threads, not the test scheduler: the cache's lock and its published index under contention.
        val calls = AtomicCounter()
        val (history, records) = LargeProgressHistory.state(exercises = 12, workouts = 60)
        val cache = ProgressIndexCache(backgroundScope, Dispatchers.Default, build = { h, r ->
            calls.increment()
            ProgressIndex.build(h, r)
        })

        val indexes = withContext(Dispatchers.Default) {
            List(16) { async { cache.get(history, records) } }.awaitAll()
        }

        assertEquals(1, calls.value)
        indexes.forEach { assertSame(indexes.first(), it) }
        assertSame(indexes.first(), cache.peek(history, records))
    }

    @Test
    fun prewarmWaitsFiveHundredMillisAndANewerOneCancelsIt() = runTest {
        val counting = CountingBuild()
        val cache = cache(counting)
        val first = history("first")
        val second = history("second")

        cache.prewarm(first, records, zone)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(0, counting.built.size)
        // A new history 300 ms in: the first prewarm (due at 500 ms) is cancelled, the second is due at 800 ms.
        cache.prewarm(second, records, zone)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(0, counting.built.size, "nothing is built before 500 ms")
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(second), counting.built)
        assertNotNull(cache.peek(second, records))
        assertNull(cache.peek(first, records))
    }

    @Test
    fun prewarmAlsoComputesTheDefaultRecordsQuery() = runTest {
        val counting = CountingBuild()
        val cache = cache(counting)
        val history = history("a")
        val w = history.single()
        val record = weightRecord(bench, 100.0, 5, null, w)
        val withRecords = listOf(w.copy(records = listOf(record)))
        val current = listOf(currentOf(record))

        cache.prewarm(withRecords, current, zone)
        advanceTimeBy(500)
        runCurrent()

        val index = assertNotNull(cache.peek(withRecords, current))
        val timeline = assertNotNull(cache.peekQuery<RecordsQuery, RecordsTimeline>(index, RecordsQuery(zone = zone)))
        assertEquals(RecordTimelines.query(index, RecordsQuery(zone = zone)).total, timeline.total)
        assertNull(cache.peekQuery<RecordsQuery, RecordsTimeline>(index, RecordsQuery(currentOnly = true, zone = zone)))
    }

    @Test
    fun queriesAreMemoizedPerIndexAndKey() = runTest {
        val cache = cache(CountingBuild())
        val one = ProgressIndex.build(history("one"), records)
        val two = ProgressIndex.build(history("two"), records)
        var computed = 0
        val compute: (ProgressIndex) -> List<Int> = { computed++; listOf(computed) }

        assertNull(cache.peekQuery<String, List<Int>>(one, "a"))
        val first = cache.query(one, "a", compute)
        val again = cache.query(one, "a", compute)
        assertSame(first, again)
        assertSame(first, cache.peekQuery<String, List<Int>>(one, "a"))
        assertEquals(1, computed)

        cache.query(one, "b", compute)
        assertEquals(2, computed, "another key is another computation")
        val other = cache.query(two, "a", compute)
        assertEquals(3, computed, "another index is another computation")
        assertTrue(other !== first)
    }

    @Test
    fun concurrentQueriesShareOneComputation() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)
        var computed = 0

        val results = List(3) { async { cache.query(index, "a") { computed++; listOf(computed) } } }.awaitAll()

        assertEquals(1, computed)
        results.forEach { assertSame(results.first(), it) }
    }

    @Test
    fun memoKeepsAtMostThirtyTwoEntries() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)
        var computed = 0
        val compute: (ProgressIndex) -> List<Int> = { computed++; listOf(computed) }

        repeat(33) { cache.query(index, it, compute) }

        assertNull(cache.peekQuery<Int, List<Int>>(index, 0), "the oldest of 33 is dropped")
        (1..32).forEach { assertNotNull(cache.peekQuery<Int, List<Int>>(index, it), "key $it") }
        cache.query(index, 0, compute)
        assertEquals(34, computed)
    }

    @Test
    fun aQueryUsedAgainIsKeptOverOlderOnes() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)
        val compute: (ProgressIndex) -> Int = { 1 }

        repeat(32) { cache.query(index, it, compute) }
        cache.query(index, 0, compute)
        cache.query(index, 32, compute)

        assertNotNull(cache.peekQuery<Int, Int>(index, 0), "used again, so the most recent")
        assertNull(cache.peekQuery<Int, Int>(index, 1))
    }

    @Test
    fun aNewlyBuiltIndexDropsThePreviousIndexQueries() = runTest {
        val cache = cache(CountingBuild())
        val old = cache.get(history("old"), records)
        cache.query(old, "chart") { 1 }
        val elsewhere = ProgressIndex.build(history("elsewhere"), records)
        cache.query(elsewhere, "chart") { 2 }

        val new = cache.get(history("new"), records)

        // The previous history's results would only keep its whole index alive; one not built here stays.
        assertNull(cache.peekQuery<String, Int>(old, "chart"))
        assertEquals(2, cache.peekQuery<String, Int>(elsewhere, "chart"))
        assertEquals(3, cache.query(new, "chart") { 3 })
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class AtomicCounter {
        private val count = AtomicInt(0)
        val value: Int get() = count.load()
        fun increment() {
            count.incrementAndFetch()
        }
    }
}
