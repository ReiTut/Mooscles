package com.mooscles.screens.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.mooscles.screens.records.RecordsQuery
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/**
 * A query over a [ProgressIndex] and its key in the cache's memo: implement it on a data class whose fields name
 * everything [compute] reads (one class per kind of query: a chart, the records timeline, a sparkline). The class is
 * part of `equals`, so two kinds never share a result, and [V] comes from the class, never from a call site. Do not
 * make the class generic: `Foo<A>` and `Foo<B>` could be equal.
 */
interface ProgressQuery<V : Any> {
    /** Pure and quick to call again: it runs off the main thread, once per index and equal query. */
    fun compute(index: ProgressIndex): V
}

/**
 * The one [ProgressIndex] of the app and the results of the queries over it (spec D15, plan decision 9). The `:app`
 * keeps one in `MoosclesApplication` (its `appScope`) and hands it to the screens by [LocalProgressIndexCache].
 *
 * **Identity.** An index belongs to one `UiState.history` and one `UiState.records`, as instances (`===`): the
 * presentation reuses both lists while the history does not change, and makes new ones when it does (a workout
 * finished, edited or deleted, an import, kg to lb). An equal list in another instance is another history. The cache
 * does not order histories: the latest is the last one asked for, so callers pass only the current `UiState`'s
 * lists, never ones a snapshot kept (asking for an older history again makes it the latest and rebuilds it).
 *
 * **Threads.** [build] and every query run in [context] ([Dispatchers.Default]), never on the caller's thread;
 * [peek] and [peekQuery] only read what is already done, so a composition can call them in the same frame. Any
 * thread may call anything here.
 *
 * **One build per history.** Callers of [get] for the same history wait for the same build. A history that is no
 * longer the latest keeps its build only while someone still waits for it (they get its own index, never a newer
 * one); with nobody waiting it is cancelled, so a build still queued never runs. A build already running cannot be
 * interrupted ([build] does not suspend): it ends and its index is dropped. [peek] serves only the latest history's
 * index, once built. Each index is immutable and in kg, so a screen may keep showing the previous one until the
 * next is ready. A build that throws is forgotten, so the next [get] tries again.
 *
 * **Queries** are memoized per index and [ProgressQuery]: the [MEMO_SIZE] most recently used, with concurrent callers
 * of the same one sharing its computation. Once this cache has built an index, only results over its latest index
 * are kept (an older one's would keep that whole history in memory): they are dropped when a new index is built,
 * and a result over another index is still computed for its caller but not kept. Before the first build, results
 * over any index are kept.
 */
