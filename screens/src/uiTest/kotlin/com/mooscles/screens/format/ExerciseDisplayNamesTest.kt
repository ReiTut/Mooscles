package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RoutineFolderUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The names the screens show (spec D11, D12): the catalog in the screen's language, typed names as typed. */
@OptIn(ExperimentalTestApi::class)
class ExerciseDisplayNamesTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /** What [read] gives in [locale], read inside the screen's composition. */
    private fun <T> inLanguage(locale: String, read: @Composable () -> T): T {
        var value: Any? = UNSET
        runComposeUiTest {
            withScreen(locale = locale) { value = read() }
            waitForIdle()
        }
        LocalAppLocale.restore()
        check(value !== UNSET) { "nothing composed in $locale" }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private val bench = ExerciseUi("catalog_barbell_bench_flat", "Barbell bench press", "Chest", "Barbell", "Total load")

    @Test
    fun catalogNameFollowsTheScreenLanguage() {
        assertEquals(false, inLanguage("en") { catalogInPortuguese() })
        assertEquals(true, inLanguage("pt-BR") { catalogInPortuguese() })
        assertEquals("Barbell bench press", inLanguage("en") { exerciseName(bench) })
        assertEquals("Supino reto com barra", inLanguage("pt-BR") { exerciseName(bench) })
    }

    @Test
    fun customKeepsItsTypedName() {
        val mine = ExerciseUi("custom_1", "Meu supino", "Chest", "Barbell", "Total load", custom = true)
        assertEquals("Meu supino", inLanguage("en") { exerciseName(mine) })
        assertEquals("Meu supino", inLanguage("pt-BR") { exerciseName(mine) })
    }

    /** A workout saved by an older build keeps the old English name; the screen shows today's catalog name. */
    @Test
    fun oldEnglishSnapshotShowsTheCurrentCatalogName() {
        val old = bench.copy(name = "Bench press")
        assertEquals("Barbell bench press", inLanguage("en") { exerciseName(old) })
        assertEquals("Supino reto com barra", inLanguage("pt-BR") { exerciseName(old) })
    }

    @Test
    fun defaultFolderIsTranslatedOnlyWhileUnrenamed() {
        val default = RoutineFolderUi("default", "My routines")
        assertEquals("My routines", inLanguage("en") { folderName(default) })
        assertEquals("Minhas rotinas", inLanguage("pt-BR") { folderName(default) })
        // Renamed by the person: their name, in every language.
        assertEquals("Treinos", inLanguage("pt-BR") { folderName(default.copy(name = "Treinos")) })
        // Another folder that happens to carry the same name is the person's own.
        assertEquals("My routines", inLanguage("pt-BR") { folderName(RoutineFolderUi("f1", "My routines")) })
    }

    private companion object {
        val UNSET = Any()
    }
}
