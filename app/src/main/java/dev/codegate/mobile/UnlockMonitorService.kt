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

class UnlockMonitorService : Service() {
    private var lastWakeLaunchAt = 0L

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_ON) {
                showForegroundNotification(unlocked = true)
                launchCodeGateAfterWake(intent.action)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showForegroundNotification(unlocked = false)
        val wakeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
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
        showForegroundNotification(unlocked = false)
        if (intent?.getBooleanExtra(EXTRA_LAUNCH_IF_INTERACTIVE, false) == true) {
            launchAfterBootIfDeviceReady()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchAfterBootIfDeviceReady() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isInteractive != true) return
        launchCodeGateAfterWake(Intent.ACTION_SCREEN_ON)
    }

    private fun launchCodeGateAfterWake(action: String?) {
        if (!Settings.canDrawOverlays(this)) return

        if (action == Intent.ACTION_SCREEN_ON) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (keyguardManager?.isKeyguardLocked == true) return
        }

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

    private fun showForegroundNotification(unlocked: Boolean) {
        val notification = buildNotification(unlocked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(unlocked: Boolean): Notification {
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
            .setContentText(getString(if (unlocked) R.string.unlock_monitor_unlocked else R.string.unlock_monitor_running))
            .setContentIntent(launchAction)
            .setOngoing(true)
            .setOnlyAlertOnce(!unlocked)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, getString(R.string.unlock_monitor_stop), stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.unlock_monitor_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.unlock_monitor_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "codegate_unlock_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "dev.codegate.mobile.STOP_UNLOCK_MONITOR"
        private const val WAKE_LAUNCH_DEBOUNCE_MS = 1_500L
        const val EXTRA_LAUNCH_IF_INTERACTIVE = "dev.codegate.mobile.LAUNCH_IF_INTERACTIVE"
    }
}
