package com.bettermifitness.sync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.api.MeResponse
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthReadiness
import com.bettermifitness.sync.sync.SyncOutcomeLabels
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.util.RelativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val profile: MeResponse? = null,
    val profileError: String? = null,
    val lastSyncLabel: String = "Never",
    val lastSyncStatusTitle: String = "No sync yet",
    val lastSyncDetail: String = "Run a sync to see results here",
    val lastSyncIsError: Boolean = false,
    val lastSyncIsWarning: Boolean = false,
    val lastBackgroundLabel: String = "Never",
    val lastBackgroundDetail: String = "Background sync has not run yet",
    val lastBackgroundIsError: Boolean = false,
    val enabledMetricsCount: Int = SyncMetric.entries.size,
    val totalMetricsCount: Int = SyncMetric.entries.size,
    val rangeDays: Int = 7,
    val autoSync: Boolean = false,
    val canSync: Boolean = true,
    val healthServiceName: String = "",
    val healthReady: Boolean = true,
    val healthStatusTitle: String = "",
    val healthStatusDetail: String = "",
    val healthNeedsAction: Boolean = false,
    val loggedOut: Boolean = false,
)

class HomeViewModel(
    private val session: MiSessionManager,
    private val tokenStore: TokenStore,
    private val healthAvailability: HealthAvailability,
) : ViewModel() {
    private val syncPreferences: SyncPreferences get() = tokenStore.sync

    private val _profile = MutableStateFlow<MeResponse?>(null)
    private val _profileError = MutableStateFlow<String?>(null)
    private val _loggedOut = MutableStateFlow(false)
    private val _health = MutableStateFlow(
        HealthReadiness(
            available = true,
            permissionsGranted = true,
            serviceName = healthAvailability.healthServiceName(),
            hint = null,
        ),
    )

    private val lastSyncPrefs = combine(
        syncPreferences.lastSyncTime,
        syncPreferences.lastSyncStatus,
        syncPreferences.lastSyncMessage,
    ) { time, status, message ->
        Triple(time, status, message)
    }

    private val lastBgPrefs = combine(
        syncPreferences.lastBackgroundSyncTime,
        syncPreferences.lastBackgroundSyncStatus,
        syncPreferences.lastBackgroundSyncMessage,
    ) { time, status, message ->
        Triple(time, status, message)
    }

    private val configPrefs = combine(
        syncPreferences.enabledMetrics,
        syncPreferences.syncRangeDays,
        syncPreferences.autoSync,
    ) { enabled, rangeDays, autoSync ->
        Triple(enabled, rangeDays, autoSync)
    }

    private val prefs = combine(lastSyncPrefs, lastBgPrefs, configPrefs) { lastSync, lastBg, config ->
        PrefsSnapshot(
            lastSync = lastSync.first,
            lastSyncStatus = lastSync.second,
            lastSyncMessage = lastSync.third,
            lastBg = lastBg.first,
            lastBgStatus = lastBg.second,
            lastBgMessage = lastBg.third,
            enabled = config.first,
            rangeDays = config.second,
            autoSync = config.third,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _profile,
        _profileError,
        prefs,
        _loggedOut,
        _health,
    ) { profile, profileError, prefsSnap, loggedOut, health ->
        HomeUiState(
            profile = profile,
            profileError = profileError,
            lastSyncLabel = RelativeTime.format(prefsSnap.lastSync),
            lastSyncStatusTitle = SyncOutcomeLabels.title(prefsSnap.lastSyncStatus),
            lastSyncDetail = SyncOutcomeLabels.detail(
                prefsSnap.lastSyncStatus,
                prefsSnap.lastSyncMessage,
            ),
            lastSyncIsError = SyncOutcomeLabels.isError(prefsSnap.lastSyncStatus),
            lastSyncIsWarning = SyncOutcomeLabels.isWarning(prefsSnap.lastSyncStatus),
            lastBackgroundLabel = RelativeTime.format(prefsSnap.lastBg),
            lastBackgroundDetail = SyncOutcomeLabels.detail(
                prefsSnap.lastBgStatus,
                prefsSnap.lastBgMessage,
            ),
            lastBackgroundIsError = SyncOutcomeLabels.isError(prefsSnap.lastBgStatus),
            enabledMetricsCount = prefsSnap.enabled.size,
            totalMetricsCount = SyncMetric.entries.size,
            rangeDays = prefsSnap.rangeDays,
            autoSync = prefsSnap.autoSync,
            canSync = prefsSnap.enabled.isNotEmpty(),
            healthServiceName = health.serviceName,
            healthReady = health.isReady,
            healthStatusTitle = health.statusTitle,
            healthStatusDetail = health.statusDetail,
            healthNeedsAction = !health.isReady,
            loggedOut = loggedOut,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            healthServiceName = healthAvailability.healthServiceName(),
        ),
    )

    init {
        loadProfile()
        refreshHealthReadiness()
    }

    fun loadProfile() {
        viewModelScope.launch {
            if (!session.isActive) {
                _profileError.value = "Session not ready"
                return@launch
            }
            try {
                _profile.value = session.api.getMe()
                _profileError.value = null
            } catch (e: Exception) {
                _profileError.value = e.message ?: "Failed to load profile"
            }
        }
    }

    fun refreshHealthReadiness() {
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

    fun logout() {
        viewModelScope.launch {
            tokenStore.clear()
            session.clear()
            _loggedOut.value = true
        }
    }

    fun consumeLoggedOut() {
        _loggedOut.value = false
    }

    private data class PrefsSnapshot(
        val lastSync: String?,
        val lastSyncStatus: String?,
        val lastSyncMessage: String?,
        val lastBg: String?,
        val lastBgStatus: String?,
        val lastBgMessage: String?,
        val enabled: Set<String>,
        val rangeDays: Int,
        val autoSync: Boolean,
    )
}
