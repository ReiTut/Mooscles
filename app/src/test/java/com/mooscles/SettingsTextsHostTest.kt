package com.mooscles

import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.backup.BackupTexts
import com.mooscles.backup.ImportSummary
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.ui.UpdatePhase
import com.mooscles.ui.importSummaryText
import com.mooscles.ui.updateSizeText
import com.mooscles.ui.updateStatusTitleText
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsTextsHostTest {
    @get:Rule val compose = createComposeRule()

    private fun summary(workouts: Int, routines: Int, custom: Int, kept: Int, prefs: Boolean) = ImportSummary(
        workoutsAdded = workouts, workoutsKept = kept, routinesAdded = routines, routinesKept = 0,
        customExercisesAdded = custom, restoresPreferences = prefs, nothingToAdd = false,
    )

    /** Composes once (optionally in [locale]) and returns every text [content] produces. */
    private fun render(locale: String? = null, content: @androidx.compose.runtime.Composable () -> List<String>): List<String> {
        var result = emptyList<String>()
        compose.setContent {
            val body = @androidx.compose.runtime.Composable { result = content() }
            if (locale == null) body() else ProvideAppLocale(locale, body)
        }
        compose.waitForIdle()
        return result
    }

    @Test
    fun englishImportSummaryEqualsBackupTexts() {
        val cases = listOf(
            summary(180, 4, 3, 28, false), summary(1, 1, 1, 1, true), summary(0, 0, 0, 0, true),
            summary(2, 0, 1, 0, false), summary(0, 1, 0, 5, false),
        )
        val rendered = render { cases.map { importSummaryText(it) } }
        assertEquals(cases.map { BackupTexts.summary(it) }, rendered)
    }

    @Test
    fun englishStatusTitlesEqualToday() {
        val today = mapOf(
            UpdatePhase.IDLE to "Ready to check",
            UpdatePhase.CHECKING to "Checking GitHub Releases",
            UpdatePhase.AVAILABLE to "Update available",
            UpdatePhase.WAITING_FOR_WIFI to "Waiting for Wi-Fi",
            UpdatePhase.DOWNLOADING to "Downloading update",
            UpdatePhase.VERIFYING to "Verifying update",
            UpdatePhase.READY to "Update ready",
            UpdatePhase.DEFERRED to "Update ready · installation deferred",
            UpdatePhase.PERMISSION_REQUIRED to "Installation permission needed",
            UpdatePhase.APPROVAL_REQUIRED to "Update ready · approval needed",
            UpdatePhase.INSTALLING to "Installing update",
            UpdatePhase.SUCCEEDED to "Update successful",
            UpdatePhase.FAILED to "Update could not finish",
            UpdatePhase.CANCELLED to "Update cancelled",
        )
        assertEquals(UpdatePhase.entries.toSet(), today.keys)
        var titles = emptyMap<UpdatePhase, String>()
        compose.setContent {
            titles = UpdatePhase.entries.associateWith { updateStatusTitleText(it) }
        }
        compose.waitForIdle()
        assertEquals(today, titles)
    }

    @Test
    fun portugueseImportSummaryUsesPluralsAndZeroStaysPlural() {
        val rendered = render("pt-BR") {
            listOf(importSummaryText(summary(180, 4, 3, 28, false)), importSummaryText(summary(1, 1, 0, 1, true)))
        }
        assertEquals(
            listOf(
                "Adicionar 180 treinos, 4 rotinas e 3 exercícios personalizados. " +
                    "28 treinos já estão neste celular e serão mantidos como estão.",
                "Adicionar 1 treino, 1 rotina e 0 exercícios personalizados. " +
                    "1 treino já está neste celular e será mantido como está. " +
                    "Suas unidades, tema e configurações de descanso também serão restaurados.",
            ),
            rendered,
        )
    }

    /** Boundary at 1 MiB, and half-up rounding around 1.05 MB (1_101_004.8 bytes). */
    @androidx.compose.runtime.Composable
    private fun sizes() = listOf(13_107_200L, 524_288L, 1_048_576L, 1_048_575L, 1_101_005L, 1_101_004L).map { updateSizeText(it) }

    @Test
    fun englishUpdateSizesKeepTheDot() {
        assertEquals(listOf("12.5 MB", "512 KB", "1.0 MB", "1023 KB", "1.1 MB", "1.0 MB"), render("en") { sizes() })
    }

    @Test
    fun portugueseUpdateSizesUseTheComma() {
        assertEquals(listOf("12,5 MB", "512 KB", "1,0 MB", "1023 KB", "1,1 MB", "1,0 MB"), render("pt-BR") { sizes() })
    }

    @Test
    fun everySettingsKeyIsUsed() {
        val xml = File("../designsystem/src/commonMain/composeResources/values/strings_settings.xml").readText()
        val keys = Regex("<(?:string|plurals)\\s+name=\"(settings_[a-z_]+)\"").findAll(xml).map { it.groupValues[1] }.toList()
        val sources = File("src/main/java/com/mooscles/ui").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val orphans = keys.filter { !Regex("\\b$it\\b").containsMatchIn(sources) }
        assertTrue("Unused settings keys: $orphans", orphans.isEmpty())
    }
}
