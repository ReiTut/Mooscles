package com.mooscles.screens.format

import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.ui.RoutineTargetUi
import com.mooscles.ui.TargetSetUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The target line's rules (spec D16), with the English and Portuguese words passed by hand. */
class RoutineTargetFormatTest {
    private val en = TargetTexts(
        prefix = "Target",
        sets = { "$it ${if (it == 1) "set" else "sets"}" },
        reps = { "$it reps" },
        warmups = { "+ $it warm-up" },
        unit = "kg",
    )
    private val pt = TargetTexts(
        prefix = "Alvo",
        sets = { "$it ${if (it == 1) "série" else "séries"}" },
        reps = { "$it reps" },
        warmups = { "+ $it aquec." },
        unit = "kg",
    )
    private val lb = TargetTexts(prefix = "Alvo", sets = pt.sets, reps = pt.reps, warmups = pt.warmups, unit = "lb")

    private fun sets(vararg pairs: Pair<Int, Double?>) = pairs.map { TargetSetUi(it.first, it.second) }
    private fun line(
        working: List<TargetSetUi>,
        warmups: Int = 0,
        texts: TargetTexts = pt,
        pounds: Boolean = false,
        locale: NumberLocale = NumberLocale.PT_BR,
    ): String? = RoutineTargetFormat.parts(RoutineTargetUi(working, warmups), pounds, locale, texts)?.joinToString("") { it.text }

    private val nb = "\u00A0"

    @Test
    fun allEqual() {
        assertEquals("Alvo 3×8 · 80${nb}kg", line(sets(8 to 80.0, 8 to 80.0, 8 to 80.0)))
        assertEquals("Target 3×8 · 80${nb}kg", line(sets(8 to 80.0, 8 to 80.0, 8 to 80.0), texts = en, locale = NumberLocale.EN))
    }

    @Test
    fun sameRepsDifferentLoads() {
        assertEquals("Alvo 3×8 · 70–80${nb}kg", line(sets(8 to 70.0, 8 to 75.0, 8 to 80.0)))
    }

    @Test
    fun differentRepsAndLoads() {
        assertEquals("Alvo 3 séries · 6–10 reps · 70–80${nb}kg", line(sets(10 to 70.0, 8 to 75.0, 6 to 80.0)))
        assertEquals("Target 3 sets · 6–10 reps · 70–80${nb}kg", line(sets(10 to 70.0, 8 to 75.0, 6 to 80.0), texts = en, locale = NumberLocale.EN))
    }

    @Test
    fun differentRepsSameLoadIsOneValue() {
        assertEquals("Alvo 2 séries · 6–8 reps · 80${nb}kg", line(sets(8 to 80.0, 6 to 80.0)))
        assertEquals("Alvo 1×8 · 80${nb}kg", line(sets(8 to 80.0)))
    }

    @Test
    fun blankLoads() {
        assertEquals("Alvo 3×12", line(sets(12 to null, 12 to null, 12 to 0.0)))
    }

    @Test
    fun blankReps() {
        assertEquals("Alvo 3 séries · 80${nb}kg", line(sets(0 to 80.0, 0 to 80.0, 0 to 80.0)))
    }

    @Test
    fun bothBlank() {
        assertEquals("Alvo 3 séries", line(sets(0 to null, 0 to null, 0 to null)))
    }

    @Test
    fun mixedBlankLoadsAreLeftOutOfTheRange() {
        assertEquals("Alvo 3×8 · 70–80${nb}kg", line(sets(8 to 70.0, 8 to null, 8 to 80.0)))
        // Blank reps are left out too, and the line stops being "N×r".
        assertEquals("Alvo 3 séries · 8 reps · 80${nb}kg", line(sets(8 to 80.0, 0 to 80.0, 8 to 80.0)))
    }

    @Test
    fun warmupsSuffix() {
        assertEquals("Alvo 3×8 · 80${nb}kg +${nb}2${nb}aquec.", line(sets(8 to 80.0, 8 to 80.0, 8 to 80.0), warmups = 2))
        assertEquals("Target 3×8 +${nb}1${nb}warm-up", line(sets(8 to null, 8 to null, 8 to null), warmups = 1, texts = en, locale = NumberLocale.EN))
    }

    @Test
    fun warmupsOnly() {
        assertEquals("Alvo +${nb}2${nb}aquec.", line(emptyList(), warmups = 2))
    }

    @Test
    fun nothingIsNull() {
        assertNull(line(emptyList()))
    }

    @Test
    fun poundsFromExactKg() {
        // 80 kg is 176.37 lb, 100 kg is 220.46 lb: converted from the exact kg, not from a rounded lb.
        assertEquals("Alvo 2×5 · 176,37${nb}lb", line(sets(5 to 80.0, 5 to 80.0), texts = lb, pounds = true))
        assertEquals("Alvo 2×5 · 176,37–220,46${nb}lb", line(sets(5 to 80.0, 5 to 100.0), texts = lb, pounds = true))
    }

    @Test
    fun numbersFollowTheLocale() {
        assertEquals("Alvo 1×5 · 72,5${nb}kg", line(sets(5 to 72.5), locale = NumberLocale.PT_BR))
        assertEquals("Alvo 1×5 · 72.5${nb}kg", line(sets(5 to 72.5), locale = NumberLocale.EN))
    }

    @Test
    fun numbersAreMarked() {
        val parts = RoutineTargetFormat.parts(RoutineTargetUi(sets(8 to 70.0, 8 to 80.0, 8 to 80.0), 2), false, NumberLocale.EN, en)!!
        assertEquals(listOf("3×8", "70–80", "2"), parts.filter { it.number }.map { it.text })
        assertTrue(parts.filter { !it.number }.none { part -> part.text.any { it.isDigit() } })
        assertEquals("Target 3×8 · 70–80${nb}kg +${nb}2${nb}warm-up", parts.joinToString("") { it.text })
    }
}
