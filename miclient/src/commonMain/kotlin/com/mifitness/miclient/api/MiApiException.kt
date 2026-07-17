package com.mifitness.miclient.api

/**
 * Transport / API failures from Mi cloud.
 * [AuthExpired] is recoverable once via passToken refresh; others guide retry policy.
 */
sealed class MiApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** serviceToken / session invalid — refresh with passToken or re-login. */
    class AuthExpired(
        message: String = "Mi session expired",
        cause: Throwable? = null,
    ) : MiApiException(message, cause)

    /** Transient network / connectivity. */
    class Network(
        message: String,
        cause: Throwable? = null,
    ) : MiApiException(message, cause)

    /** HTTP 429 or similar. */
    class RateLimited(
        message: String = "Mi cloud rate limited the request",
    ) : MiApiException(message)

    /** Non-success HTTP or business code that is not clearly auth. */
    class Server(
        val httpOrBusinessCode: Int,
        message: String,
    ) : MiApiException(message)

    class Unexpected(
        message: String,
        cause: Throwable? = null,
    ) : MiApiException(message, cause)

    /** Whether a background worker should retry without user action. */
    val isRetryable: Boolean
        get() = when (this) {
            is AuthExpired -> false
            is Network -> true
            is RateLimited -> true
            is Server -> httpOrBusinessCode >= 500 || httpOrBusinessCode == 0
            is Unexpected -> true
        }

    companion object {
        private val AUTH_BUSINESS_CODES = setOf(3, 401, 403, 1002, 10017, 70016)

        fun isAuthBusinessCode(code: Int): Boolean = code in AUTH_BUSINESS_CODES

        fun looksLikeAuthMessage(message: String?): Boolean {
            if (message.isNullOrBlank()) return false
            val m = message.lowercase()
            return listOf(
                "login",
                "log in",
                "token",
                "unauth",
                "not auth",
                "expired",
                "session",
                "invalid user",
                "please login",
                "re-login",
                "relogin",
            ).any { it in m }
        }
    }
}
