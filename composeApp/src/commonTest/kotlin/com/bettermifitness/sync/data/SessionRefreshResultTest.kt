package com.bettermifitness.sync.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRefreshResultTest {
    @Test
    fun successFlags() {
        assertTrue(SessionRefreshResult.Success.isSuccess)
        assertFalse(SessionRefreshResult.NeedsReLogin("x").isSuccess)
        assertFalse(SessionRefreshResult.TransientFailure("y").isSuccess)
    }

    @Test
    fun userMessagesPreferReason() {
        assertEquals(
            "please re-auth",
            SessionRefreshResult.NeedsReLogin("please re-auth").userMessage,
        )
        assertEquals(
            "verify",
            SessionRefreshResult.NeedsVerification("verify", notificationUrl = "https://x").userMessage,
        )
    }
}
