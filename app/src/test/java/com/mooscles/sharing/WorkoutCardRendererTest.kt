package com.mooscles.sharing

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutCardRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = WorkoutCardRenderer()

    @Test fun pngIsHighResolutionAndContainsOnlyTheMeasuredCard() {
        val card = example()
        val file = renderer.export(context, card).single()
        assertTrue(file.canonicalPath.startsWith(File(context.cacheDir, "workout-shares").canonicalPath + File.separator))
        assertTrue(file.name.endsWith(".png"))
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(renderer.layout(card).single().height, bitmap.height)
            assertTrue(bitmap.height in 700..WorkoutCardRenderer.MAX_PAGE_HEIGHT)
            assertEquals(0xFF101713.toInt(), bitmap.getPixel(0, 0))
            assertTrue(file.length() > 10_000)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun longWorkoutPaginatesEveryRowInOrderAndKeepsStatisticsOnEachPage() {
        val card = example().copy(rows = List(120) { index ->
            ShareExerciseRow("Exercise ${index + 1} with a detailed variation name", 3, "225.5 lb × 12", index % 3 == 0)
        })
        val pages = renderer.layout(card)
        assertTrue(pages.size > 1)
        assertEquals(card.rows.indices.toList(), pages.flatMap { it.rows }.map { it.measured.index })
        pages.forEach { page ->
            assertTrue(page.height <= WorkoutCardRenderer.MAX_PAGE_HEIGHT)
            assertEquals(3, page.stats.size)
            assertEquals("4,443 kg", page.stats[1].value.text.toString())
            assertTrue(page.rowsTop + page.bodyHeight + page.footerHeight < page.height)
        }
        val files = renderer.export(context, card)
        assertEquals(pages.size, files.size)
        files.forEachIndexed { index, file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertEquals(1080, bounds.outWidth)
            assertEquals(pages[index].height, bounds.outHeight)
        }
    }

    @Test fun oneExceptionallyLongExerciseContinuesWithoutDroppingAnyTextLines() {
        val card = example().copy(rows = listOf(
            ShareExerciseRow("Long exercise variation ".repeat(500), 2, "Bodyweight × 15", true),
            ShareExerciseRow("Final exercise", 3, "22 kg × 6", true),
        ))
        val pages = renderer.layout(card)
        assertTrue(pages.size > 1)
        val slices = pages.flatMap { it.rows }.filter { it.measured.index == 0 }
        assertEquals(0, slices.first().nameStart)
        assertEquals(slices.first().measured.name.lineCount, slices.last().nameEnd)
        slices.zipWithNext().forEach { (first, second) ->
            assertEquals(first.nameEnd, second.nameStart)
            assertEquals(first.bestEnd, second.bestStart)
        }
        assertEquals(1, slices.count { it.first })
        assertEquals(1, pages.flatMap { it.rows }.count { it.measured.index == 1 })
        assertTrue(pages.all { it.height <= WorkoutCardRenderer.MAX_PAGE_HEIGHT })
    }

    @Test fun exportRetainsFilesFromPreviousSharesAndOnlyCleansOldManagedDirectories() {
        val first = renderer.export(context, example()).single()
        val root = first.parentFile!!.parentFile!!
        val old = File(root, "export-old").apply { mkdirs() }
        File(old, "old.png").writeText("expired")
        val unrelated = File(root, "keep-other-files").apply { mkdirs() }
        File(unrelated, "untouched.txt").writeText("keep")
        val now = System.currentTimeMillis()
        val expired = now - WorkoutCardRenderer.RETENTION_MILLIS - 1_000
        assertTrue(old.setLastModified(expired))
        assertTrue(unrelated.setLastModified(expired))
        val next = renderer.export(context, example()).single()
        assertFalse(first.parentFile == next.parentFile)
        assertTrue(first.exists())
        assertTrue(next.exists())
        assertFalse(old.exists())
        assertTrue(unrelated.exists())
        assertEquals(0, renderer.cleanupObsoleteExports(context, now))
    }

    /** These real native-Canvas exports also serve as the visual review artifacts for this feature. */
    @Test fun generateRepresentativeWorkoutPreviews() {
        val output = File("build/outputs/share-previews").apply { mkdirs() }
        val fixtures = linkedMapOf(
            "short-kg" to example(),
            "multiple-pr" to example().copy(workoutNumber = 72, workoutName = "Milestone session", prCount = 3,
                rows = listOf(
                    ShareExerciseRow("Barbell bench press", 3, "105 kg × 3", true),
                    ShareExerciseRow("Wide-grip pull-up", 4, "Bodyweight × 12", true),
                    ShareExerciseRow("Dumbbell curl", 2, "22 kg × 6", true),
                )),
            "long-names-lb" to example().copy(workoutNumber = 73, workoutName = "Upper body · Strength and control",
                volumeText = "10,283.75 lb", rows = listOf(
                    ShareExerciseRow("Single-arm alternating dumbbell incline bench press with neutral grip", 3, "48.5 lb × 6", true),
                    ShareExerciseRow("Wide-grip pull-up with a controlled eccentric and pause at the top", 4, "Bodyweight × 12", true),
                    ShareExerciseRow("Seated cable row with close neutral grip attachment", 3, "132.3 lb × 10", false),
                    ShareExerciseRow("Very long variation name containing an uninterrupted word: VeryLongUnbrokenExerciseVariationNameForWrappingVerification", 2, "77.125 lb × 123", false),
                )),
            "long-workout" to example().copy(workoutNumber = 74, workoutName = "Full-body training", volumeText = "12,450 kg",
                rows = List(48) { index -> ShareExerciseRow("Exercise ${index + 1} · Controlled working sets", 3,
                    if (index % 4 == 0) "Bodyweight × 12" else "${20 + index} kg × 8", index % 5 == 0) }, prCount = 10),
        )
        fixtures.forEach { (name, card) ->
            val files = renderer.export(context, card)
            files.forEachIndexed { index, file ->
                val preview = File(output, "$name-${index + 1}.png")
                file.copyTo(preview, overwrite = true)
                assertTrue(preview.length() > 10_000)
            }
        }
    }

    private fun example() = WorkoutShareCard(
        workoutId = "preview-workout", workoutNumber = 71, workoutName = "Evening workout",
        dateTime = "Thursday, October 22, 2026 at 8:55 PM",
        rows = listOf(
            ShareExerciseRow("Dumbbell curl", 2, "22 kg × 6", true),
            ShareExerciseRow("Wide-grip pull-up", 3, "Bodyweight × 12", true),
            ShareExerciseRow("Seated cable row", 3, "50 kg × 11", false),
        ), durationText = "44 min", volumeText = "4,443 kg", prCount = 2,
    )
}
