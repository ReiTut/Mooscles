package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import com.mooscles.designsystem.components.ExerciseCard
import com.mooscles.designsystem.components.ExerciseCardFooter
import com.mooscles.designsystem.components.ExerciseCardHeader
import com.mooscles.designsystem.components.ExerciseCardSegment
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.SetRow
import com.mooscles.designsystem.components.SetRowState
import com.mooscles.designsystem.components.SetStatus
import com.mooscles.designsystem.components.SetType
import com.mooscles.designsystem.components.setsDoneOf
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_caption
import com.mooscles.designsystem.resources.gallery_sample_exercise
import com.mooscles.designsystem.resources.gallery_sample_exercise_meta
import com.mooscles.designsystem.resources.gallery_sample_rest_chip
import com.mooscles.designsystem.resources.gallery_section_exercise_card
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val TOTAL = 2

private fun row(index: Int, status: SetStatus) = SetRowState(
    index = index, total = TOTAL, previous = null, previousSpoken = null,
    load = "80", loadSpoken = "80", reps = 8, type = SetType.Normal, status = status,
)

/** A painter that has not loaded yet: it draws nothing, so the placeholder under it shows. */
private object LoadingPainter : Painter() {
    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
}

/**
 * The card while its image loads, with an image, with the offline note, with nothing, and
 * collapsed; then the segmented card (header, one segment per set, footer) that toggles, and its
 * collapsed headers, done and not.
 */
@Composable
fun ExerciseCardPage() {
    val name = stringResource(Res.string.gallery_sample_exercise)
    val meta = stringResource(Res.string.gallery_sample_exercise_meta)
    val media = listOf(
        "media = Image, loading" to ExerciseMedia.Image(LoadingPainter),
        "media = Image" to ExerciseMedia.Image(ColorPainter(MTheme.colors.accentSubtle)),
        "media = Offline" to ExerciseMedia.Offline,
        "media = None" to ExerciseMedia.None,
    )
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_exercise_card))
        for ((label, item) in media) {
            Sample(label) {
                ExerciseCard(name, meta, item, MIcons.Barbell, onMenu = {}, onAddSet = {}) {
                    SetRow(row(1, SetStatus.Completed), {}, {}, {}, {}, {})
                    SetRow(row(2, SetStatus.Pending), {}, {}, {}, {}, {})
                }
            }
        }
        Sample("collapsed = true") {
            ExerciseCard(name, meta, ExerciseMedia.None, MIcons.Barbell, onMenu = {}, onAddSet = {}, collapsed = true) {}
        }
        SegmentedSample(name, meta)
        Sample("ExerciseCardHeader, collapsed, done") {
            ExerciseCardHeader(
                name, meta, ExerciseMedia.None, MIcons.Barbell, onMenu = {}, collapsed = true, onToggle = {},
                progress = setsDoneOf(TOTAL, TOTAL), done = true,
            )
        }
    }
}

/** Header, a segment per set and footer, as a lazy list draws them; the header opens and closes it. */
@Composable
private fun SegmentedSample(name: String, meta: String) {
    var collapsed by remember { mutableStateOf(false) }
    val rest = stringResource(Res.string.gallery_sample_rest_chip)
    Sample("ExerciseCardHeader + ExerciseCardSegment + ExerciseCardFooter") {
        Column {
            ExerciseCardHeader(
                name, meta, ExerciseMedia.None, MIcons.Barbell, onMenu = {}, collapsed = collapsed, onToggle = { collapsed = !collapsed },
                progress = setsDoneOf(1, TOTAL), caption = stringResource(Res.string.gallery_sample_caption),
                support = { MChip(rest, selected = false, onClick = {}) },
            )
            if (!collapsed) {
                ExerciseCardSegment { SetRow(row(1, SetStatus.Completed), {}, {}, {}, {}, {}) }
                ExerciseCardSegment { SetRow(row(2, SetStatus.Pending), {}, {}, {}, {}, {}) }
                ExerciseCardFooter(onAddSet = {})
            }
        }
    }
}
