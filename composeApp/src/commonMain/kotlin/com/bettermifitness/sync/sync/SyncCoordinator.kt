package com.bettermifitness.sync.sync

import com.bettermifitness.sync.data.preferences.CredentialsPort
import com.bettermifitness.sync.data.preferences.CredentialsStore
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.SyncPreferencesPort
import com.bettermifitness.sync.data.preferences.SyncSessionPort
import com.bettermifitness.sync.data.repository.HealthSyncRunner
import com.bettermifitness.sync.data.repository.SyncRunResult
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthPermissionRequester
import com.bettermifitness.sync.health.HealthStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first

/**
 * Shared sync policy for UI, background tasks, and Shortcuts.
 * Depends on ISP ports so unit tests inject fakes without DataStore / Health SDKs.
 */
class SyncCoordinator(
    private val session: SyncSessionPort,
    private val credentials: CredentialsPort,
    private val syncPreferences: SyncPreferencesPort,
    private val healthAvailability: HealthAvailability,
    private val healthPermissions: HealthPermissionRequester,
    private val repository: HealthSyncRunner,
) {
    constructor(
        session: SyncSessionPort,
        credentials: CredentialsStore,
        syncPreferences: SyncPreferences,
        healthStore: HealthStore,
        repository: HealthSyncRunner,
    ) : this(
        session = session,
        credentials = credentials,
        syncPreferences = syncPreferences,
        healthAvailability = healthStore,
        healthPermissions = healthStore,
        repository = repository,
    )

    /**
     * @param rangeDaysOverride when > 0, overrides user range (e.g. BG 1-day refresh)
     * @param requireAutoSync when true, no-ops unless auto-sync is enabled
     * @param requestHealthPermissions when true, may show permission UI (foreground)
     * @param recordAsBackground when true, updates last background sync timestamp
     * @param resetProgress when true, clears per-metric progress (UI path)
     * @param userInitiated when true, missing login → [SyncOutcome.NotLoggedIn] instead of Skipped
     */
    suspend fun run(
        rangeDaysOverride: Int = 0,
        requireAutoSync: Boolean = false,
        requestHealthPermissions: Boolean = true,
        recordAsBackground: Boolean = false,
        resetProgress: Boolean = false,
        userInitiated: Boolean = true,
    ): SyncOutcome {
        val outcome = runInternal(
            rangeDaysOverride = rangeDaysOverride,
            requireAutoSync = requireAutoSync,
            requestHealthPermissions = requestHealthPermissions,
            recordAsBackground = recordAsBackground,
            resetProgress = resetProgress,
            userInitiated = userInitiated,
        )
        recordOutcome(outcome, recordAsBackground)
        return outcome
    }

    private suspend fun runInternal(
        rangeDaysOverride: Int,
        requireAutoSync: Boolean,
        requestHealthPermissions: Boolean,
        recordAsBackground: Boolean,
        resetProgress: Boolean,
        userInitiated: Boolean,
    ): SyncOutcome {
        if (requireAutoSync && !syncPreferences.autoSync.first()) {
            return SyncOutcome.Skipped
        }

        val token = credentials.token.first()
        if (token.isNullOrEmpty()) {
            return notLoggedInOrSkipped(userInitiated, requireAutoSync)
        }

        if (!session.ensureActive()) {
            return notLoggedInOrSkipped(userInitiated, requireAutoSync)
        }

        if (!healthAvailability.isAvailable()) {
            return SyncOutcome.HealthUnavailable
        }

        if (requestHealthPermissions) {
            try {
                healthPermissions.requestPermissions()
            } catch (e: Exception) {
                return SyncOutcome.Failed(
                    message = e.message ?: "Health permissions denied",
                    retryable = false,
                )
            }
        }

        val enabled = syncPreferences.enabledMetrics.first()
        if (enabled.isEmpty()) {
            return SyncOutcome.Skipped
        }

        val days = if (rangeDaysOverride > 0) {
            rangeDaysOverride
        } else {
            syncPreferences.syncRangeDays.first()
        }

        return try {
            if (resetProgress) repository.resetProgress()
            val now = Clock.System.now()
            val result = repository.syncAll(
                from = now.minus(days.days).toString(),
                to = now.toString(),
                enabled = enabled,
            )
            mapRunResult(result, now.toString(), recordAsBackground, userInitiated, requireAutoSync)
        } catch (e: Exception) {
            SyncOutcome.Failed(
                message = e.message,
                retryable = true,
            )
        }
    }

    private suspend fun recordOutcome(outcome: SyncOutcome, recordAsBackground: Boolean) {
        val status = outcome.toStatusCode()
        val message = outcome.userMessage()
        if (recordAsBackground) {
            syncPreferences.updateLastBackgroundSyncOutcome(status, message)
            syncPreferences.updateLastBackgroundSync(Clock.System.now().toString())
            when (outcome) {
                SyncOutcome.Success, is SyncOutcome.PartialSuccess ->
                    syncPreferences.updateLastSyncOutcome(status, message)
                else -> Unit
            }
        } else {
            syncPreferences.updateLastSyncOutcome(status, message)
        }
    }

    private suspend fun mapRunResult(
        result: SyncRunResult,
        now: String,
        recordAsBackground: Boolean,
        userInitiated: Boolean,
        requireAutoSync: Boolean,
    ): SyncOutcome {
        if (result.succeeded > 0) {
            syncPreferences.updateLastSync(now)
            if (recordAsBackground) {
                syncPreferences.updateLastBackgroundSync(now)
            }
        }

        return when {
            result.isEmpty -> SyncOutcome.Skipped
            result.isFullSuccess -> SyncOutcome.Success
            result.isPartialSuccess -> SyncOutcome.PartialSuccess(
                succeeded = result.succeeded,
                failed = result.failed,
                summary = result.summary(),
            )
            result.isTotalFailure && result.hadAuthFailure ->
                if (userInitiated && !requireAutoSync) {
                    SyncOutcome.NotLoggedIn
                } else {
                    SyncOutcome.Failed(
                        message = result.summary(),
                        retryable = false,
                    )
                }
            result.isTotalFailure -> SyncOutcome.Failed(
                message = result.summary(),
                retryable = result.hadRetryableFailure,
            )
            else -> SyncOutcome.Failed(result.summary(), retryable = result.hadRetryableFailure)
        }
    }

    private fun notLoggedInOrSkipped(userInitiated: Boolean, requireAutoSync: Boolean): SyncOutcome {
        return if (userInitiated && !requireAutoSync) {
            SyncOutcome.NotLoggedIn
        } else {
            SyncOutcome.Skipped
        }
    }
}

sealed class SyncOutcome {
    data object Success : SyncOutcome()

    data class PartialSuccess(
        val succeeded: Int,
        val failed: Int,
        val summary: String,
    ) : SyncOutcome()

    data object Skipped : SyncOutcome()
    data object NotLoggedIn : SyncOutcome()
    data object HealthUnavailable : SyncOutcome()

    data class Failed(
        val message: String?,
        val retryable: Boolean = true,
    ) : SyncOutcome()

    fun shouldRetryBackground(): Boolean = when (this) {
        is Failed -> retryable
        else -> false
    }

    fun toStatusCode(): String = when (this) {
        Success -> STATUS_SUCCESS
        is PartialSuccess -> STATUS_PARTIAL_SUCCESS
        Skipped -> STATUS_SKIPPED
        NotLoggedIn -> STATUS_NOT_LOGGED_IN
        HealthUnavailable -> STATUS_HEALTH_UNAVAILABLE
        is Failed -> STATUS_FAILED
    }

    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_PARTIAL_SUCCESS = "partial_success"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_NOT_LOGGED_IN = "not_logged_in"
        const val STATUS_HEALTH_UNAVAILABLE = "health_unavailable"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_FAILED = "failed"
    }
}
