package com.bettermifitness.sync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mifitness.miclient.auth.MiCredentials
import com.mifitness.miclient.auth.MiRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persisted Mi Account credentials and session identity (SRP: auth storage only).
 */
class CredentialsStore(
    private val dataStore: DataStore<Preferences>,
) : CredentialsPort {
    override val token: Flow<String?> = dataStore.data.map { it[TOKEN_KEY] }

    val miUserId: Flow<String?> = dataStore.data.map { it[MI_USER_ID_KEY] }

    /** Effective region used by the API client (after discovery or STS provisional). */
    val region: Flow<String?> = dataStore.data.map { prefs ->
        MiRegion.normalizeCode(prefs[REGION_KEY] ?: prefs[LOGIN_REGION_KEY])
    }

    val loginRegion: Flow<String?> = dataStore.data.map { it[LOGIN_REGION_KEY] }

    val regionDiscoverySource: Flow<String?> =
        dataStore.data.map { it[REGION_DISCOVERY_SOURCE_KEY] }

    val regionLatestEpoch: Flow<String?> =
        dataStore.data.map { it[REGION_LATEST_EPOCH_KEY] }

    override suspend fun saveCredentials(credentials: MiCredentials) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = credentials.serviceToken
            preferences[MI_USER_ID_KEY] = credentials.userId
            preferences[SSECURITY_KEY] = credentials.ssecurity
            preferences[PASS_TOKEN_KEY] = credentials.passToken
            preferences[DEVICE_ID_KEY] = credentials.deviceId
            if (credentials.cUserId.isNotBlank()) {
                preferences[C_USER_ID_KEY] = credentials.cUserId
            } else {
                preferences.remove(C_USER_ID_KEY)
            }

            val loginFromSts = MiRegion.normalizeCode(credentials.region)
            preferences[LOGIN_REGION_KEY] = loginFromSts
            // Provisional until discovery; keep existing discovered region if still signed-in refresh
            // with same tokens would re-run discovery on login only.
            if (preferences[REGION_KEY].isNullOrBlank()) {
                preferences[REGION_KEY] = loginFromSts
            }
            // Drop legacy manual mode key if present
            preferences.remove(REGION_MODE_KEY)
        }
    }

    override suspend fun loadCredentials(): MiCredentials? {
        val prefs = dataStore.data.first()
        val serviceToken = prefs[TOKEN_KEY] ?: return null
        val userId = prefs[MI_USER_ID_KEY] ?: return null
        val ssecurity = prefs[SSECURITY_KEY] ?: return null
        // passToken may be blank on legacy installs; still load for in-memory API until re-login.
        val passToken = prefs[PASS_TOKEN_KEY] ?: ""
        val deviceId = prefs[DEVICE_ID_KEY] ?: ""
        val cUserId = prefs[C_USER_ID_KEY] ?: ""
        val effective = MiRegion.normalizeCode(
            prefs[REGION_KEY] ?: prefs[LOGIN_REGION_KEY],
        )
        return MiCredentials(
            userId = userId,
            ssecurity = ssecurity,
            serviceToken = serviceToken,
            passToken = passToken,
            deviceId = deviceId,
            region = effective,
            cUserId = cUserId,
        )
    }

    /**
     * Persist auto-discovery winner and metadata for Settings.
     */
    suspend fun setDiscoveredRegion(result: MiRegion.DiscoveryResult) {
        dataStore.edit { preferences ->
            preferences[REGION_KEY] = MiRegion.normalizeCode(result.region)
            preferences[REGION_DISCOVERY_SOURCE_KEY] = result.source.name
            if (result.latestEpochSec != null) {
                preferences[REGION_LATEST_EPOCH_KEY] = result.latestEpochSec.toString()
            } else {
                preferences.remove(REGION_LATEST_EPOCH_KEY)
            }
            preferences.remove(REGION_MODE_KEY)
        }
    }

    /** Clears only credential keys (leaves sync preferences intact). */
    suspend fun clearCredentials() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(MI_USER_ID_KEY)
            preferences.remove(REGION_KEY)
            preferences.remove(REGION_MODE_KEY)
            preferences.remove(LOGIN_REGION_KEY)
            preferences.remove(REGION_DISCOVERY_SOURCE_KEY)
            preferences.remove(REGION_LATEST_EPOCH_KEY)
            preferences.remove(SSECURITY_KEY)
            preferences.remove(PASS_TOKEN_KEY)
            preferences.remove(DEVICE_ID_KEY)
            preferences.remove(C_USER_ID_KEY)
        }
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("bearer_token")
        private val MI_USER_ID_KEY = stringPreferencesKey("mi_user_id")
        private val REGION_KEY = stringPreferencesKey("region")
        private val REGION_MODE_KEY = stringPreferencesKey("region_mode") // legacy
        private val LOGIN_REGION_KEY = stringPreferencesKey("login_region")
        private val REGION_DISCOVERY_SOURCE_KEY = stringPreferencesKey("region_discovery_source")
        private val REGION_LATEST_EPOCH_KEY = stringPreferencesKey("region_latest_epoch")
        private val SSECURITY_KEY = stringPreferencesKey("ssecurity")
        private val PASS_TOKEN_KEY = stringPreferencesKey("pass_token")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val C_USER_ID_KEY = stringPreferencesKey("c_user_id")
    }
}
