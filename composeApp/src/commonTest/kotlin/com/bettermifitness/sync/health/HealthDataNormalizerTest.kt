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
    fun normalizeHeartRate_largeBatch_dedupesSinglePass() {
        val base = 1_700_000_000L
        val input = (0 until 10_000).map { i ->
            HeartRateSample(timestamp = base + (i % 1_000), bpm = 60 + (i % 40))
        }
        val out = HealthDataNormalizer.normalizeHeartRate(input)
        assertEquals(1_000, out.size)
        assertTrue(out.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
        // Last writer for timestamp base wins: i=9000 → bpm 60+(9000%40)=60.
        assertEquals(60, out[0].bpm)
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
            avgHrvMs = 40,
            tzIn15Min = 32,
        )
        val bad = SleepSession(startTime = 100L, endTime = 50L)
        val out = HealthDataNormalizer.normalizeSleep(listOf(good, bad))
        assertEquals(1, out.size)
        assertEquals(2, out[0].stages.size)
        assertEquals(40, out[0].avgHrvMs)
        assertEquals(32, out[0].tzIn15Min)
    }

    @Test
    fun normalizeSleep_mergesSameStartFullAndTruncated() {
        val bed = 1_700_000_000L
        val firstWake = 1_700_001_800L
        val fullWake = 1_700_003_600L
        val full = SleepSession(
            startTime = bed,
            endTime = fullWake,
            stages = listOf(
                SleepStage(bed, 1_700_000_900L, 2),
                SleepStage(1_700_000_900L, firstWake, 3),
                SleepStage(firstWake, 1_700_002_700L, 5),
                SleepStage(1_700_002_700L, fullWake, 3),
            ),
            tzIn15Min = 32,
        )
        val truncated = SleepSession(
            startTime = bed,
            endTime = firstWake,
            stages = listOf(
                SleepStage(bed, 1_700_000_900L, 2),
                SleepStage(1_700_000_900L, firstWake, 3),
            ),
            tzIn15Min = 32,
        )
        // Order-independent: truncated-last (the 1.0.3 failure) and full-last.
        for (input in listOf(listOf(full, truncated), listOf(truncated, full))) {
            val out = HealthDataNormalizer.normalizeSleep(input)
            assertEquals(1, out.size)
            assertEquals(fullWake, out[0].endTime)
            assertEquals(4, out[0].stages.size)
            assertEquals(fullWake, out[0].stages.last().endTime)
        }
    }

    @Test
    fun normalizeSleep_trimsOutOfWindowStage_keepsSession() {
        val session = SleepSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_003_600L,
            stages = listOf(
                SleepStage(1_700_000_000L, 1_700_001_800L, 2),
                SleepStage(1_700_001_800L, 1_700_003_600L, 3),
                // Additional sleep past wake_up_time: trimmed, session survives.
                SleepStage(1_700_003_600L, 1_700_004_000L, 3),
            ),
            tzIn15Min = 32,
        )
        val out = HealthDataNormalizer.normalizeSleep(listOf(session))
        assertEquals(1, out.size)
        assertEquals(2, out[0].stages.size)
        assertEquals(1_700_003_600L, out[0].endTime)
    }

    @Test
    fun normalizeSleep_dropsSessionOnlyWhenNoValidStages() {
        val session = SleepSession(
            startTime = 1_700_000_000L,
            endTime = 1_700_003_600L,
            stages = listOf(
                SleepStage(1_700_002_000L, 1_700_001_000L, 2), // inverted
            ),
            tzIn15Min = 32,
        )
        val out = HealthDataNormalizer.normalizeSleep(listOf(session))
        assertTrue(out.isEmpty())
    }

    @Test
    fun normalizeSleep_repairsOverlapsAfterMergingRows() {
        val bed = 1_700_000_000L
        val full = SleepSession(
            startTime = bed,
            endTime = bed + 7_200,
            stages = listOf(
                SleepStage(bed, bed + 1_800, 3),
                SleepStage(bed + 1_800, bed + 3_600, 2),
            ),
            tzIn15Min = 32,
        )
        val legacy = SleepSession(
            startTime = bed,
            endTime = bed + 7_200,
            stages = listOf(
                SleepStage(bed + 900, bed + 2_700, 4),
                SleepStage(bed + 2_700, bed + 7_200, 5),
            ),
            tzIn15Min = 32,
        )

        val out = HealthDataNormalizer.normalizeSleep(listOf(full, legacy))
        val stages = out.single().stages

        assertEquals(4, stages.size)
        assertTrue(stages.all { it.startTime < it.endTime })
        assertEquals(bed, stages[0].startTime)
        assertEquals(bed + 1_801, stages[1].startTime)
        assertEquals(bed + 2_701, stages[2].startTime)
        assertEquals(bed + 3_601, stages[3].startTime)
        assertTrue(
            stages.zipWithNext().all { (previous, next) ->
                next.startTime >= previous.endTime + 1
            },
        )
    }

    @Test
    fun normalizeSleep_dropsStageWhenRepairWouldRemoveItsDuration() {
        val bed = 1_700_000_000L
        val session = SleepSession(
            startTime = bed,
            endTime = bed + 600,
            stages = listOf(
                SleepStage(bed, bed + 100, 3),
                // This stage is entirely consumed by the required one-second gap.
                SleepStage(bed + 50, bed + 100, 2),
                SleepStage(bed + 100, bed + 600, 4),
            ),
            tzIn15Min = 32,
        )

        val stages = HealthDataNormalizer.normalizeSleep(listOf(session)).single().stages

        assertEquals(2, stages.size)
        assertEquals(3, stages[0].stage)
        assertEquals(4, stages[1].stage)
        assertEquals(bed + 101, stages[1].startTime)
        assertTrue(stages[1].startTime >= stages[0].endTime + 1)
    }

    @Test
    fun sleepRecordVersion_growsWithEndTime() {
        val stages = "1700000000:2,1700000900:3"
        val truncated = HealthRecordIds.counterVersion(1_700_001_800L, stages, 32)
        val full = HealthRecordIds.counterVersion(1_700_003_600L, stages, 32)
        assertTrue(full > truncated)
        // Identical re-sync stays identical (no write churn).
        assertEquals(full, HealthRecordIds.counterVersion(1_700_003_600L, stages, 32))
    }

    @Test
    fun normalizeHrv_filtersAndDedupes() {
        val out = HealthDataNormalizer.normalizeHrv(
            listOf(
                com.bettermifitness.sync.data.api.HrvSample(1_700_003_600L, 42.0),
                com.bettermifitness.sync.data.api.HrvSample(1_700_003_600L, 48.0), // same second, last wins
                com.bettermifitness.sync.data.api.HrvSample(1_700_003_600L, 0.0), // invalid
                com.bettermifitness.sync.data.api.HrvSample(100L, 30.0), // implausible time
            ),
        )
        assertEquals(1, out.size)
        assertEquals(48.0, out[0].hrvMs)
    }

    @Test
    fun miSleepStageCodes_matchMiPayloadAggregates() {
        assertEquals(HealthDataNormalizer.MiSleepStageKind.DEEP, HealthDataNormalizer.miSleepStageKind(2))
        assertEquals(HealthDataNormalizer.MiSleepStageKind.LIGHT, HealthDataNormalizer.miSleepStageKind(3))
        assertEquals(HealthDataNormalizer.MiSleepStageKind.REM, HealthDataNormalizer.miSleepStageKind(4))
        assertEquals(HealthDataNormalizer.MiSleepStageKind.AWAKE, HealthDataNormalizer.miSleepStageKind(5))
        assertEquals(HealthDataNormalizer.MiSleepStageKind.AWAKE, HealthDataNormalizer.miSleepStageKind(1))
        assertEquals(HealthDataNormalizer.MiSleepStageKind.UNKNOWN, HealthDataNormalizer.miSleepStageKind(99))
        assertEquals("deep", HealthDataNormalizer.miSleepStageLabel(2))
        assertEquals("light", HealthDataNormalizer.miSleepStageLabel(3))
    }

    @Test
    fun normalizeRoute_filtersAndDedupes() {
        val start = 1_700_000_000L
        val end = 1_700_003_600L
        val pts = listOf(
            com.bettermifitness.sync.data.api.WorkoutRoutePoint(start + 10, -6.4, 106.75),
            // same second: sorted then distinctBy keeps first
            com.bettermifitness.sync.data.api.WorkoutRoutePoint(start + 10, -6.41, 106.76),
            com.bettermifitness.sync.data.api.WorkoutRoutePoint(start + 20, 0.0, 0.0), // null island
            com.bettermifitness.sync.data.api.WorkoutRoutePoint(start + 30, 91.0, 106.0), // invalid lat
            com.bettermifitness.sync.data.api.WorkoutRoutePoint(start + 40, -6.42, 106.77),
        )
        val out = HealthDataNormalizer.normalizeRoute(pts, start, end)
        assertEquals(2, out.size)
        assertEquals(start + 10, out[0].timeSec)
        assertEquals(start + 40, out[1].timeSec)
    }
}
