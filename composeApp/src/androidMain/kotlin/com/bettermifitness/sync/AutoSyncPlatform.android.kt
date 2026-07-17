package com.bettermifitness.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bettermifitness.sync.sync.MiSyncWorker
import java.util.concurrent.TimeUnit

actual object AutoSyncPlatform {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context {
        check(::appContext.isInitialized) {
            "AutoSyncPlatform.init(context) must be called from Application.onCreate"
        }
        return appContext
    }

    actual fun scheduleBackgroundRefresh() {
        val ctx = requireContext()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        // OS minimum for periodic work is 15 minutes; 1 hour is a battery-friendly default
        // (still not exact-time — Doze / app standby may delay).
        val request = PeriodicWorkRequestBuilder<MiSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            MiSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    actual fun cancelBackgroundRefresh() {
        if (!::appContext.isInitialized) return
        WorkManager.getInstance(appContext).cancelUniqueWork(MiSyncWorker.UNIQUE_WORK_NAME)
    }

    actual fun backgroundRefreshStatusLabel(): String {
        return "Background sync uses WorkManager (~hourly when network is available; not exact-time)."
    }

    actual fun supportsOpportunisticRefreshTest(): Boolean = false

    actual fun runOpportunisticRefreshTest(onDone: (String) -> Unit) {
        onDone("skipped")
    }

    actual fun supportsShortcutsHelp(): Boolean = false
}
