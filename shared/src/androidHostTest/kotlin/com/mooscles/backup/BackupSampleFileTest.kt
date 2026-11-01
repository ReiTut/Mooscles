package com.mooscles.backup

import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.WorkoutStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The checked-in v1 backup sample, read through the JVM classloader, so this stays off iOS. */
class BackupSampleFileTest {
    private fun sampleText() = javaClass.classLoader!!.getResourceAsStream("backup/v1-sample.json")!!
        .readBytes().toString(Charsets.UTF_8)

    /**
     * The planned warm-up (version 6) is an optional field, which by the rule in BackupFormat.kt keeps
     * the format version: new files stay readable by older apps, and this file, written before the
     * field existed, reads its planned sets as working sets.
     */
    @Test
    fun formatVersionStaysOneForAnOptionalField() {
        val file = BackupCodec.decode(sampleText())
        assertEquals(file.formatVersion, BackupCodec.FORMAT_VERSION)
        val planned = file.data.toEntities().routineSets
        assertEquals(2, planned.size)
        assertTrue(planned.none { it.warmup })
    }

    @Test
    fun v1SampleFixtureStaysReadable() {
        val file = BackupCodec.decode(sampleText())
        assertEquals(1, file.formatVersion)
        val e = file.data.toEntities()
        assertEquals(1, e.exercises.size)
        assertEquals("Bench press", e.exercises.single().name)
        assertEquals(MuscleGroup.CHEST, e.exercises.single().muscle)
        assertEquals(1, e.routineFolders.size)
        assertEquals("My routines", e.routineFolders.single().name)
        assertEquals(1, e.routines.size)
        assertEquals("Push", e.routines.single().name)
        assertEquals(2, e.routineExercises.size)
        assertEquals(22.75, e.routineExercises.first().targetWeightKg!!, 0.0)
        assertNull(e.routineExercises.last().restSeconds)
        assertEquals(2, e.routineSets.size)
        assertEquals(8, e.routineSets.first().targetReps)
        assertEquals(2, e.workouts.size)
        assertEquals(WorkoutStatus.FINISHED, e.workouts.first().status)
        assertEquals(1, e.workoutExercises.size)
        assertEquals(LoadConvention.TOTAL_LOAD, e.workoutExercises.single().convention)
        assertEquals(1, e.workoutSets.size)
        assertEquals(22.75, e.workoutSets.single().weightKg, 0.0)
        assertEquals(2, e.checkpoints.size)
        assertEquals(LoadConvention.BODYWEIGHT, e.checkpoints.last().convention)
    }
}
