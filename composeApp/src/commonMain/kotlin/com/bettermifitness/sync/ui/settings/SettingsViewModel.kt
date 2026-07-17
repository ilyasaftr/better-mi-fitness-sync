package com.bettermifitness.sync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.AutoSyncPlatform
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthReadiness
import com.bettermifitness.sync.sync.SyncOutcomeLabels
import com.bettermifitness.sync.util.RelativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val enabledMetrics: Set<String> = SyncPreferences.ALL_METRIC_KEYS,
    val rangeDays: Int = 7,
    val autoSync: Boolean = false,
    val lastBackgroundSyncLabel: String = "Never",
    val lastBackgroundStatusTitle: String = "No attempt yet",
    val lastBackgroundDetail: String = "Background sync has not run yet",
    val lastBackgroundIsError: Boolean = false,
    val lastSyncLabel: String = "Never",
    val lastSyncStatusTitle: String = "No sync yet",
    val lastSyncDetail: String = "Run a sync to see results here",
    val lastSyncIsError: Boolean = false,
    val lastSyncIsWarning: Boolean = false,
    val bgRefreshLabel: String = "",
    val canTestBgRefresh: Boolean = false,
    val bgTestStatus: String? = null,
    val bgTestRunning: Boolean = false,
    val showShortcutsHelp: Boolean = false,
    val showBackgroundRefreshDetails: Boolean = true,
    val healthServiceName: String = "",
    val healthStatusTitle: String = "",
    val healthStatusDetail: String = "",
    val healthNeedsAction: Boolean = false,
)

class SettingsViewModel(
    private val syncPreferences: SyncPreferences,
    private val healthAvailability: HealthAvailability,
) : ViewModel() {

    private val showShortcutsHelp = AutoSyncPlatform.supportsShortcutsHelp()

    private val _local = MutableStateFlow(
        LocalSettingsState(
            bgRefreshLabel = AutoSyncPlatform.backgroundRefreshStatusLabel(),
            canTestBgRefresh = AutoSyncPlatform.supportsOpportunisticRefreshTest(),
        ),
    )
    private val _health = MutableStateFlow(
        HealthReadiness(
            available = true,
            permissionsGranted = true,
            serviceName = healthAvailability.healthServiceName(),
            hint = null,
        ),
    )

    private val lastSyncBundle = combine(
        syncPreferences.lastSyncTime,
        syncPreferences.lastSyncStatus,
        syncPreferences.lastSyncMessage,
    ) { t, s, m -> Triple(t, s, m) }

    private val lastBgBundle = combine(
        syncPreferences.lastBackgroundSyncTime,
        syncPreferences.lastBackgroundSyncStatus,
        syncPreferences.lastBackgroundSyncMessage,
    ) { t, s, m -> Triple(t, s, m) }

    private val configBundle = combine(
        syncPreferences.enabledMetrics,
        syncPreferences.syncRangeDays,
        syncPreferences.autoSync,
    ) { e, r, a -> Triple(e, r, a) }

    val uiState: StateFlow<SettingsUiState> = combine(
        configBundle,
        lastSyncBundle,
        lastBgBundle,
        _local,
        _health,
    ) { config, lastSync, lastBg, local, health ->
        SettingsUiState(
            enabledMetrics = config.first,
            rangeDays = config.second,
            autoSync = config.third,
            lastBackgroundSyncLabel = RelativeTime.format(lastBg.first),
            lastBackgroundStatusTitle = SyncOutcomeLabels.title(lastBg.second),
            lastBackgroundDetail = SyncOutcomeLabels.detail(lastBg.second, lastBg.third),
            lastBackgroundIsError = SyncOutcomeLabels.isError(lastBg.second),
            lastSyncLabel = RelativeTime.format(lastSync.first),
            lastSyncStatusTitle = SyncOutcomeLabels.title(lastSync.second),
            lastSyncDetail = SyncOutcomeLabels.detail(lastSync.second, lastSync.third),
            lastSyncIsError = SyncOutcomeLabels.isError(lastSync.second),
            lastSyncIsWarning = SyncOutcomeLabels.isWarning(lastSync.second),
            bgRefreshLabel = local.bgRefreshLabel,
            canTestBgRefresh = local.canTestBgRefresh,
            bgTestStatus = local.bgTestStatus,
            bgTestRunning = local.bgTestRunning,
            showShortcutsHelp = showShortcutsHelp,
            showBackgroundRefreshDetails = true,
            healthServiceName = health.serviceName,
            healthStatusTitle = health.statusTitle,
            healthStatusDetail = health.statusDetail,
            healthNeedsAction = !health.isReady,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            bgRefreshLabel = _local.value.bgRefreshLabel,
            canTestBgRefresh = _local.value.canTestBgRefresh,
            showShortcutsHelp = showShortcutsHelp,
            healthServiceName = healthAvailability.healthServiceName(),
        ),
    )

    init {
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch {
            try {
                _health.value = healthAvailability.readiness()
            } catch (_: Exception) {
                _health.value = HealthReadiness(
                    available = false,
                    permissionsGranted = false,
                    serviceName = healthAvailability.healthServiceName(),
                    hint = "Could not check health service status.",
                )
            }
        }
    }

    fun openHealthService() {
        healthAvailability.openHealthService()
    }

    fun setMetricEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setMetricEnabled(key, enabled)
        }
    }

    fun setSyncRangeDays(days: Int) {
        viewModelScope.launch {
            syncPreferences.setSyncRangeDays(days)
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            syncPreferences.setAutoSync(enabled)
            if (enabled) {
                AutoSyncPlatform.scheduleBackgroundRefresh()
            } else {
                AutoSyncPlatform.cancelBackgroundRefresh()
            }
        }
    }

    fun runBackgroundRefreshTest() {
        if (_local.value.bgTestRunning) return
        if (!uiState.value.autoSync) return

        _local.update {
            it.copy(bgTestRunning = true, bgTestStatus = "Running 1-day refresh…")
        }
        AutoSyncPlatform.runOpportunisticRefreshTest { status ->
            viewModelScope.launch {
                _local.update {
                    it.copy(
                        bgTestRunning = false,
                        bgTestStatus = mapBgTestStatus(status),
                    )
                }
            }
        }
    }

    private fun mapBgTestStatus(status: String): String = when (status) {
        "success" -> "Test OK — last background refresh should update"
        "partial_success" -> "Partial OK — some metrics failed (see Sync screen)"
        "skipped" -> "Skipped (turn Auto-sync ON and stay signed in)"
        "not_logged_in" -> "Not signed in"
        "cancelled" -> "Cancelled"
        else -> "Failed ($status)"
    }

    private data class LocalSettingsState(
        val bgRefreshLabel: String = "",
        val canTestBgRefresh: Boolean = false,
        val bgTestStatus: String? = null,
        val bgTestRunning: Boolean = false,
    )
}
