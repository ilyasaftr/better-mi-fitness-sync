package com.bettermifitness.sync.data

import com.bettermifitness.sync.data.api.MiDirectApi
import com.bettermifitness.sync.data.preferences.CredentialsPort
import com.bettermifitness.sync.data.preferences.SyncSessionPort
import com.mifitness.miclient.api.MiDataClient
import com.mifitness.miclient.auth.MiAuth
import com.mifitness.miclient.auth.MiAuthException
import com.mifitness.miclient.auth.MiCredentials

/**
 * Holds the current Mi session (authenticated API client).
 * Can re-mint serviceToken via [refreshSession] using the stored passToken.
 */
class MiSessionManager(
    private val credentialsStore: CredentialsPort,
    private val miAuth: MiAuth,
) : SyncSessionPort {
    private var dataClient: MiDataClient? = null
    private var directApi: MiDirectApi? = null

    /** The active API client. Throws if not logged in. */
    val api: MiDirectApi
        get() = directApi ?: throw IllegalStateException("Not logged in — call activate() first")

    val isActive: Boolean get() = directApi != null

    /** Activates the session with fresh credentials (called after login). */
    fun activate(credentials: MiCredentials) {
        dataClient?.close()
        dataClient = MiDataClient(credentials)
        directApi = MiDirectApi(dataClient!!)
    }

    /** Clears the session (called on logout). */
    fun clear() {
        dataClient?.close()
        dataClient = null
        directApi = null
    }

    /**
     * Ensures an in-memory session exists from persisted credentials.
     * @return false if there are no saved credentials
     */
    override suspend fun ensureActive(): Boolean {
        if (isActive) return true
        val creds = credentialsStore.loadCredentials() ?: return false
        activate(creds)
        return true
    }

    /**
     * Uses passToken to obtain a new serviceToken, persists it, and re-activates the client.
     * @return true if refresh succeeded
     */
    suspend fun refreshSession(): Boolean {
        val current = credentialsStore.loadCredentials() ?: return false
        if (current.passToken.isBlank()) return false
        return try {
            val refreshed = miAuth.refreshWithPassToken(current)
            credentialsStore.saveCredentials(refreshed)
            activate(refreshed)
            true
        } catch (_: MiAuthException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
