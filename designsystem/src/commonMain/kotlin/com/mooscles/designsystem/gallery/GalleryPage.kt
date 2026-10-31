package com.mooscles.designsystem.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.gallery.pages.ButtonsPage
import com.mooscles.designsystem.gallery.pages.ChartsPage
import com.mooscles.designsystem.gallery.pages.ChipsPage
import com.mooscles.designsystem.gallery.pages.ExerciseCardPage
import com.mooscles.designsystem.gallery.pages.FieldsPage
import com.mooscles.designsystem.gallery.pages.FoundationsPage
import com.mooscles.designsystem.gallery.pages.IconsPage
import com.mooscles.designsystem.gallery.pages.OverlaysPage
import com.mooscles.designsystem.gallery.pages.RecordsPage
import com.mooscles.designsystem.gallery.pages.ReorderPage
import com.mooscles.designsystem.gallery.pages.RowsPage
import com.mooscles.designsystem.gallery.pages.SetRowPage
import com.mooscles.designsystem.gallery.pages.ShellPage
import com.mooscles.designsystem.gallery.pages.StatesPage
import com.mooscles.designsystem.gallery.pages.TimerPage
import com.mooscles.designsystem.gallery.pages.WorkoutInputsPage
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_page_buttons
import com.mooscles.designsystem.resources.gallery_page_charts
import com.mooscles.designsystem.resources.gallery_page_chips
import com.mooscles.designsystem.resources.gallery_page_exercise_card
import com.mooscles.designsystem.resources.gallery_page_fields
import com.mooscles.designsystem.resources.gallery_page_foundations
import com.mooscles.designsystem.resources.gallery_page_icons
import com.mooscles.designsystem.resources.gallery_page_overlays
import com.mooscles.designsystem.resources.gallery_page_records
import com.mooscles.designsystem.resources.gallery_page_reorder
import com.mooscles.designsystem.resources.gallery_page_rows
import com.mooscles.designsystem.resources.gallery_page_set_row
import com.mooscles.designsystem.resources.gallery_page_shell
import com.mooscles.designsystem.resources.gallery_page_states
import com.mooscles.designsystem.resources.gallery_page_timer
import com.mooscles.designsystem.resources.gallery_page_workout_inputs
import org.jetbrains.compose.resources.StringResource

/** The pages of the design gallery. Each batch of components adds its own entry. */
enum class GalleryPage(val title: StringResource) {
    Foundations(Res.string.gallery_page_foundations),
    Icons(Res.string.gallery_page_icons),
    Buttons(Res.string.gallery_page_buttons),
    Chips(Res.string.gallery_page_chips),
    Rows(Res.string.gallery_page_rows),
    Fields(Res.string.gallery_page_fields),
    States(Res.string.gallery_page_states),
    Overlays(Res.string.gallery_page_overlays),
    WorkoutInputs(Res.string.gallery_page_workout_inputs),
    SetRow(Res.string.gallery_page_set_row),
    Timer(Res.string.gallery_page_timer),
    Records(Res.string.gallery_page_records),
    ExerciseCard(Res.string.gallery_page_exercise_card),
    Shell(Res.string.gallery_page_shell),
    Reorder(Res.string.gallery_page_reorder),
    Charts(Res.string.gallery_page_charts),
}

fun galleryPageTag(page: GalleryPage): String = "gallery-page-${page.name}"

fun galleryEntryTag(page: GalleryPage): String = "gallery-entry-${page.name}"

/** The number of items on the reordering page. */
const val GALLERY_REORDER_COUNT = 6

/** The tag of the reordering page's item [index] (its key: the place it started in). */
fun galleryReorderItemTag(index: Int): String = "gallery-reorder-$index"

/**
 * A gallery page the design system does not own, given by its host (plan decision 5: the figure
 * poses live in `:screens`, which the design system cannot see). [key] identifies it in
 * [GalleryState.openExtra] and in its tags; it is listed after the design system's own pages.
 */
class GalleryExtraPage(val key: String, val title: @Composable () -> String, val content: @Composable () -> Unit)

fun galleryExtraPageTag(key: String): String = "gallery-page-extra-$key"

fun galleryExtraEntryTag(key: String): String = "gallery-entry-extra-$key"

/** The page body, tagged so tests can find it. */
@Composable
fun GalleryPage.Content() {
    Box(Modifier.testTag(galleryPageTag(this))) {
        when (this@Content) {
            GalleryPage.Foundations -> FoundationsPage()
            GalleryPage.Icons -> IconsPage()
            GalleryPage.Buttons -> ButtonsPage()
            GalleryPage.Chips -> ChipsPage()
            GalleryPage.Rows -> RowsPage()
            GalleryPage.Fields -> FieldsPage()
            GalleryPage.States -> StatesPage()
            GalleryPage.Overlays -> OverlaysPage()
            GalleryPage.WorkoutInputs -> WorkoutInputsPage()
            GalleryPage.SetRow -> SetRowPage()
            GalleryPage.Timer -> TimerPage()
            GalleryPage.Records -> RecordsPage()
            GalleryPage.ExerciseCard -> ExerciseCardPage()
            GalleryPage.Shell -> ShellPage()
            GalleryPage.Reorder -> ReorderPage()
            GalleryPage.Charts -> ChartsPage()
        }
    }
}
