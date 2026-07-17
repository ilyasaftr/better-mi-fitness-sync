package com.bettermifitness.sync.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bettermifitness.sync.di.initKoin
import org.koin.core.context.GlobalContext
import org.koin.mp.KoinPlatform

/**
 * Opportunistic background sync (WorkManager).
 * Mirrors iOS BGAppRefresh: last **1 day**, requires Auto-sync ON, no permission UI.
 *
 * Retry policy:
 * - Success / partial success / skipped / not logged in / health unavailable → [Result.success]
 * - Transient [SyncOutcome.Failed] → [Result.retry] (WorkManager exponential backoff)
 * - Non-retryable failures (auth without refresh, permanent) → [Result.failure]
 */
class MiSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            ensureKoin()
            val coordinator = KoinPlatform.getKoin().get<SyncCoordinator>()
            val outcome = coordinator.run(
                rangeDaysOverride = 1,
                requireAutoSync = true,
                requestHealthPermissions = false,
                recordAsBackground = true,
                resetProgress = false,
                userInitiated = false,
            )
            Log.i(TAG, "background sync finished: $outcome")
            when (outcome) {
                SyncOutcome.Success,
                is SyncOutcome.PartialSuccess,
                SyncOutcome.Skipped,
                SyncOutcome.NotLoggedIn,
                SyncOutcome.HealthUnavailable,
                -> Result.success()
                is SyncOutcome.Failed ->
                    if (outcome.shouldRetryBackground()) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "background sync error", e)
            Result.retry()
        }
    }

    private fun ensureKoin() {
        if (GlobalContext.getOrNull() == null) {
            // Application should have started Koin; defensive for edge process starts.
            com.bettermifitness.sync.di.provideAndroidContext(applicationContext)
            initKoin()
        }
    }

    companion object {
        private const val TAG = "MiSyncWorker"
        const val UNIQUE_WORK_NAME = "mi_fitness_auto_sync"
    }
}
