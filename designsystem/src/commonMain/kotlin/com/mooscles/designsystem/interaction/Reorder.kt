package com.mooscles.designsystem.interaction

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.mooscles.designsystem.tokens.MoosclesElevation
import com.mooscles.designsystem.tokens.MoosclesColors
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_reorder_bottom
import com.mooscles.designsystem.resources.ds_reorder_down
import com.mooscles.designsystem.resources.ds_reorder_handle
import com.mooscles.designsystem.resources.ds_reorder_moved
import com.mooscles.designsystem.resources.ds_reorder_top
import com.mooscles.designsystem.resources.ds_reorder_up
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.ElevationSpec
import com.mooscles.designsystem.tokens.SpringTokens
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Test-facing: the elevation level a lifted item draws (`elevation.e3`); absent while it rests. */
val ReorderLiftElevation = SemanticsPropertyKey<ElevationSpec>("ReorderLiftElevation")

const val ReorderAnnouncerTag = "reorder-announcer"

/** What the announcer says after an accessibility move; [serial] tells two equal texts apart. */
internal class ReorderAnnouncement(val text: String, val serial: Int)

/** The tokens and haptics a drag uses, refreshed from the theme on every composition. */
internal class ReorderConfig(
    val haptics: MoosclesHaptics = NoHaptics,
    val reducedMotion: Boolean = false,
    val spring: SpringTokens = SpringTokens(1f, 1f),
    val edgeZonePx: Float = 0f,
    val scrollStepPx: Float = 0f,
    val echoHoldMs: Long = 0,
)

/** An order just written: shown while the keys are still [keysAtDrop] (the write's echo has not come back). */
private class Held<K>(val order: List<K>, val keysAtDrop: List<K>)

/**
 * Keeps [local]'s order for the keys still in [keys], drops the ones that left, and puts each new
 * key at the index it has in [keys]. The result is always a permutation of [keys].
 */
internal fun <K> reconcile(local: List<K>, keys: List<K>): List<K> {
    if (local == keys) return keys
    val present = keys.toHashSet()
    val result = local.filterTo(ArrayList(keys.size)) { it in present }
    val kept = result.toHashSet()
    for ((index, key) in keys.withIndex()) {
        if (key !in kept) result.add(minOf(index, result.size), key)
    }
    return result
}

/**
 * The state of a reorderable lazy list (spec D15): the order shown, the item being dragged and the
 * drag itself. Made by [rememberReorderState]; the list renders [order], puts [reorderItem] on each
 * item and a [ReorderHandle] in it (the handle carries the screen reader's four move actions), and
 * places one [ReorderAnnouncer] beside the list, outside it.
 *
 * Assumptions the drag relies on:
 * - each [K] is exactly one item of the lazy list, and that item's lazy key is the [K] itself (on
 *   Android the key must also fit in a `Bundle`);
 * - the reorderable items are contiguous: no other item (a footer, a set row, an item of zero
 *   height) sits between two of them during a drag. A list that shows more per entry (the routine
 *   editor's sets, plan decision 14) leaves only one item per entry while dragging.
 *

 * Rules: `onDrop` is called once, on release, only when the order changed; a cancelled gesture
 * writes nothing and gives the item back; keys that change during a drag are reconciled (keys that
 * left go, new keys come in at their index in `keys`) and the drop carries exactly the current keys;
 * the dragged key leaving ends the drag without a write. When the items change height during the
 * drag (plan decision 14: the editor collapses its cards on lift) the dragged item stays under the
 * finger: the list is scrolled to keep its slot there, and what scrolling cannot absorb is taken
 * out of the gesture, so a resize alone never reorders anything; once the finger has moved on (two
 * touch slops, net), the slot follows the item drawn under it again.
 */
