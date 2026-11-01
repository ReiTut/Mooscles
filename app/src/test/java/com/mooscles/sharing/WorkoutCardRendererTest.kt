package com.mooscles.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.screens.share.ShareCardData
import com.mooscles.screens.share.ShareCardRow
import com.mooscles.screens.share.ShareCardSlice
import com.mooscles.screens.share.ShareRecordRow
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The common card through the app's export: real fonts and the native Canvas of Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutCardRendererTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val renderer = WorkoutCardRenderer()
    private val inputs by lazy { compose.captureShareCardInputs() }
    private val background = MoosclesColors.Dark.bgBase.toArgb()

    private fun export(card: ShareCardData) = runBlocking { renderer.export(context, card, inputs.painter, inputs.measurer) }
    private fun layout(card: ShareCardData) = inputs.painter.layout(card, inputs.measurer)

    @Test fun pngIsHighResolutionAndContainsOnlyTheMeasuredCard() {
        val card = example()
        val file = export(card).single()
        assertTrue(file.canonicalPath.startsWith(File(context.cacheDir, "workout-shares").canonicalPath + File.separator))
        assertEquals("Mooscles-workout-71-1.png", file.name)
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            assertEquals(1080, bitmap.width)
            assertEquals(layout(card).single().height, bitmap.height)
            assertTrue(bitmap.height in 700..WorkoutCardRenderer.MAX_PAGE_HEIGHT)
            assertEquals(background, bitmap.getPixel(0, 0))
            assertEquals(background, bitmap.getPixel(bitmap.width - 1, bitmap.height - 1))
            assertTrue(file.length() > 10_000)
            assertBrand(bitmap, layout(card).single().brandBounds)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun longWorkoutPaginatesEveryRowInOrderAndKeepsTheHeaderOnEachPage() {
        val card = example().copy(rows = List(120) { index ->
            ShareCardRow("Exercise ${index + 1} with a detailed variation name", 3, "225.5\u00A0lb × 12", index % 3 == 0)
        })
        val pages = layout(card)
        assertTrue(pages.size > 1)
        val exercises = pages.flatMap { it.slices }.filter { it.kind == ShareCardSlice.Kind.Exercise }
        assertEquals(card.rows.indices.toList(), exercises.map { it.index })
        assertTrue(pages.all { it.height <= WorkoutCardRenderer.MAX_PAGE_HEIGHT })
        val files = export(card)
        assertEquals(pages.size, files.size)
        assertEquals(List(pages.size) { "Mooscles-workout-71-${it + 1}.png" }, files.map { it.name })
        files.forEachIndexed { index, file ->
            val bitmap = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try {
                assertEquals(1080, bitmap.width)
                assertEquals(pages[index].height, bitmap.height)
                assertBrand(bitmap, pages[index].brandBounds)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun oneExceptionallyLongExerciseContinuesWithoutDroppingAnyTextLines() {
        val card = example().copy(rows = listOf(
            ShareCardRow("Long exercise variation ".repeat(500), 2, "Bodyweight × 15", true),
            ShareCardRow("Final exercise", 3, "22\u00A0kg × 6", true),
        ))
        val pages = layout(card)
        assertTrue(pages.size > 1)
        val slices = pages.flatMap { it.slices }.filter { it.kind == ShareCardSlice.Kind.Exercise && it.index == 0 }
        assertEquals(0, slices.first().nameStart)
        assertEquals(slices.first().nameLineCount, slices.last().nameEnd)
        slices.zipWithNext().forEach { (first, second) ->
            assertEquals(first.nameEnd, second.nameStart)
            assertEquals(first.valueEnd, second.valueStart)
        }
        assertEquals(1, slices.count { it.first })
        assertEquals(1, pages.flatMap { it.slices }.count { it.kind == ShareCardSlice.Kind.Exercise && it.index == 1 })
        assertTrue(pages.all { it.height <= WorkoutCardRenderer.MAX_PAGE_HEIGHT })
    }

    /** The same rule as `ShareCardPainterTest` on the emulator, here with Robolectric's text metrics. */
    @Test fun longPreviewKeepsTodaysPageCount() {
        val pages = layout(longWorkout())
        println("SHARE host layout pages=${pages.size} heights=${pages.map { it.height }} " +
            "slices=${pages.map { it.slices.size }} lines=${pages.flatMap { it.slices }.filter { it.kind == ShareCardSlice.Kind.Exercise }.map { it.nameLineCount }.distinct()}")
        assertEquals(3, pages.size)
    }

    @Test fun emptyWorkoutStillDrawsOnePage() {
        val pages = layout(example().copy(rows = emptyList(), records = emptyList()))
        assertEquals(1, pages.size)
        assertEquals(listOf(ShareCardSlice.Kind.Section, ShareCardSlice.Kind.Empty), pages.single().slices.map { it.kind })
    }

    /** Without arguments the resources come back as written: the card fills the templates itself. */
    @Test fun textsResolveTheRawTemplatesOfTheResources() {
        val texts = inputs.texts
        assertEquals("%1\$s, %3\$s %2\$d, %4\$d · %5\$s–%6\$s", texts.dateFormat)
        assertEquals("Bodyweight × %1\$d", texts.bodyweightSet)
        assertEquals("%1\$d set", texts.labels.setCountOne)
        assertEquals("%1\$d sets", texts.labels.setCountOther)
        assertEquals("Jan", texts.months.first())
        assertEquals("Mon", texts.weekdays.first())
        assertEquals("PR", texts.labels.recordBadge)
        assertEquals("Duration", texts.labels.duration)
    }

    /** A closed preview or a retry cancels the export: it stops before the next page and leaves no files. */
    @Test fun cancelledExportStopsAndLeavesNoFiles() {
        val card = example()
        val painter = inputs.painter
        val measurer = inputs.measurer
        val root = File(context.cacheDir, "workout-shares")
        fun exports() = root.listFiles().orEmpty().filter { it.name.startsWith("export-") }.toSet()
        val before = exports()
        val cancelled = runBlocking {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                cancel()
                renderer.export(context, card, painter, measurer)
            }
            job.join()
            job.isCancelled
        }
        assertTrue(cancelled)
        assertEquals(before, exports())
    }

    @Test fun exportRetainsFilesFromPreviousSharesAndOnlyCleansOldManagedDirectories() {
        val first = export(example()).single()
        val root = first.parentFile!!.parentFile!!
        val old = File(root, "export-old").apply { mkdirs() }
        File(old, "old.png").writeText("expired")
        val unrelated = File(root, "keep-other-files").apply { mkdirs() }
        File(unrelated, "untouched.txt").writeText("keep")
        val now = System.currentTimeMillis()
        val expired = now - WorkoutCardRenderer.RETENTION_MILLIS - 1_000
        assertTrue(old.setLastModified(expired))
        assertTrue(unrelated.setLastModified(expired))
        val next = export(example()).single()
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
            "multiple-pr" to example().copy(workoutNumber = 72, workoutName = "Milestone session",
                records = listOf(
                    ShareRecordRow("Barbell bench press", "105\u00A0kg × 3"),
                    ShareRecordRow("Wide-grip pull-up", "Bodyweight × 12"),
                    ShareRecordRow("Dumbbell curl", "22\u00A0kg × 6"),
                ),
                rows = listOf(
                    ShareCardRow("Barbell bench press", 3, "105\u00A0kg × 3", true),
                    ShareCardRow("Wide-grip pull-up", 4, "Bodyweight × 12", true),
                    ShareCardRow("Dumbbell curl", 2, "22\u00A0kg × 6", true),
                )),
            "long-names-lb" to example().copy(workoutNumber = 73, workoutName = "Upper body · Strength and control",
                volumeText = "10,283.75\u00A0lb", records = listOf(
                    ShareRecordRow("Single-arm alternating dumbbell incline bench press with neutral grip", "48.5\u00A0lb × 6"),
                ), rows = listOf(
                    ShareCardRow("Single-arm alternating dumbbell incline bench press with neutral grip", 3, "48.5\u00A0lb × 6", true),
                    ShareCardRow("Wide-grip pull-up with a controlled eccentric and pause at the top", 4, "Bodyweight × 12", false),
                    ShareCardRow("Seated cable row with close neutral grip attachment", 1, "132.3\u00A0lb × 10", false),
                    ShareCardRow("Very long variation name containing an uninterrupted word: VeryLongUnbrokenExerciseVariationNameForWrappingVerification", 2, "77.125\u00A0lb × 123", false),
                )),
            "long-workout" to longWorkout(),
        )
        fixtures.forEach { (name, card) ->
            val files = export(card)
            files.forEachIndexed { index, file ->
                val preview = File(output, "$name-${index + 1}.png")
                file.copyTo(preview, overwrite = true)
                assertTrue(preview.length() > 10_000)
            }
        }
    }

    private fun assertBrand(bitmap: Bitmap, bounds: androidx.compose.ui.unit.IntRect) {
        val plate = MoosclesColors.Dark.accentSolid.toArgb()
        var found = 0
        for (x in bounds.left until bounds.right) for (y in bounds.top until bounds.bottom) {
            if (bitmap.getPixel(x, y) == plate) found++
        }
        assertTrue("no plate pixels in $bounds", found > 0)
    }

    /** The "long-workout" fixture of `ShareCardPainterTest`: three pages in the former renderer. */
    private fun longWorkout(): ShareCardData {
        val rows = List(48) { index ->
            ShareCardRow("Exercise ${index + 1} · Controlled working sets", 3,
                if (index % 4 == 0) "Bodyweight × 12" else "${20 + index}\u00A0kg × 8", index % 5 == 0)
        }
        return example().copy(workoutNumber = 74, workoutName = "Full-body training", volumeText = "12,450\u00A0kg",
            setsText = "144", rows = rows, records = rows.filter { it.hasRecord }.map { ShareRecordRow(it.name, it.bestSet) })
    }

    private fun example() = ShareCardData(
        workoutName = "Evening workout",
        dateText = "Thu, Oct 22, 2026 · 20:55–21:39",
        durationText = "44:00",
        setsText = "8",
        volumeText = "4,443\u00A0kg",
        records = listOf(ShareRecordRow("Dumbbell curl", "22\u00A0kg × 6"), ShareRecordRow("Wide-grip pull-up", "Bodyweight × 12")),
        rows = listOf(
            ShareCardRow("Dumbbell curl", 2, "22\u00A0kg × 6", true),
            ShareCardRow("Wide-grip pull-up", 3, "Bodyweight × 12", true),
            ShareCardRow("Seated cable row", 3, "50\u00A0kg × 11", false),
        ),
        workoutNumber = 71,
        labels = inputs.texts.labels,
    )
}
