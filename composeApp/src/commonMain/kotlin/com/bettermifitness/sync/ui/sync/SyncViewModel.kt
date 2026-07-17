package com.bettermifitness.sync.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.repository.HealthRepository
import com.bettermifitness.sync.data.repository.SyncProgress
import com.bettermifitness.sync.data.repository.SyncState
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.sync.SyncOutcome
import com.bettermifitness.sync.ui.SyncMetric
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val isSyncing: Boolean = false,
    val healthAvailable: Boolean = true,
    val availabilityHint: String? = null,
    val permissionError: String? = null,
    /** Non-fatal banner after a run (success / partial). */
    val outcomeMessage: String? = null,
    val outcomeIsWarning: Boolean = false,
    val healthServiceName: String = "",
    val rangeDays: Int = 7,
    val enabledMetrics: Set<String> = SyncPreferences.ALL_METRIC_KEYS,
    val progress: SyncProgress = SyncProgress(),
    val didAutoSync: Boolean = false,
    val readinessChecked: Boolean = false,
) {
    val visibleMetrics: List<SyncMetric>
        get() = SyncMetric.entries.filter { it.key in enabledMetrics }
}

/**
 * Foreground sync UI: readiness + [SyncCoordinator] for the actual work.
 */
class SyncViewModel(
    private val repository: HealthRepository,
    private val healthAvailability: HealthAvailability,
    private val syncPreferences: SyncPreferences,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _local = MutableStateFlow(
        LocalSyncState(healthServiceName = healthAvailability.healthServiceName()),
    )

    val uiState: StateFlow<SyncUiState> = combine(
        _local,
        repository.syncProgress,
        syncPreferences.enabledMetrics,
        syncPreferences.syncRangeDays,
    ) { local, progress, enabled, rangeDays ->
        SyncUiState(
            isSyncing = local.isSyncing,
            healthAvailable = local.healthAvailable,
            availabilityHint = local.availabilityHint,
            permissionError = local.permissionError,
            outcomeMessage = local.outcomeMessage,
            outcomeIsWarning = local.outcomeIsWarning,
            healthServiceName = local.healthServiceName,
            rangeDays = rangeDays,
            enabledMetrics = enabled,
            progress = progress,
            didAutoSync = local.didAutoSync,
            readinessChecked = local.readinessChecked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncUiState(
            healthServiceName = healthAvailability.healthServiceName(),
        ),
    )

    init {
        viewModelScope.launch {
            checkHealthAndMaybeAutoSync()
        }
    }

    fun startSync() {
        viewModelScope.launch { runSync() }
    }

    fun openHealthService() {
        healthAvailability.openHealthService()
    }

    fun stateFor(progress: SyncProgress, key: String): SyncState = when (key) {
        "heart_rate" -> progress.heartRate
        "resting_heart_rate" -> progress.restingHeartRate
        "sleep" -> progress.sleep
        "steps" -> progress.steps
        "distance" -> progress.distance
        "active_calories" -> progress.activeCalories
        "spo2" -> progress.spo2
        "weight" -> progress.weight
        "workouts" -> progress.workouts
        "blood_pressure" -> progress.bloodPressure
        "temperature" -> progress.temperature
        "vo2_max" -> progress.vo2Max
        else -> SyncState.Idle
    }

    private suspend fun checkHealthAndMaybeAutoSync() {
        try {
            val available = healthAvailability.isAvailable()
            val hint = healthAvailability.availabilityHint()
            _local.update {
                it.copy(
                    healthAvailable = available,
                    availabilityHint = hint,
                    readinessChecked = true,
                )
            }
            if (available && !_local.value.didAutoSync) {
                _local.update { it.copy(didAutoSync = true) }
                runSync()
            }
        } catch (_: Exception) {
            _local.update {
                it.copy(
                    healthAvailable = false,
                    availabilityHint = "Health service is not available on this device.",
                    readinessChecked = true,
                )
            }
        }
    }

    private suspend fun runSync() {
        if (_local.value.isSyncing) return
        _local.update {
            it.copy(
                isSyncing = true,
                permissionError = null,
                outcomeMessage = null,
                outcomeIsWarning = false,
            )
        }
        try {
            when (
                val outcome = syncCoordinator.run(
                    requestHealthPermissions = true,
                    resetProgress = true,
                    userInitiated = true,
                )
            ) {
                is SyncOutcome.Failed ->
                    _local.update {
                        it.copy(
                            permissionError = outcome.message ?: "Sync failed",
                            outcomeMessage = null,
                        )
                    }
                SyncOutcome.HealthUnavailable ->
                    _local.update {
                        it.copy(
                            healthAvailable = false,
                            permissionError = "${it.healthServiceName} is not available.",
                        )
                    }
                SyncOutcome.NotLoggedIn ->
                    _local.update { it.copy(permissionError = "Not logged in — sign in and try again") }
                SyncOutcome.Skipped ->
                    _local.update {
                        it.copy(outcomeMessage = "Nothing to sync (enable metrics in Settings)")
                    }
                SyncOutcome.Success ->
                    _local.update {
                        it.copy(
                            outcomeMessage = "All enabled metrics synced",
                            outcomeIsWarning = false,
                        )
                    }
                is SyncOutcome.PartialSuccess ->
                    _local.update {
                        it.copy(
                            outcomeMessage = outcome.summary,
                            outcomeIsWarning = true,
                        )
                    }
            }
        } finally {
            _local.update { it.copy(isSyncing = false) }
            // Refresh Health Connect / HealthKit readiness after a permission prompt.
            try {
                val available = healthAvailability.isAvailable()
                val hint = healthAvailability.availabilityHint()
                val perms = healthAvailability.hasWritePermissions()
                _local.update {
                    it.copy(
                        healthAvailable = available,
                        availabilityHint = when {
                            !available -> hint
                            !perms -> hint
                                ?: "Write permissions incomplete — grant access and try again."
                            else -> null
                        },
                    )
                }
            } catch (_: Exception) {
                // Keep previous readiness.
            }
        }
    }

    private data class LocalSyncState(
        val isSyncing: Boolean = false,
        val healthAvailable: Boolean = true,
        val availabilityHint: String? = null,
        val permissionError: String? = null,
        val outcomeMessage: String? = null,
        val outcomeIsWarning: Boolean = false,
        val healthServiceName: String = "",
        val didAutoSync: Boolean = false,
        val readinessChecked: Boolean = false,
    )
}
