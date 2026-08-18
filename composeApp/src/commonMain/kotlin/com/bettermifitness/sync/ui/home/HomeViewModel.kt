package com.bettermifitness.sync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.i18n.L10n
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.api.MeResponse
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthPermissionRequester
import com.bettermifitness.sync.health.HealthReadiness
import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.sync.SyncOutcomeLabels
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.util.RelativeTime
import com.mifitness.miclient.api.MiApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val profile: MeResponse? = null,
    val profileError: String? = null,
    val lastSyncLabel: String = L10n.text(L10n.homeNever),
    val lastSyncStatusTitle: String = L10n.text(L10n.outcomeNotSynced),
    val lastSyncDetail: String = L10n.text(L10n.outcomeIdleDetail),
    val lastSyncIsError: Boolean = false,
    val lastSyncIsWarning: Boolean = false,
    val lastBackgroundLabel: String = L10n.text(L10n.homeNever),
    val lastBackgroundDetail: String = L10n.text(L10n.outcomeIdleDetail),
    val lastBackgroundIsError: Boolean = false,
    /** 0 until prefs load — do not assume all metrics on (matches Settings). */
    val enabledMetricsCount: Int = 0,
    val totalMetricsCount: Int = SyncMetric.entries.size,
    val rangeDays: Int = 7,
    val autoSync: Boolean = false,
    val canSync: Boolean = false,
    val isSyncing: Boolean = false,
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
    private val healthPermissions: HealthPermissionRequester,
    private val syncCoordinator: SyncCoordinator,
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
        combine(_profile, _profileError, prefs, _loggedOut, _health) {
                profile, profileError, prefsSnap, loggedOut, health ->
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
        },
        syncCoordinator.isRunning,
    ) { base, running ->
        base.copy(isSyncing = running)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(
            healthServiceName = healthAvailability.healthServiceName(),
            isSyncing = syncCoordinator.isRunning.value,
        ),
    )

    init {
        loadProfile()
        refreshHealthReadiness()
        // Proactive 1-day refresh when the app is opened (before 4-5d expiry).
        viewModelScope.launch {
            try {
                val result = session.refreshIfStale()
                // If proactive refresh succeeded, reload profile so Home shows fresh auth.
                if (result?.isSuccess == true) {
                    try {
                        _profile.value = session.api.getMe()
                        _profileError.value = null
                    } catch (_: Exception) {
                        // Non-fatal — next loadProfile/sync will surface the error.
                    }
                }
            } catch (_: Exception) {
                // Best-effort: stale refresh must never crash app open.
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            if (!session.isActive) {
                _profileError.value = L10n.text(L10n.homeSignedIn)
                return@launch
            }
            try {
                _profile.value = session.api.getMe()
                _profileError.value = null
            } catch (e: MiApiException.AuthExpired) {
                val refresh = session.refreshSessionDetailed()
                if (refresh.isSuccess) {
                    try {
                        _profile.value = session.api.getMe()
                        _profileError.value = null
                        return@launch
                    } catch (retry: Exception) {
                        _profileError.value = retry.message ?: L10n.text(L10n.homeSignedIn)
                        return@launch
                    }
                }
                _profileError.value = refresh.userMessage
            } catch (e: Exception) {
                _profileError.value = e.message ?: L10n.text(L10n.homeSignedIn)
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
                    hint = L10n.text(L10n.healthStatusCheckFailed),
                )
            }
        }
    }

    /**
     * “Allow access”: show the Health permission sheet when possible;
     * if still not granted (or sheet won’t show again), open system Settings / Health Connect.
     */
    fun openHealthService() {
        viewModelScope.launch {
            try {
                healthPermissions.requestPermissions()
            } catch (_: Exception) {
                // User denied or request failed — fall through to Settings / HC.
            }
            if (!healthAvailability.hasWritePermissions()) {
                healthAvailability.openHealthService()
            }
            refreshHealthReadiness()
        }
    }

    /**
     * Starts a user foreground sync if none is running (single-flight).
     * Call when Home CTA is tapped, then navigate to Sync to observe progress.
     */
    fun startSyncIfIdle() {
        viewModelScope.launch {
            if (syncCoordinator.isRunning.value) return@launch
            syncCoordinator.run(
                requestHealthPermissions = true,
                resetProgress = true,
                userInitiated = true,
            )
        }
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