@Stable
class ReorderState<K : Any> internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    private var keysState by mutableStateOf<List<K>>(emptyList())
    internal var onDrop: (List<K>) -> Unit = {}
    internal var onLift: (K) -> Unit = {}
    internal var config = ReorderConfig()

    private var dragOrder by mutableStateOf<List<K>?>(null)
    private var held by mutableStateOf<Held<K>?>(null)

    /** The item being dragged, or null. */
    var dragging: K? by mutableStateOf(null)
        private set

    /** The item gliding into its slot after a drop: drawn above the others until it lands. */
    internal var settling: Any? by mutableStateOf(null)
        private set

    internal var announcement: ReorderAnnouncement? by mutableStateOf(null)
        private set
    private var announcements = 0

    /**
     * The order to show: the keys, the drag's order while dragging (reconciled with the keys), and,
     * right after a drop, the dropped order until the keys change (at most `timing.reorderEchoHold`).
     */
    val order: List<K> by derivedStateOf {
        val keys = keysState
        val local = dragOrder
        val pending = held
        when {
            local != null -> reconcile(local, keys)
            pending != null && pending.keysAtDrop == keys -> pending.order
            else -> keys
        }
    }

    // The drag. Positions are in the lazy list's item-offset space (`LazyListItemInfo.offset`).
    private var session = 0
    private var fingerDelta by mutableFloatStateOf(0f)
    private var liftFinger = 0f
    private var grab = 0f
    private var logicalBase = 0f
    private var expectedTop: Float? = null
    private var swappedOn: LazyListLayoutInfo? = null
    private var seen: LazyListLayoutInfo? = null
    private var liftOrder: List<K> = emptyList()
    private var armedStart = false
    private var armedEnd = false
    private var slop = 0f

    /**
     * True while the slot is away from the drawn item by what a scroll could not absorb ([followSlot]); [adriftAt] is
     * the finger's move (`fingerDelta`) when it went adrift.
     */
    private var adrift = false
    private var adriftAt = 0f
    private var dragJob: Job? = null
    private var pin: PinnableContainer.PinnedHandle? = null

    /** Translations of items gliding to their new slot (neighbors of a swap, the dropped item). */
    private val offsets = mutableStateMapOf<Any, Float>()
    private val offsetJobs = HashMap<Any, Job>()
    internal val itemCoordinates = HashMap<Any, LayoutCoordinates>()

    internal fun updateKeys(keys: List<K>) {
        keysState = keys
    }

    /** After a composition with new keys: a drag whose item left ends, and a stale hold goes. */
    internal fun afterKeys() {
        val keys = keysState
        val current = dragging
        if (current != null && current !in keys) finish()
        val pending = held
        if (pending != null && pending.keysAtDrop != keys) held = null
    }

    private fun itemInfo(key: Any, info: LazyListLayoutInfo = listState.layoutInfo): LazyListItemInfo? =
        info.visibleItemsInfo.firstOrNull { it.key == key }

    private val finger: Float get() = liftFinger + fingerDelta

    private fun drawnTop(size: Int): Float = finger - grab.coerceIn(0f, max(0f, size - 1f))

    /** How far [key] is drawn from its slot: under the finger while dragged, gliding otherwise. */
    internal fun translationOf(key: Any): Float {
        if (key == dragging) {
            val item = itemInfo(key) ?: return 0f
            return drawnTop(item.size) - item.offset
        }
        return offsets[key] ?: 0f
    }

    internal fun isActive(id: Int): Boolean = id == session && dragging != null

    /**
     * True while [key] is lifted. Item content can use it to show the lift in its own surface (such
     * as `bg.overlay`, the dark theme's `elevation.3` tone); [reorderItem] already draws it.
     */
    fun isLifted(key: Any): Boolean = dragging == key

    /** Lifts [key], grabbed [grabInItem] px below its top. Returns the drag's id, or -1 when it cannot lift. */
    internal fun start(key: Any, grabInItem: Float, pinnable: PinnableContainer?, touchSlop: Float): Int {
        if (dragging != null) return -1
        val shown = order
        @Suppress("UNCHECKED_CAST")
        val typed = key as K
        if (typed !in shown) return -1
        val info = listState.layoutInfo
        val item = itemInfo(key, info) ?: return -1
        val gliding = offsets[key] ?: 0f
        offsetJobs.remove(key)?.cancel()
        offsets.remove(key)
        session++
        liftOrder = shown
        dragOrder = shown
        dragging = typed
        fingerDelta = 0f
        grab = grabInItem.coerceIn(0f, max(0f, item.size - 1f))
        logicalBase = item.offset + gliding
        liftFinger = logicalBase + grab
        expectedTop = item.offset.toFloat()
        swappedOn = null
        seen = info
        slop = touchSlop
        adrift = false
        adriftAt = 0f
        armedStart = liftFinger >= info.viewportStartOffset + config.edgeZonePx
        armedEnd = liftFinger <= info.viewportEndOffset - config.edgeZonePx
        pin = pinnable?.pin()
        config.haptics.perform(HapticEvent.LongPress)
        onLift(typed)
        dragJob = scope.launch {
            launch { snapshotFlow { listState.layoutInfo }.collect { onLayout(it) } }
            launch { snapshotFlow { scrollDirection() }.collectLatest { direction -> if (direction != 0) autoScroll(direction) } }
        }
        return session
    }

    internal fun drag(id: Int, dy: Float) {
        if (!isActive(id)) return
        fingerDelta += dy
        val info = listState.layoutInfo
        val y = finger
        if (!armedEnd && (y < info.viewportEndOffset - config.edgeZonePx || y - liftFinger > slop)) armedEnd = true
        if (!armedStart && (y > info.viewportStartOffset + config.edgeZonePx || liftFinger - y > slop)) armedStart = true
        checkSwap(info)
    }

    internal fun end(id: Int) {
        if (!isActive(id)) return
        val key = dragging ?: return
        val final = order
        val keys = keysState
        // Changed against what was shown at the lift (with the keys that came or went since).
        val changed = final != reconcile(liftOrder, keys)
        val item = itemInfo(key)
        val from = if (item != null) drawnTop(item.size) - item.offset else 0f
        if (changed) hold(final, keys)
        finish()
        if (!config.reducedMotion && from != 0f) {
            settling = key
            glide(key, from) { if (settling == key) settling = null }
        }
        if (changed) onDrop(final)
    }

    internal fun cancel(id: Int) {
        if (!isActive(id)) return
        keepPosition()
        finish()
    }

    /**
     * A lazy list keeps its first visible item where it is, by key: an order that changes back
     * (a cancel, a hold that ran out) would scroll the list. Keep the position by index instead.
     */
    private fun keepPosition() {
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    }

    private fun finish() {
        dragJob?.cancel()
        dragJob = null
        pin?.release()
        pin = null
        dragOrder = null
        dragging = null
        expectedTop = null
        swappedOn = null
        seen = null
        session++
    }

    private fun hold(order: List<K>, keys: List<K>) {
        if (order == keys) {
            held = null
            return
        }
        val pending = Held(order, keys)
        held = pending
        scope.launch {
            delay(config.echoHoldMs)
            if (held === pending) {
                keepPosition()
                held = null
            }
        }
    }

    /** Moves [key] to [to] for an accessibility action: writes, holds the new order and announces [said]. */
    internal fun move(key: K, to: Int, said: String): Boolean {
        if (dragging != null) return false
        val current = order
        val from = current.indexOf(key)
        if (from < 0 || to == from || to !in current.indices) return false
        val next = current.toMutableList().apply {
            removeAt(from)
            add(to, key)
        }
        hold(next, keysState)
        announcement = ReorderAnnouncement(said, ++announcements)
        onDrop(next)
        return true
    }

    private fun onLayout(info: LazyListLayoutInfo) = checkSwap(info)

    /**
     * Once per layout: when the dragged item's slot is not where the drag left it (items resized, a
     * key came or went), scroll the slot back under the finger, and take what the scroll cannot
     * absorb out of the gesture, so nothing swaps by itself.
     */
    private fun followSlot(info: LazyListLayoutInfo, item: LazyListItemInfo) {
        if (seen === info) return
        seen = info
        val expected = expectedTop
        expectedTop = if (expected != null && abs(item.offset - expected) > SAME_PX) {
            val shift = item.offset - expected
            val consumed = listState.dispatchRawDelta(shift)
            val rest = shift - consumed
            logicalBase += rest
            if (!adrift && abs(rest) > SAME_PX) {
                adrift = true
                adriftAt = fingerDelta
            }
            item.offset - consumed
        } else {
            item.offset.toFloat()
        }
    }

    /**
     * What [followSlot] took out of the gesture keeps the slot away from the item drawn under the finger, wherever a
     * resize (or a key that came or went) moved the slot and no scroll could bring it back:
     * - at a list's end, it can be more than the finger can still travel (the 2x editor, its cards collapsed: the
     *   slot hundreds of dp below a finger at the top edge), and the places between could never be reached;
     * - at a list's top, lifting a middle item collapses the ones above it: the slot moves up, the item stays drawn
     *   under the finger, below it.
     * Once the finger is [REJOIN_SLOPS] touch slops away from where it was when the slot went adrift (its net move,
     * as the arming of the auto-scroll counts it: a trembling finger never gets there), a drag and not the resize, the
     * logical position goes back under the finger. The swaps that follow, one per layout, each with its glide and its
     * haptic, bring the slot to where the item is drawn, in whichever direction that is (down the list in the second
     * case, even with the finger moving up): the drop is where the person sees the item. The dragged item itself does
     * not move ([drawnTop] does not depend on the logical position). Until then nothing swaps ([checkSwap]), so the
     * cascade only goes one way and no swap is undone.
     */
    private fun rejoin(item: LazyListItemInfo) {
        if (abs(fingerDelta - adriftAt) <= REJOIN_SLOPS * slop) return
        logicalBase = drawnTop(item.size) - fingerDelta
        adrift = false
    }

    private fun checkSwap(info: LazyListLayoutInfo) {
        val key = dragging ?: return
        val item = itemInfo(key, info)
        if (item == null) {
            expectedTop = null
            return
        }
        followSlot(info, item)
        // After followSlot, so a shift of this very layout is taken out before the rejoin, not after it.
        if (adrift) rejoin(item)
        // No swap while adrift: the logical position is then the slot's, not the drawn item's, and the rejoin would
        // undo a swap made from it (with the 18 dp slop of iOS, lifting a middle item at the list's top swapped it up
        // one place before the rejoin took it back down).
        if (adrift || swappedOn === info) return
        val current = order
        val index = current.indexOf(key)
        if (index < 0) return
        val logical = logicalBase + fingerDelta
        val displacement = logical - item.offset
        if (displacement > 0f) {
            val next = current.getOrNull(index + 1) ?: return
            val other = itemInfo(next, info) ?: return
            // Down when the item's bottom passes the middle of the next one (and the hysteresis).
            if (logical + item.size > other.offset + other.size / 2f + hysteresis) {
                swap(current, index, index + 1, info, other, newTop = other.offset + other.size - item.size, otherNewTop = item.offset)
            }
        } else if (displacement < 0f) {
            val previous = current.getOrNull(index - 1) ?: return
            val other = itemInfo(previous, info) ?: return
            // Up when the item's top passes the middle of the previous one (and the hysteresis).
            if (logical < other.offset + other.size / 2f - hysteresis) {
                swap(current, index, index - 1, info, other, newTop = other.offset, otherNewTop = item.offset + item.size - other.size)
            }
        }
    }

    /**
     * How far past a neighbor's middle the item must go to swap: half the touch slop. With no gap
     * between the items, the way back is the same middle, and a finger trembling there would swap
     * back and forth.
     */
    private val hysteresis: Float get() = slop / 2f

    private fun swap(current: List<K>, from: Int, to: Int, info: LazyListLayoutInfo, other: LazyListItemInfo, newTop: Int, otherNewTop: Int) {
        val key = current[from]
        val first = info.visibleItemsInfo.firstOrNull()
        if (first != null && (first.key == key || first.key == other.key)) {
            // Moving the first visible item would scroll the list (see keepPosition).
            keepPosition()
        }
        dragOrder = current.toMutableList().apply {
            this[from] = this[to]
            this[to] = key
        }
        expectedTop = newTop.toFloat()
        swappedOn = info
        if (!config.reducedMotion) glide(other.key, (other.offset - otherNewTop).toFloat())
        config.haptics.perform(HapticEvent.StepperStep)
    }

    /** Draws [key] [by] px from its new slot, then lets it spring in (`spring.snappy`). */
    private fun glide(key: Any, by: Float, done: () -> Unit = {}) {
        val start = (offsets[key] ?: 0f) + by
        offsetJobs.remove(key)?.cancel()
        offsets[key] = start
        val spec = config.spring
        offsetJobs[key] = scope.launch {
            animate(start, 0f, animationSpec = spring(dampingRatio = spec.dampingRatio, stiffness = spec.stiffness)) { value, _ ->
                offsets[key] = value
            }
            offsets.remove(key)
            offsetJobs.remove(key)
            done()
        }
    }

    private fun scrollDirection(): Int {
        if (dragging == null) return 0
        val info = listState.layoutInfo
        val y = finger
        return when {
            armedEnd && y > info.viewportEndOffset - config.edgeZonePx && listState.canScrollForward -> 1
            armedStart && y < info.viewportStartOffset + config.edgeZonePx && listState.canScrollBackward -> -1
            else -> 0
        }
    }

    /** Scrolls `sizes.reorderScrollStep` a frame toward [direction] until the list ends or the finger leaves the edge. */
    private suspend fun autoScroll(direction: Int) {
        while (true) {
            withFrameNanos { }
            val consumed = listState.dispatchRawDelta(direction * config.scrollStepPx)
            if (consumed == 0f) break
            expectedTop = expectedTop?.minus(consumed)
        }
    }

    private companion object {
        /** Less than this many px between where the slot is and where it should be is the same place. */
        const val SAME_PX = 0.5f

        /** How many touch slops away (net) the finger goes before a slot left away from it comes back under it ([rejoin]). */
        const val REJOIN_SLOPS = 2f
    }
}

