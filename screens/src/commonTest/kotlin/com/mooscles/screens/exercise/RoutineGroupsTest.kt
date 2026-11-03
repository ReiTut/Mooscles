package com.mooscles.screens.exercise

import com.mooscles.data.RoutineFolder
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import kotlin.test.Test
import kotlin.test.assertEquals

/** How the add-to-routine sheet groups the routines by folder ([routineGroups]). */
class RoutineGroupsTest {
    private val home = RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)
    private val legs = RoutineFolderUi("legs", "Legs")
    private val push = RoutineUi("push", "Push", folderId = RoutineFolder.DEFAULT_ID)
    private val squat = RoutineUi("squat", "Squat", folderId = "legs")
    private val stray = RoutineUi("stray", "Stray", folderId = "gone")

    @Test
    fun foldersInOrderWithStrayRoutinesInTheDefaultFolder() {
        assertEquals(
            listOf(home to listOf(push, stray), legs to listOf(squat)),
            routineGroups(listOf(home, legs), listOf(push, squat, stray)),
        )
        // A folder without routines is left out.
        assertEquals(listOf(legs to listOf(squat)), routineGroups(listOf(home, legs), listOf(squat)))
    }

    /** Final review, item 3: without the folders (not loaded, or none known) no routine disappears. */
    @Test
    fun withoutFoldersEveryRoutineGoesToTheDefaultFolder() {
        assertEquals(listOf(home to listOf(push, squat, stray)), routineGroups(emptyList(), listOf(push, squat, stray)))
        assertEquals(emptyList(), routineGroups(emptyList(), emptyList()))
    }
}
