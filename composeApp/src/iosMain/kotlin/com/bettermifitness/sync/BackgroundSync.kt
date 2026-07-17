package com.bettermifitness.sync

import com.bettermifitness.sync.sync.SyncCoordinator
import com.bettermifitness.sync.sync.SyncOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.mp.KoinPlatform

/**
 * iOS bridge for BGTaskScheduler and App Intents.
 * Delegates policy to common [SyncCoordinator]; keeps single-flight + status strings stable for Swift.
 */
object BackgroundSync {
    const val STATUS_SUCCESS = SyncOutcome.STATUS_SUCCESS
    const val STATUS_PARTIAL_SUCCESS = SyncOutcome.STATUS_PARTIAL_SUCCESS
    const val STATUS_SKIPPED = SyncOutcome.STATUS_SKIPPED
    const val STATUS_NOT_LOGGED_IN = SyncOutcome.STATUS_NOT_LOGGED_IN
    const val STATUS_HEALTH_UNAVAILABLE = SyncOutcome.STATUS_HEALTH_UNAVAILABLE
    const val STATUS_CANCELLED = SyncOutcome.STATUS_CANCELLED
    const val STATUS_FAILED = SyncOutcome.STATUS_FAILED

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    private var currentJob: Job? = null
    private var inFlight: CompletableDeferred<String>? = null

    fun cancel() {
        currentJob?.cancel()
    }

    fun runOpportunisticBackgroundSync(completion: (String) -> Unit) {
        enqueue(
            rangeDaysOverride = 1,
            requireAutoSync = true,
            requestHealthPermissions = false,
            recordAsBackground = true,
            userInitiated = false,
            completion = completion,
        )
    }

    fun runUserInitiatedSync(completion: (String) -> Unit) {
        enqueue(
            rangeDaysOverride = 0,
            requireAutoSync = false,
            requestHealthPermissions = false,
            recordAsBackground = false,
            userInitiated = true,
            completion = completion,
        )
    }

    fun runForegroundAutoSync(completion: (String) -> Unit) {
        enqueue(
            rangeDaysOverride = 0,
            requireAutoSync = true,
            requestHealthPermissions = true,
            recordAsBackground = false,
            userInitiated = false,
            completion = completion,
        )
    }

    fun runAutoSync(completion: (Boolean) -> Unit) {
        runForegroundAutoSync { status ->
            completion(status == STATUS_SUCCESS || status == STATUS_SKIPPED)
        }
    }

    private fun enqueue(
        rangeDaysOverride: Int,
        requireAutoSync: Boolean,
        requestHealthPermissions: Boolean,
        recordAsBackground: Boolean,
        userInitiated: Boolean,
        completion: (String) -> Unit,
    ) {
        scope.launch {
            val status = try {
                joinOrStart(
                    rangeDaysOverride = rangeDaysOverride,
                    requireAutoSync = requireAutoSync,
                    requestHealthPermissions = requestHealthPermissions,
                    recordAsBackground = recordAsBackground,
                    userInitiated = userInitiated,
                )
            } catch (_: CancellationException) {
                STATUS_CANCELLED
            } catch (_: Exception) {
                STATUS_FAILED
            }
            completion(status)
        }
    }

    private suspend fun joinOrStart(
        rangeDaysOverride: Int,
        requireAutoSync: Boolean,
        requestHealthPermissions: Boolean,
        recordAsBackground: Boolean,
        userInitiated: Boolean,
    ): String {
        val deferred: CompletableDeferred<String> = mutex.withLock {
            val existing = inFlight
            if (existing != null) {
                return@withLock existing
            }
            val created = CompletableDeferred<String>()
            inFlight = created
            currentJob = scope.launch {
                val result = try {
                    doSyncWork(
                        rangeDaysOverride = rangeDaysOverride,
                        requireAutoSync = requireAutoSync,
                        requestHealthPermissions = requestHealthPermissions,
                        recordAsBackground = recordAsBackground,
                        userInitiated = userInitiated,
                    )
                } catch (_: CancellationException) {
                    STATUS_CANCELLED
                } catch (_: Exception) {
                    STATUS_FAILED
                }
                created.complete(result)
                mutex.withLock {
                    if (inFlight === created) {
                        inFlight = null
                        currentJob = null
                    }
                }
            }
            created
        }
        return deferred.await()
    }

    private suspend fun doSyncWork(
        rangeDaysOverride: Int,
        requireAutoSync: Boolean,
        requestHealthPermissions: Boolean,
        recordAsBackground: Boolean,
        userInitiated: Boolean,
    ): String {
        doInitKoin()
        val coordinator = KoinPlatform.getKoin().get<SyncCoordinator>()
        return coordinator.run(
            rangeDaysOverride = rangeDaysOverride,
            requireAutoSync = requireAutoSync,
            requestHealthPermissions = requestHealthPermissions,
            recordAsBackground = recordAsBackground,
            resetProgress = false,
            userInitiated = userInitiated,
        ).toStatusCode()
    }
}
