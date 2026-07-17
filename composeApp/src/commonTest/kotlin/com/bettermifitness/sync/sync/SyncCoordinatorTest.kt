package com.bettermifitness.sync.sync

import com.bettermifitness.sync.data.preferences.CredentialsPort
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.data.preferences.SyncPreferencesPort
import com.bettermifitness.sync.data.preferences.SyncSessionPort
import com.bettermifitness.sync.data.repository.HealthSyncRunner
import com.bettermifitness.sync.data.repository.SyncProgress
import com.bettermifitness.sync.data.repository.SyncRunResult
import com.bettermifitness.sync.health.HealthAvailability
import com.bettermifitness.sync.health.HealthPermissionRequester
import com.mifitness.miclient.auth.MiCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Policy tests for [SyncCoordinator] with pure in-memory fakes (no DataStore / network).
 */
class SyncCoordinatorTest {

    @Test
    fun requireAutoSync_whenDisabled_skips() = runBlocking {
        val prefs = FakePrefs(autoSync = false)
        val outcome = coordinator(prefs = prefs, token = "t").run(
            requireAutoSync = true,
            requestHealthPermissions = false,
            userInitiated = false,
        )
        assertEquals(SyncOutcome.Skipped, outcome)
    }

    @Test
    fun missingToken_userInitiated_notLoggedIn() = runBlocking {
        val prefs = FakePrefs()
        val outcome = coordinator(prefs = prefs, token = null).run(
            requestHealthPermissions = false,
            userInitiated = true,
        )
        assertEquals(SyncOutcome.NotLoggedIn, outcome)
        assertEquals(SyncOutcome.STATUS_NOT_LOGGED_IN, prefs.lastStatus)
    }

    @Test
    fun healthUnavailable() = runBlocking {
        val prefs = FakePrefs()
        val health = FakeHealth(available = false)
        val outcome = coordinator(prefs = prefs, token = "t", health = health).run(
            requestHealthPermissions = false,
            userInitiated = true,
        )
        assertEquals(SyncOutcome.HealthUnavailable, outcome)
    }

    @Test
    fun permissionDenied_failedNonRetryable() = runBlocking {
        val prefs = FakePrefs()
        val health = FakeHealth(available = true, permissionError = "denied")
        val outcome = coordinator(prefs = prefs, token = "t", health = health).run(
            requestHealthPermissions = true,
            userInitiated = true,
        )
        assertIs<SyncOutcome.Failed>(outcome)
        assertEquals(false, outcome.retryable)
        assertTrue(outcome.message!!.contains("denied"))
    }

    @Test
    fun fullSuccess_persistsOutcome() = runBlocking {
        val prefs = FakePrefs()
        val runner = FakeSyncRunner(
            result = SyncRunResult(
                attempted = 2,
                succeeded = 2,
                failed = 0,
                totalRecords = 10,
                hadRetryableFailure = false,
                hadAuthFailure = false,
                errorMessages = emptyList(),
            ),
        )
        val outcome = coordinator(prefs = prefs, token = "t", runner = runner).run(
            requestHealthPermissions = false,
            userInitiated = true,
            resetProgress = true,
        )
        assertEquals(SyncOutcome.Success, outcome)
        assertTrue(runner.resetCalled)
        assertEquals("success", prefs.lastStatus)
    }

    @Test
    fun partialSuccess_mapsCorrectly() = runBlocking {
        val prefs = FakePrefs()
        val runner = FakeSyncRunner(
            result = SyncRunResult(
                attempted = 3,
                succeeded = 2,
                failed = 1,
                totalRecords = 5,
                hadRetryableFailure = true,
                hadAuthFailure = false,
                errorMessages = listOf("timeout"),
            ),
        )
        val outcome = coordinator(prefs = prefs, token = "t", runner = runner).run(
            requestHealthPermissions = false,
            userInitiated = true,
        )
        assertIs<SyncOutcome.PartialSuccess>(outcome)
        assertEquals(2, outcome.succeeded)
        assertEquals(1, outcome.failed)
        assertEquals(false, outcome.shouldRetryBackground())
    }

