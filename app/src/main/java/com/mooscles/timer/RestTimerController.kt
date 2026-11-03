package com.mooscles.timer

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mooscles.MainActivity
import com.mooscles.MoosclesApplication
import com.mooscles.R
import com.mooscles.moosclesStore
import com.mooscles.ui.TimerUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

class RestTimerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val persistence: RestTimerPersistence = RestTimerPersistence(context.moosclesStore),
    private val activeWorkoutId: suspend () -> String? = {
        (context.applicationContext as MoosclesApplication).repository.activeWorkout()?.id
    },
    private val sessionChanges: Flow<String?> = (context.applicationContext as MoosclesApplication)
        .repository.activeWorkoutIds,
    private val now: () -> Long = System::currentTimeMillis,
    /** The number of the next pending set of a workout exercise, for the notifications' text. */
    private val pendingSet: suspend (workoutExerciseId: String) -> Int? = { id ->
        (context.applicationContext as MoosclesApplication).repository.pendingSetNumber(id)
    },
) : RestTimer {
    private val mutex = Mutex()
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private var activeTone: ToneGenerator? = null
    private val problem = MutableStateFlow<String?>(null)
    private val problemCode = MutableStateFlow<RestTimerError?>(null)
    override val error: StateFlow<String?> = problem.asStateFlow()
    override val errorCode: StateFlow<RestTimerError?> = problemCode.asStateFlow()
    override fun clearError() { problem.value = null; problemCode.value = null }
    private val texts = RestNotificationTexts(context)
    private val inFront = MutableStateFlow(false)
    /** Whether an Activity is started; the progress notification only shows while it is not. */
    var foreground: Boolean
        get() = inFront.value
        set(value) { inFront.value = value }
    /** Each state read from storage, never the placeholder [state] starts with: null until the first read. */
    private val stored = MutableStateFlow<RestTimerState?>(null)
    // Reading errors never replace the on-disk store. Retain the last good state (or the
    // initial idle state) while retrying, allowing recovery from transient storage failures.
    val state = persistence.states.onEach { stored.value = it }.retryWhen { failure, attempt ->
        report(failure)
        delay((attempt + 1).coerceAtMost(30) * 1000)
        true
    }.catch { failure ->
        report(failure)
        emit(RestTimerState())
    }.stateIn(scope, SharingStarted.Eagerly, RestTimerState())
    private val ticks = flow { while (true) { emit(Unit); delay(250) } }
    override val ui: StateFlow<TimerUi> = combine(state, ticks) { s, _ ->
        val now = now()
        if (s.running && s.remaining(now) == 0L) scope.launch { complete(s.token) }
        TimerUi(s.exerciseName, ceil(s.remaining(now) / 1000.0).toInt(), (s.durationMillis / 1000).toInt(),
            s.running, s.paused, s.finished, progress = s.progress(now), workoutExerciseId = s.workoutExerciseId)
    }.stateIn(scope, SharingStarted.Eagerly, TimerUi())
    init {
        // The rest channel keeps its id and importance; recreating it only updates its localized name.
        notifications.createNotificationChannel(NotificationChannel(RestNotifications.CHANNEL, texts.channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = texts.channelDescription; enableVibration(true)
        })
        notifications.createNotificationChannel(NotificationChannel(RestNotifications.PROGRESS_CHANNEL, texts.progressChannelName, NotificationManager.IMPORTANCE_LOW).apply {
            description = texts.progressChannelDescription; setSound(null, null); enableVibration(false); setShowBadge(false)
        })
        // D16: in the background, a running or paused rest has an ongoing notification. It follows
        // only the stored state (never the ticks), and a process that starts in the background with
        // a rest saved shows it again.
        scope.launch {
            combine(stored.filterNotNull(), inFront) { s, front -> s.takeIf { !front && showsProgress(it) } }
                .distinctUntilChanged()
                .collectLatest { s -> if (s == null) notifications.cancel(RestNotifications.PROGRESS_ID) else postProgress(s) }
        }
        // Observe actual persisted sessions, never a synthetic UI/loading value. Re-reading the
        // snapshot also prevents a delayed emission from clearing a newly started session.
        scope.launch {
            sessionChanges.retryWhen { failure, attempt ->
                report(failure)
                delay((attempt + 1).coerceAtMost(30) * 1000)
                true
            }.collect {
                var restored = false
                while (!restored) {
                    safely {
                        mutex.withLock {
                            val workoutId = activeWorkoutId()
                            if (workoutId == null) cancelAlerts()
                            schedule(persistence.reconcileSession(workoutId))
                        }
                        restored = true
                    }
                    if (!restored) delay(1000)
                }
            }
        }
    }
    private fun report(failure: Throwable) {
        if (failure is CancellationException) throw failure
        // Every failure reported here is a storage access. The code goes first, so the text never appears without it.
        problemCode.value = RestTimerError.STORAGE_UNAVAILABLE
        problem.value = "Unable to access the rest timer: ${failure.message ?: "local storage is unavailable"}. Your workout data is saved separately."
    }
    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (failure: Exception) { report(failure) }
    }
    private fun intent(token: Long = 0) = PendingIntent.getBroadcast(context, 1,
        Intent(context, RestAlarmReceiver::class.java).putExtra("token", token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun schedule(s: RestTimerState) {
        alarm.cancel(intent())
        if (!s.running) return
        val pending = intent(s.token)
        // Exact alarms require special user access on Android 12+. A permission-free fallback is inexact.
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.deadline, pending)
        } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.deadline, pending)
    }
    private fun showsProgress(s: RestTimerState) = s.paused || (s.running && s.remaining(now()) > 0)
    private fun mayNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private suspend fun nextSet(s: RestTimerState): Int? {
        val id = s.workoutExerciseId ?: return null
        return try { pendingSet(id) } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            null // The text then names the exercise alone.
        }
    }
    /** Opens the workout; both rest notifications use it (decision 25). */
    private fun openWorkout() = PendingIntent.getActivity(context, OPEN_WORKOUT_REQUEST,
        // MainActivity has the standard launch mode: CLEAR_TOP with SINGLE_TOP returns to the instance
        // already in the task (onNewIntent) instead of stacking a second one when it is not on top.
        Intent(context, MainActivity::class.java).setAction(RestNotifications.OPEN_WORKOUT)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    /** An explicit, immutable broadcast to the receiver, which is not exported. */
    private fun action(name: String, request: Int) = PendingIntent.getBroadcast(context, request,
        Intent(context, RestTimerActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private suspend fun postProgress(s: RestTimerState) {
        if (!mayNotify()) return
        val next = nextSet(s)
        // The Activity may have come to the front while the set was read.
        if (inFront.value) return
        val builder = NotificationCompat.Builder(context, RestNotifications.PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_rest_notification)
            .setContentTitle(texts.progressTitle)
            .setContentText(texts.progressText(s.exerciseName, next))
            .setContentIntent(openWorkout())
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, texts.minus, action(RestNotifications.ACTION_MINUS, MINUS_REQUEST))
            .addAction(0, texts.plus, action(RestNotifications.ACTION_PLUS, PLUS_REQUEST))
            .addAction(0, texts.skip, action(RestNotifications.ACTION_SKIP, SKIP_REQUEST))
        // The system counts a running rest down itself: no service, and the app is not woken every second.
        if (s.running) builder.setShowWhen(true).setWhen(s.deadline).setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(s.remaining(now()) + RestNotifications.PROGRESS_TIMEOUT_MARGIN_MILLIS)
        else builder.setShowWhen(false).setSubText(texts.paused(s.pausedMillis))
        notifications.notify(RestNotifications.PROGRESS_ID, builder.build())
    }
    private fun cancelAlerts() {
        alarm.cancel(intent())
        notifications.cancel(RestNotifications.FINISHED_ID)
        notifications.cancel(RestNotifications.PROGRESS_ID)
        activeTone?.let { tone -> runCatching { tone.stopTone(); tone.release() } }
        activeTone = null
    }
    private suspend fun mutate(command: suspend RestTimerPersistence.() -> RestTimerState) = safely {
        mutex.withLock {
            val workoutId = activeWorkoutId()
            if (workoutId == null) cancelAlerts()
            val restored = persistence.reconcileSession(workoutId)
            if (workoutId == null) {
                schedule(restored)
                return@withLock
            }
            // Schedule before returning, after DataStore has made the transition durable.
            schedule(persistence.command())
        }
    }
    override suspend fun start(name: String, seconds: Int, workoutExerciseId: String?) {
        notifications.cancel(RestNotifications.FINISHED_ID)
        mutate { this.start(name, seconds, workoutExerciseId) }
    }
    override suspend fun pause() { mutate { this.pause() } }
    override suspend fun resume() { mutate { this.resume() } }
    override suspend fun reset() { mutate { this.reset() }; notifications.cancel(RestNotifications.FINISHED_ID) }
    override suspend fun skip() { mutate { this.skip() }; notifications.cancel(RestNotifications.FINISHED_ID) }
    override suspend fun adjust(seconds: Int) { mutate { this.adjust(seconds) } }
    override suspend fun clear() = safely { clearForSessionEnd() }
    /** Session completion navigation must not report success after a failed durable clear. */
    override suspend fun clearForSessionEnd() {
        mutex.withLock {
            // Stop external effects even if the following durable clear temporarily fails.
            cancelAlerts()
            schedule(persistence.clear())
        }
    }
    suspend fun complete(token: Long) = safely {
        mutex.withLock {
            val workoutId = activeWorkoutId()
            if (workoutId == null) cancelAlerts()
            schedule(persistence.reconcileSession(workoutId))
            if (workoutId == null) return@withLock
            val s = persistence.claimCompletion(token) ?: return@withLock
            schedule(persistence.snapshot())
            notifications.cancel(RestNotifications.PROGRESS_ID)
            if (foreground) {
                runCatching {
                    val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                    activeTone = tone
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 600)
                    scope.launch {
                        delay(1000)
                        if (activeTone === tone) { tone.release(); activeTone = null }
                    }
                }
            } else if (mayNotify()) {
                val notification = NotificationCompat.Builder(context, RestNotifications.CHANNEL).setSmallIcon(R.drawable.ic_rest_notification)
                    .setContentTitle(texts.finishedTitle).setContentText(texts.finishedText(s.exerciseName, nextSet(s)))
                    .setContentIntent(openWorkout()).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
                notifications.notify(RestNotifications.FINISHED_ID, notification)
            }
        }
    }

    private companion object {
        // PendingIntent request codes; the alarm uses 1 on its own receiver.
        const val OPEN_WORKOUT_REQUEST = 3
        const val MINUS_REQUEST = 4
        const val PLUS_REQUEST = 5
        const val SKIP_REQUEST = 6
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as MoosclesApplication
        app.appScope.launch { try { app.timer.complete(intent.getLongExtra("token", 0)) } finally { pending.finish() } }
    }
}
