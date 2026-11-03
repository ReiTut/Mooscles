package com.mooscles.screens.exercise.figures

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.illustration.ExerciseFigure
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureSegment
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.exerciseMedia
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.ui.ExerciseUi
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/**
 * The pilot sheet of gate G1 (spec D4, plan Task 9), and the sheet of each lot after it (plan
 * Task L): the 20 pilot exercises, or the lot's, by their pt-BR name,
 * frame A (start) and frame B (end, the one lists show), in both themes, once as list tiles at
 * `sizes.media` (64 dp) and once at the detail size (the useful width of a 412 dp phone, capped at
 * 360 dp). Four PNGs, `pilot-{light,dark}-{64,detail}.png` (`<lot>-…` for a lot), written with `Bitmap.compress` into the
 * test app's `captures` folder and copied by the shell to `/sdcard/Download/mooscles-pilot`, which
 * survives the uninstall at the end of the run (`adb pull` it afterwards).
 *
 * Opt-in, like the phase 3 fixtures: it only runs with `-e pilotSheet true`
 * (`-Pandroid.testInstrumentationRunnerArguments.pilotSheet=true`) and is skipped in a full run.
 * `-e lot <family>` (`…Arguments.lot=barbell`) captures a lot instead: the figures of that family
 * table of [ExerciseFigures.families] that are not in the pilot, in the table's order. `-e lot shrugs`
 * captures the three shrugs of every family (`shrugs-…`), the figures the shoulder lift is for.
 *
 * A window holds only part of a sheet, so each sheet is composed page by page in one composition
 * and the pages are stacked into one bitmap. The 64 dp sheet uses the emulator's density, where
 * the pixels of a small tile matter. The detail sheet is drawn at [DETAIL_DENSITY], so its two
 * 360 dp frames fit side by side in the window and the file stays small; the figure is vector, so
 * only the resolution changes, not the proportions.
 */
