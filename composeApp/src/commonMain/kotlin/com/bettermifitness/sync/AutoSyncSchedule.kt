package com.bettermifitness.sync

import com.bettermifitness.sync.data.preferences.SyncPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Restores or clears the platform background schedule based on the Auto-sync preference.
 * Call after Koin starts (Android [Application], iOS launch).
 */
object AutoSyncSchedule {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun restore() {
        scope.launch {
            rescheduleIfEnabledSuspend()
        }
    }

    /** Same as [restore] — for iOS scenePhase / after BG task. */
    fun rescheduleIfEnabled() {
        restore()
    }

    private suspend fun rescheduleIfEnabledSuspend() {
        val enabled = try {
            KoinPlatform.getKoin().get<SyncPreferences>().autoSync.first()
        } catch (_: Exception) {
            false
        }
        if (enabled) {
            AutoSyncPlatform.scheduleBackgroundRefresh()
        } else {
            AutoSyncPlatform.cancelBackgroundRefresh()
        }
    }
}
