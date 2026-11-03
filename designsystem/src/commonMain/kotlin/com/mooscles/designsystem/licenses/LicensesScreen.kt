package com.mooscles.designsystem.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_licenses_mooscles_media
import com.mooscles.designsystem.resources.ds_licenses_title
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The tag of the screen's scrolling content. */
const val LicensesContentTag = "licenses-content"

/** The tag of a divider drawn for a rule line ("-----") of a license file. */
const val LicenseRuleTag = "license-rule"

/** The name of a bundled work on the licenses screen. */
private sealed interface LicenseName {
    /** A proper name, the same in every language ("Inter", "MuscleMap"). */
    class Proper(val text: String) : LicenseName

    /** A name that is translated, read from the strings. */
    class Translated(val resource: StringResource) : LicenseName
}

@Composable
private fun LicenseName.text(): String = when (this) {
    is LicenseName.Proper -> text
    is LicenseName.Translated -> stringResource(resource)
}

/**
 * A bundled work and its license. The license name is a proper name, the same in every language,
 * and the text is the license file shipped in `files/licenses/`, with its words unchanged; the
 * screen only reflows its hard-wrapped lines (see [licenseBlocks]).
 */
private class BundledLicense(val name: LicenseName, val license: String, val path: String) {
    constructor(name: String, license: String, path: String) : this(LicenseName.Proper(name), license, path)
}

private val bundled = listOf(
    BundledLicense("Inter", "SIL Open Font License 1.1", "files/licenses/inter_ofl.txt"),
    BundledLicense("Barlow Condensed", "SIL Open Font License 1.1", "files/licenses/barlow_condensed_ofl.txt"),
    BundledLicense("Lucide", "ISC License", "files/licenses/lucide_isc.txt"),
    // The body map, adapted from MuscleMap (spec D6 and D9); the file names the source commit.
    BundledLicense("MuscleMap", "MIT License", "files/licenses/musclemap_mit.txt"),
    // The own figures, poses, tips and pt-BR names (spec D9). Plain text despite the extension.
    BundledLicense(
        LicenseName.Translated(Res.string.ds_licenses_mooscles_media),
        "CC0 1.0 Universal",
        "files/licenses/media-CC0.md",
    ),
)

/**
 * The licenses the app must show: Inter and Barlow Condensed (OFL), the Lucide icons (ISC), the
 * MuscleMap body map (MIT) and the app's own illustrations, exercise tips and names (CC0), each
 * with its full notice read from the bundled files.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val texts by produceState(emptyMap<String, String>()) {
        value = bundled.associate { it.path to Res.readBytes(it.path).decodeToString() }
    }
    val space = MTheme.space
    Column(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        TopBar(stringResource(Res.string.ds_licenses_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(LicensesContentTag)
                .verticalScroll(rememberScrollState())
                .padding(bottom = space.s6),
        ) {
            bundled.forEach { entry ->
                SectionHeader(entry.name.text())
                MText(
                    entry.license,
                    MTheme.type.labelLg,
                    color = MTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = space.s4),
                )
                texts[entry.path]?.let { text ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = space.s4, vertical = space.s3),
                        verticalArrangement = Arrangement.spacedBy(space.s3),
                    ) {
                        remember(text) { licenseBlocks(text) }.forEach { block ->
                            when (block) {
                                is LicenseBlock.Paragraph ->
                                    MText(block.text, MTheme.type.bodyMd, color = MTheme.colors.textSecondary, modifier = Modifier.fillMaxWidth())
                                LicenseBlock.Rule -> Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(MTheme.sizes.border)
                                        .background(MTheme.colors.borderSubtle)
                                        .testTag(LicenseRuleTag),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
