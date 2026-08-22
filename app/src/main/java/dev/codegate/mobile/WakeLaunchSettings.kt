package dev.codegate.mobile

import android.content.Context

object WakeLaunchSettings {
    private const val PREFERENCES = "codegate_wake_launch"
    private const val ENABLED = "enabled"
    private const val START_AFTER_BOOT = "start_after_boot"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun startsAfterBoot(context: Context): Boolean =
        preferences(context).getBoolean(START_AFTER_BOOT, true)

    fun setStartsAfterBoot(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(START_AFTER_BOOT, enabled).apply()
    }

    private fun preferences(context: Context) =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
