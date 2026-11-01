package com.mooscles.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReduced(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = readReduced(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = readReduced(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

private fun readReduced(resolver: android.content.ContentResolver): Boolean =
    isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
