package com.bettermifitness.sync.data.preferences

import com.mifitness.miclient.auth.MiCredentials
import kotlinx.coroutines.flow.Flow

/** ISP: auth token + credentials used by sync policy. */
interface CredentialsPort {
    val token: Flow<String?>
    suspend fun loadCredentials(): MiCredentials?
    suspend fun saveCredentials(credentials: MiCredentials)
}

/** ISP: sync settings + last-run outcome persistence. */
interface SyncPreferencesPort {
    val autoSync: Flow<Boolean>
    val enabledMetrics: Flow<Set<String>>
    val syncRangeDays: Flow<Int>
    suspend fun updateLastSync(timestamp: String)
    suspend fun updateLastBackgroundSync(timestamp: String)
    suspend fun updateLastSyncOutcome(status: String, message: String?)
    suspend fun updateLastBackgroundSyncOutcome(status: String, message: String?)
}

/** ISP: ensure Mi API session is usable. */
interface SyncSessionPort {
    suspend fun ensureActive(): Boolean
}
