package com.mooscles.screens.routines

import com.mooscles.data.NameRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The name of a duplicated routine (plan decision 17): the suffix is never the part that is cut. */
class RoutineNamesTest {
    private val en = "${RoutineNames.NAME_TOKEN} (copy)"
    private val pt = "${RoutineNames.NAME_TOKEN} (cópia)"

    @Test
    fun shortNameGetsTheSuffix() {
        assertEquals("Treino A (cópia)", RoutineNames.copyName("Treino A", pt))
        assertEquals("Push day (copy)", RoutineNames.copyName("  Push day ", en))
    }

    @Test
    fun a120CharacterNameIsCutToMakeRoomForTheSuffix() {
        val name = "a".repeat(NameRule.MAX_LENGTH)
        for (template in listOf(en, pt)) {
            val copy = RoutineNames.copyName(name, template)
            assertEquals(NameRule.MAX_LENGTH, copy.length)
            assertTrue(copy.endsWith(template.removePrefix(RoutineNames.NAME_TOKEN)), copy)
        }
    }

    @Test
    fun anEmojiAtTheBoundaryIsNotSplit() {
        // "(cópia)" takes 8 with its space: the room is 112, and the emoji sits at 111 and 112.
        val name = "a".repeat(111) + "💪" + "tail"
        val copy = RoutineNames.copyName(name, pt)
        assertEquals("a".repeat(111) + " (cópia)", copy)
        assertTrue(copy.none { it.isSurrogate() })
        // One more character of room and the whole emoji fits.
        val roomy = RoutineNames.copyName("a".repeat(110) + "💪" + "tail", pt)
        assertEquals("a".repeat(110) + "💪 (cópia)", roomy)
    }
}
