package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import com.mooscles.designsystem.components.NumericInput
import com.mooscles.designsystem.components.SetRowCells
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetField
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi

/**
 * The field of a set row being edited. [keyboard] is true while the field has the keyboard (a
 * second tap, the keyboard button or a screen reader focusing the field), and [error] when the
 * row is in edit because it was refused (a check without reps). [stepperFocus] asks the field's
 * stepper to take the focus, as when the edit comes from a screen reader's action (D21); it is not
 * saved.
 */
data class SetEditTarget(
    val setId: String,
    val field: SetField,
    val keyboard: Boolean = false,
    val error: Boolean = false,
    val stepperFocus: Boolean = false,
)

/**
 * What the active workout screen keeps on its own, by the real ids of sets and entries, and
 * survives recreation ([rememberActiveWorkoutScreenState]):
 *
 * - [cleared]: the (set, field) pairs the person emptied on purpose, which get no suggestion again
 *   this session (spec D10);
 * - [optimistic]: the done state a check asked for and the database has not echoed yet, by set
 *   id; the disc draws it at once and it goes away when the state arrives equal, or when the
 *   screen drops it because the write failed ([dropOptimistic], [dropAllOptimistic]). It is not
 *   saved: it only bridges one echo, and after a recreation the database is the truth;
 * - [editing]: the row and field being edited, if any;
 * - [drafts]: what the person typed or stepped in a field, by (set, field), until the database
 *   holds the same value. A draft is the text as entered ("82,5", "80.0", an invalid "82,"), and
 *   the field shows it instead of the saved value. With it, by the same key, the values written
 *   since it was entered and not yet seen back ([echo]), so a late echo of an older write cannot
 *   erase a newer draft, and the unit the load drafts are written in ([useUnit]);
 * - [lastRemoved]: the set removed last, whose "Undo" toast is up ([UndoRemovalToast]);
 * - [typeSheetFor]: the set whose type sheet is open ([SetTypeSheetHost]);
 * - [expanded] and [manual]: which exercise cards are open, by entry id, and which of them the
 *   person opened or closed by hand ([CardExpansion], spec D9);
 * - [checkedHere] and [announced]: the sets checked on this screen whose completion has not come
 *   back yet, and the best record announced per category ([RecordReaction], spec D17).
 */
