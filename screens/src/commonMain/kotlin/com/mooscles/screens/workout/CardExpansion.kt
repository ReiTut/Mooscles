package com.mooscles.screens.workout

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.ui.WorkoutExerciseUi
import kotlinx.coroutines.delay

/**
 * Which exercise cards of the active workout are open (spec D9). The current exercise is the
 * first with a pending set, and its card is open; the others are collapsed. A card the person
 * opened or closed by hand stays so: the advance never changes it.
 *
 * `expanded` holds each card's state as last decided (by the person or by [advance]); an entry
 * not in it (one just added) follows the current exercise. Since the advance writes `expanded`
 * only after `timing.cardAdvance`, the card whose last set was just done stays open meanwhile.
 */
object CardExpansion {
    /** The id of the first entry with a pending set; null when every set is done (or there is none). */
    fun current(exercises: List<WorkoutExerciseUi>): String? =
        exercises.firstOrNull { entry -> entry.sets.any { !it.completed } }?.id

    /** Whether the card of [entryId] is open. */
    fun isOpen(entryId: String, exercises: List<WorkoutExerciseUi>, expanded: Map<String, Boolean>, manual: Set<String>): Boolean =
        if (entryId in manual) expanded[entryId] == true else expanded[entryId] ?: (entryId == current(exercises))

    /**
     * The states after the advance: every card not toggled by hand is open only when it is the
     * current exercise; a card in [manual] keeps its state. Entries not in [exercises] are dropped.
     */
    fun advance(exercises: List<WorkoutExerciseUi>, expanded: Map<String, Boolean>, manual: Set<String>): Map<String, Boolean> {
        val current = current(exercises)
        return exercises.associate { entry -> entry.id to if (entry.id in manual) expanded[entry.id] == true else entry.id == current }
    }

    /**
     * The list index of the header of [entryId], with the cards laid out by [exerciseItems] from
     * index [first]: a collapsed card is one item, an open one its header, the column header, a
     * row per set and the footer. Null when the entry is not in [exercises].
     */
    fun headerIndex(
        entryId: String,
        exercises: List<WorkoutExerciseUi>,
        expanded: Map<String, Boolean>,
        manual: Set<String>,
        first: Int = 0,
    ): Int? {
        var index = first
        for (entry in exercises) {
            if (entry.id == entryId) return index
            index += itemCount(entry, isOpen(entry.id, exercises, expanded, manual))
        }
        return null
    }

    /**
     * The list index of the row of [setId], laid out as for [headerIndex]: the header, the column
     * header, then a row per set. A set whose card is collapsed has no row: its header's index.
     * Null when no entry of [exercises] has the set.
     */
    fun rowIndex(
        setId: String,
        exercises: List<WorkoutExerciseUi>,
        expanded: Map<String, Boolean>,
        manual: Set<String>,
        first: Int = 0,
    ): Int? {
        var index = first
        for (entry in exercises) {
            val open = isOpen(entry.id, exercises, expanded, manual)
            val position = entry.sets.indexOfFirst { it.id == setId }
            if (position >= 0) return if (open) index + 2 + position else index
            index += itemCount(entry, open)
        }
        return null
    }

    /** The number of list items [exerciseItems] gives [entry]. */
    internal fun itemCount(entry: WorkoutExerciseUi, open: Boolean): Int = if (open) entry.sets.size + 3 else 1

    /** [isOpen] with the states [screen] keeps. */
    internal fun isOpen(entryId: String, exercises: List<WorkoutExerciseUi>, screen: ActiveWorkoutScreenState): Boolean =
        isOpen(entryId, exercises, screen.expanded, screen.manual)
}

/**
 * The advance of D9, put on the screen once beside the list of [exercises] (laid out with
 * [exerciseItems] from index [firstIndex] of [listState]). When the current exercise changes (the
 * last pending set of one is done, a set of an earlier one is undone, an exercise is removed),
 * it waits `timing.cardAdvance` and then collapses the cards that are no longer current and opens
 * the new current one ([ActiveWorkoutScreenState.advanceCards]); the list items enter and move
 * with `spring.snappy` ([setRowItemMotion]). If that opened the new current card, the list scrolls
 * to its header. With reduced motion the wait stays (it is the time to see the check) and the
 * switch is instant: the items fade in `motion.fast` without sliding and the list jumps.
 *
 * The first run with exercises (the screen shown, or recreated) applies the advance at once,
 * without scrolling. While the list is empty (still loading) nothing is applied, so the restored
 * choices are kept.
 */
@Composable
internal fun CardAdvance(
    exercises: List<WorkoutExerciseUi>,
    screen: ActiveWorkoutScreenState,
    listState: LazyListState,
    firstIndex: Int = 0,
) {
    val current = CardExpansion.current(exercises)
    val latest by rememberUpdatedState(exercises)
    val reduced = MTheme.reducedMotion
    val wait = MTheme.timing.cardAdvance
    val started = remember(screen) { booleanArrayOf(false) }
    LaunchedEffect(screen, current) {
        // A workout still loading has no exercises: what was restored waits for them.
        if (latest.isEmpty()) return@LaunchedEffect
        val first = !started[0]
        started[0] = true
        // The wait is time to see the check, not motion: reduced motion keeps it.
        if (!first) delay(wait)
        val opened = screen.advanceCards(latest)
        val target = CardExpansion.current(latest)
        if (first || target == null || target !in opened) return@LaunchedEffect
        // Once the list has laid out the cards as they are now.
        withFrameNanos { }
        val index = CardExpansion.headerIndex(target, latest, screen.expanded, screen.manual, firstIndex) ?: return@LaunchedEffect
        if (reduced) listState.scrollToItem(index) else listState.animateScrollToItem(index)
    }
}

/**
 * Back to the set [setId] after a rest (spec "Expandido": it closes on its own "e volta para a
 * série seguinte"): the list, laid out from [firstIndex] as for [CardAdvance], scrolls to its row
 * unless the row is already whole on screen (a jump with [reduced] motion), and then the row takes
 * the focus ([ActiveWorkoutScreenState.rowFocus], its first control, never a field). In touch mode
 * the focus request does nothing, which is what is wanted; a set whose card is collapsed brings
 * its header into view, with no focus.
 */
internal suspend fun returnToSet(
    setId: String,
    exercises: List<WorkoutExerciseUi>,
    screen: ActiveWorkoutScreenState,
    listState: LazyListState,
    reduced: Boolean,
    firstIndex: Int = 0,
) {
    val index = CardExpansion.rowIndex(setId, exercises, screen.expanded, screen.manual, firstIndex) ?: return
    if (!listState.layoutInfo.showsWhole(index)) {
        if (reduced) listState.scrollToItem(index) else listState.animateScrollToItem(index)
    }
    // Once the list has composed the row where it now shows it.
    withFrameNanos { }
    screen.rowFocus[setId]?.requestFocus()
}

/** Whether the item at [index] is laid out wholly inside the viewport. */
private fun LazyListLayoutInfo.showsWhole(index: Int): Boolean {
    val item = visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset >= viewportStartOffset && item.offset + item.size <= viewportEndOffset
}
