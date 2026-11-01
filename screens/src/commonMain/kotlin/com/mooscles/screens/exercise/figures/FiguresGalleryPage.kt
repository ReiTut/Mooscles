package com.mooscles.screens.exercise.figures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.gallery.GalleryExtraPage
import com.mooscles.designsystem.illustration.ExerciseFigure
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureSegment
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_page_figures
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.exerciseMedia
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.workout.EquipmentIcons
import com.mooscles.ui.ExerciseUi
import org.jetbrains.compose.resources.stringResource

/** The gallery's key of the figures page. */
const val FIGURES_GALLERY_KEY = "figures"

/** Test tag of the page's list. */
const val FIGURES_GALLERY_LIST_TAG = "figures-gallery-list"

/** Test tag of one frame on the figures page: `figure-<stableId>-start` or `-end`. */
fun figureFrameTag(stableId: String, start: Boolean): String = "figure-$stableId-${if (start) "start" else "end"}"

/**
 * The design gallery's "Figures" page (plan decision 5), for reviewing the poses: every exercise
 * with a figure, by its shown name, with the list thumbnail (the end frame at `sizes.media`) and the
 * start and end frames side by side at half the width, in a lazy list. It follows the gallery's theme, text size
 * and language controls.
 */
fun figuresGalleryPage(): GalleryExtraPage = GalleryExtraPage(
    key = FIGURES_GALLERY_KEY,
    title = { stringResource(Res.string.gallery_page_figures) },
    content = { FiguresPage() },
)

@Composable
private fun FiguresPage() {
    val portuguese = catalogInPortuguese()
    // Built once: the lots grow this page to the whole catalog, so it is lazy too.
    val entries = remember {
        val catalog = ExerciseCatalog.exercises.associateBy { it.id }
        ExerciseFigures.stableIds.mapNotNull { stableId ->
            val frames = ExerciseFigures.forExercise("${ExerciseCatalog.ID_PREFIX}$stableId") ?: return@mapNotNull null
            val exercise = catalog.getValue("${ExerciseCatalog.ID_PREFIX}$stableId").let {
                ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label)
            }
            FigureEntry(stableId, exercise, frames, exerciseMedia(exercise))
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(FIGURES_GALLERY_LIST_TAG),
        contentPadding = PaddingValues(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
    ) {
        items(entries, key = { it.stableId }) { entry ->
            val highlights = (entry.media as? ExerciseMedia.Figure)?.highlights.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExerciseThumbnail(entry.media, EquipmentIcons.forEquipment(entry.exercise.equipment), MTheme.sizes.media)
                    MText(
                        ExerciseNames.display(entry.exercise.id, entry.exercise.name, custom = false, portuguese),
                        MTheme.type.titleSm,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
                    Frame(entry.frames.start, highlights, figureFrameTag(entry.stableId, start = true), Modifier.weight(1f))
                    Frame(entry.frames.end, highlights, figureFrameTag(entry.stableId, start = false), Modifier.weight(1f))
                }
            }
        }
    }
}

/** One exercise of the page, resolved once. */
private class FigureEntry(val stableId: String, val exercise: ExerciseUi, val frames: FigureFrames, val media: ExerciseMedia)

@Composable
private fun Frame(pose: FigurePose, highlights: Set<FigureSegment>, tag: String, modifier: Modifier) {
    ExerciseFigure(
        pose, highlights, contentDescription = null,
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(MTheme.radius.md))
            .background(MTheme.colors.bgInset)
            .testTag(tag),
    )
}