@Stable
class ActiveWorkoutScreenState(
    cleared: Set<Pair<String, SetField>> = emptySet(),
    optimistic: Map<String, Boolean> = emptyMap(),
    editing: SetEditTarget? = null,
    drafts: Map<Pair<String, SetField>, String> = emptyMap(),
    sent: Map<Pair<String, SetField>, List<Double>> = emptyMap(),
    draftsPounds: Boolean? = null,
    lastRemoved: RemovedSet? = null,
    typeSheetFor: String? = null,
    expanded: Map<String, Boolean> = emptyMap(),
    manual: Set<String> = emptySet(),
    checkedHere: Set<String> = emptySet(),
    announced: Map<String, RecordBest> = emptyMap(),
) {
    var cleared: Set<Pair<String, SetField>> by mutableStateOf(cleared)
        internal set
    var optimistic: Map<String, Boolean> by mutableStateOf(optimistic)
        internal set
    var editing: SetEditTarget? by mutableStateOf(editing)
        internal set
    var drafts: Map<Pair<String, SetField>, String> by mutableStateOf(drafts)
        internal set

    /**
     * The set removed last, with what "Undo" puts back, while its toast is up. Only this one can
     * be undone (D13): another removal replaces it, [closeUndo] (finishing, discarding) ends it, and
     * so does [closeUndoFor] when its exercise is swapped or removed.
     */
    var lastRemoved: RemovedSet? by mutableStateOf(lastRemoved)
        internal set

    /** The id of the set whose type sheet is open, if any ([openTypeSheet]). */
    var typeSheetFor: String? by mutableStateOf(typeSheetFor)
        internal set

    /**
     * Whether each exercise card is open, by entry id, as last decided: by the person
     * ([toggleCard]) or by the advance to the next exercise ([advanceCards]). An entry not in it
     * follows the current exercise ([CardExpansion.isOpen]).
     */
    var expanded: Map<String, Boolean> by mutableStateOf(expanded)
        internal set

    /** The entries whose card the person opened or closed by hand: the advance never changes them (D9). */
    var manual: Set<String> by mutableStateOf(manual)
        internal set

    /**
     * The ids of the sets the person checked on this screen (a tap, a swipe) whose completion the
     * state has not shown yet. The first emission with the set done consumes it ([settleRecords]):
     * only then can its record be celebrated. A record already in the state when the screen opened
     * (a resumed workout, a restarted app), inherited or kept through an edit is on no set here.
     */
    var checkedHere: Set<String> by mutableStateOf(checkedHere)
        internal set

    /**
     * The best record announced this session in each category ([RecordAnnouncer.category]): a
     * record is announced only when it strictly beats it, so none plays twice.
     */
    var announced: Map<String, RecordBest> by mutableStateOf(announced)
        internal set

    /** Notes that the person checked [setId] here, so its completion, once seen, may be celebrated. */
    internal fun checkedHere(setId: String) {
        if (setId !in checkedHere) checkedHere = checkedHere + setId
    }

    /** The person unchecked [setId] (or it went): a completion still on its way is not celebrated. */
    internal fun uncheckedHere(setId: String) {
        if (setId in checkedHere) checkedHere = checkedHere - setId
    }

    /** Takes [step]: its checks are consumed and its best records kept. */
    internal fun settleRecords(step: RecordStep) {
        if (step.consumed.isNotEmpty()) checkedHere = checkedHere - step.consumed
        if (step.announced != announced) announced = step.announced
    }

    /** Opens or closes the card of [entryId], shown [open] now, by the person's hand. */
    internal fun toggleCard(entryId: String, open: Boolean) {
        manual = manual + entryId
        expanded = expanded + (entryId to !open)
    }

    /**
     * Applies the advance of D9 to [exercises] ([CardExpansion.advance]) and forgets the entries
     * no longer in the workout. Returns the entries it opened: closed before, open now.
     */
    internal fun advanceCards(exercises: List<WorkoutExerciseUi>): Set<String> {
        // No exercises is a workout still loading (after a process death, say), not one emptied:
        // what was restored stays until the exercises arrive.
        if (exercises.isEmpty()) return emptySet()
        val before = exercises.filter { CardExpansion.isOpen(it.id, exercises, expanded, manual) }.mapTo(HashSet()) { it.id }
        val ids = exercises.mapTo(HashSet()) { it.id }
        if (manual.any { it !in ids }) manual = manual.filterTo(HashSet()) { it in ids }
        val next = CardExpansion.advance(exercises, expanded, manual)
        if (next != expanded) expanded = next
        return next.filterValues { it }.keys - before
    }

    /**
     * Where the cells of the first set row of each exercise sat in its last layout, by entry id,
     * for the column titles of the card ([SetRowColumns]). Not saved: the rows report it again.
     */
    internal val columns = mutableStateMapOf<String, SetRowCells>()

    /** Keeps [cells] as the layout of the first row of [entryId], when it changed. */
    internal fun reportColumns(entryId: String, cells: SetRowCells) {
        if (columns[entryId] != cells) columns[entryId] = cells
    }

    /** Opens the type sheet of [setId], as a tap on the set's number does. */
    fun openTypeSheet(setId: String) {
        typeSheetFor = setId
    }

    /** Closes the "Undo" toast of the last removal, if it is up, so the removal can no longer be undone. */
    fun closeUndo() {
        lastRemoved = null
    }

    /**
     * [closeUndo] when the set removed last was in the exercise [entryId], as that exercise is
     * swapped or removed: its set has nowhere to go back to (the entry now holds another
     * exercise, or is gone), so "Undo" must not put it there.
     */
    fun closeUndoFor(entryId: String) {
        if (lastRemoved?.entryId == entryId) lastRemoved = null
    }

    /**
     * Removes [removed]'s set: forgets everything kept for it ([forget]), so an undo brings back
     * the values of the snapshot and no older draft, keeps [removed] for "Undo" (replacing, and so
     * closing, the toast of the previous removal) and sends `removeSet`.
     */
    internal fun removeSet(removed: RemovedSet, actions: MoosclesActions) {
        forget(removed.set.id)
        lastRemoved = removed
        actions.removeSet(removed.set.id)
    }

    /** Forgets the drafts, awaited writes, cleared fields, optimistic state, edit, type sheet and pending check of [setId]. */
    internal fun forget(setId: String) {
        if (drafts.keys.any { it.first == setId }) drafts = drafts.filterKeys { it.first != setId }
        if (sent.keys.any { it.first == setId }) sent = sent.filterKeys { it.first != setId }
        if (cleared.any { it.first == setId }) cleared = cleared.filterTo(HashSet()) { it.first != setId }
        if (setId in optimistic) optimistic = optimistic - setId
        if (editing?.setId == setId) editing = null
        if (typeSheetFor == setId) typeSheetFor = null
        uncheckedHere(setId)
    }

    /**
     * The values (kg or reps) written for a field while its draft was held, oldest first, until
     * the database shows them. Snapshot state: a row reads it while composing the value of a
     * draft (the exact kg of a converted draft, the value standing for an invalid one).
     */
    internal var sent: Map<Pair<String, SetField>, List<Double>> by mutableStateOf(sent)
        private set

    /** Counts a screen reader's requests to edit a field, so repeating one moves the focus to the stepper again (D21). */
    internal var stepperFocusRequests: Int by mutableIntStateOf(0)
        private set

    /** Puts [field] of [setId] in edit from a screen reader's action, keeping the row's error, and focuses its stepper. */
    internal fun editFromReader(setId: String, field: SetField) {
        val error = editing?.takeIf { it.setId == setId && it.field == field }?.error == true
        editing = SetEditTarget(setId, field, error = error, stepperFocus = true)
        stepperFocusRequests++
    }

    /** The unit the load drafts are written in; null until a row first reports it. */
    internal var draftsPounds: Boolean? = draftsPounds
        private set

    /** Where each composed row (and its edit panel) is, for [endsSetEditingOnOutsideTap]. Not state. */
    internal val rowAreas = HashMap<String, LayoutCoordinates>()

    /**
     * The focus of each composed row, so the screen can hand the keyboard's focus to the next set
     * when the rest timer closes after the rest ([WorkoutTimerLayer]). Not state.
     */
    internal val rowFocus = HashMap<String, FocusRequester>()

    /** Where the editing scope is, for the same. Not state. */
    internal var scopeArea: LayoutCoordinates? = null

    /** Marks [field] of [setId] as emptied on purpose. */
    internal fun clear(setId: String, field: SetField) {
        if ((setId to field) !in cleared) cleared = cleared + (setId to field)
    }

    /** Draws [setId] as [completed] until the state says the same. */
    internal fun expect(setId: String, completed: Boolean) {
        optimistic = optimistic + (setId to completed)
    }

    /** Drops the optimistic value of [setId] once the state's [completed] matches it. */
    internal fun settle(setId: String, completed: Boolean) {
        if (optimistic[setId] == completed) optimistic = optimistic - setId
    }

    /** Whether [set] is drawn as done: the optimistic value while there is one, the database's otherwise. */
    fun completedOf(set: SetUi): Boolean = optimistic[set.id] ?: set.completed

    /**
     * Forgets the optimistic value of [setId], so its row shows the database's done state again.
     * For a write that failed: no echo will come to settle it.
     */
    fun dropOptimistic(setId: String) {
        if (setId in optimistic) optimistic = optimistic - setId
    }

    /**
     * [dropOptimistic] for every set, as when an error arrives and the screen cannot tell which
     * write failed. It also forgets the writes still awaited ([dropPendingWrites]): a failed write
     * sends no echo either.
     */
    fun dropAllOptimistic() {
        if (optimistic.isNotEmpty()) optimistic = emptyMap()
        dropPendingWrites()
    }

    /**
     * Forgets every value written and not yet seen back, for a write that failed or was lost
     * (Task 19 calls it with [dropAllOptimistic] when an error arrives). The drafts stay, so the
     * field still shows what the person entered; with nothing awaited, the database's value is
     * what stands, and the next step or key writes that draft again instead of taking it as
     * already sent.
     */
    fun dropPendingWrites() {
        if (sent.isNotEmpty()) sent = emptyMap()
    }

    /** Holds [text] as the draft of [field] of [setId]. */
    internal fun type(setId: String, field: SetField, text: String) {
        val key = setId to field
        if (drafts[key] != text) drafts = drafts + (key to text)
    }

    /**
     * Forgets the draft of [field] of [setId], so the field shows the saved value; the writes
     * still awaited stay, so their late echoes are still recognized.
     */
    internal fun dropDraft(setId: String, field: SetField) {
        val key = setId to field
        if (key in drafts) drafts = drafts - key
    }

    /** Notes that [value] was written for [field] of [setId]; its echo will be awaited. */
    internal fun sent(setId: String, field: SetField, value: Double) {
        val key = setId to field
        sent = sent + (key to sent[key].orEmpty() + value)
    }

    /** The value the database will hold for [field] once every write is done: the last one sent, or [saved]. */
    internal fun expected(setId: String, field: SetField, saved: Double): Double = sent[setId to field]?.lastOrNull() ?: saved

    /**
     * The database now holds [saved] for [field] of [setId] (it held [previous] before, if known).
     * [draftValue] is the value of the field's draft, null when there is none or it is invalid.
     *
     * Writes are applied in order, and an emission may skip some, so [saved] is the result of one
     * of the writes still awaited: the first one equal to it, which acknowledges it and the older
     * ones. Once nothing is awaited and the draft has the saved value (in any form: "80.0", "082",
     * "82,5"), the draft goes and the field shows the saved value. A value nobody here wrote,
     * with writes in flight, is an older state and changes nothing. One that arrives with nothing
     * in flight is a change from elsewhere: it shows unless the field holds something the person
     * entered that was not written (an invalid text), which is never lost; a draft that only
     * repeated the old value gives way.
     */
    internal fun echo(setId: String, field: SetField, saved: Double, previous: Double?, draftValue: Double?) {
        val key = setId to field
        val pending = sent[key].orEmpty()
        val at = pending.indexOf(saved)
        if (at >= 0) {
            val left = pending.drop(at + 1)
            sent = if (left.isEmpty()) sent - key else sent + (key to left)
            if (left.isEmpty() && draftValue == saved && key in drafts) drafts = drafts - key
        } else if (pending.isEmpty() && previous != null && saved != previous && draftValue == previous && key in drafts) {
            drafts = drafts - key
        }
    }

    /**
     * When a row leaves the edit: a draft with the saved value goes, if the last write sent is the
     * saved one too. It covers echoes the database folded into one ("8", "80", "8" seen as one
     * "8") and a draft that never needed a write ("80.0" over a saved 80).
     */
    internal fun settleDraft(setId: String, field: SetField, saved: Double, draftValue: Double?) {
        val key = setId to field
        val pending = sent[key].orEmpty()
        if (key in drafts && draftValue == saved && (pending.isEmpty() || pending.last() == saved)) {
            drafts = drafts - key
            if (key in sent) sent = sent - key
        }
    }

    /**
     * The load drafts are now shown in [pounds]. Each valid one is rewritten in the new unit from
     * its exact kg: the value written for it when there is one ([text] of it in the old unit is the
     * draft), the typed number otherwise. [text] writes a kg value in a unit as a field shows it.
     * An invalid draft has no unit and stays as it is.
     */
    internal fun useUnit(pounds: Boolean, text: (kg: Double, pounds: Boolean) -> String) {
        val before = draftsPounds
        draftsPounds = pounds
        if (before == null || before == pounds) return
        drafts = drafts.mapValues { (key, draft) ->
            if (key.second != SetField.LOAD || draft.isEmpty()) return@mapValues draft
            val kg = sent[key]?.lastOrNull { text(it, before) == draft }
                ?: NumericInput.parseDecimal(draft, max = Double.MAX_VALUE)?.let { WeightUnits.toKg(it, before) }
            if (kg == null) draft else text(kg, pounds)
        }
    }

    companion object {
        /**
         * Saves the fields as lists of strings, booleans and numbers, which every platform can
         * keep: a cleared pair as "FIELD:setId" (the field name has no colon; an id may), the edit
         * target as [setId, field, keyboard, error], a draft as [field, setId, text] and its
         * awaited writes as [field, setId, values], the last removal as [entryId, position, number,
         * setId, reps, weightKg, completed, warmup, assistance], the type sheet's set id, the open
         * cards as [entryId, open], the entries toggled by hand, the sets checked here and the best
         * records announced as [category, weightKg, reps].
         * [optimistic] is left out on purpose: a write lost with the process must not come back as
         * a full disc. So is the removed set's record: the record is the presentation's, computed
         * again once the set is back.
         */
        val Saver: Saver<ActiveWorkoutScreenState, Any> = listSaver<ActiveWorkoutScreenState, Any?>(
            save = { state ->
                listOf(
                    ArrayList(state.cleared.map { (id, field) -> "${field.name}:$id" }),
                    state.editing?.let { arrayListOf<Any>(it.setId, it.field.name, it.keyboard, it.error) },
                    ArrayList(state.drafts.map { (key, text) -> arrayListOf<Any>(key.second.name, key.first, text) }),
                    ArrayList(state.sent.map { (key, values) -> arrayListOf<Any>(key.second.name, key.first, ArrayList(values)) }),
                    state.draftsPounds,
                    state.lastRemoved?.let {
                        arrayListOf<Any>(
                            it.entryId, it.position, it.number,
                            it.set.id, it.set.reps, it.set.weightKg, it.set.completed, it.set.warmup, it.set.assistance,
                        )
                    },
                    state.typeSheetFor,
                    ArrayList(state.expanded.map { (id, open) -> arrayListOf<Any>(id, open) }),
                    ArrayList(state.manual),
                    ArrayList(state.checkedHere),
                    ArrayList(state.announced.map { (category, best) -> arrayListOf<Any>(category, best.weightKg, best.reps) }),
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                val cleared = (saved[0] as List<String>).map { it.substringAfter(':') to SetField.valueOf(it.substringBefore(':')) }.toSet()
                @Suppress("UNCHECKED_CAST")
                val editing = (saved[1] as List<Any>?)?.let {
                    SetEditTarget(it[0] as String, SetField.valueOf(it[1] as String), it[2] as Boolean, it[3] as Boolean)
                }
                @Suppress("UNCHECKED_CAST")
                val drafts = (saved.getOrNull(2) as List<List<Any>>?).orEmpty()
                    .associate { (it[1] as String to SetField.valueOf(it[0] as String)) to it[2] as String }
                @Suppress("UNCHECKED_CAST")
                val sent = (saved.getOrNull(3) as List<List<Any>>?).orEmpty()
                    .associate { (it[1] as String to SetField.valueOf(it[0] as String)) to (it[2] as List<Double>).toList() }
                @Suppress("UNCHECKED_CAST")
                val lastRemoved = (saved.getOrNull(5) as List<Any>?)?.let {
                    RemovedSet(
                        entryId = it[0] as String,
                        position = it[1] as Int,
                        set = SetUi(
                            id = it[3] as String, reps = it[4] as Int, weightKg = it[5] as Double,
                            completed = it[6] as Boolean, warmup = it[7] as Boolean, assistance = it[8] as Boolean,
                        ),
                        number = it[2] as Int,
                    )
                }
                @Suppress("UNCHECKED_CAST")
                val expanded = (saved.getOrNull(7) as List<List<Any>>?).orEmpty().associate { it[0] as String to it[1] as Boolean }
                @Suppress("UNCHECKED_CAST")
                val manual = (saved.getOrNull(8) as List<String>?).orEmpty().toSet()
                @Suppress("UNCHECKED_CAST")
                val checkedHere = (saved.getOrNull(9) as List<String>?).orEmpty().toSet()
                val announced = (saved.getOrNull(10) as List<*>?).orEmpty().filterIsInstance<List<*>>()
                    .associate { it[0] as String to RecordBest(it[1] as Double, it[2] as Int) }
                ActiveWorkoutScreenState(
                    cleared = cleared,
                    editing = editing,
                    drafts = drafts,
                    sent = sent,
                    draftsPounds = saved.getOrNull(4) as Boolean?,
                    lastRemoved = lastRemoved,
                    typeSheetFor = saved.getOrNull(6) as String?,
                    expanded = expanded,
                    manual = manual,
                    checkedHere = checkedHere,
                    announced = announced,
                )
            },
        )
    }
}

/**
 * The [ActiveWorkoutScreenState] of the screen, kept across recreation and process death, and
 * started afresh when [workoutId] changes (another workout, or none).
 */
@Composable
fun rememberActiveWorkoutScreenState(workoutId: String? = null): ActiveWorkoutScreenState =
    rememberSaveable(workoutId, saver = ActiveWorkoutScreenState.Saver) { ActiveWorkoutScreenState() }
