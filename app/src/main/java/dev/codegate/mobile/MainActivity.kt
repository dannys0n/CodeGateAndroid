package dev.codegate.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.codegate.mobile.ui.theme.CodeGateAndroidTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && WakeLaunchSettings.isEnabled(this)) startUnlockMonitor()
        if (WakeLaunchSettings.isEnabled(this)) requestOverlayPermissionIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (WakeLaunchSettings.isEnabled(this)) {
            startUnlockMonitor()
            requestRequiredPermissions()
        }
        enableEdgeToEdge()
        val repository = LessonRepository(applicationContext)
        setContent {
            CodeGateAndroidTheme {
                CodeGatePrototype(
                    repository = repository,
                    onSubmit = ::finishAndRemoveTask,
                    onWakeLaunchChanged = ::setWakeLaunchEnabled
                )
            }
        }
    }

    private fun startUnlockMonitor() {
        ContextCompat.startForegroundService(this, Intent(this, UnlockMonitorService::class.java))
    }

    private fun requestRequiredPermissions() {
        if (!WakeLaunchSettings.isEnabled(this)) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestOverlayPermissionIfNeeded()
    }

    private fun setWakeLaunchEnabled(enabled: Boolean) {
        WakeLaunchSettings.setEnabled(this, enabled)
        if (enabled) {
            startUnlockMonitor()
            requestRequiredPermissions()
        } else {
            stopService(Intent(this, UnlockMonitorService::class.java))
        }
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (Settings.canDrawOverlays(this)) return
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }
}
