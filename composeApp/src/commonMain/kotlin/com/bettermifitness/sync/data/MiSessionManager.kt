package com.bettermifitness.sync.data

import com.bettermifitness.sync.data.api.MiDirectApi
import com.bettermifitness.sync.data.preferences.CredentialsPort
import com.bettermifitness.sync.data.preferences.SyncSessionPort
import com.mifitness.miclient.api.MiDataClient
import com.mifitness.miclient.auth.MiAuth
import com.mifitness.miclient.auth.MiAuthException
import com.mifitness.miclient.auth.MiCredentials
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Holds the current Mi session (authenticated API client).
 * Can re-mint serviceToken via [refreshSession] using the stored passToken (APK force-refresh).
 */
class MiSessionManager(
    private val credentialsStore: CredentialsPort,
    private val miAuth: MiAuth,
) : SyncSessionPort {
    private var dataClient: MiDataClient? = null
    private var directApi: MiDirectApi? = null
    private val refreshMutex = Mutex()

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
        if (creds.serviceToken.isBlank() || creds.userId.isBlank() || creds.ssecurity.isBlank()) {
            return false
        }
        activate(creds)
        return true
    }

    /**
     * Rebuilds the in-memory client from persisted credentials (e.g. after region change).
     * @return false if not signed in
     */
    suspend fun reloadFromStore(): Boolean {
        val creds = credentialsStore.loadCredentials() ?: return false
        activate(creds)
        return true
    }

    /**
     * Uses passToken to obtain a new serviceToken, persists it, and re-activates the client.
     * Serialized so concurrent metric failures do not stampede passport (APK update lock).
     */
    suspend fun refreshSessionDetailed(): SessionRefreshResult = refreshMutex.withLock {
        val current = credentialsStore.loadCredentials()
            ?: return@withLock SessionRefreshResult.NeedsReLogin("No saved credentials")
        if (current.passToken.isBlank()) {
            return@withLock SessionRefreshResult.NeedsReLogin(
                "No passToken saved — sign in again to stay connected across days",
            )
        }
        if (current.deviceId.isBlank()) {
            return@withLock SessionRefreshResult.NeedsReLogin(
                "No deviceId saved — sign in again",
            )
        }

        return@withLock try {
            val refreshed = miAuth.refreshWithPassToken(current)
            credentialsStore.saveCredentials(refreshed)
            val effective = credentialsStore.loadCredentials() ?: refreshed
            activate(effective)
            SessionRefreshResult.Success
        } catch (e: MiAuthException) {
            when (e.kind) {
                MiAuthException.Kind.MissingPassToken,
                MiAuthException.Kind.MissingDeviceId,
                MiAuthException.Kind.InvalidCredential,
                -> SessionRefreshResult.NeedsReLogin(e.message ?: "Sign in again")
                MiAuthException.Kind.NeedsVerification ->
                    SessionRefreshResult.NeedsVerification(
                        reason = e.message ?: "Xiaomi requires re-verification",
                        notificationUrl = e.notificationUrl,
                    )
                MiAuthException.Kind.StsFailed,
                MiAuthException.Kind.Generic,
                -> SessionRefreshResult.TransientFailure(e.message ?: "Session refresh failed")
            }
        } catch (e: Exception) {
            SessionRefreshResult.TransientFailure(
                e.message?.takeIf { it.isNotBlank() } ?: (e::class.simpleName ?: "Refresh error"),
            )
        }
    }

    /**
     * @return true if refresh succeeded
     */
    suspend fun refreshSession(): Boolean = refreshSessionDetailed().isSuccess
}
