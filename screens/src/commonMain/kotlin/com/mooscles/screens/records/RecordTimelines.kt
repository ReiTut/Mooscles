package com.mooscles.screens.records

import androidx.compose.runtime.Immutable
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.ProgressQuery
import com.mooscles.screens.progress.RecordEvent
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone

/**
 * What the Records tab asks of the timeline (spec D11). [text] is trimmed (only spaces is no search); [currentOnly]
 * lists the current records and ignores [firstRecords]; [zone] decides which month a record belongs to.
 */
data class RecordsQuery(
    val text: String = "",
    val currentOnly: Boolean = false,
    val firstRecords: Boolean = false,
    val zone: TimeZone,
) : ProgressQuery<RecordsTimeline> {
    override fun compute(index: ProgressIndex): RecordsTimeline = RecordTimelines.query(index, this)
}

/** The events of one local month, newest first. */
@Immutable
class RecordMonth(val year: Int, val month: Month, val items: List<RecordEvent>)

/** The months of a query, newest first, and the number of events in them. */
@Immutable
class RecordsTimeline(val months: List<RecordMonth>, val total: Int)

/** The query behind the Records tab (spec D11), over the events and current records of a [ProgressIndex]. */
object RecordTimelines {
    fun query(index: ProgressIndex, query: RecordsQuery): RecordsTimeline {
        val text = query.text.trim()
        val matches = if (text.isEmpty()) null else index.searchRecords(text)
        fun found(event: RecordEvent) = matches == null || event.record.exercise.id in matches

        // One pass over the events in the index's order. A search also lists the current records of what it found,
        // first records included, so those events pass even without the chip.
        val chosen: List<RecordEvent> = if (query.currentOnly) {
            index.currentRecords.filter(::found)
        } else {
            val inOrder = index.events.filter {
                found(it) && (query.firstRecords || !it.record.firstRecord || (matches != null && it.current))
            }
            // A current record the events lack (its fallback) joins at its place by the index's own order.
            val extras = if (matches == null) emptyList() else {
                val seen = HashSet<RecordEvent>(inOrder)
                index.currentRecords.filter { found(it) && it !in seen }
            }
            if (extras.isEmpty()) inOrder else (inOrder + extras).sortedWith(index.eventOrder)
        }
        // The current copy of an event is the event itself: identity dedupes it.
        val events = chosen.distinct()
        val months = ArrayList<RecordMonth>()
        var year = 0
        var month: Month? = null
        var items = ArrayList<RecordEvent>()
        for (event in events) {
            val date = ScreenDates.localDate(event.record.date, query.zone)
            if (month != null && (date.year != year || date.month != month)) {
                months += RecordMonth(year, month, items)
                items = ArrayList()
            }
            year = date.year
            month = date.month
            items += event
        }
        if (month != null) months += RecordMonth(year, month, items)
        return RecordsTimeline(months, events.size)
    }

    /**
     * A key that is the same on every query and unique along a timeline, and a plain String, so a lazy list can save
     * it in a Bundle: workout, exercise, convention, kind, load (exact bits, never rounded) and assistance.
     */
    fun key(event: RecordEvent): String {
        val record = event.record
        return "${record.workoutId}|${record.exercise.id}|${record.exercise.convention}|${record.kind}|" +
            "${record.weightKg.toBits()}|${record.assistance}"
    }
}