/**
 * Remembers the [ReorderState] of a lazy list showing [keys] (spec D15). [onDrop] gets the new order
 * once, when the finger lifts or an accessibility action moves an item; [onLift] runs when an item is
 * lifted (the editor collapses its cards there, plan decision 14).
 */
@Composable
fun <K : Any> rememberReorderState(
    listState: LazyListState,
    keys: List<K>,
    onDrop: (List<K>) -> Unit,
    onLift: (K) -> Unit = {},
): ReorderState<K> {
    val scope = rememberCoroutineScope()
    val state = remember(listState) { ReorderState<K>(listState, scope) }
    // In composition, so `order` follows the keys in this very frame (an item gone from the caller's
    // data must not be asked for).
    state.updateKeys(keys)
    val haptics = MTheme.haptics
    val reduced = MTheme.reducedMotion
    val spring = MTheme.motion.springSnappy
    val sizes = MTheme.sizes
    val hold = MTheme.timing.reorderEchoHold
    val density = LocalDensity.current
    val edge = with(density) { sizes.reorderEdgeZone.toPx() }
    val step = with(density) { sizes.reorderScrollStep.toPx() }
    SideEffect {
        state.onDrop = onDrop
        state.onLift = onLift
        state.config = ReorderConfig(haptics, reduced, spring, edge, step, hold)
        state.afterKeys()
    }
    return state
}

