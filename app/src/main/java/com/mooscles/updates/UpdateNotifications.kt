package com.mooscles.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mooscles.MainActivity
import com.mooscles.R

class UpdateNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun ready(version: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 2,
            Intent(context, MainActivity::class.java).setAction(OPEN_UPDATES).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(2, NotificationCompat.Builder(context, "updates").setSmallIcon(R.drawable.ic_rest_notification)
            .setContentTitle("Mooscles update ready")
            .setContentText("Version $version is ready. Open Settings when you have finished training.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }
    fun clear() { manager.cancel(2) }
    companion object { const val OPEN_UPDATES = "com.mooscles.OPEN_UPDATES" }
}
