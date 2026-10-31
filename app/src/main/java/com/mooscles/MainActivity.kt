package com.mooscles

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.mooscles.timer.RestNotifications
import com.mooscles.updates.UpdateNotifications
import com.mooscles.ui.AppThemeBars
import com.mooscles.ui.MoosclesApp

class MainActivity : ComponentActivity(), AppThemeBars {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val updateApproval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch { runCatching { (application as MoosclesApplication).updates.approvalReturned(result.resultCode == RESULT_CANCELED) } }
    }
    // enableEdgeToEdge applies these styles now and again on every configuration change the
    // Activity survives without being recreated (a 90° to 270° rotation, for one). They must ask the
    // app's theme, not the system's night mode, or a dark app under a light system gets light bars.
    // Until MoosclesTheme reports the theme, they follow the system, as the default styles do.
    private var darkTheme: Boolean? = null
    private fun barsDark(resources: Resources) = darkTheme
        ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
    // The scrims are enableEdgeToEdge's defaults (EdgeToEdge.DefaultLightScrim and DefaultDarkScrim).
    private val statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT, ::barsDark)
    private val navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b), ::barsDark)
    override fun appThemeResolved(dark: Boolean) {
        if (darkTheme == dark) return
        darkTheme = dark
        enableEdgeToEdge(statusBarStyle, navigationBarStyle)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle, navigationBarStyle)
        setContent {
            val state = vm.contentUi.collectAsStateWithLifecycle()
            val timer = vm.timerUi.collectAsStateWithLifecycle()
            val workoutRequest = vm.workoutRequest.collectAsStateWithLifecycle()
            // Plan 4A decision 15: the common code reads no platform state; the detail's figure
            // stops while the Activity is not started (in the background).
            val lifecycleState = lifecycle.currentStateAsState()
            val app = application as MoosclesApplication
            ProvideExercisePhotos(app, this) {
                ProvideProgressIndex(app) {
                    MoosclesApp(state.value, vm, timer, workoutRequest.value, foreground = lifecycleState.value.isAtLeast(Lifecycle.State.STARTED))
                }
            }
        }
        // Only a fresh launch from a notification asks for its screen: a recreated Activity
        // (rotation, process restore) keeps the same intent, and asking again would navigate
        // away from wherever the user went since. A new notification tap arrives in onNewIntent.
        if (savedInstanceState == null) openFrom(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.installIntents.collect { intent ->
                    // Launch from this Activity so Android can return the approval result to it.
                    updateApproval.launch(Intent(intent).apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val prefs = getPreferences(MODE_PRIVATE)
            if (!prefs.getBoolean("notification_requested", false)) {
                prefs.edit().putBoolean("notification_requested", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    override fun onStart() { super.onStart(); (application as MoosclesApplication).timer.foreground = true }
    override fun onResume() { super.onResume(); (application as MoosclesApplication).appScope.launch { (application as MoosclesApplication).updates.onForeground() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); openFrom(intent) }
    private fun openFrom(intent: Intent?) {
        when (intent?.action) {
            UpdateNotifications.OPEN_UPDATES -> vm.openUpdateSettings()
            RestNotifications.OPEN_WORKOUT -> vm.openWorkout()
        }
    }
    override fun onStop() { (application as MoosclesApplication).timer.foreground = false; super.onStop() }
}