/**
 * How a lifted item shows `elevation.e3`: in the light theme its shadow; in the dark, where that
 * level has no shadow, a `border.strong` outline (a 3:1 line on every surface it can be on).
 */
internal class ReorderLiftLook(val shadow: Dp?, val outline: Color?)

internal fun reorderLiftLook(colors: MoosclesColors, elevation: MoosclesElevation, dark: Boolean): ReorderLiftLook =
    if (dark) ReorderLiftLook(shadow = null, outline = colors.borderStrong) else ReorderLiftLook(shadow = elevation.e3.shadowLight, outline = null)

/**
 * Goes on each item of the list (its outermost modifier, before any padding the lift should not
 * scale): the item follows the finger while dragged, drawn above the others with `elevation.e3` (a
 * shadow in the light theme, a `border.strong` outline in the dark, see [ReorderState.isLifted]) and
 * scaled to `motion.dragLiftScale` (only the elevation with reduced motion); its neighbors glide to
 * their new place with `spring.snappy` (instantly with reduced motion). Do not also give the item a
 * placement animation (`animateItem`'s `placementSpec`): this one does it.
 */
fun Modifier.reorderItem(state: ReorderState<*>, key: Any): Modifier = composed {
    val lifted = state.dragging == key
    val raised = lifted || state.settling == key
    val reduced = MTheme.reducedMotion
    val motion = MTheme.motion
    val elevation = MTheme.elevation.e3
    val look = reorderLiftLook(MTheme.colors, MTheme.elevation, MTheme.isDark)
    val outlineWidth = MTheme.sizes.borderSelected
    val shape = RoundedCornerShape(MTheme.radius.lg)
    val scale by animateFloatAsState(
        targetValue = if (lifted && !reduced) motion.dragLiftScale else 1f,
        animationSpec = tween(durationMillis = motion.fast, easing = motion.easeOut),
        label = "reorderLift",
    )
    DisposableEffect(state, key) { onDispose { state.itemCoordinates.remove(key) } }
    zIndex(if (raised) 1f else 0f)
        .graphicsLayer {
            translationY = state.translationOf(key)
            scaleX = scale
            scaleY = scale
            val shadow = look.shadow
            if (lifted && shadow != null) {
                shadowElevation = shadow.toPx()
                this.shape = shape
                clip = false
            }
        }
        .drawWithContent {
            drawContent()
            val outline = look.outline
            if (lifted && outline != null) {
                // Inside the edge, as Modifier.border draws it.
                val stroke = outlineWidth.toPx()
                translate(left = stroke / 2f, top = stroke / 2f) {
                    val inner = Size(size.width - stroke, size.height - stroke)
                    drawOutline(shape.createOutline(inner, layoutDirection, this), outline, style = Stroke(stroke))
                }
            }
        }
        .onPlaced { state.itemCoordinates[key] = it }
        .semantics { if (lifted) this[ReorderLiftElevation] = elevation }
}

