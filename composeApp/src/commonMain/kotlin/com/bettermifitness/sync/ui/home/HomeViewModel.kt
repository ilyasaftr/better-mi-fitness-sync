package com.bettermifitness.sync.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.i18n.L10n
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.api.MeResponse
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.data.preferences.UserPrefsSnapshot
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthPermissionRequester
import com.bettermifitness.sync.health.HealthReadiness
import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.sync.SyncOutcomeLabels
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.util.RelativeTime
import com.mifitness.miclient.api.MiApiException
import com.mifitness.miclient.auth.PassportCoreInfoClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val profile: MeResponse? = null,
    val profileError: String? = null,
    /** Mi account avatar URL (passport coreInfo); empty keeps the initial letter. */
    val avatarUrl: String = "",
    /** False until the first hot-snapshot emission — screens hold skeleton. */
    val prefsReady: Boolean = false,
    val lastSyncLabel: String = L10n.text(L10n.homeNever),
    val lastSyncStatusTitle: String = L10n.text(L10n.outcomeNotSynced),
    val lastSyncDetail: String = L10n.text(L10n.outcomeIdleDetail),
    val lastSyncIsError: Boolean = false,
    val lastSyncIsWarning: Boolean = false,
    val lastBackgroundLabel: String = L10n.text(L10n.homeNever),
    val lastBackgroundDetail: String = L10n.text(L10n.outcomeIdleDetail),
    val lastBackgroundIsError: Boolean = false,
    /** Empty until prefs load — do not assume all metrics on. */
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
    private val coreInfoClient: PassportCoreInfoClient,
    syncPreferences: SyncPreferences,
) : ViewModel() {
    /** Hot snapshot: single DataStore subscription, shared with all screens. */
    private val prefsSnapshot: StateFlow<UserPrefsSnapshot> = syncPreferences.snapshot

    private val profileState = MutableStateFlow<MeResponse?>(null)
    private val profileErrorState = MutableStateFlow<String?>(null)
    private val avatarUrlState = MutableStateFlow("")
    private val loggedOutState = MutableStateFlow(false)
    private val healthState = MutableStateFlow(
        HealthReadiness(
            available = true,
            permissionsGranted = true,
            serviceName = healthAvailability.healthServiceName(),
            hint = null,
        ),
    )

    private val lastSyncPrefs = combine(
        tokenStore.sync.lastSyncTime,
        tokenStore.sync.lastSyncStatus,
        tokenStore.sync.lastSyncMessage,
    ) { time, status, message ->
        Triple(time, status, message)
    }

    private val lastBgPrefs = combine(
        tokenStore.sync.lastBackgroundSyncTime,
        tokenStore.sync.lastBackgroundSyncStatus,
        tokenStore.sync.lastBackgroundSyncMessage,
    ) { time, status, message ->
        Triple(time, status, message)
    }

    /** Stable config from the hot snapshot; volatile status from outcome prefs. */
    private val prefs = combine(lastSyncPrefs, lastBgPrefs, prefsSnapshot) { lastSync, lastBg, snap ->
        PrefsSnapshot(
            ready = snap.ready,
            lastSync = lastSync.first,
            lastSyncStatus = lastSync.second,
            lastSyncMessage = lastSync.third,
            lastBg = lastBg.first,
            lastBgStatus = lastBg.second,
            lastBgMessage = lastBg.third,
            enabled = snap.enabledMetrics,
            rangeDays = snap.syncRangeDays,
            autoSync = snap.autoSync,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(profileState, profileErrorState, avatarUrlState) { profile, profileError, avatarUrl ->
            Triple(profile, profileError, avatarUrl)
        },
        prefs,
        loggedOutState,
        healthState,
        syncCoordinator.isRunning,
    ) { avatarTriple, prefsSnap, loggedOut, health, running ->
        val (profile, profileError, avatarUrl) = avatarTriple
        HomeUiState(
            profile = profile,
            profileError = profileError,
            avatarUrl = avatarUrl,
            prefsReady = prefsSnap.ready,
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
            isSyncing = running,
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
            isSyncing = syncCoordinator.isRunning.value,
        ),
    )

    init {
        loadProfile()
        refreshHealthReadiness()
    }

    fun loadProfile() {
        viewModelScope.launch {
            if (!session.isActive) {
                profileErrorState.value = L10n.text(L10n.homeSignedIn)
                return@launch
            }
            try {
                val me = session.api.getMe()
                profileState.value = me
                profileErrorState.value = null
                // Best-effort avatar: never fails the profile load.
                viewModelScope.launch {
                    avatarUrlState.value = resolveAvatarUrl(me.result?.icon)
                }
            } catch (e: MiApiException.AuthExpired) {
                val refresh = session.refreshSessionDetailed()
                if (refresh.isSuccess) {
                    try {
                        val me = session.api.getMe()
                        profileState.value = me
                        profileErrorState.value = null
                        viewModelScope.launch {
                            avatarUrlState.value = resolveAvatarUrl(me.result?.icon)
                        }
                        return@launch
                    } catch (retry: Exception) {
                        profileErrorState.value = retry.message ?: L10n.text(L10n.homeSignedIn)
                        return@launch
                    }
                }
                profileErrorState.value = refresh.userMessage
            } catch (e: Exception) {
                profileErrorState.value = e.message ?: L10n.text(L10n.homeSignedIn)
            }
        }
    }

    /** coreInfo avatar primary, fitness-profile icon secondary, else initial fallback. */
    private suspend fun resolveAvatarUrl(fitnessIcon: String?): String {
        return try {
            val creds = tokenStore.loadCredentials() ?: return fitnessIcon.orEmpty()
            val coreInfo = coreInfoClient.avatarAddress(creds)
            PassportCoreInfoClient.resolveAvatarUrl(coreInfo, fitnessIcon)
        } catch (_: Exception) {
            fitnessIcon.orEmpty()
        }
    }

    fun refreshHealthReadiness() {
        viewModelScope.launch {
            try {
                healthState.value = healthAvailability.readiness()
            } catch (_: Exception) {
                healthState.value = HealthReadiness(
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
            loggedOutState.value = true
        }
    }

    fun consumeLoggedOut() {
        loggedOutState.value = false
    }

    private data class PrefsSnapshot(
        val ready: Boolean,
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
