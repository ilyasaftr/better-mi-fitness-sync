package com.bettermifitness.sync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mifitness.miclient.auth.MiCredentials
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

    val region: Flow<String?> = dataStore.data.map { it[REGION_KEY] }

    override suspend fun saveCredentials(credentials: MiCredentials) {
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = credentials.serviceToken
            preferences[MI_USER_ID_KEY] = credentials.userId
            preferences[REGION_KEY] = credentials.region
            preferences[SSECURITY_KEY] = credentials.ssecurity
            preferences[PASS_TOKEN_KEY] = credentials.passToken
            preferences[DEVICE_ID_KEY] = credentials.deviceId
        }
    }

    override suspend fun loadCredentials(): MiCredentials? {
        val prefs = dataStore.data.first()
        val serviceToken = prefs[TOKEN_KEY] ?: return null
        val userId = prefs[MI_USER_ID_KEY] ?: return null
        val ssecurity = prefs[SSECURITY_KEY] ?: return null
        val passToken = prefs[PASS_TOKEN_KEY] ?: return null
        val deviceId = prefs[DEVICE_ID_KEY] ?: return null
        val region = prefs[REGION_KEY] ?: "sg"
        return MiCredentials(
            userId = userId,
            ssecurity = ssecurity,
            serviceToken = serviceToken,
            passToken = passToken,
            deviceId = deviceId,
            region = region,
        )
    }

    /** Clears only credential keys (leaves sync preferences intact). */
    suspend fun clearCredentials() {
        dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
            preferences.remove(MI_USER_ID_KEY)
            preferences.remove(REGION_KEY)
            preferences.remove(SSECURITY_KEY)
            preferences.remove(PASS_TOKEN_KEY)
            preferences.remove(DEVICE_ID_KEY)
        }
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("bearer_token")
        private val MI_USER_ID_KEY = stringPreferencesKey("mi_user_id")
        private val REGION_KEY = stringPreferencesKey("region")
        private val SSECURITY_KEY = stringPreferencesKey("ssecurity")
        private val PASS_TOKEN_KEY = stringPreferencesKey("pass_token")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }
}