/**
 * Goes on the drag handle of [key]'s item: a long press lifts the item (the `LongPress` haptic) and a
 * drag moves it (the `StepperStep` haptic at each position crossed); within `sizes.reorderEdgeZone` of
 * an edge the list scrolls along. A quick swipe on the handle scrolls the list instead of lifting.
 * The item stays composed while dragged, even scrolled out of view.
 */
fun Modifier.reorderHandle(state: ReorderState<*>, key: Any): Modifier = composed {
    val pinnable by rememberUpdatedState(LocalPinnableContainer.current)
    val holder = remember { HandleCoordinates() }
    onPlaced { holder.coordinates = it }
        .pointerInput(state, key) {
            var id = -1
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val item = state.itemCoordinates[key]
                    val handle = holder.coordinates
                    val grab = if (item != null && handle != null && item.isAttached && handle.isAttached) {
                        item.localPositionOf(handle, offset).y
                    } else {
                        offset.y
                    }
                    id = state.start(key, grab, pinnable, viewConfiguration.touchSlop)
                },
                onDragEnd = { state.end(id) },
                onDragCancel = { state.cancel(id) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(id, amount.y)
                },
            )
        }
}

private class HandleCoordinates {
    var coordinates: LayoutCoordinates? = null
}

/**
 * The alternatives to dragging (WCAG 2.5.7): "Move up", "Move down", "Move to top" and "Move to
 * bottom" as accessibility actions of the node it is on, only the possible ones. Each calls `onDrop`
 * with the new order and has the [ReorderAnnouncer] say "[name] moved to position 2 of 5".
 * [ReorderHandle] already carries them. Put them elsewhere only on a node a screen reader focuses
 * (one that merges its descendants, or a leaf with a description): actions on any other node are
 * out of reach. It sets the node's custom actions: a node with other custom actions should list
 * these with its own.
 */
