package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.font.FontFamily
import com.mooscles.designsystem.tokens.MoosclesColors
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mooscles.backup.ImportSummary
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.ui.BackupUi
import com.mooscles.ui.DeveloperGallery
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.MoosclesApp
import com.mooscles.ui.MoosclesTheme
import com.mooscles.ui.SETTINGS_TOP_BAR_TAG
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.SettingsScreen
import com.mooscles.ui.UiState
import com.mooscles.ui.UpdatePhase
import com.mooscles.ui.UpdateUi
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsScreenHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun lock() = DeveloperGallery.resetForTest()

    @After fun relock() {
        LocalAppLocale.restore()
        DeveloperGallery.resetForTest()
    }

    private val installed = UpdateUi(installedVersionName = "1.4.5", installedVersionCode = 16)

    /**
     * Characterization: written against the Settings screen as it shipped before the design
     * system, and kept unchanged after the rebuild. Every control calls the same action with the
     * same value.
     */
    @Test
    fun everyControlCallsTheSameActionsAsBefore() {
        val calls = mutableListOf<String>()
        val state = mutableStateOf(UiState(loading = false, updates = installed))
        compose.setContent { MoosclesTheme("light") { SettingsScreen(state.value, recording(calls)) } }

        listOf("System", "Light", "Dark", "Kilograms · kg", "Pounds · lb").forEach { reach(hasText(it)).performClick() }
        // The automatic rest switch is the only toggle outside the updater.
        reach(isToggleable() and !hasTestTag("update-automatic") and !hasTestTag("update-wifi-only") and
            !hasTestTag("update-prereleases")).performClick()
        reach(hasTestTag("default-rest-duration")).performTextReplacement("1:30")
        listOf("update-automatic", "update-wifi-only", "update-prereleases", "update-check").forEach { reach(hasTestTag(it)).performClick() }
        // The document pickers return nothing here, so the two buttons call no action by themselves.
        reach(hasTestTag("data-export")).performClick()
        reach(hasTestTag("data-import")).performClick()

        fun phase(tag: String, update: UpdateUi) {
            compose.runOnIdle { state.value = state.value.copy(updates = update) }
            reach(hasTestTag(tag)).performClick()
        }
        val offered = installed.copy(availableVersionName = "1.5.0", availableVersionCode = 17)
        phase("update-download", offered.copy(phase = UpdatePhase.AVAILABLE))
        phase("update-cancel", offered.copy(phase = UpdatePhase.DOWNLOADING, downloadedBytes = 1_048_576, totalBytes = 2_097_152))
        phase("update-install", offered.copy(phase = UpdatePhase.READY, verifiedApkReady = true))
        phase("update-install", offered.copy(phase = UpdatePhase.PERMISSION_REQUIRED, verifiedApkReady = true))
        phase("update-install", offered.copy(phase = UpdatePhase.APPROVAL_REQUIRED, verifiedApkReady = true))
        phase("update-retry", offered.copy(phase = UpdatePhase.FAILED, verifiedApkReady = true))
        phase("update-retry", offered.copy(phase = UpdatePhase.FAILED))
        phase("update-retry", installed.copy(phase = UpdatePhase.FAILED))
        phase("update-install", offered.copy(phase = UpdatePhase.CANCELLED, verifiedApkReady = true))
        phase("update-download", offered.copy(phase = UpdatePhase.CANCELLED))
        phase("update-cancel", offered.copy(phase = UpdatePhase.WAITING_FOR_WIFI))

        compose.runOnIdle { state.value = state.value.copy(backup = BackupUi(message = "Exported 1 workout, 0 routines and 0 custom exercises")) }
        reach(hasTestTag("data-message-dismiss")).performClick()
        fun dialog(summary: ImportSummary, button: String) {
            compose.runOnIdle { state.value = state.value.copy(backup = BackupUi(pendingImport = summary)) }
            compose.onNodeWithText(button).performClick()
            compose.runOnIdle { state.value = state.value.copy(backup = BackupUi()) }
        }
        val news = ImportSummary(2, 0, 1, 0, 0, false, nothingToAdd = false)
        dialog(news, "Import")
        dialog(news, "Cancel")
        dialog(ImportSummary(0, 5, 0, 2, 0, false, nothingToAdd = true), "OK")

        compose.runOnIdle {
            assertEquals(
                listOf(
                    "setTheme:system", "setTheme:light", "setTheme:dark", "setPounds:false", "setPounds:true",
                    "setAutoRest:false", "setDefaultRest:90",
                    "setAutomaticUpdates:false", "setUpdateWifiOnly:false", "setIncludePrereleases:true", "checkForUpdates",
                    "downloadUpdate", "cancelUpdate", "installUpdate", "installUpdate", "installUpdate",
                    "installUpdate", "downloadUpdate", "checkForUpdates", "installUpdate", "downloadUpdate", "cancelUpdate",
                    "clearBackupMessage", "confirmImport", "cancelImport", "cancelImport",
                ),
                calls,
            )
        }
    }

    @Test
    fun sectionsFollowTheSpecOrder() {
        showSettings(UiState(loading = false, updates = installed))
        val sections = listOf("Appearance", "Weight units", "Automatic rest", "Your data", "App updates", "Just you and your progress")
        sections.forEach { compose.onNode(hasText(it) and isHeading(), useUnmergedTree = true).assertExists() }
        val tops = (sections + "Open-source licenses").map { text ->
            compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().fetchSemanticsNode().positionInRoot.y
        }
        assertEquals("Sections out of order: $tops", tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
        // The old page heading and its subtitle are gone; the top bar says "Settings".
        compose.onNodeWithText("Make it yours", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun unknownThemeValueSelectsNoRadioAndStillRenders() {
        showSettings(UiState(loading = false, updates = installed, preferences = PreferencesUi(theme = "amoled")))
        listOf("theme-system", "theme-light", "theme-dark").forEach { compose.onNodeWithTag(it).assertIsNotSelected() }
        compose.onNodeWithTag("unit-kg").assertIsSelected()
        compose.onNodeWithTag("app-updates").performScrollTo().assertExists()
    }

    @Test
    fun updateRequestOpensSettingsAtTheUpdatesSection() {
        val state = mutableStateOf(UiState(loading = false, updates = installed))
        compose.setContent { MoosclesApp(state.value, recording(mutableListOf())) }
        compose.onNodeWithTag("tab-routines").assertIsSelected()

        // From another screen.
        compose.runOnIdle { state.value = state.value.copy(updateSettingsRequest = 1) }
        assertUpdatesShownWithoutScrolling()

        // From Settings already open, scrolled back to the top.
        compose.onNodeWithTag("settings-content").performScrollToNode(hasTestTag("theme-system"))
        compose.onNodeWithText("App updates").assertIsNotDisplayed()
        compose.runOnIdle { state.value = state.value.copy(updateSettingsRequest = 2) }
        assertUpdatesShownWithoutScrolling()

        // Settings replaced itself: one back returns to Train.
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab-routines").assertIsSelected()
        compose.onNodeWithTag("app-updates").assertDoesNotExist()
    }

    private fun assertUpdatesShownWithoutScrolling() {
        compose.onNodeWithTag("app-updates").assertIsDisplayed()
        // The section header itself is on screen, at the top of the content.
        compose.onNodeWithText("App updates").assertIsDisplayed()
        val header = compose.onNodeWithText("App updates").fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("settings-content").fetchSemanticsNode().boundsInRoot
        assertTrue("header $header outside $content", header.top >= content.top && header.bottom <= content.bottom)
    }

    @Test
    fun settingsTitleIsTheOnlySettingsTextAndBackLeaves() {
        compose.setContent { MoosclesApp(UiState(loading = false, updates = installed), recording(mutableListOf())) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNode(hasText("Settings") and isHeading()).assertExists()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("tab-routines").assertIsSelected()
    }

    /**
     * Review focus 3: a 320 dp phone at 200% text in Portuguese, with the longest updater titles.
     * Every control scrolls into view with a 48 dp target, and no button label is cut: no word
     * is broken, nothing passes the button or the screen, and the lines fit their height.
     */
    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h640dp")
    // Real text measurement: the legacy graphics measure one pixel per character.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyControlIsReachableAtTwoHundredPercentOn320dpInPortuguese() {
        DeveloperGallery.registerVersionTap(0)
        repeat(DeveloperGallery.TAPS - 1) { DeveloperGallery.registerVersionTap(100L * (it + 1)) }
        val notes = "Melhorias no registro de treino. ".repeat(20)
        val offered = installed.copy(availableVersionName = "1.5.0", availableVersionCode = 17, releaseNotes = notes,
            message = "The download will resume when the connection returns.", lastCheckedAt = 1_798_000_000_000L)
        val state = mutableStateOf(UiState(loading = false, updates = offered.copy(phase = UpdatePhase.DEFERRED),
            backup = BackupUi(message = "This file is not a Mooscles backup.", messageIsError = true)))
        compose.setContent {
            ProvideAppLocale("pt-BR") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    MoosclesTheme("light") { SettingsScreen(state.value, recording(mutableListOf())) }
                }
            }
        }
        compose.onNodeWithText("Atualização pronta · instalação adiada").performScrollTo().assertIsDisplayed()
        val screenWidth = compose.onNodeWithTag("settings-content").fetchSemanticsNode().boundsInRoot.width
        assertTrue("not a 320 dp screen: $screenWidth px", screenWidth <= with(compose.density) { 320.dp.toPx() } + 1f)

        val buttons = listOf("data-export", "data-import", "data-message-dismiss", "update-check", "update-read-all")
        val controls = listOf("theme-system", "theme-light", "theme-dark", "unit-kg", "unit-lb", "auto-rest",
            "default-rest-duration", "update-installed-version", "update-automatic", "update-wifi-only",
            "update-prereleases", "open-gallery", "open-licenses") + buttons
        controls.forEach { reachable(it, button = it in buttons, screenWidth) }

        // Every clickable node of the screen, tagged or not.
        val clickable = compose.onAllNodes(hasClickAction())
        repeat(clickable.fetchSemanticsNodes().size) { index ->
            clickable[index].performScrollTo().assertTouchTargetIsAtLeast48dp()
        }

        // Each step of the updater with its button.
        listOf(
            offered.copy(phase = UpdatePhase.AVAILABLE) to "update-download",
            offered.copy(phase = UpdatePhase.DOWNLOADING, downloadedBytes = 1_048_576, totalBytes = 13_107_200) to "update-cancel",
            offered.copy(phase = UpdatePhase.WAITING_FOR_WIFI) to "update-cancel",
            offered.copy(phase = UpdatePhase.READY, verifiedApkReady = true) to "update-install",
            offered.copy(phase = UpdatePhase.PERMISSION_REQUIRED, verifiedApkReady = true) to "update-install",
            offered.copy(phase = UpdatePhase.APPROVAL_REQUIRED, verifiedApkReady = true) to "update-install",
            offered.copy(phase = UpdatePhase.FAILED, verifiedApkReady = true) to "update-retry",
            offered.copy(phase = UpdatePhase.FAILED) to "update-retry",
            offered.copy(phase = UpdatePhase.CANCELLED) to "update-download",
        ).forEach { (update, tag) ->
            compose.runOnIdle { state.value = state.value.copy(updates = update) }
            reachable(tag, button = true, screenWidth)
        }

        // The import dialogs: "Cancelar" and "Importar", then "Fechar" alone.
        compose.runOnIdle { state.value = state.value.copy(backup = BackupUi(pendingImport = ImportSummary(180, 28, 4, 0, 3, true, nothingToAdd = false))) }
        reachable("dialog-dismiss", button = true, screenWidth)
        reachable("dialog-confirm", button = true, screenWidth)
        compose.runOnIdle { state.value = state.value.copy(backup = BackupUi()) }
        compose.runOnIdle { state.value = state.value.copy(backup = BackupUi(pendingImport = ImportSummary(0, 5, 0, 2, 0, false, nothingToAdd = true))) }
        reachable("dialog-confirm", button = true, screenWidth)
        compose.onNodeWithTag("dialog-dismiss").assertDoesNotExist()
    }

    /**
     * [tag] is reachable: scrolled into view (on the screen, or inside a dialog's window, whose card
     * scrolls when its text at 200 % is taller than the screen), displayed, a 48 dp target, within
     * the 320 dp width, and its texts whole.
     */
    private fun reachable(tag: String, button: Boolean, screenWidth: Float) {
        val node = compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            .assertTouchTargetIsAtLeast48dp()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue("$tag $bounds passes the 320 dp screen", bounds.left >= -0.5f && bounds.right <= screenWidth + 0.5f)
        if (!button) return
        val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and
            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$tag has no text", texts.isNotEmpty())
        texts.forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val label = layout.layoutInput.text.text
            // Measured with the design system's font, not a platform fallback.
            val family = layout.layoutInput.style.fontFamily
            assertTrue("$tag \"$label\" is measured with $family", family != null && family != FontFamily.Default && family !is androidx.compose.ui.text.font.GenericFontFamily)
            assertFalse("$tag \"$label\" exceeds its lines", layout.multiParagraph.didExceedMaxLines)
            // Every word sits on one line and fits the width the label really got: no word is broken.
            Regex("\\S+").findAll(label).forEach { word ->
                val first = word.range.first
                val last = word.range.last
                assertEquals("$tag \"$label\": \"${word.value}\" is broken across lines",
                    layout.getLineForOffset(first), layout.getLineForOffset(last))
                val needed = layout.getBoundingBox(last).right - layout.getBoundingBox(first).left
                assertTrue("$tag \"$label\": \"${word.value}\" needs $needed px, has ${layout.size.width}",
                    needed <= layout.size.width + 0.5f)
            }
            assertTrue("$tag \"$label\" is cut in height", layout.multiParagraph.height <= layout.size.height + 0.5f)
            val box = text.boundsInRoot
            assertTrue("$tag \"$label\" $box passes its button $bounds",
                box.left >= bounds.left - 0.5f && box.right <= bounds.right + 0.5f &&
                    box.top >= bounds.top - 0.5f && box.bottom <= bounds.bottom + 0.5f)
        }
    }

    @Test
    fun licensesRowOpensTheLicenses() {
        compose.setContent { MoosclesApp(UiState(loading = false, updates = installed), recording(mutableListOf())) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("open-licenses").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("SIL OPEN FONT LICENSE", substring = true)).fetchSemanticsNodes().size >= 2
        }
        compose.onNode(hasText("Open-source licenses") and isHeading()).assertIsDisplayed()
        listOf("Inter", "Barlow Condensed", "Lucide").forEach { compose.onNode(hasText(it) and isHeading()).assertExists() }
        compose.onAllNodes(hasText("ISC", substring = true)).onFirst().assertExists()
        compose.onNode(hasText("Permission to use, copy, modify, and/or distribute this software", substring = true)).assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("open-licenses").assertExists()
    }

    @Test
    fun galleryEntryIsHiddenUntilUnlocked() {
        var opened = 0
        compose.setContent {
            MoosclesTheme("light") {
                SettingsScreen(UiState(loading = false, updates = installed), recording(mutableListOf()), onOpenGallery = { opened++ })
            }
        }
        val version = compose.onNodeWithTag("update-installed-version").performScrollTo()
        // The tap target is at least 48 dp. Critique #8: it is read as plain text, one node with
        // the version and no role or click action, since a tap shows nothing until the seventh.
        version.assertTouchTargetIsAtLeast48dp()
        val config = version.fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.Role))
        assertFalse("the version row must not offer a click action", config.contains(SemanticsActions.OnClick))
        version.assert(hasText("Installed 1.4.5 · build 16"))
        repeat(DeveloperGallery.TAPS - 1) {
            compose.onNodeWithTag("update-installed-version").performScrollTo().performClick()
            compose.onNodeWithTag("open-gallery").assertDoesNotExist()
        }
        compose.onNodeWithTag("update-installed-version").performScrollTo().performClick()
        compose.onNodeWithTag("open-gallery").performScrollTo().assertIsDisplayed().assert(hasText("Component gallery")).performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    /** Critique #10: the top bar is a traversal group placed before the content, so Back and the title are read first. */
    @Test
    fun topBarIsReadBeforeTheContent() {
        compose.setContent { MoosclesApp(UiState(loading = false, updates = installed), recording(mutableListOf())) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        val config = compose.onNodeWithTag(SETTINGS_TOP_BAR_TAG).fetchSemanticsNode().config
        assertEquals(true, config.getOrNull(SemanticsProperties.IsTraversalGroup))
        val index = config.getOrNull(SemanticsProperties.TraversalIndex)
        assertTrue("traversal index $index must come before the content (0)", index != null && index < 0f)
        compose.onNode(hasAnyAncestor(hasTestTag(SETTINGS_TOP_BAR_TAG)) and hasText("Settings") and isHeading()).assertExists()
        compose.onNode(hasAnyAncestor(hasTestTag(SETTINGS_TOP_BAR_TAG)) and hasContentDescription("Back") and hasClickAction()).assertExists()
    }

    @Test
    fun privacyTextIsUnchanged() {
        showSettings(UiState(loading = false, updates = installed))
        compose.onNodeWithText("Just you and your progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Your routines, workout history, and records stay on this device. Training works offline. App connections check " +
                "and download updates from GitHub. Sharing creates a workout image on your device and opens the app you choose, " +
                "where you select the recipient and confirm sending. Workout data is excluded from automatic cloud backups. Use " +
                "Export data above to keep your own copy.",
        ).performScrollTo().assertIsDisplayed()
    }

    private fun showSettings(state: UiState) {
        compose.setContent { MoosclesTheme(state.preferences.theme) { SettingsScreen(state, recording(mutableListOf())) } }
    }

    /** Review I1: each question's radios form one selectable group, read as "1 of 3". */
    @Test
    fun radiosAreGroupedForScreenReaders() {
        showSettings(UiState(loading = false, updates = installed, preferences = PreferencesUi(theme = "dark", pounds = true)))
        val groups = mapOf("theme-group" to listOf("theme-system", "theme-light", "theme-dark"), "unit-group" to listOf("unit-kg", "unit-lb"))
        groups.forEach { (group, options) ->
            compose.onNodeWithTag(group).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            options.forEach { option ->
                compose.onNodeWithTag(option)
                    .assert(hasParent(hasTestTag(group)))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            }
        }
        compose.onNodeWithTag("theme-dark").assertIsSelected()
        listOf("theme-system", "theme-light", "unit-kg").forEach { compose.onNodeWithTag(it).assertIsNotSelected() }
        compose.onNodeWithTag("unit-lb").assertIsSelected()
    }

    /**
     * Review I2: an updater request already handled is not repeated when the composition is
     * recreated (rotation): the user who went on to the licenses stays there.
     */
    @Test
    fun handledUpdateRequestIsNotRepeatedAfterRecreation() {
        val state = mutableStateOf(UiState(loading = false, updates = installed))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, recording(mutableListOf())) }
        compose.runOnIdle { state.value = state.value.copy(updateSettingsRequest = 1) }
        compose.onNodeWithTag("app-updates").assertIsDisplayed()
        compose.onNodeWithTag("open-licenses").performScrollTo().performClick()
        compose.onNode(hasText("Open-source licenses") and isHeading()).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("Open-source licenses") and isHeading()).assertIsDisplayed()
        compose.onNodeWithTag("app-updates").assertDoesNotExist()

        // A new request still lands on the updates.
        compose.runOnIdle { state.value = state.value.copy(updateSettingsRequest = 2) }
        compose.onNodeWithTag("app-updates").assertIsDisplayed()
    }

    /** Review M2: like the field before it, the rest field stays on one line; Enter types nothing. */
    @Test
    fun restFieldEnterTypesNoLineBreak() {
        val calls = mutableListOf<String>()
        compose.setContent { MoosclesTheme("light") { SettingsScreen(UiState(loading = false, updates = installed), recording(calls)) } }
        val field = compose.onNodeWithTag("default-rest-duration").performScrollTo()
        field.performClick()
        field.performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Use 0:00 to 5:00, with seconds from 00 to 59.").assertDoesNotExist()
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("2:00")))
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    /** Review M3: the notes, the status card and the error banner of the screen read at 4.5:1 or more. */
    @Test
    fun pilotTextPairsMeetContrastInBothThemes() {
        for (colors in listOf(MoosclesColors.Light, MoosclesColors.Dark)) {
            listOf(
                colors.textSecondary to colors.bgBase, colors.textPrimary to colors.bgBase,
                colors.textSecondary to colors.bgRaised, colors.textPrimary to colors.bgRaised,
                colors.textPrimary to colors.dangerSubtle, colors.textSecondary to colors.dangerSubtle,
                colors.accentText to colors.bgBase,
            ).forEach { (fg, bg) -> assertTrue("$fg on $bg: ${contrast(fg, bg)}", contrast(fg, bg) >= 4.5) }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** The touch area (the bounds plus any minimum touch size padding) is 48 by 48 dp or more. */
    private fun SemanticsNodeInteraction.assertTouchTargetIsAtLeast48dp(): SemanticsNodeInteraction {
        val node = fetchSemanticsNode()
        val touch = node.touchBoundsInRoot
        val min = with(compose.density) { 48.dp.toPx() } - 0.5f
        assertTrue("touch target ${touch.width} x ${touch.height} px under 48 dp: ${node.config}", touch.width >= min && touch.height >= min)
        return this
    }

    /** Scrolls the screen until [matcher] is shown and returns that node. */
    private fun reach(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
        return compose.onNode(matcher)
    }

    /** Records every action as "name" or "name:arg", like the other host tests' recorders. */
    private fun recording(calls: MutableList<String>): MoosclesActions = Proxy.newProxyInstance(
        MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Settings actions"
            else -> {
                calls += if (args.isNullOrEmpty()) method.name else method.name + ":" + args.joinToString(",")
                null
            }
        }
    } as MoosclesActions
}
