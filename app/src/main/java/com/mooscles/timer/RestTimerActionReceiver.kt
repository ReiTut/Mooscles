package com.mooscles.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mooscles.MoosclesApplication
import kotlinx.coroutines.launch

/**
 * −15 s, +15 s and Skip from the rest notification. Not exported: only the notification's explicit,
 * immutable intents reach it. It also runs in a cold process, without an Activity.
 */
class RestTimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as MoosclesApplication
        app.appScope.launch {
            try { RestTimerActions(app.trainingGate, app.updates, app.timer).perform(intent.action) } finally { pending.finish() }
        }
    }
}
