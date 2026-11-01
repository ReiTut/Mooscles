package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.ErrorBanner
import com.mooscles.designsystem.components.MProgressBar
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_banner
import com.mooscles.designsystem.resources.gallery_sample_empty_body
import com.mooscles.designsystem.resources.gallery_sample_empty_title
import com.mooscles.designsystem.resources.gallery_sample_not_now
import com.mooscles.designsystem.resources.gallery_sample_retry
import com.mooscles.designsystem.resources.gallery_sample_start
import com.mooscles.designsystem.resources.gallery_section_states
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** Progress (determinate and indeterminate), the error banner, an empty state and skeletons. */
@Composable
fun StatesPage() {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_states))
        MProgressBar(progress = 0.4f)
        MProgressBar(progress = null)
        ErrorBanner(
            stringResource(Res.string.gallery_sample_banner),
            actionLabel = stringResource(Res.string.gallery_sample_retry),
            onAction = {},
        )
        EmptyState(
            icon = MIcons.FileText,
            title = stringResource(Res.string.gallery_sample_empty_title),
            body = stringResource(Res.string.gallery_sample_empty_body),
            primaryAction = stringResource(Res.string.gallery_sample_start) to {},
            secondaryAction = stringResource(Res.string.gallery_sample_not_now) to {},
        )
        Skeleton(Modifier.fillMaxWidth().height(MTheme.sizes.buttonM))
        Skeleton(Modifier.fillMaxWidth(fraction = MTheme.sizes.progressIndeterminateFraction * 2).height(MTheme.sizes.buttonS))
    }
}
