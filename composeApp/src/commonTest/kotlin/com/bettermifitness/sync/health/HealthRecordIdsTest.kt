package com.bettermifitness.sync.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HealthRecordIdsTest {

    @Test
    fun ids_areStableAndPrefixed() {
        assertEquals("mifit-steps-1700000000", HealthRecordIds.steps(1_700_000_000L))
        assertEquals("mifit-hr-1700000000", HealthRecordIds.heartRateSample(1_700_000_000L))
        assertEquals("mifit-workout-1700000000", HealthRecordIds.workout(1_700_000_000L))
        assertTrue(HealthRecordIds.sleepSession(1L).startsWith("mifit-"))
    }

    @Test
    fun heartRateWindow_floorsToFiveMinutes() {
        val t = 1_700_000_123L
        val window = HealthRecordIds.heartRateWindowStart(t)
        assertEquals(0L, window % HealthRecordIds.HEART_RATE_WINDOW_SEC)
        assertTrue(window <= t)
        assertEquals(
            HealthRecordIds.heartRateWindow(window),
            "mifit-hr-$window",
        )
    }

    @Test
    fun version_identicalInputs_match() {
        val a = HealthRecordIds.version(1_700_000_000L, 72)
        val b = HealthRecordIds.version(1_700_000_000L, 72)
        assertEquals(a, b)
        assertTrue(a > 0)
    }

    @Test
    fun version_differentContent_differs() {
        val a = HealthRecordIds.version(1_700_000_000L, 72)
        val b = HealthRecordIds.version(1_700_000_000L, 80)
        assertNotEquals(a, b)
    }

    @Test
    fun counterVersion_higherCounter_isHigher() {
        val low = HealthRecordIds.counterVersion(100, "hour")
        val high = HealthRecordIds.counterVersion(500, "hour")
        assertTrue(high > low)
    }
}
