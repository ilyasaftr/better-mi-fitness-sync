package com.mifitness.miclient.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiApiExceptionTest {

    @Test
    fun authBusinessCodes() {
        assertTrue(MiApiException.isAuthBusinessCode(401))
        assertTrue(MiApiException.isAuthBusinessCode(3))
        assertFalse(MiApiException.isAuthBusinessCode(0))
        assertFalse(MiApiException.isAuthBusinessCode(500))
    }

    @Test
    fun authMessageHeuristics() {
        assertTrue(MiApiException.looksLikeAuthMessage("Please login again"))
        assertTrue(MiApiException.looksLikeAuthMessage("token expired"))
        assertFalse(MiApiException.looksLikeAuthMessage("no data for range"))
        assertFalse(MiApiException.looksLikeAuthMessage(null))
    }

    @Test
    fun retryableFlags() {
        assertFalse(MiApiException.AuthExpired().isRetryable)
        assertTrue(MiApiException.Network("offline").isRetryable)
        assertTrue(MiApiException.RateLimited().isRetryable)
        assertTrue(MiApiException.Server(503, "down").isRetryable)
        assertFalse(MiApiException.Server(400, "bad").isRetryable)
    }
}
