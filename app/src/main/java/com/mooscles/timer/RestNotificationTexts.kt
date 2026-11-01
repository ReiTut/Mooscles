package com.mooscles.timer

import android.content.Context
import com.mooscles.R
import com.mooscles.data.WorkoutRepository
import java.util.Locale
import kotlin.math.ceil

/** The rest notifications' channels, ids and intent actions (D16, decisions 24 and 25). */
object RestNotifications {
    /** The end-of-rest channel, unchanged since it was created: its id and importance are installed state. */
    const val CHANNEL = "rest"
    const val PROGRESS_CHANNEL = "rest_progress"
    /** The end-of-rest notification, unchanged. */
    const val FINISHED_ID = 1
    /** Not 2: that id is the updater's (`UpdateNotifications`). */
    const val PROGRESS_ID = 3
    /**
     * How long after a running rest's end the system removes its notification. With an inexact alarm
     * and no process left, the chronometer would otherwise go on counting below zero.
     */
    const val PROGRESS_TIMEOUT_MARGIN_MILLIS = 2_000L
    const val OPEN_WORKOUT = "com.mooscles.timer.OPEN_WORKOUT"
    const val ACTION_MINUS = "com.mooscles.timer.MINUS15"
    const val ACTION_PLUS = "com.mooscles.timer.PLUS15"
    const val ACTION_SKIP = "com.mooscles.timer.SKIP"
}

/** The rest notifications' words, from the app's resources in the device language. */
internal class RestNotificationTexts(private val context: Context) {
    val channelName get() = context.getString(R.string.rest_channel_name)
    val channelDescription get() = context.getString(R.string.rest_channel_description)
    val progressChannelName get() = context.getString(R.string.rest_progress_channel_name)
    val progressChannelDescription get() = context.getString(R.string.rest_progress_channel_description)
    val progressTitle get() = context.getString(R.string.rest_progress_title)
    val minus get() = context.getString(R.string.rest_action_minus)
    val plus get() = context.getString(R.string.rest_action_plus)
    val skip get() = context.getString(R.string.rest_action_skip)
    val finishedTitle get() = context.getString(R.string.rest_finished_title)

    /** "Bench press · next set 5"; without a pending set, the name alone. */
    fun progressText(name: String, nextSet: Int?) =
        if (nextSet == null) name else context.getString(R.string.rest_progress_text, name, nextSet)

    /** "Next: Bench press, set 5"; without a pending set, the name alone. */
    fun finishedText(name: String, nextSet: Int?) =
        if (nextSet == null) name else context.getString(R.string.rest_finished_text, name, nextSet)

    /** "1:08 · paused": a paused rest has no countdown, so its remaining time is fixed. */
    fun paused(remainingMillis: Long) = context.getString(R.string.rest_progress_paused, clock(remainingMillis))

    /** Minutes and seconds, rounded up as the dock shows them ("1:08"). */
    fun clock(millis: Long): String {
        val seconds = ceil(millis.coerceAtLeast(0) / 1000.0).toLong()
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}

/**
 * The number of the first pending set of the workout exercise [workoutExerciseId], in its order
 * (1-based, as the set rows count them), or null when it has none or is gone.
 */
internal suspend fun WorkoutRepository.pendingSetNumber(workoutExerciseId: String): Int? {
    val entry = workoutExercise(workoutExerciseId) ?: return null
    val sets = workoutSets(entry.workoutId).filter { it.workoutExerciseId == workoutExerciseId }.sortedBy { it.position }
    val index = sets.indexOfFirst { !it.completed }
    return if (index < 0) null else index + 1
}
