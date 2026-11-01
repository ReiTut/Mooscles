package com.mooscles.screens.workout

import androidx.compose.runtime.saveable.SaverScope
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.SetField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The draft rules of [ActiveWorkoutScreenState]: echoes, settling, the unit switch and saving. */
class SetDraftsTest {
    private val load = "s1" to SetField.LOAD

    /** The draft "text" of the load, typed after writing each of [written]. */
    private fun typed(text: String, vararg written: Double) = ActiveWorkoutScreenState().apply {
        type("s1", SetField.LOAD, text)
        written.forEach { sent("s1", SetField.LOAD, it) }
    }

    private fun ActiveWorkoutScreenState.echo(saved: Double, previous: Double?, draftValue: Double?) =
        echo("s1", SetField.LOAD, saved, previous, draftValue)

    @Test
    fun anEchoAcknowledgesItsWriteAndTheOlderOnes() {
        // "8", "80", "8": the first echo of 8 is the first write, not the last.
        val aba = typed("8", 8.0, 80.0, 8.0)
        aba.echo(8.0, 85.0, 8.0)
        assertEquals("8", aba.drafts[load])
        assertEquals(listOf(80.0, 8.0), aba.sent[load])
        aba.echo(80.0, 8.0, 8.0)
        assertEquals("8", aba.drafts[load], "the late 80 must not come back")
        aba.echo(8.0, 80.0, 8.0)
        assertNull(aba.drafts[load])
        assertNull(aba.sent[load])

        // An emission that folds the first writes acknowledges them too.
        val folded = typed("8", 8.0, 80.0, 8.0)
        folded.echo(80.0, 85.0, 8.0)
        assertEquals(listOf(8.0), folded.sent[load])
        folded.echo(8.0, 80.0, 8.0)
        assertNull(folded.drafts[load])
    }

    @Test
    fun aDraftInAnotherFormGoesOnTheEqualEcho() {
        // "80.0" typed for 80: once 80 is saved and nothing else is awaited, the saved value shows.
        val state = typed("80.0", 80.0)
        state.echo(80.0, 85.0, 80.0)
        assertNull(state.drafts[load])
        // A draft with another value stays: an invalid one (null) and one that was not written.
        val invalid = typed("82,", 80.0)
        invalid.echo(80.0, 85.0, null)
        assertEquals("82,", invalid.drafts[load])
        assertNull(invalid.sent[load])
    }

    @Test
    fun aChangeFromElsewhereKeepsWhatWasNotWritten() {
        // In flight: a value nobody here wrote is an older state.
        val pending = typed("24", 24.0)
        pending.echo(30.0, 20.0, 24.0)
        assertEquals("24", pending.drafts[load])
        assertEquals(listOf(24.0), pending.sent[load])
        // Nothing in flight: an invalid draft stays, one that repeated the old value gives way.
        val invalid = typed("82,")
        invalid.echo(30.0, 20.0, null)
        assertEquals("82,", invalid.drafts[load])
        val repeated = typed("20.0")
        repeated.echo(30.0, 20.0, 20.0)
        assertNull(repeated.drafts[load])
    }

    @Test
    fun leavingTheEditSettlesADraftThatRepeatsTheSavedValue() {
        // "80" and "8" over a saved 8 change nothing the database emits: the draft is the saved value.
        val folded = typed("8", 80.0, 8.0)
        folded.settleDraft("s1", SetField.LOAD, 8.0, 8.0)
        assertNull(folded.drafts[load])
        assertNull(folded.sent[load])
        // The last write is not saved yet, or the draft has another value: it stays.
        val waiting = typed("90", 90.0)
        waiting.settleDraft("s1", SetField.LOAD, 85.0, 90.0)
        assertEquals("90", waiting.drafts[load])
        val other = typed("82,")
        other.settleDraft("s1", SetField.LOAD, 85.0, null)
        assertEquals("82,", other.drafts[load])
    }

    @Test
    fun theUnitSwitchRewritesLoadDraftsFromTheirExactKg() {
        val text = { kg: Double, pounds: Boolean ->
            MoosclesNumbers.localize(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), NumberLocale.EN)
        }
        val state = ActiveWorkoutScreenState()
        state.useUnit(false, text)
        state.type("s1", SetField.LOAD, "22.5")
        state.sent("s1", SetField.LOAD, 22.5)
        state.type("s2", SetField.LOAD, "40")
        state.type("s3", SetField.LOAD, "82,")
        state.type("s1", SetField.REPS, "12")
        state.useUnit(true, text)
        assertEquals("49.6", state.drafts["s1" to SetField.LOAD])
        assertEquals("88.18", state.drafts["s2" to SetField.LOAD])
        assertEquals("82,", state.drafts["s3" to SetField.LOAD], "an invalid draft has no unit")
        assertEquals("12", state.drafts["s1" to SetField.REPS])
        // Back to kg: 49.6 lb is the 22.5 kg that was written, not 22.498 kg.
        state.useUnit(false, text)
        assertEquals("22.5", state.drafts["s1" to SetField.LOAD])
        // The same unit again changes nothing.
        state.useUnit(false, text)
        assertEquals("22.5", state.drafts["s1" to SetField.LOAD])
    }

    /** Review M1: after a failed write, nothing is awaited any more, so the next value is written again. */
    @Test
    fun droppingPendingWritesForgetsWhatWasAwaited() {
        val state = typed("90", 90.0)
        state.sent("s2", SetField.REPS, 9.0)
        assertEquals(90.0, state.expected("s1", SetField.LOAD, 85.0))
        state.dropPendingWrites()
        assertEquals(emptyMap(), state.sent)
        assertEquals(85.0, state.expected("s1", SetField.LOAD, 85.0), "the database's value stands")
        assertEquals("90", state.drafts[load], "what the person typed stays")
        // The screen drops every optimistic value on an error; that forgets the awaited writes too.
        state.sent("s1", SetField.LOAD, 90.0)
        state.dropAllOptimistic()
        assertEquals(emptyMap(), state.sent)
    }

    @Test
    fun saverKeepsDraftsTheirWritesAndTheirUnit() {
        val state = ActiveWorkoutScreenState(
            drafts = mapOf(("s:1" to SetField.LOAD) to "82,", ("s2" to SetField.REPS) to ""),
            sent = mapOf(("s2" to SetField.REPS) to listOf(0.0, 8.0), ("s:1" to SetField.LOAD) to listOf(36.287389)),
            draftsPounds = true,
        )
        val scope = SaverScope { true }
        val saved = with(ActiveWorkoutScreenState.Saver) { scope.save(state) }
        val restored = requireNotNull(saved?.let { ActiveWorkoutScreenState.Saver.restore(it) })
        assertEquals(state.drafts, restored.drafts)
        assertEquals(state.sent, restored.sent)
        assertEquals(true, restored.draftsPounds)
        val empty = requireNotNull(with(ActiveWorkoutScreenState.Saver) { scope.save(ActiveWorkoutScreenState()) }?.let { ActiveWorkoutScreenState.Saver.restore(it) })
        assertEquals(emptyMap(), empty.drafts)
        assertEquals(emptyMap(), empty.sent)
        assertNull(empty.draftsPounds)
    }
}
