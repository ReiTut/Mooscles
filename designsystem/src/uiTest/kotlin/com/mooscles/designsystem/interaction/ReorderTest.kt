package com.mooscles.designsystem.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.assertFalse
import com.mooscles.designsystem.tokens.MoosclesColors
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.INJECTS_DRAG
import com.mooscles.designsystem.INJECTS_TOUCH_CANCEL
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.SheetTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_reorder_bottom
import com.mooscles.designsystem.resources.ds_reorder_down
import com.mooscles.designsystem.resources.ds_reorder_moved
import com.mooscles.designsystem.resources.ds_reorder_top
import com.mooscles.designsystem.resources.ds_reorder_up
import com.mooscles.designsystem.tokens.MoosclesElevation
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

private const val LIST = "reorder-list"
private val INITIAL = List(12) { "item-$it" }

/** Three heights, by the item's number: 56, 96 and 144 dp (a row, a card, a tall card). */
private val HEIGHTS = listOf(56.dp, 96.dp, 144.dp)
private val LIST_HEIGHT = 360.dp
private const val LONG_PRESS_MARGIN = 100L

private fun handle(key: String) = "$key-handle"

private fun heightOf(key: String): Dp = HEIGHTS[(key.removePrefix("item-").toIntOrNull() ?: 0) % HEIGHTS.size]

/** The caller of the reorder state: its keys, what it was given, and how its items size. */
private class Harness {
    var keys by mutableStateOf(INITIAL)
    var collapsed by mutableStateOf(false)
    val drops = mutableListOf<List<String>>()
    val lifts = mutableListOf<String>()

    /** False: a drop is recorded but the keys stay (a write whose echo has not come back). */
    var applyDrops = true

    /** True: lifting collapses every item to 40 dp (plan decision 14). */
    var collapseOnLift = false
    lateinit var state: ReorderState<String>
    lateinit var list: LazyListState

    /** The menus' moves ([reorderMover]) of each composed item, as of the last composition. */
    val movers = mutableMapOf<String, (Int) -> Boolean>()

    /** Each order the list was laid out with, once per change: one swap per layout shows as one transposition each. */
    val shown = mutableListOf<List<String>>()

    fun heightFor(key: String): Dp = if (collapsed) 40.dp else heightOf(key)
}

/**
 * A lazy list of items of three heights, each with a 48 dp handle at its top start, the four
 * accessibility actions, and the announcer beside the list. [width] narrows the list.
 */
@Composable
private fun ReorderList(h: Harness, height: Dp = LIST_HEIGHT, width: Dp? = null) {
    val list = rememberLazyListState()
    h.list = list
    val state = rememberReorderState(
        list,
        h.keys,
        onDrop = { order ->
            h.drops += order
            if (h.applyDrops) h.keys = order
        },
        onLift = { key ->
            h.lifts += key
            if (h.collapseOnLift) h.collapsed = true
        },
    )
    h.state = state
    Box(Modifier.height(height).then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())) {
        LazyColumn(Modifier.fillMaxSize().testTag(LIST), state = list) {
            // The list runs this again, in its measure, for each new order it lays out with, sometimes more than once
            // in a frame: recorded here, each order shown is one layout's (a composition of this function sees only
            // the last of a frame).
            val order = state.order
            if (h.shown.lastOrNull() != order) h.shown += order
            items(order, key = { it }) { key ->
                Row(
                    Modifier
                        // Inset, so a lifted (scaled) item stays within the list's bounds.
                        .padding(horizontal = 16.dp)
                        .reorderItem(state, key)
                        .fillMaxWidth()
                        .height(h.heightFor(key))
                        .testTag(key),
                ) {
                    ReorderHandle(state, key, key, Modifier.testTag(handle(key)))
                    h.movers[key] = reorderMover(state, key, key)
                    BasicText(key)
                }
            }
        }
        ReorderAnnouncer(state)
    }
}