@Composable
fun <K : Any> Modifier.reorderActions(state: ReorderState<K>, key: K, name: String): Modifier {
    val order = state.order
    val index = order.indexOf(key)
    val count = order.size
    val up = stringResource(Res.string.ds_reorder_up)
    val down = stringResource(Res.string.ds_reorder_down)
    val top = stringResource(Res.string.ds_reorder_top)
    val bottom = stringResource(Res.string.ds_reorder_bottom)
    // Positions are 1-based: up lands on `index`, down on `index + 2`.
    val saidUp = stringResource(Res.string.ds_reorder_moved, name, index, count)
    val saidDown = stringResource(Res.string.ds_reorder_moved, name, index + 2, count)
    val saidTop = stringResource(Res.string.ds_reorder_moved, name, 1, count)
    val saidBottom = stringResource(Res.string.ds_reorder_moved, name, count, count)
    if (index < 0) return this
    val last = count - 1
    return semantics {
        customActions = buildList {
            if (index > 0) add(CustomAccessibilityAction(up) { state.move(key, index - 1, saidUp) })
            if (index < last) add(CustomAccessibilityAction(down) { state.move(key, index + 1, saidDown) })
            if (index > 0) add(CustomAccessibilityAction(top) { state.move(key, 0, saidTop) })
            if (index < last) add(CustomAccessibilityAction(bottom) { state.move(key, last, saidBottom) })
        }
    }
}