class ProgressIndexCache(
    private val scope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.Default,
    private val prewarmDelayMillis: Long = 500,
    private val build: (List<WorkoutUi>, List<RecordUi>) -> ProgressIndex = ProgressIndex::build,
) {
    /**
     * Where every build, query and prewarm runs: a child of [scope] whose children fail alone, so a failed one does
     * not cancel the others. A build or a query keeps its failure in its `Deferred` for the callers that wait; the
     * prewarm, which nobody waits for, catches its own ([prewarm]), since an uncaught one would crash the app.
     */
    private val children = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

    private class Ready(val history: List<WorkoutUi>, val records: List<RecordUi>, val index: ProgressIndex)

    /** One history's build; [waiters] (callers of [get] still waiting) is read and written under [lock] only. */
    private class Build(val history: List<WorkoutUi>, val records: List<RecordUi>, val index: Deferred<ProgressIndex>) {
        var waiters = 0
        fun of(history: List<WorkoutUi>, records: List<RecordUi>) = this.history === history && this.records === records
    }

    private val lock = Mutex()

    /** The latest history's build (under [lock]). */
    private var latest: Build? = null

    /** Older builds that someone still waits for (under [lock]). */
    private val superseded = ArrayList<Build>()

    /** The latest history's index once built: written under [lock], read anywhere. */
    @Volatile
    private var ready: Ready? = null

    /** The index of exactly these instances, if it is built and theirs is the latest history; never builds. */
    fun peek(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex? =
        ready?.takeIf { it.history === history && it.records === records }?.index

    /**
     * The index of [history] and [records], built once in [context]; concurrent callers wait for the same build. Throws
     * what the build threw.
     */
    suspend fun get(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex {
        peek(history, records)?.let { return it }
        val build = lock.withLock {
            val found = latest?.takeIf { it.of(history, records) } ?: superseded.firstOrNull { it.of(history, records) }
            (found ?: start(history, records)).also { it.waiters++ }
        }
        var failed = false
        try {
            return build.index.await()
        } catch (error: Throwable) {
            // A build that threw is forgotten, so the next call tries again; a cancelled caller is not a failure.
            failed = error !is CancellationException
            throw error
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    build.waiters--
                    if (build !== latest && build.waiters == 0) {
                        build.index.cancel()
                        superseded.remove(build)
                    }
                    if (failed && build === latest) latest = null
                }
            }
        }
    }

    /** Under [lock]: makes [history] the latest, cancelling the previous build unless someone still waits for it. */
    private fun start(history: List<WorkoutUi>, records: List<RecordUi>): Build {
        latest?.let { previous -> if (previous.waiters == 0) previous.index.cancel() else superseded += previous }
        val index = children.async(context) {
            val index = build(history, records)
            // Only the latest history is published: an older build that ends late never replaces a newer index. The
            // memo is cleared even if this build is cancelled now, so no older index's results outlive the swap.
            withContext(NonCancellable) {
                val published = lock.withLock {
                    val isLatest = latest?.of(history, records) == true
                    if (isLatest) ready = Ready(history, records, index)
                    isLatest
                }
                if (published) queryLock.withLock { memo = memo.filter { it.slot.index === index } }
            }
            index
        }
        return Build(history, records, index).also { latest = it }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val prewarming = AtomicReference<Job?>(null)

    /**
     * Builds the index of [history] and [records] in the background after [prewarmDelayMillis], and with it the
     * Records tab's default query ([RecordsQuery] in [zone]), so the tabs open with both ready (plan decision 9). A
     * newer prewarm cancels a pending one; a build that someone else waits for goes on. It never throws: a build or a
     * query that fails is dropped here (a screen that opens later tries again and shows its own state), because a
     * failure nobody waits for would crash the app at every launch.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun prewarm(history: List<WorkoutUi>, records: List<RecordUi>, zone: TimeZone) {
        val job = children.launch(start = CoroutineStart.LAZY) {
            delay(prewarmDelayMillis)
            try {
                query(get(history, records), RecordsQuery(zone = zone))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Dropped on purpose: see above.
            }
        }
        prewarming.exchange(job)?.cancel()
        job.start()
    }

    /** A query's place in the memo: the index by identity (it has no `equals` of its own), the query by `equals`. */
    private data class Slot(val index: ProgressIndex, val query: ProgressQuery<*>)

    private class Memo(val slot: Slot, val value: Any)

    private val queryLock = Mutex()

    /** Most recently used first: written under [queryLock], read anywhere. */
    @Volatile
    private var memo: List<Memo> = emptyList()

    /** The queries being computed (under [queryLock]). */
    private val computing = HashMap<Slot, Deferred<Any>>()

    /** The result of [query] over [index], if it is already computed and kept; never computes. */
    fun <V : Any> peekQuery(index: ProgressIndex, query: ProgressQuery<V>): V? {
        val slot = Slot(index, query)
        // The slot's query class fixes V, so the value stored under it is a V.
        @Suppress("UNCHECKED_CAST")
        return memo.firstOrNull { it.slot == slot }?.value as V?
    }

    /**
     * The result of [query] over [index]: from the memo, or computed once in [context] (concurrent callers of an
     * equal query wait for that one computation, which ends even if they stop waiting). Throws what [ProgressQuery.compute] threw.
     */
    suspend fun <V : Any> query(index: ProgressIndex, query: ProgressQuery<V>): V {
        val slot = Slot(index, query)
        val running = queryLock.withLock {
            val hit = memo.firstOrNull { it.slot == slot }
            if (hit != null) {
                if (memo.first() !== hit) memo = listOf(hit) + memo.filter { it !== hit }
                @Suppress("UNCHECKED_CAST")
                return hit.value as V
            }
            computing.getOrPut(slot) {
                children.async(context) {
                    var value: V? = null
                    try {
                        query.compute(index).also { value = it }
                    } finally {
                        withContext(NonCancellable) {
                            queryLock.withLock {
                                computing.remove(slot)
                                val kept = ready?.index
                                value?.takeIf { kept == null || kept === index }?.let { done ->
                                    memo = (listOf(Memo(slot, done)) + memo.filter { it.slot != slot }).take(MEMO_SIZE)
                                }
                            }
                        }
                    }
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return running.await() as V
    }

    companion object {
        /** How many query results the memo keeps. */
        const val MEMO_SIZE = 32
    }
}

/** The app's one [ProgressIndexCache]; null outside the app (tests, the gallery), where each caller keeps its own. */
val LocalProgressIndexCache = staticCompositionLocalOf<ProgressIndexCache?> { null }

/**
 * What a progress computation has given a composition so far ([rememberProgressIndex], [rememberProgressQuery]).
 * A screen shows its skeleton for [Loading], its value for [Ready] and its no-data state for [Failed]; it never sees the
 * exception, and never shows a skeleton for a failure.
 */
@Immutable
sealed interface ProgressLoad<out V : Any> {
    /** Nothing yet: the first build or query is running. */
    data object Loading : ProgressLoad<Nothing>

    /** The current attempt threw and there is nothing valid to show; the next history or query tries again. */
    data object Failed : ProgressLoad<Nothing>

    /** A value: the current one, or the previous one while the next is computed. */
    @Immutable
    class Ready<out V : Any>(val value: V) : ProgressLoad<V>
}

/** The value of a [ProgressLoad.Ready], or null while loading or after a failure. */
val <V : Any> ProgressLoad<V>.valueOrNull: V? get() = (this as? ProgressLoad.Ready<V>)?.value

/**
 * The [ProgressIndex] of [history] and [records]: [ProgressLoad.Ready] in the same frame when the cache already has
 * it, otherwise [ProgressLoad.Loading] until the first build (off the main thread) ends. When the history changes, the
 * previous index stays [ProgressLoad.Ready] until the new one is ready, so a screen never falls back to its skeleton.
 * Without a [LocalProgressIndexCache], a cache of this composition.
 *
 * **Errors** never reach the composition as exceptions. A build that throws keeps the previous index (still valid data)
 * when there is one, and is [ProgressLoad.Failed] when there is none; the next history goes back to loading.
 */
@Composable
fun rememberProgressIndex(history: List<WorkoutUi>, records: List<RecordUi>): ProgressLoad<ProgressIndex> {
    val cache = progressIndexCache()
    var latest by remember(cache) { mutableStateOf<ProgressIndex?>(null) }
    var failed by remember(cache, history, records) { mutableStateOf(false) }
    val ready = cache.peek(history, records)
    LaunchedEffect(cache, history, records) {
        try {
            latest = cache.get(history, records)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = true
        }
    }
    val value = ready ?: latest
    return remember(value, failed) { loadOf(value, failed) }
}

/**
 * The result of [query] over [index] ([ProgressIndexCache.query]): [ProgressLoad.Ready] in the same frame when it is
 * memoized, otherwise [ProgressLoad.Loading] until the first result, computed off the main thread. A new index or
 * query keeps the previous result [ProgressLoad.Ready] until the new one is ready. A [ProgressLoad.Loading] or
 * [ProgressLoad.Failed] index passes through. A query that throws is [ProgressLoad.Failed] even with a previous
 * result, which belongs to another query or index and would disagree with the screen for good.
 */
@Composable
fun <V : Any> rememberProgressQuery(index: ProgressLoad<ProgressIndex>, query: ProgressQuery<V>): ProgressLoad<V> {
    val cache = progressIndexCache()
    val built = index.valueOrNull
    var latest by remember(cache) { mutableStateOf<V?>(null) }
    var failed by remember(cache, built, query) { mutableStateOf(false) }
    val ready = built?.let { cache.peekQuery(it, query) }
    LaunchedEffect(cache, built, query) {
        if (built == null) return@LaunchedEffect
        try {
            latest = cache.query(built, query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = true
        }
    }
    // A failed index or query shows nothing of before; a running one keeps the previous result.
    val gone = index is ProgressLoad.Failed || (built != null && ready == null && failed)
    val value = when {
        built == null || gone -> null
        else -> ready ?: latest
    }
    return remember(value, gone) { loadOf(value, gone) }
}

private fun <V : Any> loadOf(value: V?, failed: Boolean): ProgressLoad<V> = when {
    value != null -> ProgressLoad.Ready(value)
    failed -> ProgressLoad.Failed
    else -> ProgressLoad.Loading
}

/** The provided cache, or one that lives as long as this composition. */
@Composable
private fun progressIndexCache(): ProgressIndexCache {
    val provided = LocalProgressIndexCache.current
    val scope = rememberCoroutineScope()
    val own = remember(scope, provided) { if (provided == null) ProgressIndexCache(scope) else null }
    return provided ?: checkNotNull(own)
}