class ReorderTest {
    /** A failed language test must not leak Portuguese into the next test. */
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /** Puts a finger on [key]'s handle and holds it past the long-press timeout: the item lifts. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.lift(key: String) {
        var longPress = 0L
        onNodeWithTag(handle(key), useUnmergedTree = true).performTouchInput {
            longPress = viewConfiguration.longPressTimeoutMillis
            down(center)
        }
        mainClock.advanceTimeBy(longPress + LONG_PRESS_MARGIN)
        waitForIdle()
    }

    /** Moves the finger [dy] in [steps] moves, letting the list settle after each. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.drag(dy: Dp, steps: Int = 20, afterEach: () -> Unit = {}) {
        val step = with(density) { dy.toPx() } / steps
        repeat(steps) {
            onNodeWithTag(LIST).performTouchInput { moveBy(Offset(0f, step)) }
            waitForIdle()
            afterEach()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.release() {
        onNodeWithTag(LIST).performTouchInput { up() }
        waitForIdle()
    }

    private fun moved(vararg order: Int): List<String> {
        val front = order.map { "item-$it" }
        return front + INITIAL.filterNot { it in front }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dragAcrossItemsOfDifferentHeightsReordersWithoutJumps() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        val haptics = RecordingHaptics()
        withTheme(haptics = haptics) { ReorderList(h) }
        waitForIdle()
        lift("item-0")
        val stepPx = with(density) { 10.dp.toPx() }
        // Not measured at rest: grown to 102% at the top of the list, its top is clipped by the list.
        var top: Float? = null
        // 56 dp down past the 96 dp item, then past the 144 dp one: the item follows the finger.
        drag(200.dp) {
            val now = with(density) { onNodeWithTag("item-0").getBoundsInRoot().top.toPx() }
            val before = top
            if (before != null) assertTrue(abs(now - before - stepPx) < 1.5f, "the dragged item jumped: $before -> $now, step $stepPx")
            top = now
        }
        assertEquals(moved(1, 2, 0), h.state.order)
        // One step per position crossed: no swap back and forth.
        assertEquals(listOf(HapticEvent.LongPress, HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events)
        release()
        assertEquals(listOf(moved(1, 2, 0)), h.drops)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dropWritesOnceWithTheNewOrder() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        withTheme { ReorderList(h) }
        waitForIdle()
        lift("item-2")
        drag((-120).dp)
        assertEquals(emptyList(), h.drops)
        release()
        assertEquals(listOf(moved(0, 2, 1)), h.drops)
        assertEquals(moved(0, 2, 1), h.keys)
        assertEquals(h.keys, h.state.order)
        assertNull(h.state.dragging)
        // The new order is laid out: item-2 now sits right under item-0.
        val first = onNodeWithTag("item-0").getBoundsInRoot()
        val second = onNodeWithTag("item-2").getBoundsInRoot()
        assertTrue(abs((second.top - first.bottom).value) < 1f, "item-2 must follow item-0: ${first.bottom} / ${second.top}")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nothingIsWrittenWhileDragging() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        withTheme { ReorderList(h) }
        waitForIdle()
        lift("item-0")
        drag(200.dp)
        assertEquals("item-0", h.state.dragging)
        assertEquals(moved(1, 2, 0), h.state.order, "the shown order follows the drag")
        assertEquals(INITIAL, h.keys)
        assertEquals(emptyList(), h.drops)
        release()
        assertEquals(1, h.drops.size)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun cancelledGestureRestoresAndWritesNothing() = runComposeUiTest {
        if (!INJECTS_DRAG || !INJECTS_TOUCH_CANCEL) return@runComposeUiTest
        val h = Harness()
        withTheme { ReorderList(h) }
        waitForIdle()
        val listTop = onNodeWithTag(LIST).getBoundsInRoot().top
        lift("item-0")
        drag(200.dp)
        onNodeWithTag(LIST).performTouchInput { cancel() }
        waitForIdle()
        assertEquals(emptyList(), h.drops)
        assertNull(h.state.dragging)
        assertEquals(INITIAL, h.state.order)
        assertTrue(abs((onNodeWithTag("item-0").getBoundsInRoot().top - listTop).value) < 1f, "item-0 is back on top")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun autoScrollsNearTheEdges() {
        // Down: the finger held in the bottom 64 dp scrolls the list, and the item goes along.
        runComposeUiTest {
            if (!INJECTS_DRAG) return@runComposeUiTest
            val h = Harness()
            withTheme { ReorderList(h) }
            waitForIdle()
            lift("item-0")
            // The handle's center is 24 dp down; 300 dp more is 36 dp above the list's bottom.
            drag(300.dp, steps = 30)
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertTrue(h.list.firstVisibleItemIndex > 0, "the list scrolled: ${h.list.firstVisibleItemIndex}")
            val index = h.state.order.indexOf("item-0")
            assertTrue(index > 4, "item-0 went along with the scroll: $index")
            release()
            assertEquals(1, h.drops.size)
            assertEquals(index, h.drops.single().indexOf("item-0"))
        }
        // Up: from the end of the list, the top 64 dp scroll it back.
        runComposeUiTest {
            if (!INJECTS_DRAG) return@runComposeUiTest
            val h = Harness()
            withTheme { ReorderList(h) }
            waitForIdle()
            runOnIdle { h.list.requestScrollToItem(INITIAL.lastIndex) }
            waitForIdle()
            val start = h.list.firstVisibleItemIndex
            assertTrue(start > 0)
            lift("item-11")
            drag((-220).dp, steps = 22)
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            assertTrue(h.list.firstVisibleItemIndex < start, "the list scrolled up: $start -> ${h.list.firstVisibleItemIndex}")
            val index = h.state.order.indexOf("item-11")
            assertTrue(index < INITIAL.lastIndex - 3, "item-11 went along with the scroll: $index")
            release()
            assertEquals(1, h.drops.size)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aFlingOnTheHandleScrollsWithoutLifting() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        val haptics = RecordingHaptics()
        withTheme(haptics = haptics) { ReorderList(h) }
        waitForIdle()
        onNodeWithTag(handle("item-3"), useUnmergedTree = true).performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 200.dp.toPx(), durationMillis = 150)
        }
        waitForIdle()
        assertTrue(h.list.firstVisibleItemIndex > 0 || h.list.firstVisibleItemScrollOffset > 0, "the list scrolled")
        assertEquals(emptyList(), haptics.events)
        assertEquals(emptyList(), h.lifts)
        assertEquals(emptyList(), h.drops)
        assertNull(h.state.dragging)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun accessibilityActionsMoveAndAnnounce() = runComposeUiTest {
        // Four items (352 dp): all composed in the 360 dp list.
        val four = INITIAL.take(4)
        val h = Harness().apply { keys = four }
        withTheme { ProvideAppLocale("en") { ReorderList(h) } }
        waitForIdle()
        val up = getString(Res.string.ds_reorder_up)
        val down = getString(Res.string.ds_reorder_down)
        val top = getString(Res.string.ds_reorder_top)
        val bottom = getString(Res.string.ds_reorder_bottom)
        assertEquals("Move up", up)

        // The node a screen reader stops on: the handle, "Reorder <name>", merging its descendants.
        fun stop(key: String): SemanticsNode {
            val node = onNode(hasContentDescription("Reorder $key")).fetchSemanticsNode()
            assertTrue(node.config.isMergingSemanticsOfDescendants, "$key: the handle is a stop of its own")
            return node
        }

        fun labels(key: String) = stop(key).config[SemanticsActions.CustomActions].map { it.label }

        fun perform(key: String, label: String) {
            val action = stop(key).config[SemanticsActions.CustomActions].single { it.label == label }
            runOnIdle { assertTrue(action.action()) }
            waitForIdle()
        }

        suspend fun assertAnnounced(position: Int) {
            val announcer = onNodeWithTag(ReorderAnnouncerTag, useUnmergedTree = true)
            announcer.assertContentDescriptionEquals(getString(Res.string.ds_reorder_moved, "item-0", position, four.size))
            assertEquals(LiveRegionMode.Polite, announcer.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        }

        // The item's own node, which a screen reader does not stop on, has none.
        assertNull(onNodeWithTag("item-0").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions))
        // The first has no "up" nor "to top"; a middle one has all four; the last no "down" nor "to bottom".
        assertEquals(listOf(down, bottom), labels("item-0"))
        assertEquals(listOf(up, down, top, bottom), labels("item-2"))
        assertEquals(listOf(up, top), labels("item-3"))

        perform("item-0", down)
        assertEquals(listOf("item-1", "item-0", "item-2", "item-3"), h.drops.last())
        assertAnnounced(2)
        assertEquals(listOf(up, down, top, bottom), labels("item-0"))

        perform("item-0", bottom)
        assertEquals(listOf("item-1", "item-2", "item-3", "item-0"), h.drops.last())
        assertAnnounced(4)
        assertEquals(listOf(up, top), labels("item-0"))

        perform("item-0", up)
        assertEquals(listOf("item-1", "item-2", "item-0", "item-3"), h.drops.last())
        assertAnnounced(3)

        perform("item-0", top)
        assertEquals(four, h.drops.last())
        assertAnnounced(1)
        assertEquals(4, h.drops.size)
        assertEquals(h.drops.last(), h.state.order)
    }

    /**
     * The menus' moves ([reorderMover]) do what the screen reader's actions do: the new order is
     * held while its write has not come back, the write is made once, the polite live region says
     * where the item went, and a move to the index it has, out of range or during a drag is refused
     * and writes nothing.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reorderMoverHoldsTheOrderAndAnnouncesThroughTheLiveRegion() = runComposeUiTest {
        val four = INITIAL.take(4)
        val h = Harness().apply {
            keys = four
            // The write has not come back: only the held order shows the move.
            applyDrops = false
        }
        withTheme { ProvideAppLocale("en") { ReorderList(h) } }
        waitForIdle()
        fun mover(key: String) = checkNotNull(h.movers[key]) { "no mover for $key" }

        assertTrue(mover("item-0")(2), "a move to another place is made")
        waitForIdle()
        assertEquals(listOf("item-1", "item-2", "item-0", "item-3"), h.state.order, "the new order is held")
        assertEquals(listOf(listOf("item-1", "item-2", "item-0", "item-3")), h.drops, "one write")
        val announcer = onNodeWithTag(ReorderAnnouncerTag, useUnmergedTree = true)
        announcer.assertContentDescriptionEquals(getString(Res.string.ds_reorder_moved, "item-0", 3, four.size))
        assertEquals(LiveRegionMode.Polite, announcer.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])

        // Refused, with nothing written: the place it has, out of range both ways.
        assertEquals(false, mover("item-0")(2))
        assertEquals(false, mover("item-0")(-1))
        assertEquals(false, mover("item-0")(four.size))
        assertEquals(1, h.drops.size)
        assertEquals(listOf("item-1", "item-2", "item-0", "item-3"), h.state.order)

        // A second move goes from the held order, and is announced in its turn.
        assertTrue(mover("item-3")(0))
        waitForIdle()
        assertEquals(listOf("item-3", "item-1", "item-2", "item-0"), h.state.order)
        assertEquals(listOf("item-3", "item-1", "item-2", "item-0"), h.drops.last())
        onNodeWithTag(ReorderAnnouncerTag, useUnmergedTree = true)
            .assertContentDescriptionEquals(getString(Res.string.ds_reorder_moved, "item-3", 1, four.size))
    }

    /** While an item is being dragged, a menu's move is refused. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reorderMoverRefusesWhileDragging() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        withTheme { ReorderList(h) }
        waitForIdle()
        lift("item-0")
        assertTrue(h.state.dragging != null, "the item is lifted")
        assertEquals(false, checkNotNull(h.movers["item-1"])(0))
        assertEquals(emptyList(), h.drops)
        release()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun liftHasElevationAndScaleAndReducedMotionKeepsOnlyElevation() {
        for (reduced in listOf(false, true)) {
            runComposeUiTest {
                if (!INJECTS_DRAG) return@runComposeUiTest
                val h = Harness()
                withTheme(reducedMotion = reduced) { ReorderList(h, width = 300.dp) }
                waitForIdle()
                val resting = onNodeWithTag("item-1").getBoundsInRoot().let { it.right - it.left }
                assertNull(onNodeWithTag("item-1").fetchSemanticsNode().config.getOrNull(ReorderLiftElevation))
                lift("item-1")
                val node = onNodeWithTag("item-1").fetchSemanticsNode()
                assertEquals(MoosclesElevation().e3, node.config[ReorderLiftElevation], "reduced=$reduced")
                val lifted = onNodeWithTag("item-1").getBoundsInRoot().let { it.right - it.left }
                val expected = if (reduced) resting else resting * 1.02f
                assertTrue(abs((lifted - expected).value) < 0.5f, "reduced=$reduced: $resting -> $lifted, expected $expected")
                release()
                assertNull(onNodeWithTag("item-1").fetchSemanticsNode().config.getOrNull(ReorderLiftElevation))
                val dropped = onNodeWithTag("item-1").getBoundsInRoot().let { it.right - it.left }
                assertTrue(abs((dropped - resting).value) < 0.5f, "back to its size: $dropped")
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hapticOnLiftAndOnEachPositionCrossed() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        val haptics = RecordingHaptics()
        withTheme(haptics = haptics) { ReorderList(h) }
        waitForIdle()
        lift("item-0")
        assertEquals(listOf(HapticEvent.LongPress), haptics.events)
        drag(200.dp)
        assertEquals(listOf(HapticEvent.LongPress, HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events)
        // Back up 100 dp: it crosses the 144 dp item again, not the 96 dp one.
        drag((-100).dp, steps = 10)
        assertEquals(moved(1, 0), h.state.order)
        assertEquals(listOf(HapticEvent.LongPress) + List(3) { HapticEvent.StepperStep }, haptics.events)
        release()
        assertEquals(4, haptics.events.size, "the drop adds no haptic")
    }

    /** Inside a sheet (its own window, through MOverlayWindow): the drag reorders and the sheet stays put. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun worksInsideABottomSheet() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        var dismissed = 0
        withTheme { MBottomSheet(onDismiss = { dismissed++ }) { ReorderList(h, height = 240.dp) } }
        waitForIdle()
        val sheetTop = onNodeWithTag(SheetTag).getBoundsInRoot().top
        lift("item-0")
        drag(120.dp, steps = 12) {
            val now = onNodeWithTag(SheetTag).getBoundsInRoot().top
            assertTrue(abs((now - sheetTop).value) < 1f, "the sheet moved: $sheetTop -> $now")
        }
        assertEquals(moved(1, 0), h.state.order)
        release()
        assertEquals(listOf(moved(1, 0)), h.drops)
        assertEquals(0, dismissed)
        assertTrue(abs((onNodeWithTag(SheetTag).getBoundsInRoot().top - sheetTop).value) < 1f, "the sheet stayed open")
    }

    /** Plan decision 14: lifting collapses the items; the lifted one stays under the finger and nothing moves by itself. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun itemsResizingOnLiftKeepTheDraggedItemUnderTheFinger() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness().apply { collapseOnLift = true }
        withTheme { ReorderList(h) }
        waitForIdle()
        val finger = onNodeWithTag(handle("item-3"), useUnmergedTree = true).getBoundsInRoot().let { (it.top + it.bottom) / 2 }
        lift("item-3")
        assertTrue(h.collapsed)
        val lifted = onNodeWithTag("item-3").getBoundsInRoot()
        assertTrue(finger > lifted.top && finger < lifted.bottom, "finger at $finger, item at ${lifted.top}..${lifted.bottom}")
        drag(8.dp, steps = 4)
        val moved = onNodeWithTag("item-3").getBoundsInRoot()
        val now = finger + 8.dp
        assertTrue(now > moved.top && now < moved.bottom, "finger at $now, item at ${moved.top}..${moved.bottom}")
        assertEquals(INITIAL, h.state.order, "the resize alone reorders nothing")
        release()
        assertEquals(emptyList(), h.drops)
    }

    /**
     * The 2x routine flow (investigation of 2026-10-30): the list scrolled to the handle of an item not composed yet,
     * which puts that item first, at the top edge; lifting collapses everything until the whole list fits, and the
     * list goes back to its top. The slot is then 200 dp below the finger and no scroll can bring it back, while the
     * item is drawn under the finger, over the first item. Dragged up from there and dropped, it goes first: where it
     * is drawn, not five places down where its slot was left.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSlotTheScrollCannotBringBackFollowsTheFingerOnceItMoves() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        // Eight 144 dp items (1152 dp in the 360 dp list); collapsed, 40 dp each, they all fit.
        val tall = List(8) { "item-${3 * it + 2}" }
        val dragged = tall[5]
        val h = Harness().apply {
            keys = tall
            collapseOnLift = true
        }
        withTheme { ReorderList(h) }
        waitForIdle()
        onNodeWithTag(LIST).performScrollToNode(hasTestTag(handle(dragged)))
        waitForIdle()
        assertEquals(5, h.list.firstVisibleItemIndex, "$dragged is the first item shown (offset ${h.list.firstVisibleItemScrollOffset})")
        lift(dragged)
        assertTrue(h.collapsed)
        assertEquals(0, h.list.firstVisibleItemIndex, "the collapsed list is back at its top")
        assertEquals(0, h.list.firstVisibleItemScrollOffset, "the collapsed list is back at its top")
        // Decision 14: the resize alone reorders nothing.
        assertEquals(tall, h.state.order)
        val listTop = onNodeWithTag(LIST).getBoundsInRoot().top
        val first = onNodeWithTag(tall[0]).getBoundsInRoot()
        val lifted = onNodeWithTag(dragged).getBoundsInRoot()
        assertTrue(lifted.top < first.bottom, "$dragged is drawn over ${tall[0]}: ${lifted.top - listTop} / ${first.top - listTop}..${first.bottom - listTop}")
        // 40 dp: past the rejoin's two touch slops on both platforms while the slop is at most 20 dp.
        drag((-40).dp, steps = 20)
        assertEquals(dragged, h.state.order.first(), "dragged up over the first item: ${h.state.order}")
        assertEquals(emptyList(), h.drops, "nothing is written while dragging")
        release()
        assertEquals(listOf(listOf(dragged) + (tall - dragged)), h.drops)
    }

    /**
     * Review I1: in the same geometry, a finger held still but trembling (2 px back and forth, many times: a long
     * path, almost no net move) does not bring the slot to the finger. Nothing reorders, and the release writes nothing.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aTremblingFingerDoesNotPullAFarSlotToIt() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val tall = List(8) { "item-${3 * it + 2}" }
        val dragged = tall[5]
        val h = Harness().apply {
            keys = tall
            collapseOnLift = true
        }
        withTheme { ReorderList(h) }
        waitForIdle()
        onNodeWithTag(LIST).performScrollToNode(hasTestTag(handle(dragged)))
        waitForIdle()
        lift(dragged)
        assertTrue(h.collapsed)
        fun move(px: Float) {
            onNodeWithTag(LIST).performTouchInput { moveBy(Offset(0f, px)) }
            waitForIdle()
        }
        // 120 px of path, at most 2 px away from where the finger went down.
        repeat(30) {
            move(-2f)
            move(2f)
        }
        assertEquals(tall, h.state.order, "a tremble reordered")
        release()
        assertEquals(emptyList(), h.drops)
    }

    /**
     * Review I2 (ruled correct): at the list's top, lifting a middle item collapses the ones above it, so its slot
     * moves up and no scroll can bring it back; the item stays drawn under the finger, below where its slot went.
     * Nothing swaps before; once the finger has moved on two touch slops (here up), the slot goes to where the item
     * is drawn, which is down the list, one swap per layout, only down, with the dragged item never jumping. Up from there, it goes back up the
     * same way, and dropped above the first item it goes first.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSlotLeftAboveTheFingerGoesToWhereTheItemIsDrawnOneSwapAtATime() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness().apply { collapseOnLift = true }
        val haptics = RecordingHaptics()
        withTheme(haptics = haptics) { ReorderList(h) }
        waitForIdle()
        var slop = 0f
        onNodeWithTag(LIST).performTouchInput { slop = viewConfiguration.touchSlop }
        lift("item-3")
        assertTrue(h.collapsed)
        assertEquals(INITIAL, h.state.order, "the resize alone reorders nothing")
        val shownAtLift = h.shown.size
        fun shownSinceLift() = h.shown.drop(shownAtLift)
        fun swaps() = haptics.events.count { it == HapticEvent.StepperStep }
        fun topPx() = with(density) { onNodeWithTag("item-3").getBoundsInRoot().top.toPx() }
        fun move(px: Float) {
            val before = topPx()
            onNodeWithTag(LIST).performTouchInput { moveBy(Offset(0f, px)) }
            waitForIdle()
            val now = topPx()
            assertTrue(abs(now - before - px) < 1.5f, "the dragged item jumped: $before -> $now, step $px")
        }

        /** item-3's index in each order shown, from [from] on: one place per order, toward [step] only (no swap undone). */
        fun assertOneWay(orders: List<List<String>>, from: Int, step: Int) {
            var index = from
            for (order in orders) {
                val next = order.indexOf("item-3")
                assertEquals(index + step, next, "item-3 must move one place ${if (step > 0) "down" else "up"} per order shown: $orders")
                index = next
            }
        }
        // Three slops up, in half slops: past two slops (the fifth move) the slot rejoins the item.
        repeat(6) { move(-slop / 2f) }
        val down = h.state.order.indexOf("item-3")
        assertTrue(down > 3, "the slot went down to where item-3 is drawn: ${h.state.order}")
        val cascade = shownSinceLift()
        // Down only, from index 3: with the finger moving up, nothing swapped up first to be undone.
        assertOneWay(cascade, from = 3, step = 1)
        assertEquals(cascade.size, swaps(), "one haptic per swap shown: $cascade")
        // Where it is drawn: its slot is within half a collapsed item (and the hysteresis) of it.
        val drawn = onNodeWithTag("item-3").getBoundsInRoot().top
        val slotTop = onNodeWithTag(h.state.order[down - 1]).getBoundsInRoot().bottom
        assertTrue(abs((drawn - slotTop).value) < 20f + with(density) { slop.toDp().value } / 2f, "drawn at $drawn, slot at $slotTop")
        // Up in 2 dp moves until it is drawn 4 dp or less below the list's top: above the first item's middle, and not
        // yet clipped by the list (its bounds would stop following the finger).
        val climb = with(density) { 2.dp.toPx() }
        val stop = with(density) { onNodeWithTag(LIST).getBoundsInRoot().top.toPx() + 4.dp.toPx() }
        while (topPx() > stop) move(-climb)
        assertEquals("item-3", h.state.order.first(), "${h.state.order}")
        assertEquals(emptyList(), h.drops, "nothing is written while dragging")
        // Up only, one place per order shown, from where the cascade left it.
        assertOneWay(shownSinceLift().drop(cascade.size), from = down, step = -1)
        assertEquals(shownSinceLift().size, swaps(), "one haptic per swap shown: ${shownSinceLift()}")
        // Every order shown since the lift is one swap of two neighbors away from the one before: no jump of several places.
        val orders = listOf(INITIAL) + shownSinceLift()
        for ((a, b) in orders.zipWithNext()) {
            val changed = a.indices.filter { a[it] != b[it] }
            assertTrue(changed.size == 2 && changed[1] == changed[0] + 1, "more than one swap in a layout: $a -> $b (all: $orders)")
        }
        release()
        assertEquals(listOf(moved(3)), h.drops)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun listChangeDuringDragDropsWithTheCurrentKeys() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        withTheme { ReorderList(h) }
        waitForIdle()
        lift("item-1")
        drag(120.dp, steps = 12)
        assertEquals(moved(0, 2, 1), h.state.order)
        // An echo of another write: item-5 leaves and a new item comes in where item-5 was.
        val echo = INITIAL.toMutableList().apply { this[5] = "item-new" }
        h.keys = echo
        waitForIdle()
        val expected = listOf("item-0", "item-2", "item-1", "item-3", "item-4", "item-new") + INITIAL.drop(6)
        assertEquals(expected, h.state.order)
        assertEquals("item-1", h.state.dragging)
        drag(4.dp, steps = 2)
        release()
        assertEquals(listOf(expected), h.drops)
        assertEquals(echo.toSet(), h.drops.single().toSet())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun draggedItemThatDisappearsEndsTheDragWithoutAWrite() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        withTheme { ReorderList(h) }
        waitForIdle()
        lift("item-1")
        drag(120.dp, steps = 12)
        h.keys = INITIAL - "item-1"
        waitForIdle()
        assertNull(h.state.dragging)
        assertEquals(INITIAL - "item-1", h.state.order)
        onNodeWithTag("item-1").assertDoesNotExist()
        // The finger is still down and keeps moving: nothing follows it, and lifting it writes nothing.
        onNodeWithTag(LIST).performTouchInput { moveBy(Offset(0f, 40.dp.toPx())) }
        waitForIdle()
        assertEquals(INITIAL - "item-1", h.state.order)
        release()
        assertEquals(emptyList(), h.drops)
    }

    /** A write whose echo has not come back: the dropped order shows until the keys change, at most `timing.reorderEchoHold`. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun droppedOrderWaitsForTheKeys() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness().apply { applyDrops = false }
        withTheme { ReorderList(h) }
        waitForIdle()
        lift("item-2")
        drag((-120).dp)
        release()
        assertEquals(listOf(moved(0, 2, 1)), h.drops)
        assertEquals(INITIAL, h.keys)
        assertEquals(moved(0, 2, 1), h.state.order, "no jump back while the write is on its way")
        mainClock.advanceTimeBy(MoosclesTiming().reorderEchoHold + LONG_PRESS_MARGIN)
        waitForIdle()
        assertEquals(INITIAL, h.state.order, "a write that never echoes gives the keys back")
    }

    /**
     * Review I2: the lift shows in both themes, also with reduced motion (no scale): the light
     * theme's shadow around the item, the dark theme's `border.strong` outline on its edge.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun liftIsVisibleInBothThemes() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                if (!INJECTS_DRAG) return@runComposeUiTest
                val h = Harness()
                withTheme(dark = dark, reducedMotion = true) { ReorderList(h, width = 300.dp) }
                waitForIdle()
                val before = onNodeWithTag(LIST).captureToImage().toPixelMap()
                lift("item-1")
                assertTrue(h.state.isLifted("item-1"))
                val after = onNodeWithTag(LIST).captureToImage().toPixelMap()
                var changed = 0
                for (x in 0 until after.width) for (y in 0 until after.height) if (before[x, y] != after[x, y]) changed++
                assertTrue(changed > after.width, "dark=$dark: the lift must show, $changed pixels changed")
                if (dark) {
                    // A pixel just inside the item's left edge, halfway down, is the outline.
                    val list = onNodeWithTag(LIST).getBoundsInRoot()
                    val item = onNodeWithTag("item-1").getBoundsInRoot()
                    val x = with(density) { (item.left - list.left).toPx() + 0.5f }.toInt()
                    val y = with(density) { ((item.top + item.bottom) / 2 - list.top).toPx() }.toInt()
                    assertEquals(MoosclesColors.Dark.borderStrong, after[x, y], "the outline at ($x, $y)")
                }
                release()
                assertFalse(h.state.isLifted("item-1"))
            }
        }
    }

    /** Review M3: a finger trembling on a neighbor's middle (items with no gap) does not swap back and forth. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aTremblingFingerOnTheThresholdSwapsAtMostOnce() = runComposeUiTest {
        if (!INJECTS_DRAG) return@runComposeUiTest
        val h = Harness()
        val haptics = RecordingHaptics()
        withTheme(haptics = haptics) { ReorderList(h) }
        waitForIdle()
        lift("item-0")
        fun swaps() = haptics.events.count { it == HapticEvent.StepperStep }
        fun move(px: Float) {
            onNodeWithTag(LIST).performTouchInput { moveBy(Offset(0f, px)) }
            waitForIdle()
        }
        // item-1 is 96 dp: around 48 dp down item-0's bottom reaches its middle. Find the exact
        // point a pixel at a time: the first swap.
        drag(40.dp, steps = 8)
        var steps = 0
        while (swaps() == 0 && steps < 80) {
            move(1f)
            steps++
        }
        assertEquals(1, swaps(), "it swaps once past the middle")
        // Trembling there, 2 px each way, must not swap back and forth.
        repeat(10) {
            move(-2f)
            move(2f)
        }
        assertEquals(1, swaps(), "swapped back and forth on the threshold")
        // A clear move still swaps.
        drag(16.dp, steps = 4)
        assertEquals(moved(1, 0), h.state.order)
        release()
        assertEquals(listOf(moved(1, 0)), h.drops)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun handleLabelNamesTheItem() {
        for ((tag, expected) in listOf("en" to "Reorder Bench press", "pt-BR" to "Reordenar Bench press")) {
            runComposeUiTest {
                val h = Harness().apply { keys = listOf("Bench press") }
                withTheme {
                    ProvideAppLocale(tag) {
                        Box {
                            ReorderList(h)
                            BasicText(reorderHandleLabel("Supino reto"))
                        }
                    }
                }
                waitForIdle()
                val node = onNodeWithTag(handle("Bench press"), useUnmergedTree = true)
                node.assertContentDescriptionEquals(expected)
                val bounds = node.getBoundsInRoot()
                val min = MoosclesTargets().min
                assertTrue(bounds.right - bounds.left >= min && bounds.bottom - bounds.top >= min, "a 48 dp target: $bounds")
                val portuguese = if (tag == "en") "Reorder Supino reto" else "Reordenar Supino reto"
                onNodeWithText(portuguese).assertExists()
            }
        }
    }
}
