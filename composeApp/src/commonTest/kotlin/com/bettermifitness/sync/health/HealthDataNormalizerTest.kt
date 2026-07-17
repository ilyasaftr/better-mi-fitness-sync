package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SleepStage
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.StepsRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthDataNormalizerTest {

    @Test
    fun toEpochSeconds_convertsMillis() {
        val ms = 1_700_000_000_000L
        assertEquals(1_700_000_000L, HealthDataNormalizer.toEpochSeconds(ms))
    }

    @Test
    fun toEpochSeconds_keepsSeconds() {
        val sec = 1_700_000_000L
        assertEquals(sec, HealthDataNormalizer.toEpochSeconds(sec))
    }

    @Test
    fun normalizeHeartRate_filtersInvalidBpmAndTimestamps() {
        val input = listOf(
            HeartRateSample(timestamp = 1_700_000_000L, bpm = 72),
            HeartRateSample(timestamp = 1_700_000_010L, bpm = 0), // invalid
            HeartRateSample(timestamp = 50L, bpm = 80), // too old
            HeartRateSample(timestamp = 1_700_000_020_000L, bpm = 90), // ms
        )
        val out = HealthDataNormalizer.normalizeHeartRate(input)
        assertEquals(2, out.size)
        assertEquals(72, out[0].bpm)
        assertEquals(1_700_000_020L, out[1].timestamp)
        assertEquals(90, out[1].bpm)
    }

    @Test
    fun normalizeSpO2_filtersOutOfRange() {
        val out = HealthDataNormalizer.normalizeSpO2(
            listOf(
                SpO2Sample(1_700_000_000L, 98),
                SpO2Sample(1_700_000_001L, 10), // invalid
            ),
        )
        assertEquals(1, out.size)
        assertEquals(98, out[0].percentage)
    }

    @Test
    fun normalizeSteps_requiresPositiveCount() {
        val out = HealthDataNormalizer.normalizeSteps(
            listOf(
                StepsRecord(date = "1700000000", steps = 120),
                StepsRecord(date = "1700003600", steps = 0),
            ),
        )
        assertEquals(1, out.size)
        assertEquals(120, out[0].steps)
    }

    @Test
    fun normalizeHeartRate_dedupesSameTimestamp_keepsLast() {
        val t = 1_700_000_000L
        val out = HealthDataNormalizer.normalizeHeartRate(
            listOf(
                HeartRateSample(t, 70),
                HeartRateSample(t, 75),
            ),
        )
        assertEquals(1, out.size)
        assertEquals(75, out[0].bpm)
    }

    @Test
    fun normalizeSteps_sameHour_keepsHigherCount() {
        val hour = "1700000000"
        val out = HealthDataNormalizer.normalizeSteps(
            listOf(
                StepsRecord(date = hour, steps = 100),
                StepsRecord(date = hour, steps = 450),
                StepsRecord(date = hour, steps = 200),
            ),
        )
        assertEquals(1, out.size)
        assertEquals(450, out[0].steps)
    }

    @Test
    fun normalizeSleep_dropsInvertedRanges() {
        val good = SleepSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_003_600L,
            stages = listOf(
                SleepStage(1_700_000_000L, 1_700_001_800L, 2),
                SleepStage(1_700_001_800L, 1_700_003_600L, 3),
            ),
        )
        val bad = SleepSession(startTime = 100L, endTime = 50L)
        val out = HealthDataNormalizer.normalizeSleep(listOf(good, bad))
        assertEquals(1, out.size)
        assertEquals(2, out[0].stages.size)
    }

    @Test
    fun miSleepStageLabels_matchMiCodes() {
        assertEquals("light", HealthDataNormalizer.miSleepStageLabel(2))
        assertEquals("deep", HealthDataNormalizer.miSleepStageLabel(3))
        assertEquals("rem", HealthDataNormalizer.miSleepStageLabel(4))
        assertEquals("awake", HealthDataNormalizer.miSleepStageLabel(5))
        assertTrue(HealthDataNormalizer.miSleepStageLabel(99) == "unknown")
    }
}
