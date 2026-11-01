package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.components.PrToast
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_pr_toast_before
import com.mooscles.designsystem.resources.ds_pr_toast_title
import com.mooscles.designsystem.resources.gallery_sample_exercise
import com.mooscles.designsystem.resources.gallery_sample_pr_detail
import com.mooscles.designsystem.resources.gallery_section_records
import com.mooscles.designsystem.resources.gallery_show_pr_toast
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The record badge, soft and solid, and the record toast, which a button shows. The toast
 * shows at the top of the area under the section header, as it would under a screen's top
 * bar: it covers the samples, never the header that names them.
 */
@Composable
fun RecordsPage() {
    val exercise = stringResource(Res.string.gallery_sample_exercise)
    val detail = stringResource(Res.string.gallery_sample_pr_detail)
    val title = stringResource(Res.string.ds_pr_toast_title, exercise)
    val before = stringResource(Res.string.ds_pr_toast_before, "80 kg × 6")
    var shown by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(MTheme.space.s4), verticalArrangement = Arrangement.spacedBy(MTheme.space.s4)) {
        PageHeader(stringResource(Res.string.gallery_section_records))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s4)) {
                PrBadge()
                PrBadge(soft = true)
                MButton(stringResource(Res.string.gallery_show_pr_toast), { shown = true }, variant = MButtonVariant.Secondary)
            }
            // The card brings its own `space.s4` margin; the page padding already gives it.
            PrToast(title, "$detail · $before", shown, { shown = false }, Modifier.align(Alignment.TopCenter).bleedHorizontally(MTheme.space.s4))
        }
    }
}
