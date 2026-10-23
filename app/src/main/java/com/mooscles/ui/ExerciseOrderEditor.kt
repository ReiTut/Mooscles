package com.mooscles.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlin.math.abs

/** The persistent routine/session entry ID, rather than a catalog ID or name. */
data class OrderExerciseUi(val id: String, val name: String)

@Composable
fun ExercisePlanActions(onAdd: () -> Unit, onReorder: () -> Unit, canReorder: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f).testTag("exercise-plan-add")) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("Add Exercise")
        }
        OutlinedButton(onClick = onReorder, enabled = canReorder,
            modifier = Modifier.weight(1f).testTag("exercise-plan-reorder")) {
            Icon(Icons.Outlined.DragHandle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text("Reorder Exercises")
        }
    }
}

/**
 * A separate order-only draft. The full editor remains mounted behind this
 * window, keeping its invalid/unfinished numeric drafts and focus state alive.
 * Only Save Changes emits a command; Back, outside dismissal and Cancel discard.
 */
@Composable
fun ExerciseOrderEditor(
    entries: List<OrderExerciseUi>,
    onSave: (List<String>) -> Unit,
    onCancel: () -> Unit,
    saving: Boolean = false,
) {
    var orderedIds by rememberSaveable { mutableStateOf(entries.map { it.id }) }
    val entriesById = remember(entries) { entries.associateBy { it.id } }
    val sameEntries = orderedIds.size == entries.size && orderedIds.toSet() == entriesById.keys
    val listState = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }
    var draggedHeight by remember { mutableIntStateOf(0) }
    val edgeSize = with(LocalDensity.current) { 64.dp.toPx() }
    val maxScrollPerFrame = with(LocalDensity.current) { 18.dp.toPx() }

    fun move(id: String, destination: Int): Boolean {
        val from = orderedIds.indexOf(id)
        if (from < 0 || destination !in orderedIds.indices || from == destination || saving) return false
        orderedIds = orderedIds.toMutableList().apply { add(destination, removeAt(from)) }
        return true
    }

    fun updateDragOrder() {
        val id = draggedId ?: return
        val from = orderedIds.indexOf(id)
        if (from < 0) return
        val center = draggedTop + draggedHeight / 2f
        // Use the visible slot's index, rather than its old occupant's key:
        // several pointer events can arrive before Compose measures the move.
        val visible = listState.layoutInfo.visibleItemsInfo
        val target = visible.minByOrNull { abs(center - (it.offset + it.size / 2f)) } ?: return
        move(id, target.index)
    }

    // Scrolling continues while a handle is held near an edge, even when the
    // pointer stops moving. The dragged row's viewport position stays fixed.
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val center = draggedTop + draggedHeight / 2f
            val top = info.viewportStartOffset.toFloat()
            val bottom = info.viewportEndOffset.toFloat()
            val velocity = when {
                center < top + edgeSize -> -maxScrollPerFrame * ((top + edgeSize - center) / edgeSize).coerceIn(0f, 1f)
                center > bottom - edgeSize -> maxScrollPerFrame * ((center - bottom + edgeSize) / edgeSize).coerceIn(0f, 1f)
                else -> 0f
            }
            if (velocity != 0f) {
                listState.scrollBy(velocity)
                updateDragOrder()
            }
        }
    }

    Dialog(onDismissRequest = { if (!saving) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp).testTag("exercise-order-editor"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reorder Exercises", style = MaterialTheme.typography.headlineSmall)
                Text("Drag a handle to move an exercise. Tap it for move-up and move-down options.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("exercise-order-list"),
                    contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(orderedIds, key = { _, id -> id }) { index, id ->
                        val entry = entriesById[id] ?: return@itemsIndexed
                        val dragging = draggedId == id
                        val laidOutTop = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset ?: 0
                        var menu by remember(id) { mutableStateOf(false) }
                        Surface(color = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp), tonalElevation = if (dragging) 6.dp else 0.dp,
                            modifier = Modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) draggedTop - laidOutTop else 0f }
                                .testTag("exercise-order-row-$id").semantics(mergeDescendants = true) {
                                    stateDescription = "Position ${index + 1} of ${orderedIds.size}"
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("Move up") { move(id, orderedIds.indexOf(id) - 1) })
                                        if (index < orderedIds.lastIndex) add(CustomAccessibilityAction("Move down") { move(id, orderedIds.indexOf(id) + 1) })
                                    }
                                }) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                Box {
                                    IconButton(onClick = { menu = true }, enabled = !saving,
                                        modifier = Modifier.testTag("exercise-order-drag-$id").pointerInput(id, saving) {
                                            if (!saving) detectDragGestures(
                                                onDragStart = {
                                                    val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                                                    if (row != null) {
                                                        draggedId = id
                                                        draggedTop = row.offset.toFloat()
                                                        draggedHeight = row.size
                                                    }
                                                },
                                                onDragCancel = { draggedId = null },
                                                onDragEnd = { draggedId = null },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    if (draggedId == id) {
                                                        draggedTop += amount.y
                                                        updateDragOrder()
                                                    }
                                                })
                                        }) { Icon(Icons.Outlined.DragHandle, "Reorder ${entry.name}") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0 && !saving,
                                            onClick = { move(id, orderedIds.indexOf(id) - 1); menu = false })
                                        DropdownMenuItem(text = { Text("Move down") }, enabled = index < orderedIds.lastIndex && !saving,
                                            onClick = { move(id, orderedIds.indexOf(id) + 1); menu = false })
                                    }
                                }
                            }
                        }
                    }
                }
                if (!sameEntries) Text("Exercises changed while this view was open. Cancel and reopen to reorder them.", color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, enabled = !saving, modifier = Modifier.weight(1f).testTag("exercise-order-cancel")) { Text("Cancel") }
                    Button(onClick = { onSave(orderedIds.toList()) }, enabled = sameEntries && !saving,
                        modifier = Modifier.weight(1f).testTag("exercise-order-save")) { Text(if (saving) "Saving…" else "Save Changes") }
                }
            }
        }
    }
}
