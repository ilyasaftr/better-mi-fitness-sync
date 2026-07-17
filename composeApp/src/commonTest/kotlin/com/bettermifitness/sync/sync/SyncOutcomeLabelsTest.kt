package com.bettermifitness.sync.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncOutcomeLabelsTest {

    @Test
    fun titles_forKnownStatuses() {
        assertEquals("You're up to date", SyncOutcomeLabels.title(SyncOutcome.STATUS_SUCCESS))
        assertEquals("Almost done", SyncOutcomeLabels.title(SyncOutcome.STATUS_PARTIAL_SUCCESS))
        assertEquals("Please sign in", SyncOutcomeLabels.title(SyncOutcome.STATUS_NOT_LOGGED_IN))
        assertEquals("Not synced yet", SyncOutcomeLabels.title(null))
    }

    @Test
    fun detail_prefersMessage() {
        assertEquals(
            "3 ok, 1 failed",
            SyncOutcomeLabels.detail(SyncOutcome.STATUS_PARTIAL_SUCCESS, "3 ok, 1 failed"),
        )
        assertTrue(
            SyncOutcomeLabels.detail(SyncOutcome.STATUS_FAILED, null)
                .contains("try again", ignoreCase = true),
        )
        assertEquals(
            "Mi Fitness synced to Health",
            SyncOutcomeLabels.detail(SyncOutcome.STATUS_SUCCESS, null),
        )
    }

    @Test
    fun severityFlags() {
        assertTrue(SyncOutcomeLabels.isError(SyncOutcome.STATUS_FAILED))
        assertTrue(SyncOutcomeLabels.isWarning(SyncOutcome.STATUS_PARTIAL_SUCCESS))
        assertFalse(SyncOutcomeLabels.isError(SyncOutcome.STATUS_SUCCESS))
    }
}
