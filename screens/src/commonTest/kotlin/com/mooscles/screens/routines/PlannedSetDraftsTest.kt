package com.mooscles.screens.routines

import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.SetField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The drafts of the planned set rows (plan decision 16), ported from the old `RoutineEditorHostTest`: a row shows its
 * draft or else the saved value; each valid edit gives the one write to send; an invalid draft is never written; an
 * older acknowledgement never replaces a newer draft.
 */
class PlannedSetDraftsTest {
    private val sets = linkedMapOf<String, RoutineSetUi>()
    private val saves = mutableListOf<PlannedSave>()

    init {
        reset()
    }

    private fun reset(vararg changes: RoutineSetUi) {
        sets.clear()
        listOf(
            RoutineSetUi("bench-1", 8, 17.0),
            RoutineSetUi("bench-2", 8, 17.0, false),
            RoutineSetUi("bench-3", 7, 17.0, false),
            RoutineSetUi("fly-1", 6, 70.0, false),
        ).forEach { sets[it.id] = it }
        changes.forEach { sets[it.id] = it }
    }

    private fun drafts(pounds: Boolean = false, locale: NumberLocale = NumberLocale.EN) =
        PlannedSetDrafts(pounds, locale) { sets[it] }

    private fun PlannedSetDrafts.type(id: String, field: SetField, text: String) {
        edit(id, field, text)?.let { saves += it }
    }

    /** The repository echoes [save]: the set now holds its values, manual. */
    private fun PlannedSetDrafts.echo(save: PlannedSave) {
        val next = sets.getValue(save.setId).copy(reps = save.reps, weightKg = save.weightKg, inherited = false)
        sets[save.setId] = next
        acknowledge(next)
    }

    private fun change(id: String, update: (RoutineSetUi) -> RoutineSetUi) {
        sets[id] = update(sets.getValue(id))
    }

    @Test
    fun individualRowsShowReferenceValuesAndSaveOnlyTheEditedRow() {
        val d = drafts()
        assertEquals("17", d.shown("bench-1", SetField.LOAD))
        assertEquals("8", d.shown("bench-1", SetField.REPS))
        assertEquals("7", d.shown("bench-3", SetField.REPS))
        d.type("bench-1", SetField.LOAD, "17.5")
        d.type("bench-1", SetField.REPS, "9")
        assertEquals(listOf(PlannedSave("bench-1", 8, 17.5), PlannedSave("bench-1", 9, 17.5)), saves)
        assertEquals("17", d.shown("bench-2", SetField.LOAD))
        assertEquals("8", d.shown("bench-2", SetField.REPS))
        assertEquals("70", d.shown("fly-1", SetField.LOAD))
        assertEquals("6", d.shown("fly-1", SetField.REPS))
    }

    @Test
    fun blankRepetitionsAndInvalidWeightRemainEditableWithoutBlockingSiblingSaves() {
        val d = drafts()
        d.type("bench-1", SetField.LOAD, "-")
        d.type("bench-1", SetField.REPS, "")
        assertEquals(listOf(PlannedSave("bench-1", 0, 17.0)), saves)
        d.type("bench-1", SetField.REPS, "0")
        d.type("bench-1", SetField.REPS, "-1")
        d.type("bench-1", SetField.REPS, "1.5")
        assertEquals(1, saves.size)
        d.type("bench-1", SetField.REPS, "12")
        assertEquals(PlannedSave("bench-1", 12, 17.0), saves.last())
        assertEquals("-", d.shown("bench-1", SetField.LOAD))
        d.type("bench-1", SetField.LOAD, "18,25")
        assertEquals(PlannedSave("bench-1", 12, 18.25), saves.last())
    }

    @Test
    fun olderAutosaveAcknowledgementsCannotReplaceNewerDrafts() {
        val d = drafts()
        d.type("bench-1", SetField.REPS, "9")
        d.type("bench-1", SetField.REPS, "10")
        change("bench-1") { it.copy(reps = 9, inherited = false) }
        d.acknowledge(sets.getValue("bench-1"))
        assertEquals("10", d.shown("bench-1", SetField.REPS))
        change("bench-1") { it.copy(reps = 10, inherited = false) }
        d.acknowledge(sets.getValue("bench-1"))
        assertEquals("10", d.shown("bench-1", SetField.REPS))
        assertEquals(2, saves.size)
    }

