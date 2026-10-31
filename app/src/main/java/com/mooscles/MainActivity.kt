package com.mooscles

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.mooscles.updates.UpdateNotifications
import com.mooscles.ui.MoosclesApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val updateApproval = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        lifecycleScope.launch { runCatching { (application as MoosclesApplication).updates.approvalReturned(result.resultCode == RESULT_CANCELED) } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state = vm.contentUi.collectAsStateWithLifecycle()
            val timer = vm.timerUi.collectAsStateWithLifecycle()
            val opening = vm.workoutOpening.collectAsStateWithLifecycle()
            MoosclesApp(state.value, vm, timer, opening)
        }
        if (intent?.action == UpdateNotifications.OPEN_UPDATES) vm.openUpdateSettings()
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
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); if (intent.action == UpdateNotifications.OPEN_UPDATES) vm.openUpdateSettings() }
    override fun onStop() { (application as MoosclesApplication).timer.foreground = false; super.onStop() }
}
