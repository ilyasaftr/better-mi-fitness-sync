package com.bettermifitness.sync.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.repository.HealthRepository
import com.bettermifitness.sync.data.repository.SyncProgress
import com.bettermifitness.sync.data.repository.SyncState
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthPermissionRequester
import com.bettermifitness.sync.i18n.L10n
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
    val readinessChecked: Boolean = false,
) {
    val visibleMetrics: List<SyncMetric>
        get() = SyncMetric.entries.filter { it.key in enabledMetrics }
}

/**
 * Foreground sync UI: observes shared [SyncCoordinator] + repository progress.
 * Does **not** own single-flight — [SyncCoordinator.run] is the only gate.
 */
class SyncViewModel(
    private val repository: HealthRepository,
    private val healthAvailability: HealthAvailability,
    private val healthPermissions: HealthPermissionRequester,
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
        syncCoordinator.isRunning,
    ) { local, progress, enabled, rangeDays, running ->
        SyncUiState(
            // App-wide: still true after leaving and re-entering Sync.
            isSyncing = running,
            healthAvailable = local.healthAvailable,
            availabilityHint = local.availabilityHint,
            permissionError = local.permissionError,
            outcomeMessage = local.outcomeMessage,
            outcomeIsWarning = local.outcomeIsWarning,
            healthServiceName = local.healthServiceName,
            rangeDays = rangeDays,
            enabledMetrics = enabled,
            progress = progress,
            readinessChecked = local.readinessChecked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncUiState(
            healthServiceName = healthAvailability.healthServiceName(),
            isSyncing = syncCoordinator.isRunning.value,
        ),
    )

    init {
        viewModelScope.launch {
            checkHealthReadiness()
        }
    }

    fun startSync() {
        viewModelScope.launch {
            // Fast path — coordinator still enforces single-flight.
            if (syncCoordinator.isRunning.value) return@launch
            runSync()
        }
    }

    fun openHealthService() {
        viewModelScope.launch {
            try {
                healthPermissions.requestPermissions()
            } catch (_: Exception) {
                // Denied / failed — open Settings or Health Connect below.
            }
            if (!healthAvailability.hasWritePermissions()) {
                healthAvailability.openHealthService()
            }
            checkHealthReadiness()
        }
    }

    fun stateFor(progress: SyncProgress, key: String): SyncState = when (key) {
        "heart_rate" -> progress.heartRate
        "resting_heart_rate" -> progress.restingHeartRate
        "sleep" -> progress.sleep
        "hrv" -> progress.hrv
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

    /** Health check only — never auto-starts sync on screen enter (spam-safe). */
    private suspend fun checkHealthReadiness() {
        try {
            val available = healthAvailability.isAvailable()
            val hint = healthAvailability.availabilityHint()
            val perms = if (available) {
                try {
                    healthAvailability.hasWritePermissions()
                } catch (_: Exception) {
                    true
                }
            } else {
                false
            }
            _local.update {
                it.copy(
                    healthAvailable = available,
                    availabilityHint = when {
                        !available -> hint
                        !perms -> hint
                            ?: L10n.text(L10n.healthPermissionsIncomplete)
                        else -> null
                    },
                    readinessChecked = true,
                )
            }
        } catch (_: Exception) {
            _local.update {
                it.copy(
                    healthAvailable = false,
                    availabilityHint = L10n.text(L10n.healthNotAvailable),
                    readinessChecked = true,
                )
            }
        }
    }

    private suspend fun runSync() {
        _local.update {
            it.copy(
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
                SyncOutcome.AlreadyRunning -> {
                    // Another caller owns the run; UI already shows isSyncing via coordinator.
                }
                is SyncOutcome.Failed ->
                    _local.update {
                        it.copy(
                            permissionError = outcome.message ?: L10n.text(L10n.syncFailed),
                            outcomeMessage = null,
                        )
                    }
                SyncOutcome.HealthUnavailable ->
                    _local.update {
                        it.copy(
                            healthAvailable = false,
                            permissionError = L10n.textFmt(L10n.syncHealthUnavailable, it.healthServiceName),
                        )
                    }
                SyncOutcome.NotLoggedIn ->
                    _local.update { it.copy(permissionError = L10n.text(L10n.syncNotLoggedIn)) }
                SyncOutcome.Skipped ->
                    _local.update {
                        it.copy(outcomeMessage = L10n.text(L10n.syncNothingToSync))
                    }
                SyncOutcome.Success ->
                    _local.update {
                        it.copy(
                            outcomeMessage = L10n.text(L10n.syncAllMetricsSynced),
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
                                ?: L10n.text(L10n.healthPermissionsIncomplete)
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
        val healthAvailable: Boolean = true,
        val availabilityHint: String? = null,
        val permissionError: String? = null,
        val outcomeMessage: String? = null,
        val outcomeIsWarning: Boolean = false,
        val healthServiceName: String = "",
        val readinessChecked: Boolean = false,
    )
}
