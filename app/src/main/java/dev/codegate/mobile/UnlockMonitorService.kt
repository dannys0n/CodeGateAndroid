package dev.codegate.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UnlockMonitorService : Service() {
    private var lastWakeLaunchAt = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var unlockCheckJob: Job? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> waitForUnlock()
                Intent.ACTION_SCREEN_OFF -> unlockCheckJob?.cancel()
                Intent.ACTION_USER_PRESENT -> {
                    unlockCheckJob?.cancel()
                    launchCodeGateAfterWake()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showForegroundNotification()
        val wakeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            wakeFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        showForegroundNotification()
        if (intent?.getBooleanExtra(EXTRA_LAUNCH_IF_INTERACTIVE, false) == true) {
            launchAfterBootIfDeviceReady()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchAfterBootIfDeviceReady() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isInteractive != true) return
        waitForUnlock()
    }

    private fun waitForUnlock() {
        unlockCheckJob?.cancel()
        unlockCheckJob = serviceScope.launch {
            repeat(UNLOCK_CHECK_ATTEMPTS) {
                if (!isDeviceLocked()) {
                    launchCodeGateAfterWake()
                    return@launch
                }
                delay(UNLOCK_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun isDeviceLocked(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun launchCodeGateAfterWake() {
        if (!Settings.canDrawOverlays(this) || isDeviceLocked()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeLaunchAt < WAKE_LAUNCH_DEBOUNCE_MS) return
        lastWakeLaunchAt = now

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }

    private fun showForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val launchAction = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, UnlockMonitorService::class.java).setAction(ACTION_STOP)
        val stopAction = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.unlock_monitor_title))
            .setContentText(getString(R.string.unlock_monitor_running))
            .setContentIntent(launchAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.unlock_monitor_stop), stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.unlock_monitor_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.unlock_monitor_channel_description)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "codegate_unlock_monitor_silent"
        private const val LEGACY_CHANNEL_ID = "codegate_unlock_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "dev.codegate.mobile.STOP_UNLOCK_MONITOR"
        private const val WAKE_LAUNCH_DEBOUNCE_MS = 1_500L
        private const val UNLOCK_CHECK_INTERVAL_MS = 500L
        private const val UNLOCK_CHECK_ATTEMPTS = 60
        const val EXTRA_LAUNCH_IF_INTERACTIVE = "dev.codegate.mobile.LAUNCH_IF_INTERACTIVE"
    }
}
