package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_section_icons
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** Every icon with its name at 16, 20, 24 and 28. */
@Composable
fun IconsPage() {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_icons))
        for ((name, icon) in MIcons.all) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (size in listOf(MTheme.sizes.icon16, MTheme.sizes.icon20, MTheme.sizes.icon24, MTheme.sizes.icon28)) {
                    MIcon(icon, contentDescription = null, size = size)
                }
                BasicText(
                    name,
                    modifier = Modifier.width(MTheme.sizes.media * 2),
                    style = MTheme.type.caption.copy(color = MTheme.colors.textSecondary),
                )
            }
        }
    }
}
