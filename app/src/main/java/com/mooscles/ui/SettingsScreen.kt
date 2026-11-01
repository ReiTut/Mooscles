package com.mooscles.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.RadioGroup
import com.mooscles.designsystem.components.RadioRow
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.SettingsRow
import com.mooscles.designsystem.components.SwitchRow
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_licenses_title
import com.mooscles.designsystem.resources.settings_appearance
import com.mooscles.designsystem.resources.settings_auto_rest
import com.mooscles.designsystem.resources.settings_auto_rest_desc
import com.mooscles.designsystem.resources.settings_auto_rest_hint
import com.mooscles.designsystem.resources.settings_default_rest
import com.mooscles.designsystem.resources.settings_privacy_body
import com.mooscles.designsystem.resources.settings_privacy_title
import com.mooscles.designsystem.resources.settings_rest_each_exercise
import com.mooscles.designsystem.resources.settings_theme_dark
import com.mooscles.designsystem.resources.settings_theme_light
import com.mooscles.designsystem.resources.settings_theme_system
import com.mooscles.designsystem.resources.settings_title
import com.mooscles.designsystem.resources.settings_unit_kg
import com.mooscles.designsystem.resources.settings_unit_lb
import com.mooscles.designsystem.resources.settings_units_hint
import com.mooscles.designsystem.resources.settings_weight_units
import com.mooscles.designsystem.theme.MTheme
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Routes and arguments of Settings and the screens it opens. */
object SettingsRoutes {
    /** The Settings route; `section` is "" (top of the screen) or [UPDATES]. */
    const val SETTINGS = "settings?section={section}"
    const val SECTION_ARG = "section"
    const val UPDATES = "updates"
    const val LICENSES = "licenses"

    /** Settings opened at [section]. */
    fun settings(section: String = ""): String = if (section.isEmpty()) "settings" else "settings?section=$section"
}

/**
 * Settings, built only with the design system. The sections follow the spec: appearance,
 * weight units, automatic rest, your data, app updates, privacy and the open-source licenses.
 * With [section] equal to [SettingsRoutes.UPDATES] the screen opens scrolled to the updates,
 * once per visit (coming back from another screen keeps the user's own scroll).
 */
@Composable
fun SettingsScreen(
    state: UiState,
    actions: MoosclesActions,
    section: String = "",
    onOpenLicenses: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
) {
    val prefs = state.preferences
    val space = MTheme.space
    val scroll = rememberScrollState()
    var updatesTop by remember { mutableStateOf<Int?>(null) }
    var sectionShown by rememberSaveable(section) { mutableStateOf(false) }
    LaunchedEffect(updatesTop) {
        val top = updatesTop
        if (section == SettingsRoutes.UPDATES && !sectionShown && top != null) {
            scroll.scrollTo(top)
            sectionShown = true
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MTheme.colors.bgBase)
            .testTag("settings-content")
            .verticalScroll(scroll)
            .padding(bottom = space.s6),
    ) {
        SectionHeader(stringResource(Res.string.settings_appearance))
        RadioGroup(Modifier.testTag("theme-group")) {
            listOf(
                "system" to Res.string.settings_theme_system,
                "light" to Res.string.settings_theme_light,
                "dark" to Res.string.settings_theme_dark,
            ).forEach { (theme, label) ->
                // An unknown stored value (none of the three) selects no row.
                RadioRow(stringResource(label), selected = prefs.theme == theme, onSelect = { actions.setTheme(theme) },
                    modifier = Modifier.testTag("theme-$theme"))
            }
        }

        SectionHeader(stringResource(Res.string.settings_weight_units))
        RadioGroup(Modifier.testTag("unit-group")) {
            RadioRow(stringResource(Res.string.settings_unit_kg), selected = !prefs.pounds, onSelect = { actions.setPounds(false) },
                modifier = Modifier.testTag("unit-kg"))
            RadioRow(stringResource(Res.string.settings_unit_lb), selected = prefs.pounds, onSelect = { actions.setPounds(true) },
                modifier = Modifier.testTag("unit-lb"))
        }
        SettingsNote(stringResource(Res.string.settings_units_hint))

        SectionHeader(stringResource(Res.string.settings_auto_rest))
        SwitchRow(stringResource(Res.string.settings_auto_rest_desc), prefs.autoRest, actions::setAutoRest,
            Modifier.testTag("auto-rest"))
        SettingsNote(stringResource(Res.string.settings_auto_rest_hint))
        SettingsRestDurationField(
            prefs.defaultRestSeconds, actions::setDefaultRest,
            Modifier.fillMaxWidth().padding(horizontal = space.s4, vertical = space.s2),
            label = stringResource(Res.string.settings_default_rest), testTag = "default-rest-duration",
        )
        SettingsNote(stringResource(Res.string.settings_rest_each_exercise))

        DataSection(state.backup, workoutActive = state.active != null, actions = actions,
            modifier = Modifier.padding(top = space.s4))

        UpdateSection(
            state.updates, actions,
            // First in the chain, so the position is the section's own place in the column.
            Modifier
                .onGloballyPositioned { updatesTop = it.positionInParent().y.roundToInt() }
                .padding(top = space.s4),
            onOpenGallery = onOpenGallery,
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.s4, vertical = space.s6),
            horizontalArrangement = Arrangement.spacedBy(space.s3),
            verticalAlignment = Alignment.Top,
        ) {
            MIcon(MIcons.Lock, contentDescription = null, size = MTheme.sizes.icon24, tint = MTheme.colors.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                MText(stringResource(Res.string.settings_privacy_title), MTheme.type.titleSm, modifier = Modifier.semantics { heading() })
                MText(stringResource(Res.string.settings_privacy_body), MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
            }
        }

        SettingsRow(stringResource(Res.string.ds_licenses_title), Modifier.testTag("open-licenses"), onClick = onOpenLicenses)
    }
}

/**
 * The top bar of the Settings route: back and the title, which is the only "Settings" text.
 * The scaffold composes it after the content, so it is its own traversal group placed first:
 * a screen reader reads Back and the title before the settings.
 */
@Composable
fun SettingsTopBar(onBack: () -> Unit) {
    TopBar(
        stringResource(Res.string.settings_title),
        Modifier
            .testTag(SETTINGS_TOP_BAR_TAG)
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
            .background(MTheme.colors.bgBase)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        onBack = onBack,
    )
}

/** The tag of [SettingsTopBar]. */
const val SETTINGS_TOP_BAR_TAG = "settings-top-bar"

/** A secondary explanation under a group of settings. */
@Composable
private fun SettingsNote(text: String) {
    MText(text, MTheme.type.bodyMd, color = MTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s1))
}
