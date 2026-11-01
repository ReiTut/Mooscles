package com.mooscles.backup

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupCodecTest {
    private fun sample(muscle: String = "CHEST") = BackupFile(
        format = BackupCodec.FORMAT,
        formatVersion = BackupCodec.FORMAT_VERSION,
        exportedAt = "2026-10-27T12:00:00Z",
        app = BackupApp("1.4.5", 16, "android"),
        preferences = BackupPreferences(false, "system", true, 90, "default"),
        data = BackupData(
            exercises = listOf(ExerciseRow("e1", "Bench press", muscle, "BARBELL", "TOTAL_LOAD", true)),
            routineFolders = listOf(RoutineFolderRow("default", "My routines", 0)),
            routines = listOf(RoutineRow("r1", "Push", 0, "default")),
            routineExercises = listOf(
                RoutineExerciseRow("re1", "r1", "e1", 0, 3, 8, 22.75, 120),
                RoutineExerciseRow("re2", "r1", "e1", 1, 2, 0, null, null),
            ),
            routineSets = listOf(
                RoutineSetRow("rs1", "re1", 0, 8, 0.1 + 0.2, true),
                RoutineSetRow("rs2", "re1", 1, 0, null, false),
            ),
            workouts = listOf(
                WorkoutRow("w1", "Push", 1000L, 2000L, "FINISHED", "r1"),
                WorkoutRow("w2", "Open", 3000L, null, "ACTIVE", null),
            ),
            workoutExercises = listOf(
                WorkoutExerciseRow("we1", "w1", "e1", "Bench press", muscle, "BARBELL", "TOTAL_LOAD", 0, 90),
            ),
            workoutSets = listOf(WorkoutSetRow("ws1", "we1", 0, 8, 22.75, true, false, false)),
            routineTargetCheckpoints = listOf(
                CheckpointRow("e1", "TOTAL_LOAD", 22.75, 8),
                CheckpointRow("e1", "BODYWEIGHT", null, 5),
            ),
        ),
    )

    @Test
    fun encodeThenDecodeReturnsEqualFile() {
        val file = sample()
        assertEquals(file, BackupCodec.decode(BackupCodec.encode(file)))
    }

    @Test
    fun decodeIgnoresUnknownFields() {
        val text = BackupCodec.encode(sample())
            .replaceFirst("{", "{\"extra\": 1,")
            .replaceFirst("\"name\":\"Bench press\"", "\"name\":\"Bench press\",\"extra\":1")
        assertTrue(text.contains("\"Bench press\",\"extra\":1"))
        assertEquals(sample(), BackupCodec.decode(text))
    }

    @Test
    fun encodeIsCompact() {
        val text = BackupCodec.encode(sample())
        assertTrue(text.startsWith("{\"format\":\"mooscles-backup\",\"formatVersion\":1,"))
        assertTrue('\n' !in text, "compact output has no line breaks")
    }

    @Test
    fun decodeRejectsNewerVersion() {
        val e = assertFailsWith<BackupException.NewerVersion> {
            BackupCodec.decode("""{"format":"mooscles-backup","formatVersion":2,"data":"different"}""")
        }
        assertEquals(2, e.found)
    }

    @Test
    fun decodeRejectsOtherFormat() {
        assertFailsWith<BackupException.NotABackup> {
            BackupCodec.decode("""{"format":"something","formatVersion":1}""")
        }
        assertFailsWith<BackupException.NotABackup> { BackupCodec.decode("[]") }
    }

    @Test
    fun decodeRejectsMalformedJson() {
        listOf("{", "", """{"format":"mooscles-backup","formatVersion":1}""").forEach { text ->
            assertFailsWith<BackupException.Corrupted>(text) { BackupCodec.decode(text) }
        }
    }

    @Test
    fun decodeAcceptsUtf8Bom() {
        val file = sample()
        assertEquals(file, BackupCodec.decode("\uFEFF" + BackupCodec.encode(file)))
    }

    @Test
    fun routineSetWarmupRoundTrips() {
        val file = withPlannedWarmup()
        val text = BackupCodec.encode(file)
        assertTrue(text.contains("\"inheritPr\":false,\"warmup\":true}"), text)
        val decoded = BackupCodec.decode(text)
        assertEquals(file, decoded)
        assertEquals(listOf(true, false), decoded.data.toEntities().routineSets.map { it.warmup })
    }

    /** A file exported before version 6 has no warmup key: its planned sets are working sets. */
    @Test
    fun fileWithoutWarmupReadsAsWorkingSets() {
        val text = BackupCodec.encode(sample()).replace(",\"warmup\":false}", "}")
        assertFalse(text.contains("\"warmup\":false}"), text)
        // workout_sets has its own warmup, which a file of today always carries; only the planned one is missing.
        assertTrue(text.contains("\"inheritPr\":true}"), text)
        val decoded = BackupCodec.decode(text)
        assertEquals(sample(), decoded)
        assertTrue(decoded.data.toEntities().routineSets.none { it.warmup })
    }

    /** An app before version 6 reads a new file with the same Json and drops the field: the planned warm-up becomes a working set. */
    @Test
    fun olderReaderIgnoresWarmup() {
        val text = BackupCodec.encode(withPlannedWarmup())
        val old = BackupCodec.json.decodeFromString(OldFile.serializer(), text)
        assertEquals(
            listOf(
                OldRoutineSetRow("rs1", "re1", 0, 8, 0.1 + 0.2, false),
                OldRoutineSetRow("rs2", "re1", 1, 0, null, false),
            ),
            old.data.routineSets,
        )
        // Apps before version 6 refuse a formatVersion above 1; the optional field keeps the file at 1.
        assertEquals(1, old.formatVersion)
    }

    private fun withPlannedWarmup() = sample().let { file ->
        file.copy(data = file.data.copy(routineSets = listOf(
            RoutineSetRow("rs1", "re1", 0, 8, 0.1 + 0.2, inheritPr = false, warmup = true),
            RoutineSetRow("rs2", "re1", 1, 0, null, inheritPr = false),
        )))
    }

    // The shape of RoutineSetRow before version 6, inside only as much of the file as an old reader needs.
    @Serializable
    private data class OldRoutineSetRow(
        val id: String, val routineExerciseId: String, val position: Int, val targetReps: Int,
        val targetWeightKg: Double?, val inheritPr: Boolean,
    )

    @Serializable
    private data class OldData(val routineSets: List<OldRoutineSetRow>)

    @Serializable
    private data class OldFile(val format: String, val formatVersion: Int, val data: OldData)

    @Test
    fun unknownEnumIsInvalid() {
        val file = BackupCodec.decode(BackupCodec.encode(sample(muscle = "TAIL")))
        assertFailsWith<BackupException.Invalid> { file.data.toEntities() }
    }
}
