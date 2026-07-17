package com.bettermifitness.sync.sync

import com.bettermifitness.sync.data.repository.SyncProgress
import com.bettermifitness.sync.data.repository.SyncRunResult
import com.bettermifitness.sync.data.repository.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncOutcomeTest {

    @Test
    fun fullSuccess_fromProgress() {
        val progress = SyncProgress(
            heartRate = SyncState.Success(10),
            steps = SyncState.Success(3),
        )
        val result = SyncRunResult.from(
            progress = progress,
            metricKeys = listOf("heart_rate", "steps"),
            retryableFlags = emptyList(),
            authFailure = false,
        )
        assertTrue(result.isFullSuccess)
        assertEquals(2, result.succeeded)
        assertEquals(0, result.failed)
        assertEquals(13, result.totalRecords)
    }

    @Test
    fun partialSuccess_fromProgress() {
        val progress = SyncProgress(
            heartRate = SyncState.Success(5),
            sleep = SyncState.Error("timeout"),
        )
        val result = SyncRunResult.from(
            progress = progress,
            metricKeys = listOf("heart_rate", "sleep"),
            retryableFlags = listOf(true),
            authFailure = false,
        )
        assertTrue(result.isPartialSuccess)
        assertTrue(result.hadRetryableFailure)
        assertTrue(result.summary().contains("Partial"))
    }

    @Test
    fun totalAuthFailure_flags() {
        val progress = SyncProgress(
            heartRate = SyncState.Error("Session expired"),
            steps = SyncState.Error("Session expired"),
        )
        val result = SyncRunResult.from(
            progress = progress,
            metricKeys = listOf("heart_rate", "steps"),
            retryableFlags = listOf(false, false),
            authFailure = true,
        )
        assertTrue(result.isTotalFailure)
        assertTrue(result.hadAuthFailure)
        assertFalse(result.hadRetryableFailure)
    }

    @Test
    fun statusCodes_stable() {
        assertEquals("success", SyncOutcome.Success.toStatusCode())
        assertEquals(
            "partial_success",
            SyncOutcome.PartialSuccess(1, 1, "Partial").toStatusCode(),
        )
        assertEquals("failed", SyncOutcome.Failed("x", retryable = true).toStatusCode())
    }

    @Test
    fun backgroundRetry_onlyTransientFailures() {
        assertTrue(SyncOutcome.Failed("net", retryable = true).shouldRetryBackground())
        assertFalse(SyncOutcome.Failed("auth", retryable = false).shouldRetryBackground())
        assertFalse(
            SyncOutcome.PartialSuccess(2, 1, "Partial").shouldRetryBackground(),
        )
        assertFalse(SyncOutcome.Success.shouldRetryBackground())
        assertFalse(SyncOutcome.NotLoggedIn.shouldRetryBackground())
    }
}