@OptIn(ExperimentalTestApi::class)
class PilotSheetCaptureTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun capturePilotSheet() {
        assumeTrue(
            "Pass -e pilotSheet true to capture the G1 pilot sheet",
            InstrumentationRegistry.getArguments().getString("pilotSheet") == "true",
        )
        val lot = InstrumentationRegistry.getArguments().getString("lot")
        val ids = if (lot == null) {
            ExerciseFigures.PILOT
        } else if (lot == SHRUGS) {
            ExerciseFigures.stableIds.filter { it.endsWith("_shrug") }
        } else {
            val family = checkNotNull(ExerciseFigures.families[lot]) { "No figure family $lot" }
            family.keys.filter { it !in ExerciseFigures.PILOT }
        }
        assertTrue(ids.isNotEmpty(), "The ${lot ?: "pilot"} sheet has no figures")
        val prefix = lot ?: "pilot"
        val dir = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("captures"))
        val written = mutableListOf<File>()
        for (dark in listOf(false, true)) {
            val theme = if (dark) "dark" else "light"
            written += File(dir, "$prefix-$theme-64.png").also { sheet(ids, dark, tiles = true, it) }
            written += File(dir, "$prefix-$theme-detail.png").also { sheet(ids, dark, tiles = false, it) }
        }
        for (file in written) assertTrue(file.length() > 0, "${file.name} was not written")
        // Out of the test app's folder, which the uninstall at the end of the run deletes.
        shell("mkdir -p $EXPORT")
        for (file in written) shell("cp ${file.absolutePath} $EXPORT/${file.name}")
        val listed = shell("ls $EXPORT")
        for (file in written) assertTrue(file.name in listed, "${file.name} is not in $EXPORT: $listed")
    }

    /** One sheet of [ids], page by page, stacked into [out]. */
    private fun sheet(ids: List<String>, dark: Boolean, tiles: Boolean, out: File) {
        val pages = ids.chunked(if (tiles) TILE_PAGE else DETAIL_PAGE)
        val shots = mutableListOf<Bitmap>()
        runComposeUiTest {
            var page by mutableIntStateOf(0)
            withScreen(dark = dark, locale = "pt-BR", density = if (tiles) null else DETAIL_DENSITY) {
                SheetPage(pages[page], tiles)
            }
            for (index in pages.indices) {
                page = index
                waitForIdle()
                shots += onNodeWithTag(PAGE_TAG).captureToImage().asAndroidBitmap()
            }
        }
        val width = shots.maxOf { it.width }
        val stacked = Bitmap.createBitmap(width, shots.sumOf { it.height }, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(stacked)
        var top = 0f
        for (shot in shots) {
            canvas.drawBitmap(shot, 0f, top, null)
            top += shot.height
            shot.recycle()
        }
        out.outputStream().use { check(stacked.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        stacked.recycle()
    }

    private fun shell(command: String): String {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().decodeToString()
        }
    }

    private companion object {
        const val EXPORT = "/sdcard/Download/mooscles-pilot"

        /** The lot of every shrug, across the families (the shoulder lift's figures). */
        const val SHRUGS = "shrugs"

        /** Exercises per window: two columns of tiles, or three exercises with both frames at the detail size. */
        const val TILE_PAGE = 10
        const val DETAIL_PAGE = 3

        /** 1.35 instead of the emulator's 2.625: two 360 dp frames side by side fit the 1080 px window. */
        const val DETAIL_DENSITY = 1.35f
    }
}

/** One window of the sheet: [ids] in two columns of tiles, or one under the other at the detail size. */
@Composable
private fun SheetPage(ids: List<String>, tiles: Boolean) {
    val portuguese = catalogInPortuguese()
    val catalog = ExerciseCatalog.exercises.associateBy { it.id }
    Column(
        Modifier.wrapContentSize(Alignment.TopStart).testTag(PAGE_TAG)
            .background(MTheme.colors.bgBase).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(if (tiles) MTheme.space.s3 else MTheme.space.s6),
    ) {
        val rows = if (tiles) ids.chunked(2) else ids.map { listOf(it) }
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s4)) {
                for (id in row) {
                    val exercise = catalog.getValue("catalog_$id").let {
                        ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label)
                    }
                    val frames = checkNotNull(ExerciseFigures.forExercise(exercise.id)) { "$id has no figure" }
                    val highlights = (exerciseMedia(exercise) as ExerciseMedia.Figure).highlights
                    val name = ExerciseNames.display(exercise.id, exercise.name, custom = false, portuguese)
                    if (tiles) {
                        TileEntry(name, exercise.equipment, frames, highlights)
                    } else {
                        DetailEntry(name, frames, highlights)
                    }
                }
            }
        }
    }
}

/** The list tile, frame A then frame B, at 64 dp, with the name above (A is never shown in a list). */
@Composable
private fun TileEntry(name: String, equipment: String, frames: FigureFrames, highlights: Set<FigureSegment>) {
    val icon = EquipmentIcons.forEquipment(equipment)
    Column(Modifier.width(TILE_COLUMN), verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        MText(name, MTheme.type.caption, color = MTheme.colors.textSecondary, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
            ExerciseThumbnail(ExerciseMedia.Figure(frames.start, highlights), icon, MTheme.sizes.media)
            ExerciseThumbnail(ExerciseMedia.Figure(frames.end, highlights), icon, MTheme.sizes.media)
        }
    }
}

/** Both frames at the detail size, on the detail's inset square, A on the left. */
@Composable
private fun DetailEntry(name: String, frames: FigureFrames, highlights: Set<FigureSegment>) {
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        MText(name, MTheme.type.titleSm)
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s4)) {
            DetailFrame(frames.start, highlights)
            DetailFrame(frames.end, highlights)
        }
    }
}

@Composable
private fun DetailFrame(pose: FigurePose, highlights: Set<FigureSegment>) {
    ExerciseFigure(
        pose, highlights, contentDescription = null,
        Modifier.size(DETAIL_SIZE).clip(RoundedCornerShape(MTheme.radius.md)).background(MTheme.colors.bgInset),
    )
}

/** Test tag of one window of the sheet. */
private const val PAGE_TAG = "pilot-sheet-page"

/** The detail's frame: a 412 dp phone's width less the 16 dp gutters, capped at 360 dp (spec, detail screen). */
private val DETAIL_SIZE: Dp = 360.dp

/** One exercise of the 64 dp sheet: two tiles and their gap, with room for a two-line name. */
private val TILE_COLUMN: Dp = 176.dp
