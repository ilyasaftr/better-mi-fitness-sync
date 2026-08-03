package com.bettermifitness.sync.data

/**
 * Outcome of passToken → serviceToken refresh (APK force-refresh parity).
 */
sealed class SessionRefreshResult {
    data object Success : SessionRefreshResult()

    /** passToken invalid/missing — full sign-in required. */
    data class NeedsReLogin(val reason: String) : SessionRefreshResult()

    /** Xiaomi security challenge during refresh. */
    data class NeedsVerification(
        val reason: String,
        val notificationUrl: String? = null,
    ) : SessionRefreshResult()

    /** Network / STS glitch — may retry later. */
    data class TransientFailure(val reason: String) : SessionRefreshResult()

    val isSuccess: Boolean get() = this is Success

    val userMessage: String
        get() = when (this) {
            Success -> "Session renewed"
            is NeedsReLogin -> reason.ifBlank { "Session expired — sign in again" }
            is NeedsVerification -> reason.ifBlank {
                "Xiaomi requires re-verification — sign in again"
            }
            is TransientFailure -> reason.ifBlank { "Could not renew session — try again" }
        }
}
