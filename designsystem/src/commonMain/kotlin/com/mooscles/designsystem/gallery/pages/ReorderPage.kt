package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import com.mooscles.designsystem.components.ExerciseCardHeader
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.gallery.GALLERY_REORDER_COUNT
import com.mooscles.designsystem.gallery.galleryReorderItemTag
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.ReorderAnnouncer
import com.mooscles.designsystem.interaction.ReorderHandle
import com.mooscles.designsystem.interaction.rememberReorderState
import com.mooscles.designsystem.interaction.reorderItem
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_reorder_hint
import com.mooscles.designsystem.resources.gallery_sample_deadlift
import com.mooscles.designsystem.resources.gallery_sample_exercise
import com.mooscles.designsystem.resources.gallery_sample_exercise_meta
import com.mooscles.designsystem.resources.gallery_sample_exercise_next
import com.mooscles.designsystem.resources.gallery_sample_lateral_raise
import com.mooscles.designsystem.resources.gallery_sample_pull_up
import com.mooscles.designsystem.resources.gallery_sample_squat
import com.mooscles.designsystem.resources.gallery_sample_target
import com.mooscles.designsystem.resources.gallery_section_reorder
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private val names = listOf(
    Res.string.gallery_sample_exercise,
    Res.string.gallery_sample_squat,
    Res.string.gallery_sample_deadlift,
    Res.string.gallery_sample_exercise_next,
    Res.string.gallery_sample_pull_up,
    Res.string.gallery_sample_lateral_raise,
)

/**
 * Phase 4B (spike S2): a list to reorder by dragging the handles, with items of three heights (a
 * card header with the target line, one without, a plain row). Screen readers get the four move
 * actions on each handle, and the announcer says where it went.
 */
@Composable
fun ReorderPage() {
    val labels = names.map { stringResource(it) }
    val meta = stringResource(Res.string.gallery_sample_exercise_meta)
    val target = AnnotatedString(stringResource(Res.string.gallery_sample_target))
    var keys by remember { mutableStateOf(List(GALLERY_REORDER_COUNT) { it }) }
    val listState = rememberLazyListState()
    val state = rememberReorderState(listState, keys, onDrop = { keys = it })
    val space = MTheme.space
    Column(Modifier.fillMaxSize().padding(horizontal = space.s4)) {
        Column(Modifier.padding(top = space.s4), verticalArrangement = Arrangement.spacedBy(space.s2)) {
            PageHeader(stringResource(Res.string.gallery_section_reorder))
            MText(stringResource(Res.string.gallery_reorder_hint), MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = space.s4),
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                items(state.order, key = { it }) { index ->
                    val name = labels[index]
                    Box(Modifier.reorderItem(state, index).testTag(galleryReorderItemTag(index))) {
                        val handle: @Composable () -> Unit = { ReorderHandle(state, index, name) }
                        when (index % 3) {
                            0 -> ExerciseCardHeader(
                                name, meta, ExerciseMedia.None, MIcons.Barbell, onMenu = {}, collapsed = true, onToggle = {},
                                handle = handle, subline = target,
                            )
                            1 -> ExerciseCardHeader(
                                name, meta, ExerciseMedia.None, MIcons.Dumbbell, onMenu = {}, collapsed = true, onToggle = {},
                                handle = handle,
                            )
                            else -> Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MTheme.sizes.rowMin)
                                    .background(MTheme.colors.bgSurface, RoundedCornerShape(MTheme.radius.lg))
                                    .padding(end = space.s4),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                handle()
                                MText(name, MTheme.type.bodyLg)
                            }
                        }
                    }
                }
            }
            ReorderAnnouncer(state)
        }
    }
}
