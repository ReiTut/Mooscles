package com.mooscles.screens.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsQuery
import com.mooscles.screens.records.RecordsTimeline
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
 * The one [ProgressIndex] of the app and the results of the queries over it (spec D15, plan decision 9). The `:app`
 * keeps one in `MoosclesApplication` (its `appScope`) and hands it to the screens by [LocalProgressIndexCache].
 *
 * **Identity.** An index belongs to one `UiState.history` and one `UiState.records`, as instances (`===`): the
 * presentation reuses both lists while the history does not change, and makes new ones when it does (a workout
 * finished, edited or deleted, an import, kg to lb). An equal list in another instance is another history.
 *
 * **Threads.** [build] and every query run in [context] ([Dispatchers.Default]), never on the caller's thread;
 * [peek] and [peekQuery] only read what is already done, so a composition can call them in the same frame. Any
 * thread may call anything here.
 *
 * **One build per history.** Callers of [get] for the same history wait for the same build. A history that is no
 * longer the latest keeps its build only while someone still waits for it (they get its own index, never a newer
 * one); with nobody waiting it is cancelled, so a build still queued never runs, and a build already running is
 * dropped when it ends. [peek] serves only the latest history's index, once built. Each index is immutable and in
 * kg, so a screen may keep showing the previous one until the next is ready.
 *
 * **Queries** are memoized per index and key: the [MEMO_SIZE] most recently used, with concurrent callers of the same
 * one sharing its computation. When a new history's index is built, the previous index's results are dropped (they
 * would only keep that whole history in memory); results over an index this cache did not build stay until pushed out.
 */
class ProgressIndexCache(
    private val scope: CoroutineScope,
    private val context: CoroutineContext = Dispatchers.Default,
    private val prewarmDelayMillis: Long = 500,
    private val build: (List<WorkoutUi>, List<RecordUi>) -> ProgressIndex = ProgressIndex::build,
) {
    /** Where every build, query and prewarm runs: a child of [scope], where one that fails cancels nothing else. */
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

    /** The index of [history] and [records], built once in [context]; concurrent callers wait for the same build. */
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
            var replaced: ProgressIndex? = null
            // Only the latest history is published: an older build that ends late never replaces a newer index.
            val published = lock.withLock {
                val isLatest = latest?.of(history, records) == true
                if (isLatest) {
                    replaced = ready?.index
                    ready = Ready(history, records, index)
                }
                isLatest
            }
            val previous = replaced
            if (published && previous != null) queryLock.withLock { memo = memo.filter { it.slot.index !== previous } }
            index
        }
        return Build(history, records, index).also { latest = it }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val prewarming = AtomicReference<Job?>(null)

    /**
     * Builds the index of [history] and [records] in the background after [prewarmDelayMillis], and with it the
     * Records tab's default query ([RecordsQuery] in [zone]), so the tabs open with both ready (plan decision 9). A
     * newer prewarm cancels a pending one; a build that someone else waits for goes on.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun prewarm(history: List<WorkoutUi>, records: List<RecordUi>, zone: TimeZone) {
        val job = children.launch(start = CoroutineStart.LAZY) {
            delay(prewarmDelayMillis)
            val index = get(history, records)
            val query = RecordsQuery(zone = zone)
            // Typed: in a Unit block, an inferred V would be Unit and the memo would keep nothing useful.
            query<RecordsQuery, RecordsTimeline>(index, query) { RecordTimelines.query(it, query) }
        }
        prewarming.exchange(job)?.cancel()
        job.start()
    }

    /** A query's place in the memo: the index by identity (it has no `equals` of its own), the key by `equals`. */
    private data class Slot(val index: ProgressIndex, val key: Any)

    private class Memo(val slot: Slot, val value: Any)

    private val queryLock = Mutex()

    /** Most recently used first: written under [queryLock], read anywhere. */
    @Volatile
    private var memo: List<Memo> = emptyList()

    /** The queries being computed (under [queryLock]). */
    private val computing = HashMap<Slot, Deferred<Any>>()

    /** The result of [key] over [index], if it is already computed; never computes. */
    fun <K : Any, V : Any> peekQuery(index: ProgressIndex, key: K): V? {
        val slot = Slot(index, key)
        @Suppress("UNCHECKED_CAST")
        return memo.firstOrNull { it.slot == slot }?.value as V?
    }

    /**
     * The result of [key] over [index]: from the memo, or [compute]d once in [context] (concurrent callers of the same
     * key wait for that one computation, which ends and is kept even if they stop waiting). [key] must be a value
     * (a data class), equal for the same query; one key always means the same [compute].
     */
    suspend fun <K : Any, V : Any> query(index: ProgressIndex, key: K, compute: (ProgressIndex) -> V): V {
        val slot = Slot(index, key)
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
                        compute(index).also { value = it }
                    } finally {
                        withContext(NonCancellable) {
                            queryLock.withLock {
                                computing.remove(slot)
                                value?.let { done -> memo = (listOf(Memo(slot, done)) + memo.filter { it.slot != slot }).take(MEMO_SIZE) }
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
 * The [ProgressIndex] of [history] and [records]: in the same frame when the cache already has it, otherwise null
 * until the first build (off the main thread) ends. When the history changes, the previous index stays until the new
 * one is ready, so a screen never falls back to its skeleton. Without a [LocalProgressIndexCache], a cache of this
 * composition.
 */
@Composable
fun rememberProgressIndex(history: List<WorkoutUi>, records: List<RecordUi>): ProgressIndex? {
    val cache = progressIndexCache()
    var latest by remember(cache) { mutableStateOf<ProgressIndex?>(null) }
    val ready = cache.peek(history, records)
    LaunchedEffect(cache, history, records) {
        latest = cache.get(history, records)
    }
    return ready ?: latest
}

/**
 * The result of [key] over [index] ([ProgressIndexCache.query]): in the same frame when it is memoized, otherwise
 * null until the first result, computed off the main thread. A new index or key keeps the previous result until the
 * new one is ready. [compute] must not capture state that [key] does not name.
 */
@Composable
fun <K : Any, V : Any> rememberProgressQuery(index: ProgressIndex?, key: K, compute: (ProgressIndex) -> V): V? {
    val cache = progressIndexCache()
    val currentCompute by rememberUpdatedState(compute)
    var latest by remember(cache) { mutableStateOf<V?>(null) }
    val ready = index?.let { cache.peekQuery<K, V>(it, key) }
    LaunchedEffect(cache, index, key) {
        if (index == null) return@LaunchedEffect
        val computation = currentCompute
        latest = cache.query(index, key, computation)
    }
    return ready ?: latest
}

/** The provided cache, or one that lives as long as this composition. */
@Composable
private fun progressIndexCache(): ProgressIndexCache {
    val provided = LocalProgressIndexCache.current
    val scope = rememberCoroutineScope()
    val own = remember(scope, provided) { if (provided == null) ProgressIndexCache(scope) else null }
    return provided ?: checkNotNull(own)
}