    @Test
    fun totalRetryableFailure_shouldRetryBackground() = runBlocking {
        val prefs = FakePrefs()
        val runner = FakeSyncRunner(
            result = SyncRunResult(
                attempted = 1,
                succeeded = 0,
                failed = 1,
                totalRecords = 0,
                hadRetryableFailure = true,
                hadAuthFailure = false,
                errorMessages = listOf("network"),
            ),
        )
        val outcome = coordinator(prefs = prefs, token = "t", runner = runner).run(
            requestHealthPermissions = false,
            userInitiated = true,
        )
        assertIs<SyncOutcome.Failed>(outcome)
        assertTrue(outcome.shouldRetryBackground())
    }

    @Test
    fun emptyEnabledMetrics_skips() = runBlocking {
        val prefs = FakePrefs(enabled = emptySet())
        val outcome = coordinator(prefs = prefs, token = "t").run(
            requestHealthPermissions = false,
            userInitiated = true,
        )
        assertEquals(SyncOutcome.Skipped, outcome)
    }

    private fun coordinator(
        prefs: FakePrefs,
        token: String?,
        sessionOk: Boolean = true,
        health: FakeHealth = FakeHealth(),
        runner: FakeSyncRunner = FakeSyncRunner(),
    ) = SyncCoordinator(
        session = FakeSession(sessionOk),
        credentials = FakeCredentials(token),
        syncPreferences = prefs,
        healthAvailability = health,
        healthPermissions = health,
        repository = runner,
    )

    private class FakeSession(private val ok: Boolean) : SyncSessionPort {
        override suspend fun ensureActive(): Boolean = ok
    }

    private class FakeCredentials(token: String?) : CredentialsPort {
        override val token: Flow<String?> = MutableStateFlow(token)
        override suspend fun loadCredentials(): MiCredentials? = null
        override suspend fun saveCredentials(credentials: MiCredentials) = Unit
    }

    private class FakePrefs(
        autoSync: Boolean = true,
        enabled: Set<String> = SyncPreferences.ALL_METRIC_KEYS,
        rangeDays: Int = 7,
    ) : SyncPreferencesPort {
        override val autoSync = MutableStateFlow(autoSync)
        override val enabledMetrics = MutableStateFlow(enabled)
        override val syncRangeDays = MutableStateFlow(rangeDays)
        var lastStatus: String? = null
        var lastMessage: String? = null

        override suspend fun updateLastSync(timestamp: String) = Unit
        override suspend fun updateLastBackgroundSync(timestamp: String) = Unit
        override suspend fun updateLastSyncOutcome(status: String, message: String?) {
            lastStatus = status
            lastMessage = message
        }
        override suspend fun updateLastBackgroundSyncOutcome(status: String, message: String?) {
            lastStatus = status
            lastMessage = message
        }
    }

    private class FakeHealth(
        var available: Boolean = true,
        var permissionError: String? = null,
    ) : HealthAvailability, HealthPermissionRequester {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun availabilityHint(): String? = if (available) null else "missing"
        override fun healthServiceName(): String = "Test Health"
        override fun openHealthService() = Unit
        override suspend fun hasWritePermissions(): Boolean = available && permissionError == null
        override suspend fun requestPermissions() {
            permissionError?.let { throw Exception(it) }
        }
    }

    private class FakeSyncRunner(
        private val result: SyncRunResult = SyncRunResult(
            attempted = 0,
            succeeded = 0,
            failed = 0,
            totalRecords = 0,
            hadRetryableFailure = false,
            hadAuthFailure = false,
            errorMessages = emptyList(),
        ),
    ) : HealthSyncRunner {
        var resetCalled = false
        private val progress = MutableStateFlow(SyncProgress())
        override val syncProgress: StateFlow<SyncProgress> = progress.asStateFlow()
        override suspend fun syncAll(from: String, to: String, enabled: Set<String>): SyncRunResult =
            result
        override fun resetProgress() {
            resetCalled = true
        }
    }
}
