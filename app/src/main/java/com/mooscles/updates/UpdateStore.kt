package com.mooscles.updates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mooscles.ui.UpdatePhase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

val Context.updateStore by preferencesDataStore(name = "app_updates")

/** Separate from training preferences and Room: update cleanup never touches workout data. */
data class UpdateSnapshot(
    val automatic: Boolean = true, val wifiOnly: Boolean = true, val prereleases: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.IDLE, val available: AvailableUpdate? = null,
    val checkedAt: Long = 0, val attemptedAt: Long = 0, val retryAt: Long = 0,
    val checkFailures: Int = 0, val cache: ReleaseCache = ReleaseCache(),
    val downloadedBytes: Long = 0, val generation: Long = 0, val cancelledVersion: Long = 0,
    val sessionId: Int = -1, val pendingActionUri: String? = null,
    val sourcePermissionPending: Boolean = false, val installAttemptedVersion: Long = 0,
    val lastInstalledVersion: Long = 0, val message: String? = null,
)

class UpdateStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("state_v1")
    val flow = store.data.map { decode(it[key]) }.catch {
        emit(UpdateSnapshot(phase = UpdatePhase.FAILED,
            message = "Update settings could not be read. Your workout data is unaffected."))
    }
    suspend fun snapshot() = flow.first()
    suspend fun change(transform: (UpdateSnapshot) -> UpdateSnapshot): UpdateSnapshot {
        var result = UpdateSnapshot()
        store.edit { values -> result = transform(decode(values[key])); values[key] = encode(result) }
        return result
    }

    companion object {
        fun encode(s: UpdateSnapshot): String = JSONObject().apply {
            put("automatic", s.automatic); put("wifiOnly", s.wifiOnly); put("prereleases", s.prereleases)
            put("phase", s.phase.name); put("available", s.available?.let(UpdateJson::encodeAvailable))
            put("checkedAt", s.checkedAt); put("attemptedAt", s.attemptedAt); put("retryAt", s.retryAt)
            put("checkFailures", s.checkFailures); put("cache", s.cache.toJson())
            put("downloadedBytes", s.downloadedBytes); put("generation", s.generation)
            put("cancelledVersion", s.cancelledVersion); put("sessionId", s.sessionId)
            put("pendingActionUri", s.pendingActionUri); put("sourcePermissionPending", s.sourcePermissionPending)
            put("installAttemptedVersion", s.installAttemptedVersion); put("lastInstalledVersion", s.lastInstalledVersion)
            put("message", s.message)
        }.toString()

        fun decode(text: String?): UpdateSnapshot {
            if (text == null) return UpdateSnapshot()
            return try {
                val j = JSONObject(text)
                UpdateSnapshot(
                    automatic = j.optBoolean("automatic", true), wifiOnly = j.optBoolean("wifiOnly", true),
                    prereleases = j.optBoolean("prereleases", false),
                    phase = runCatching { UpdatePhase.valueOf(j.optString("phase", "IDLE")) }.getOrDefault(UpdatePhase.IDLE),
                    available = UpdateJson.decodeAvailable(j.optString("available")),
                    checkedAt = j.optLong("checkedAt"), attemptedAt = j.optLong("attemptedAt"), retryAt = j.optLong("retryAt"),
                    checkFailures = j.optInt("checkFailures"), cache = ReleaseCache.fromJson(j.optString("cache")),
                    downloadedBytes = j.optLong("downloadedBytes"), generation = j.optLong("generation"),
                    cancelledVersion = j.optLong("cancelledVersion"), sessionId = j.optInt("sessionId", -1),
                    pendingActionUri = j.optString("pendingActionUri").takeIf { it.isNotBlank() },
                    sourcePermissionPending = j.optBoolean("sourcePermissionPending"),
                    installAttemptedVersion = j.optLong("installAttemptedVersion"), lastInstalledVersion = j.optLong("lastInstalledVersion"),
                    message = j.optString("message").takeIf { it.isNotBlank() },
                )
            } catch (_: Exception) {
                UpdateSnapshot(phase = UpdatePhase.FAILED, message = "Saved update state was unreadable. Check again to recover; workout data is preserved.")
            }
        }
    }
}

object UpdatePolicy {
    const val FOREGROUND_INTERVAL = 60 * 60 * 1000L
    const val MANUAL_INTERVAL = 30 * 1000L
    fun canCheck(state: UpdateSnapshot, manual: Boolean, now: Long): Boolean =
        now >= state.retryAt && (state.attemptedAt == 0L || now - state.attemptedAt >=
            if (manual) MANUAL_INTERVAL else FOREGROUND_INTERVAL)

    fun retryDelay(failures: Int): Long = (60_000L * (1L shl failures.coerceIn(0, 8))).coerceAtMost(6 * 60 * 60_000L)
    fun mayInstall(hasActiveWorkout: Boolean, hasRestTimer: Boolean) = !hasActiveWorkout && !hasRestTimer
    fun restTimerBusy(timer: com.mooscles.timer.RestTimerState, now: Long): Boolean =
        timer.paused || timer.running && timer.remaining(now) > 0
    fun newer(candidate: Long, installed: Long) = candidate > installed
}
