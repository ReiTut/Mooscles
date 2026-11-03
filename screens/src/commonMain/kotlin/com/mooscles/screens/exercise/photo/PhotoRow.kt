package com.mooscles.screens.exercise.photo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.designsystem.components.ExerciseThumbnail
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.exercise_form_photo
import com.mooscles.screens.resources.exercise_form_photo_camera
import com.mooscles.screens.resources.exercise_form_photo_gallery
import com.mooscles.screens.resources.exercise_form_photo_remove
import org.jetbrains.compose.resources.stringResource

/** The test tags of [PhotoRow]. */
internal object PhotoRowTags {
    /** The preview tile while there is a photo. */
    const val PREVIEW = "exercise-form-photo-preview"

    /** The preview tile while there is none (the equipment's icon). */
    const val EMPTY = "exercise-form-photo-empty"
    /** The placeholder shown while a chosen photo is prepared. */
    const val PREPARING = "exercise-form-photo-preparing"
    const val GALLERY = "exercise-form-photo-gallery"
    const val CAMERA = "exercise-form-photo-camera"
    const val REMOVE = "exercise-form-photo-remove"
}

/**
 * The content of the form's "Photo" section (decision 12): the square preview of the photo (the
 * equipment's [icon] while there is none) and, beside it, "Choose from gallery", "Take photo" (only with
 * [cameraAvailable]) and, when [canRemove], "Remove photo". The buttons wrap under the preview with large text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoRow(
    preview: ImageBitmap?,
    icon: ImageVector,
    preparing: Boolean,
    canRemove: Boolean,
    cameraAvailable: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.exercise_form_photo)
    val media = remember(preview) { preview?.let { ExerciseMedia.Image(BitmapPainter(it)) } ?: ExerciseMedia.None }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3), verticalAlignment = Alignment.Top) {
        if (preparing) {
            Skeleton(Modifier.size(MTheme.sizes.media).testTag(PhotoRowTags.PREPARING), RoundedCornerShape(MTheme.radius.md))
        } else ExerciseThumbnail(
            media,
            icon,
            MTheme.sizes.media,
            Modifier
                .testTag(if (preview != null) PhotoRowTags.PREVIEW else PhotoRowTags.EMPTY)
                .then(if (preview != null) Modifier.semantics { contentDescription = label } else Modifier.clearAndSetSemantics {}),
        )
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        ) {
            MButton(
                stringResource(Res.string.exercise_form_photo_gallery),
                onClick = onGallery,
                modifier = Modifier.testTag(PhotoRowTags.GALLERY),
                variant = MButtonVariant.Secondary,
            )
            if (cameraAvailable) {
                MButton(
                    stringResource(Res.string.exercise_form_photo_camera),
                    onClick = onCamera,
                    modifier = Modifier.testTag(PhotoRowTags.CAMERA),
                    variant = MButtonVariant.Secondary,
                )
            }
            if (canRemove) {
                MButton(
                    stringResource(Res.string.exercise_form_photo_remove),
                    onClick = onRemove,
                    modifier = Modifier.testTag(PhotoRowTags.REMOVE),
                    variant = MButtonVariant.Text,
                )
            }
        }
    }
}
