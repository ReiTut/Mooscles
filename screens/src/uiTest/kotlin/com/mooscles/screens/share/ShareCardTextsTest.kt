package com.mooscles.screens.share

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The resources resolve to templates (the card fills them later, off the composition). */
@OptIn(ExperimentalTestApi::class)
class ShareCardTextsTest {
    private val utc = TimeZone.UTC
    private fun at(h: Int, mi: Int) = LocalDateTime(2024, 10, 29, h, mi).toInstant(utc).toEpochMilliseconds()

    // 2024-10-29 is a Tuesday (the plan's example date).
    private val source = run {
        val pullup = WorkoutExercise("e", "w", "pullup", "Barra fixa", MuscleGroup.BACK, Equipment.BODYWEIGHT,
            LoadConvention.BODYWEIGHT, 0)
        AppData(
            workouts = listOf(Workout("w", "Costas", at(18, 2), at(18, 44), WorkoutStatus.FINISHED)),
            workoutExercises = listOf(pullup),
            sets = listOf(WorkoutSet("s", "e", 0, 12, 0.0, true, false, false)),
        )
    }

    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private fun texts(locale: String): ShareCardTexts {
        var texts: ShareCardTexts? = null
        runComposeUiTest {
            withScreen(locale = locale) { texts = rememberShareCardTexts() }
            waitForIdle()
        }
        LocalAppLocale.restore()
        return checkNotNull(texts)
    }

    @Test
    fun englishCard() {
        val card = ShareCards.from(source, "w", false, NumberLocale.EN, utc, texts("en"))
        assertEquals("Tue, Oct 29, 2024 · 18:02–18:44", card.dateText)
        assertEquals("Bodyweight × 12", card.rows.single().bestSet)
        assertEquals("Duration", card.labels.duration)
        assertEquals("PR", card.labels.recordBadge)
        assertEquals(false, texts("en").catalogPortuguese)
    }

    @Test
    fun portugueseCard() {
        val card = ShareCards.from(source, "w", false, NumberLocale.PT_BR, utc, texts("pt-BR"))
        assertEquals("ter., 29 de out. de 2024 · 18:02–18:44", card.dateText)
        assertEquals("Peso corporal × 12", card.rows.single().bestSet)
        assertEquals("Duração", card.labels.duration)
        assertEquals("Nenhuma série concluída", card.labels.noCompletedSets)
        // The catalog's names follow the card's language (plan decision 11).
        assertEquals(true, texts("pt-BR").catalogPortuguese)
    }
}
