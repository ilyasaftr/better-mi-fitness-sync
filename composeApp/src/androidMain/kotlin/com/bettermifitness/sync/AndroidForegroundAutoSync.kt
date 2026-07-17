package com.bettermifitness.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.sync.SyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Mirrors iOS scenePhase.active: when Auto-sync is on, run a full-range sync
 * as the app comes to foreground (user-configured range).
 */
object AndroidForegroundAutoSync {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scope.launch { runIfEnabled() }
                }
            },
        )
    }

    private suspend fun runIfEnabled() {
        try {
            val prefs = KoinPlatform.getKoin().get<SyncPreferences>()
            if (!prefs.autoSync.first()) return
            val coordinator = KoinPlatform.getKoin().get<SyncCoordinator>()
            val outcome = coordinator.run(
                rangeDaysOverride = 0,
                requireAutoSync = true,
                requestHealthPermissions = false,
                recordAsBackground = false,
                resetProgress = false,
                userInitiated = false,
            )
            // Permission UI is not available from ProcessLifecycle; user must grant on Sync screen once.
            if (outcome is SyncOutcome.Failed) {
                android.util.Log.w("AndroidForegroundAutoSync", "sync failed: ${outcome.message}")
            }
        } catch (e: Exception) {
            android.util.Log.w("AndroidForegroundAutoSync", "sync error", e)
        }
    }
}