    @Test
    fun repositoryPrRefreshUpdatesBothValuesWithoutSavingDuringComposition() {
        val d = drafts()
        change("bench-1") { it.copy(reps = 6, weightKg = 22.0) }
        d.acknowledge(sets.getValue("bench-1"))
        assertEquals("22", d.shown("bench-1", SetField.LOAD))
        assertEquals("6", d.shown("bench-1", SetField.REPS))
        assertEquals("17", d.shown("bench-2", SetField.LOAD))
        assertTrue(saves.isEmpty())
        d.type("bench-1", SetField.REPS, "7")
        assertEquals(PlannedSave("bench-1", 7, 22.0), saves.single())
    }

    @Test
    fun editingRepetitionsRetainsExactKilogramsBehindRoundedPoundsAndInvalidDraft() {
        val exactKg = 22.123456789
        change("bench-1") { it.copy(weightKg = exactKg) }
        val d = drafts(pounds = true)
        val rounded = d.shown("bench-1", SetField.LOAD)
        assertEquals(MoosclesNumbers.plain(WeightUnits.fromKg(exactKg, true)), rounded)
        d.type("bench-1", SetField.LOAD, "-")
        d.type("bench-1", SetField.REPS, "9")
        assertEquals(exactKg, saves.single().weightKg!!, 0.0)
        d.type("bench-1", SetField.LOAD, rounded)
        // The rounded text stands for the exact kilograms: nothing new to write.
        assertEquals(1, saves.size)
        d.type("bench-1", SetField.LOAD, "50.5")
        assertEquals(WeightUnits.toKg(50.5, true), saves.last().weightKg!!, 0.0)
    }

    @Test
    fun explicitlyClearedWeightSavesBodyweightAndPrInheritanceIsAnExplicitSeparateAction() {
        val d = drafts()
        d.type("bench-1", SetField.LOAD, "")
        assertEquals(PlannedSave("bench-1", 8, null), saves.single())
        assertEquals("", d.shown("bench-1", SetField.LOAD))
        // The inheritance is its own action (the menu's "Follow the record"), never a side effect of the edit.
        assertEquals(1, saves.size)
    }

    @Test
    fun replacingAnExerciseResetsDraftBuffersAndNeverAutosavesTheOldMovement() {
        val d = drafts()
        d.type("bench-2", SetField.LOAD, "20")
        saves.clear()
        // The entry's exercise is replaced: its targets come back empty and the screen drops the drafts of its sets.
        change("bench-2") { it.copy(weightKg = null, reps = 12, inherited = true) }
        d.drop("bench-2")
        d.acknowledge(sets.getValue("bench-2"))
        assertEquals("", d.shown("bench-2", SetField.LOAD))
        assertEquals("12", d.shown("bench-2", SetField.REPS))
        assertTrue(saves.isEmpty())
        d.type("bench-2", SetField.REPS, "13")
        assertEquals(PlannedSave("bench-2", 13, null), saves.single())
    }

    @Test
    fun explicitPrDefaultAcknowledgementClearsAnInvalidDraftEvenWhenThePairIsUnchanged() {
        val d = drafts()
        d.type("bench-2", SetField.LOAD, "-")
        assertEquals("-", d.shown("bench-2", SetField.LOAD))
        // "Use the record" is acknowledged with the same pair, now following the record.
        change("bench-2") { it.copy(inherited = true) }
        d.acknowledge(sets.getValue("bench-2"))
        assertEquals("17", d.shown("bench-2", SetField.LOAD))
        assertEquals("8", d.shown("bench-2", SetField.REPS))
        assertTrue(saves.isEmpty())
    }

    @Test
    fun delayedPrDefaultAcknowledgementCannotOverwriteAMoreRecentManualEdit() {
        val d = drafts()
        d.drop("bench-2") // the person asked to follow the record
        d.type("bench-2", SetField.REPS, "12")
        // The record's acknowledgement arrives late, with the record's values.
        change("bench-2") { it.copy(inherited = true, reps = 6, weightKg = 22.0) }
        d.acknowledge(sets.getValue("bench-2"))
        assertEquals("12", d.shown("bench-2", SetField.REPS))
        assertEquals("17", d.shown("bench-2", SetField.LOAD))
        change("bench-2") { it.copy(inherited = false, reps = 12, weightKg = 17.0) }
        d.acknowledge(sets.getValue("bench-2"))
        assertEquals("12", d.shown("bench-2", SetField.REPS))
        assertEquals(PlannedSave("bench-2", 12, 17.0), saves.single())
    }

