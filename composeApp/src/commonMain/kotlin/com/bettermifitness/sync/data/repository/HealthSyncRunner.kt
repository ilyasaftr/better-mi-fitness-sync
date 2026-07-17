package com.bettermifitness.sync.data.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * ISP: multi-metric sync orchestration (fetch → write → progress).
 * [HealthRepository] is the production implementation; tests use fakes.
 */
interface HealthSyncRunner {
    val syncProgress: StateFlow<SyncProgress>

    suspend fun syncAll(
        from: String,
        to: String,
        enabled: Set<String>,
    ): SyncRunResult

    fun resetProgress()
}
