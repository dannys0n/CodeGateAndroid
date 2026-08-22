package dev.codegate.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        if (!WakeLaunchSettings.isEnabled(context) || !WakeLaunchSettings.startsAfterBoot(context)) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, UnlockMonitorService::class.java).putExtra(
                UnlockMonitorService.EXTRA_LAUNCH_IF_INTERACTIVE,
                true
            )
        )
    }
}
