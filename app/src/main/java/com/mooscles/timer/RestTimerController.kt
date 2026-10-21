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
    private val persistence: RestTimerPersistence = RestTimerPersistence(context.moosclesStore)
) {
    private val mutex = Mutex()
    private val alarm = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val problem = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = problem.asStateFlow()
    fun clearError() { problem.value = null }
    var foreground: Boolean = false
    // Reading errors never replace the on-disk store. Retain the last good state (or the
    // initial idle state) while retrying, allowing recovery from transient storage failures.
    val state = persistence.states.retryWhen { failure, attempt ->
        report(failure)
        delay((attempt + 1).coerceAtMost(30) * 1000)
        true
    }.catch { failure ->
        report(failure)
        emit(RestTimerState())
    }.stateIn(scope, SharingStarted.Eagerly, RestTimerState())
    private val ticks = flow { while (true) { emit(System.currentTimeMillis()); delay(250) } }
    val ui: StateFlow<TimerUi> = combine(state, ticks) { s, now ->
        if (s.running && s.remaining(now) == 0L) scope.launch { complete(s.token) }
        TimerUi(s.exerciseName, ceil(s.remaining(now) / 1000.0).toInt(), (s.durationMillis / 1000).toInt(), s.running, s.paused, s.finished)
    }.stateIn(scope, SharingStarted.Eagerly, TimerUi())
    init {
        notifications.createNotificationChannel(NotificationChannel("rest", "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when an exercise rest period ends"; enableVibration(true)
        })
        // Wait for actual disk state. The synthetic StateFlow initial value must never cancel a
        // previously registered alarm while a killed process is being recreated.
        scope.launch {
            var restored = false
            while (!restored) {
                safely {
                    mutex.withLock { schedule(persistence.snapshot()) }
                    restored = true
                }
                if (!restored) delay(1000)
            }
        }
    }
    private fun report(failure: Throwable) {
        if (failure is CancellationException) throw failure
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
    private suspend fun mutate(command: suspend RestTimerPersistence.() -> RestTimerState) = safely {
        mutex.withLock {
            // Schedule before returning, after DataStore has made the transition durable.
            schedule(persistence.command())
        }
    }
    suspend fun start(name: String, seconds: Int) {
        notifications.cancel(1)
        mutate { this.start(name, seconds) }
    }
    suspend fun pause() { mutate { this.pause() } }
    suspend fun resume() { mutate { this.resume() } }
    suspend fun reset() { mutate { this.reset() }; notifications.cancel(1) }
    suspend fun skip() { mutate { this.skip() }; notifications.cancel(1) }
    suspend fun adjust(seconds: Int) { mutate { this.adjust(seconds) } }
    suspend fun complete(token: Long) = safely {
        val s = mutex.withLock {
            val claimed = persistence.claimCompletion(token)
            if (claimed != null) schedule(persistence.snapshot())
            claimed
        }
        if (s == null) return@safely
        if (foreground) {
            runCatching { val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85); tone.startTone(ToneGenerator.TONE_PROP_ACK, 600); scope.launch { delay(1000); tone.release() } }
        } else if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, "rest").setSmallIcon(R.drawable.ic_rest_notification)
                .setContentTitle("Rest complete").setContentText("${s.exerciseName} • Ready for your next set")
                .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
            notifications.notify(1, notification)
        }
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as MoosclesApplication
        app.appScope.launch { try { app.timer.complete(intent.getLongExtra("token", 0)) } finally { pending.finish() } }
    }
}
