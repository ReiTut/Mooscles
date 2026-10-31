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
import kotlin.test.assertIs
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
        val timeline = assertNotNull(cache.peekQuery(index, RecordsQuery(zone = zone)))
        assertEquals(RecordTimelines.query(index, RecordsQuery(zone = zone)).total, timeline.total)
        assertNull(cache.peekQuery(index, RecordsQuery(currentOnly = true, zone = zone)))
    }

    @Test
    fun aFailingPrewarmStaysQuietAndALaterGetBuildsAgain() = runTest {
        // A defect in the build must not crash the app 500 ms after every launch; runTest fails on an uncaught one.
        var calls = 0
        val cache = ProgressIndexCache(backgroundScope, StandardTestDispatcher(testScheduler), build = { history, records ->
            calls++
            if (calls == 1) error("a history the index cannot read")
            ProgressIndex.build(history, records)
        })
        val history = history("a")

        cache.prewarm(history, records, zone)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, calls)
        assertNull(cache.peek(history, records))

        // The failed build was forgotten: a screen that opens later builds again.
        val index = cache.get(history, records)
        assertEquals(2, calls)
        assertSame(index, cache.peek(history, records))
    }

    /**
     * Final review of phase 5A (CR-3): the build's only caller (a prewarm a newer one cancelled, a screen that left) is
     * gone when the build fails, so nobody sees the failure; the build forgets itself, and the next get builds again
     * instead of getting the old failure.
     */
    @Test
    fun aFailedBuildWhoseOnlyCallerLeftIsForgottenToo() = runTest {
        var calls = 0
        val builds = HeldDispatcher()
        val cache = ProgressIndexCache(backgroundScope, builds, build = { history, records ->
            calls++
            if (calls == 1) error("a history the index cannot read")
            ProgressIndex.build(history, records)
        })
        val history = history("a")

        val caller = launch { cache.get(history, records) }
        runCurrent()
        caller.cancel()
        runCurrent()
        // Still the latest history, so the build is not cancelled: it runs, with nobody waiting, and throws.
        builds.runAll()
        runCurrent()
        assertEquals(1, calls)

        val index = async { cache.get(history, records) }
        runCurrent()
        builds.runAll()
        runCurrent()
        assertEquals(2, calls, "the next get builds again")
        assertSame(index.await(), cache.peek(history, records))
    }

    @Test
    fun aNewerPrewarmLeavesABuildSomeoneWaitsFor() = runTest {
        val counting = CountingBuild()
        val builds = HeldDispatcher()
        val cache = cache(counting, builds)
        val first = history("first")
        val second = history("second")

        cache.prewarm(first, records, zone)
        advanceTimeBy(500)
        runCurrent()
        // The first prewarm's build is pending, and a screen now waits for it too.
        val screen = async { cache.get(first, records) }
        runCurrent()
        cache.prewarm(second, records, zone)
        runCurrent()
        builds.runAll()
        runCurrent()

        assertSame(first, counting.from[screen.await()])
        assertEquals(listOf(first), counting.built, "the second prewarm is still waiting its 500 ms")
    }

    @Test
    fun aQueuedBuildNobodyWaitsForIsCancelledByTheNextHistory() = runTest {
        val counting = CountingBuild()
        val builds = HeldDispatcher()
        val cache = cache(counting, builds)
        val old = history("old")
        val new = history("new")

        // The collector leaves first, so the build is still the latest, with nobody waiting...
        val oldCollector = launch { cache.get(old, records) }
        runCurrent()
        oldCollector.cancel()
        runCurrent()
        // ...until the next history arrives and cancels it.
        val newCollector = async { cache.get(new, records) }
        runCurrent()
        builds.runAll()
        runCurrent()

        assertEquals(listOf(new), counting.built)
        assertSame(new, counting.from[newCollector.await()])
    }

    @Test
    fun aQueryAsTheLastStatementOfALaunchKeepsItsType() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)
        val query = RecordsQuery(zone = zone)

        // The block expects Unit; the query's class, not the call site, decides what is stored.
        launch { cache.query(index, query) }
        runCurrent()

        assertIs<RecordsTimeline>(cache.peekQuery(index, query))
    }

    /** Counts its computations; [name] makes queries differ, and the class keeps them apart from other kinds. */
    private class Counter {
        var computed = 0
    }

    private data class Numbered(val name: Any, val counter: Counter) : ProgressQuery<List<Int>> {
        override fun compute(index: ProgressIndex): List<Int> {
            counter.computed++
            return listOf(counter.computed)
        }
    }

    private data class Constant(val name: String, val value: Int) : ProgressQuery<Int> {
        override fun compute(index: ProgressIndex): Int = value
    }

    @Test
    fun queriesAreMemoizedPerIndexAndKey() = runTest {
        val cache = cache(CountingBuild())
        val one = ProgressIndex.build(history("one"), records)
        val two = ProgressIndex.build(history("two"), records)
        val counter = Counter()

        assertNull(cache.peekQuery(one, Numbered("a", counter)))
        val first = cache.query(one, Numbered("a", counter))
        val again = cache.query(one, Numbered("a", counter))
        assertSame(first, again)
        assertSame(first, cache.peekQuery(one, Numbered("a", counter)))
        assertEquals(1, counter.computed)

        cache.query(one, Numbered("b", counter))
        assertEquals(2, counter.computed, "another key is another computation")
        val other = cache.query(two, Numbered("a", counter))
        assertEquals(3, counter.computed, "another index is another computation")
        assertTrue(other !== first)
        // Another kind of query with the same field is another key, never the same result.
        assertNull(cache.peekQuery(one, Constant("a", 1)))
    }

    @Test
    fun concurrentQueriesShareOneComputation() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)
        val counter = Counter()

        val results = List(3) { async { cache.query(index, Numbered("a", counter)) } }.awaitAll()

        assertEquals(1, counter.computed)
        results.forEach { assertSame(results.first(), it) }
    }

    @Test
    fun memoKeepsAtMostThirtyTwoEntries() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)
        val counter = Counter()

        repeat(33) { cache.query(index, Numbered(it, counter)) }

        assertNull(cache.peekQuery(index, Numbered(0, counter)), "the oldest of 33 is dropped")
        (1..32).forEach { assertNotNull(cache.peekQuery(index, Numbered(it, counter)), "key $it") }
        cache.query(index, Numbered(0, counter))
        assertEquals(34, counter.computed)
    }

    @Test
    fun aQueryUsedAgainIsKeptOverOlderOnes() = runTest {
        val cache = cache(CountingBuild())
        val index = ProgressIndex.build(history("a"), records)

        repeat(32) { cache.query(index, Constant("$it", 1)) }
        cache.query(index, Constant("0", 1))
        cache.query(index, Constant("32", 1))

        assertNotNull(cache.peekQuery(index, Constant("0", 1)), "used again, so the most recent")
        assertNull(cache.peekQuery(index, Constant("1", 1)))
    }

    @Test
    fun onlyTheLatestBuiltIndexKeepsQueries() = runTest {
        val cache = cache(CountingBuild())
        val elsewhere = ProgressIndex.build(history("elsewhere"), records)
        cache.query(elsewhere, Constant("chart", 2))
        assertEquals(2, cache.peekQuery(elsewhere, Constant("chart", 2)), "kept before the first build")
        val old = cache.get(history("old"), records)
        cache.query(old, Constant("chart", 1))

        val new = cache.get(history("new"), records)

        // An older index's results would only keep its whole history in memory.
        assertNull(cache.peekQuery(old, Constant("chart", 1)))
        assertNull(cache.peekQuery(elsewhere, Constant("chart", 2)))
        assertEquals(1, cache.query(old, Constant("chart", 1)), "still computed for its caller")
        assertNull(cache.peekQuery(old, Constant("chart", 1)), "but not kept")
        assertEquals(3, cache.query(new, Constant("chart", 3)))
        assertEquals(3, cache.peekQuery(new, Constant("chart", 3)))
    }

    @Test
    fun aQueryOverTheOldIndexThatEndsAfterTheSwapIsNotKept() = runTest {
        val builds = HeldDispatcher()
        val cache = cache(CountingBuild(), builds)
        val first = async { cache.get(history("old"), records) }
        runCurrent()
        builds.runAll()
        runCurrent()
        val old = first.await()

        // The new build is queued before the query over the old index, so it publishes while the query is pending.
        val next = async { cache.get(history("new"), records) }
        runCurrent()
        val late = async { cache.query(old, Constant("chart", 1)) }
        runCurrent()
        builds.runAll()
        runCurrent()

        next.await()
        assertEquals(1, late.await())
        assertNull(cache.peekQuery(old, Constant("chart", 1)))
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
