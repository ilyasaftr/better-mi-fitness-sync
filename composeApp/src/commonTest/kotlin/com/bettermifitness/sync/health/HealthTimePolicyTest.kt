package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.StepsRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HealthTimePolicyTest {

    @Test
    fun isNotFuture_allowsPastAndPresent() {
        val now = 1_700_000_000L
        assertTrue(HealthTimePolicy.isNotFuture(now, now))
        assertTrue(HealthTimePolicy.isNotFuture(now - 1, now))
        assertTrue(HealthTimePolicy.isNotFuture(now + 60, now)) // within skew
    }

    @Test
    fun isNotFuture_rejectsBeyondSkew() {
        val now = 1_700_000_000L
        assertFalse(HealthTimePolicy.isNotFuture(now + HealthTimePolicy.CLOCK_SKEW_SECONDS + 1, now))
    }

    @Test
    fun clampInterval_clampsOpenEnd() {
        val now = 1_700_000_500L
        val start = 1_700_000_000L
        val end = start + 3599
        val clamped = HealthTimePolicy.clampInterval(start, end, now)
        assertNotNull(clamped)
        assertEquals(start, clamped.first)
        assertEquals(now + HealthTimePolicy.CLOCK_SKEW_SECONDS, clamped.second)
    }

    @Test
    fun clampInterval_dropsFutureStart() {
        val now = 1_700_000_000L
        assertNull(HealthTimePolicy.clampInterval(now + 10_000, now + 20_000, now))
    }

    /**
     * Issue #10: GMT+3 at 02:24 local = still 23:24 previous day UTC.
     * RHR sample at 00:00 UTC of "today" is in the future as absolute Instant.
     */
    @Test
    fun normalizeHeartRate_dropsIssue10FutureRestingHr() {
        val nowSec = Instant.parse("2026-08-07T23:24:03Z").epochSeconds
        val futureMidnightUtc = Instant.parse("2026-08-08T00:00:00Z").epochSeconds
        val pastMidnightUtc = Instant.parse("2026-08-07T00:00:00Z").epochSeconds

        val out = HealthDataNormalizer.normalizeHeartRate(
            listOf(
                HeartRateSample(timestamp = futureMidnightUtc, bpm = 55),
                HeartRateSample(timestamp = pastMidnightUtc, bpm = 58),
            ),
            nowEpochSeconds = nowSec,
        )
        assertEquals(1, out.size)
        assertEquals(pastMidnightUtc, out[0].timestamp)
        assertEquals(58, out[0].bpm)
    }

    @Test
    fun normalizeSteps_dropsFutureHourStart() {
        val nowSec = Instant.parse("2026-08-07T23:24:03Z").epochSeconds
        val futureHour = Instant.parse("2026-08-08T00:00:00Z").epochSeconds
        val pastHour = Instant.parse("2026-08-07T00:00:00Z").epochSeconds
        val out = HealthDataNormalizer.normalizeSteps(
            listOf(
                StepsRecord(date = futureHour.toString(), steps = 100),
                StepsRecord(date = pastHour.toString(), steps = 50),
            ),
            nowEpochSeconds = nowSec,
        )
        assertEquals(1, out.size)
        assertEquals(pastHour.toString(), out[0].date)
    }
}
