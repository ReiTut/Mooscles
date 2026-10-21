package com.mooscles.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mooscles.MoosclesApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val retry = (applicationContext as MoosclesApplication).updates.check(inputData.getBoolean("manual", false))
        if (retry && runAttemptCount < 6) Result.retry() else Result.success()
    } catch (e: Exception) { if (e is CancellationException) throw e; Result.retry() }
}

class UpdateDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if ((applicationContext as MoosclesApplication).updates.download(runAttemptCount, inputData.getLong("generation", -1))) Result.retry() else Result.success()
    } catch (e: Exception) { if (e is CancellationException) throw e; Result.failure() }
}

class UpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext as MoosclesApplication
        app.appScope.launch { try { app.updates.onForeground() } finally { pending.finish() } }
    }
}
