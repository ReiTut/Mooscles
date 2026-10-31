package com.mooscles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.mooscles.ui.TrainingNumberField
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Public editing/accessibility contracts shared by visible workout number fields. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalTestApi::class)
class TrainingNumberFieldHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadAndRepsExposeVisibleAccessibleLabelsAndFullHeightEditableTargets() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(240.dp)) {
                    TrainingNumberField("31.1250", {}, "kg", Modifier.testTag("load"), decimal = true)
                    TrainingNumberField("", {}, "Reps", Modifier.testTag("reps"))
                }
            }
        }
        for ((tag, label) in listOf("load" to "kg", "reps" to "Reps")) {
            val field = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled()
            field.assert(hasSetTextAction()).assert(accessibleLabel(label))
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            field.assertHeightIsAtLeast(56.dp)
        }
        compose.onNodeWithTag("load").assert(editable("31.1250"))
        compose.onNodeWithTag("reps").assert(editable(""))
    }

    @Test fun precisePartialCommaAndBlankDraftsReachTheCallbackWithoutNormalization() {
        val value = mutableStateOf("")
        val submitted = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                TrainingNumberField(value.value, { value.value = it; submitted += it }, "kg",
                    Modifier.width(240.dp).testTag("load"), decimal = true)
            }
        }
        val drafts = listOf("31.1250", "31.", "31,1250", ".", "")
        for (draft in drafts) {
            compose.onNodeWithTag("load").performTextReplacement(draft)
            compose.onNodeWithTag("load").assert(editable(draft))
            compose.runOnIdle { assertEquals(draft, value.value); assertEquals(draft, submitted.last()) }
        }
        compose.runOnIdle { assertEquals("Rendering and selection must not submit extra edits", drafts, submitted) }
    }

    @Test fun parentAndErrorUpdatesPreserveFocusedTextSelectionWithoutSubmittingAnEdit() {
        val value = mutableStateOf("31.1250")
        val error = mutableStateOf(false)
        val revision = mutableIntStateOf(0)
        val submitted = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(240.dp)) {
                    Text("Parent revision ${revision.intValue}")
                    TrainingNumberField(value.value, { value.value = it; submitted += it }, "kg",
                        Modifier.testTag("load"), isError = error.value, decimal = true)
                }
            }
        }
        val field = compose.onNodeWithTag("load")
        field.performClick().performTextInputSelection(TextRange(2, 5))
        field.assertIsFocused().assert(editable("31.1250"))
        assertEquals(TextRange(2, 5), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])

        compose.runOnIdle { error.value = true; revision.intValue++ }
        field.assertIsFocused().assert(editable("31.1250"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        assertEquals(TextRange(2, 5), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertTrue("Invalid input must expose an accessible error",
            field.fetchSemanticsNode().config[SemanticsProperties.Error].isNotBlank())

        compose.runOnIdle { error.value = false; revision.intValue++ }
        field.assertIsFocused().assert(editable("31.1250"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        assertEquals(TextRange(2, 5), field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        compose.runOnIdle { assertTrue("Focus, selection and error rendering must not autosave", submitted.isEmpty()) }
    }

    private fun editable(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
    private fun accessibleLabel(label: String) = SemanticsMatcher("Accessible field label $label") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == label } == true
    }
}
