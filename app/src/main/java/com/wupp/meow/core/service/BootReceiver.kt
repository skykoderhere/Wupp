package com.wupp.meow.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the companion realtime sync service automatically after device reboot or app update
 * when first-install onboarding has already been completed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val setupDone = context.getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_completed_v1", false)
        if (setupDone) {
            val appCtx = context.applicationContext
            SyncWatchdogJobService.schedulePeriodic(appCtx)
            WaRealtimeService.ensureStarted(appCtx)
        }
    }
}
