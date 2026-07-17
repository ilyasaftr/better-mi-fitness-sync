package com.bettermifitness.sync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.mifitness.miclient.auth.MiCredentials
import kotlinx.coroutines.flow.Flow

/**
 * Façade over [CredentialsStore] + [SyncPreferences] for call sites that need both
 * (logout, full clear). Prefer injecting the focused stores when possible (ISP).
 */
class TokenStore(
    private val dataStore: DataStore<Preferences>,
    val credentials: CredentialsStore = CredentialsStore(dataStore),
    val sync: SyncPreferences = SyncPreferences(dataStore),
) {
    val token: Flow<String?> get() = credentials.token
    val miUserId: Flow<String?> get() = credentials.miUserId
    val region: Flow<String?> get() = credentials.region

    val lastSyncTime: Flow<String?> get() = sync.lastSyncTime
    val lastBackgroundSyncTime: Flow<String?> get() = sync.lastBackgroundSyncTime
    val autoSync: Flow<Boolean> get() = sync.autoSync
    val enabledMetrics: Flow<Set<String>> get() = sync.enabledMetrics
    val syncRangeDays: Flow<Int> get() = sync.syncRangeDays

    suspend fun saveCredentials(c: MiCredentials) = credentials.saveCredentials(c)

    suspend fun loadCredentials(): MiCredentials? = credentials.loadCredentials()

    suspend fun updateLastSync(timestamp: String) = sync.updateLastSync(timestamp)

    suspend fun updateLastBackgroundSync(timestamp: String) = sync.updateLastBackgroundSync(timestamp)

    suspend fun setAutoSync(enabled: Boolean) = sync.setAutoSync(enabled)

    suspend fun setMetricEnabled(key: String, enabled: Boolean) =
        sync.setMetricEnabled(key, enabled)

    suspend fun setSyncRangeDays(days: Int) = sync.setSyncRangeDays(days)

    /** Full wipe (logout). */
    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        val ALL_METRIC_KEYS: Set<String> get() = SyncPreferences.ALL_METRIC_KEYS
    }
}