    @Test
    fun stepsUseTwoAndAHalfKgOrFiveLbAndOneRep() {
        val kg = drafts()
        assertEquals(PlannedSave("bench-2", 8, 19.5), kg.step("bench-2", SetField.LOAD, 1))
        assertEquals("19.5", kg.shown("bench-2", SetField.LOAD))
        assertEquals(PlannedSave("bench-2", 9, 19.5), kg.step("bench-2", SetField.REPS, 1))
        assertEquals(PlannedSave("bench-2", 8, 19.5), kg.step("bench-2", SetField.REPS, -1))

        reset()
        val lb = drafts(pounds = true)
        // 17 kg shows as 37.48 lb; a step is 5 lb.
        val up = assertNotNull(lb.step("bench-2", SetField.LOAD, 1))
        assertEquals("42.48", lb.shown("bench-2", SetField.LOAD))
        assertEquals(WeightUnits.toKg(42.48, true), up.weightKg!!, 0.0)

        reset()
        val bottom = drafts()
        // The reps stop at 0 and the load at 0 (no load): a step past them writes nothing.
        assertEquals(PlannedSave("bench-2", 0, 17.0), bottom.step("bench-2", SetField.REPS, -8))
        assertNull(bottom.step("bench-2", SetField.REPS, -1))
        assertEquals(PlannedSave("bench-1", 8, null), bottom.step("bench-1", SetField.LOAD, -100))
    }

    @Test
    fun anInvalidDraftIsNeverTheValueOfAStepOrASnapshot() {
        val d = drafts()
        d.type("bench-1", SetField.LOAD, "82,")
        assertTrue(saves.isEmpty())
        val snapshot = d.snapshot(sets.getValue("bench-1"))
        assertEquals(17.0, snapshot.weightKg)
        assertEquals(8, snapshot.reps)
        d.type("bench-1", SetField.REPS, "9")
        val pending = d.snapshot(sets.getValue("bench-1"))
        assertEquals(9, pending.reps)
        assertEquals(false, pending.inherited)
    }

    @Test
    fun settleDropsDraftsOnceNothingIsInFlight() {
        val d = drafts()
        // "09" is valid and shown as typed; the saved 9 is shown "9".
        d.type("bench-1", SetField.REPS, "09")
        d.settle("bench-1")
        assertEquals("09", d.shown("bench-1", SetField.REPS))
        d.echo(saves.single())
        assertEquals("09", d.shown("bench-1", SetField.REPS))
        d.settle("bench-1")
        assertEquals("9", d.shown("bench-1", SetField.REPS))
        assertEquals("17", d.shown("bench-1", SetField.LOAD))
    }

    @Test
    fun foldedEchoesDoNotLeaveTheLastWritePending() {
        val d = drafts()
        d.type("bench-1", SetField.REPS, "9")
        d.type("bench-1", SetField.REPS, "10")
        d.type("bench-1", SetField.REPS, "9")
        // Room folded the three writes into one echo of 9: it matches the first write, and the last one too, so
        // nothing stays in flight.
        change("bench-1") { it.copy(reps = 9, inherited = false) }
        d.acknowledge(sets.getValue("bench-1"))
        d.settle("bench-1")
        assertEquals("9", d.shown("bench-1", SetField.REPS))
        // A later change from elsewhere is not ignored as "older".
        change("bench-1") { it.copy(reps = 6, weightKg = 22.0) }
        d.acknowledge(sets.getValue("bench-1"))
        assertEquals("6", d.shown("bench-1", SetField.REPS))
        assertEquals("22", d.shown("bench-1", SetField.LOAD))
    }

    @Test
    fun forgottenWritesLetTheSavedValuesBackAndTheNextEditWriteAgain() {
        val d = drafts()
        d.type("bench-1", SetField.REPS, "12")
        assertEquals(1, saves.size)
        // The write failed: nothing will be echoed.
        d.forgetWrites()
        assertEquals("12", d.shown("bench-1", SetField.REPS)) // still the person's, until the row is left
        d.settle("bench-1")
        assertEquals("8", d.shown("bench-1", SetField.REPS))
        d.type("bench-1", SetField.REPS, "12")
        assertEquals(PlannedSave("bench-1", 12, 17.0), saves.last())
        assertEquals(2, saves.size)
    }

    @Test
    fun aRunOfStepsWritesOnceWhenItIsReleased() {
        val d = drafts()
        d.step("bench-2", SetField.REPS, 1)
        d.step("bench-2", SetField.REPS, 1)
        val last = d.step("bench-2", SetField.REPS, 1)
        assertEquals(PlannedSave("bench-2", 11, 17.0), last)
        assertEquals(last, d.release("bench-2"))
        assertNull(d.release("bench-2"))
        // Typing writes at once and leaves nothing held.
        d.step("bench-2", SetField.REPS, 1)
        d.type("bench-2", SetField.REPS, "20")
        assertNull(d.release("bench-2"))
    }
}
