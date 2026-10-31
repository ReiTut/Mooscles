package com.mooscles.screens.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.records_rule_assistance
import com.mooscles.screens.resources.records_rule_excluded
import com.mooscles.screens.resources.records_rule_reps
import com.mooscles.screens.resources.records_rule_weight
import com.mooscles.screens.resources.records_rules
import org.jetbrains.compose.resources.stringResource

/** The rules, in the order the sheet lists them. */
private val RULES = listOf(
    Res.string.records_rule_weight,
    Res.string.records_rule_reps,
    Res.string.records_rule_excluded,
    Res.string.records_rule_assistance,
)

/**
 * "How we count records" (spec "Recordes (aba)"): today's rules, translated: load is the heaviest working set; reps
 * are more reps at the same load, bodyweight included; warm-ups and incomplete sets do not count; assisted sets have
 * records of their own. A heading over the four, scrolling when the text is large and the window short.
 */
@Composable
internal fun RecordRulesSheet(onDismiss: () -> Unit) {
    MBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(RecordsTags.RULES_SHEET)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MTheme.space.s4)
                .padding(bottom = MTheme.space.s6),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        ) {
            MText(
                stringResource(Res.string.records_rules),
                MTheme.type.titleMd,
                color = MTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            RULES.forEachIndexed { index, rule ->
                MText(
                    stringResource(rule),
                    MTheme.type.bodyMd,
                    color = MTheme.colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().testTag(RecordsTags.rule(index)),
                )
            }
        }
    }
}