/**
 * The moves of [reorderActions] for a menu's buttons: the function takes the index to move [key] to and does what
 * the screen reader's action does, the same hold of the new order and the same announcement ("[name] moved to
 * position 2 of 5"). It returns false, and writes nothing, for the index it already has, one out of range, or while
 * an item is being dragged.
 */
@Composable
fun <K : Any> reorderMover(state: ReorderState<K>, key: K, name: String): (to: Int) -> Boolean {
    val count = state.order.size
    val saids = List(count) { stringResource(Res.string.ds_reorder_moved, name, it + 1, count) }
    return { to -> to in saids.indices && state.move(key, to, saids[to]) }
}

/** "Reorder Bench press" / "Reordenar Supino reto": the label of [name]'s drag handle. */
@Composable
fun reorderHandleLabel(name: String): String = stringResource(Res.string.ds_reorder_handle, name)

/**
 * The drag handle of [key]'s item: the grip icon in a 48 dp target. For a screen reader it is a stop
 * of its own, "Reorder [name]", that carries the four move actions ([reorderActions]) in place of
 * the drag.
 */
@Composable
fun <K : Any> ReorderHandle(state: ReorderState<K>, key: K, name: String, modifier: Modifier = Modifier) {
    val label = reorderHandleLabel(name)
    Box(
        modifier
            .size(MTheme.targets.min)
            .reorderHandle(state, key)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .reorderActions(state, key, name),
        contentAlignment = Alignment.Center,
    ) {
        MIcon(MIcons.GripVertical, null, size = MTheme.sizes.icon24, tint = MTheme.colors.textSecondary)
    }
}

/**
 * The 1 dp polite live region that says where [reorderActions] moved an item. Place one beside the
 * list. Between announcements it is empty (cleared after `timing.timerAnnounceHold`), so a screen
 * reader skips it.
 */
@Composable
fun ReorderAnnouncer(state: ReorderState<*>, modifier: Modifier = Modifier) {
    val said = state.announcement
    var cleared by remember { mutableIntStateOf(-1) }
    val hold = MTheme.timing.timerAnnounceHold
    LaunchedEffect(said?.serial) {
        if (said != null) {
            delay(hold)
            cleared = said.serial
        }
    }
    val text = if (said == null || said.serial == cleared) "" else said.text
    Box(
        modifier
            .size(MTheme.sizes.border)
            .testTag(ReorderAnnouncerTag)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (text.isNotEmpty()) contentDescription = text
            },
    )
}
