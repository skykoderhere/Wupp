package com.wupp.meow.core.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.wa.bridge.whatsbridge.Whatsbridge

/**
 * Lightweight OS-native watchdog using Android's JobScheduler (zero extra dependencies)
 * to ensure [WaRealtimeService] is revived if killed by OEM memory/task sweepers.
 */
class SyncWatchdogJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val appCtx = applicationContext
        val setupDone = appCtx.getSharedPreferences("wupp_setup_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_completed_v1", false)
        val loggedIn = setupDone && runCatching { Whatsbridge.isLoggedIn() }.getOrDefault(false)
        if (loggedIn) {
            if (WaRealtimeService.instance == null) {
                Log.i(TAG, "Watchdog reviving WaRealtimeService")
                WaRealtimeService.ensureStarted(appCtx)
            } else {
                WaRealtimeService.instance?.nudgeReconnectIfNeeded()
            }
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val TAG = "SyncWatchdogJob"
        private const val PERIODIC_JOB_ID = 42011
        private const val RETRY_JOB_ID = 42012
        private const val TASK_REMOVED_REQ_CODE = 42013
        private const val PERIODIC_INTERVAL_MS = 15L * 60L * 1000L

        fun schedulePeriodic(context: Context) {
            val appCtx = context.applicationContext
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val existing = scheduler.getPendingJob(PERIODIC_JOB_ID)
            if (existing != null) return
            val job = JobInfo.Builder(
                PERIODIC_JOB_ID,
                ComponentName(appCtx, SyncWatchdogJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIODIC_INTERVAL_MS)
                .build()
            runCatching { scheduler.schedule(job) }
        }

        fun scheduleOneShotRetry(context: Context, delayMs: Long = 20_000L) {
            val appCtx = context.applicationContext
            val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val job = JobInfo.Builder(
                RETRY_JOB_ID,
                ComponentName(appCtx, SyncWatchdogJobService::class.java)
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs * 3)
                .build()
            runCatching { scheduler.schedule(job) }
        }

        fun scheduleTaskRemovedBounce(context: Context) {
            val appCtx = context.applicationContext
            scheduleOneShotRetry(appCtx, delayMs = 3_000L)
            if (!WaRealtimeService.isIgnoringBatteryOptimizations(appCtx)) return
            val alarmMgr = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val svcIntent = Intent(appCtx, WaRealtimeService::class.java).apply {
                action = WaRealtimeService.ACTION_RECONNECT
            }
            val pi = PendingIntent.getForegroundService(
                appCtx,
                TASK_REMOVED_REQ_CODE,
                svcIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching {
                alarmMgr.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1_500L,
                    pi
                )
            }
        }
    }
}
