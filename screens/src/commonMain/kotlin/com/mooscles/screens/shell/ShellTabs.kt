package com.mooscles.screens.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mooscles.designsystem.components.MTab
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.shell_tab_history
import com.mooscles.screens.resources.shell_tab_library
import com.mooscles.screens.resources.shell_tab_records
import com.mooscles.screens.resources.shell_tab_train
import org.jetbrains.compose.resources.stringResource

/**
 * The four destinations of the tab bar (spec D2), in order. The keys are the app's routes, so the
 * tags are `tab-routines`, `tab-library`, `tab-history` and `tab-records`.
 */
object ShellTabs {
    const val TRAIN = "routines"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val RECORDS = "records"

    val keys: List<String> = listOf(TRAIN, LIBRARY, HISTORY, RECORDS)
}

/**
 * The tabs of [ShellTabs], localized: Train with the dumbbell, Library with `LayoutGrid`, History
 * with `History` and Records with the record plate, each with its filled variant when selected.
 * The same list while the language does not change.
 */
@Composable
fun shellTabs(): List<MTab> {
    val train = stringResource(Res.string.shell_tab_train)
    val library = stringResource(Res.string.shell_tab_library)
    val history = stringResource(Res.string.shell_tab_history)
    val records = stringResource(Res.string.shell_tab_records)
    return remember(train, library, history, records) {
        listOf(
            MTab(ShellTabs.TRAIN, train, MIcons.Dumbbell, MIcons.DumbbellFilled),
            MTab(ShellTabs.LIBRARY, library, MIcons.LayoutGrid, MIcons.LayoutGridFilled),
            MTab(ShellTabs.HISTORY, history, MIcons.History, MIcons.HistoryFilled),
            MTab(ShellTabs.RECORDS, records, MIcons.PlateRecord, MIcons.PlateRecordFilled),
        )
    }
}
