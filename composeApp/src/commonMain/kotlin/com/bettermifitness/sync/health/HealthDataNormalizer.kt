package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SleepStage
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.TemperatureSample
import com.bettermifitness.sync.data.api.Vo2MaxSample
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutRoutePoint
import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.data.api.WorkoutTimedSample

/**
 * Pure helpers that sanitize Mi fitness payloads before platform health writes.
 * Shared across Android / iOS so invalid records fail in unit tests, not only on device.
 *
 * Also **dedupes** by stable logical key (last write wins after time sort) so a single
 * sync batch never inserts the same clientRecordId twice.
 */
object HealthDataNormalizer {

    /** Mi sometimes returns ms; HealthKit/HC expect seconds. */
    fun toEpochSeconds(raw: Long): Long {
        // Heuristic: timestamps in ms are ~1e12; seconds ~1e9.
        return if (raw > 10_000_000_000L) raw / 1000L else raw
    }

    fun isPlausibleEpochSeconds(seconds: Long): Boolean {
        // 2000-01-01 .. 2100-01-01
        return seconds in 946_684_800L..4_102_444_800L
    }

    fun normalizeHeartRate(samples: List<HeartRateSample>): List<HeartRateSample> {
        return samples.mapNotNull { s ->
            val t = toEpochSeconds(s.timestamp)
            if (!isPlausibleEpochSeconds(t)) return@mapNotNull null
            if (s.bpm !in 20..250) return@mapNotNull null
            HeartRateSample(timestamp = t, bpm = s.bpm)
        }
            .sortedBy { it.timestamp }
            // Last sample at a given second wins.
            .associateBy { it.timestamp }
            .values
            .sortedBy { it.timestamp }
    }

    fun normalizeSpO2(samples: List<SpO2Sample>): List<SpO2Sample> {
        return samples.mapNotNull { s ->
            val t = toEpochSeconds(s.timestamp)
            if (!isPlausibleEpochSeconds(t)) return@mapNotNull null
            if (s.percentage !in 50..100) return@mapNotNull null
            SpO2Sample(timestamp = t, percentage = s.percentage)
        }
            .sortedBy { it.timestamp }
            .associateBy { it.timestamp }
            .values
            .sortedBy { it.timestamp }
    }

    fun normalizeSteps(records: List<StepsRecord>): List<StepsRecord> {
        return records.mapNotNull { r ->
            val ts = r.date.toLongOrNull()?.let { toEpochSeconds(it) } ?: return@mapNotNull null
            if (!isPlausibleEpochSeconds(ts)) return@mapNotNull null
            if (r.steps <= 0 || r.steps > 200_000) return@mapNotNull null
            StepsRecord(date = ts.toString(), steps = r.steps, distance = r.distance, calories = r.calories)
        }
            .sortedBy { it.date.toLong() }
            // Prefer higher step count for the same hour bucket (partial day re-sync).
            .groupBy { it.date }
            .map { (_, group) -> group.maxBy { it.steps } }
            .sortedBy { it.date.toLong() }
    }

    fun normalizeSleep(sessions: List<SleepSession>): List<SleepSession> {
        return sessions.mapNotNull { session ->
            val start = toEpochSeconds(session.startTime)
            val end = toEpochSeconds(session.endTime)
            if (!isPlausibleEpochSeconds(start) || !isPlausibleEpochSeconds(end)) return@mapNotNull null
            if (end <= start) return@mapNotNull null
            val stages = session.stages.mapNotNull { stage ->
                val s = toEpochSeconds(stage.startTime)
                val e = toEpochSeconds(stage.endTime)
                if (e <= s) return@mapNotNull null
                if (s < start || e > end) return@mapNotNull null
                SleepStage(startTime = s, endTime = e, stage = stage.stage)
            }
                .sortedBy { it.startTime }
                .associateBy { it.startTime }
                .values
                .sortedBy { it.startTime }
            val inBedStart = toEpochSeconds(session.inBedStart).takeIf { isPlausibleEpochSeconds(it) } ?: start
            val inBedEnd = toEpochSeconds(session.inBedEnd).takeIf { isPlausibleEpochSeconds(it) } ?: end
            SleepSession(
                startTime = start,
                endTime = end,
                inBedStart = inBedStart,
                inBedEnd = inBedEnd,
                stages = stages,
            )
        }
            .sortedBy { it.startTime }
            .associateBy { it.startTime }
            .values
            .sortedBy { it.startTime }
    }

    /**
     * Mi Fitness sleep states → platform-neutral labels for tests.
     * 2=light/core, 3=deep, 4=REM, 5=awake
     */
    fun miSleepStageLabel(stage: Int): String = when (stage) {
        5, 1 -> "awake"
        2 -> "light"
        3 -> "deep"
        4 -> "rem"
        else -> "unknown"
    }

    fun normalizeDistance(samples: List<DistanceSample>): List<DistanceSample> =
        samples.mapNotNull { s ->
            val start = toEpochSeconds(s.startTime)
            val end = toEpochSeconds(s.endTime)
            if (!isPlausibleEpochSeconds(start) || end <= start) return@mapNotNull null
            if (s.meters <= 0 || s.meters > 500_000) return@mapNotNull null
            DistanceSample(startTime = start, endTime = end, meters = s.meters)
        }
            .sortedBy { it.startTime }
            .groupBy { it.startTime }
            .map { (_, group) -> group.maxBy { it.meters } }
            .sortedBy { it.startTime }

    fun normalizeActiveCalories(samples: List<ActiveCaloriesSample>): List<ActiveCaloriesSample> =
        samples.mapNotNull { s ->
            val start = toEpochSeconds(s.startTime)
            val end = toEpochSeconds(s.endTime)
            if (!isPlausibleEpochSeconds(start) || end <= start) return@mapNotNull null
            if (s.kilocalories <= 0 || s.kilocalories > 50_000) return@mapNotNull null
            ActiveCaloriesSample(startTime = start, endTime = end, kilocalories = s.kilocalories)
        }
            .sortedBy { it.startTime }
            .groupBy { it.startTime }
            .map { (_, group) -> group.maxBy { it.kilocalories } }
            .sortedBy { it.startTime }

    fun normalizeWeight(measurements: List<WeightMeasurement>): List<WeightMeasurement> =
        measurements.mapNotNull { m ->
            val t = toEpochSeconds(m.timestamp)
            if (!isPlausibleEpochSeconds(t)) return@mapNotNull null
            if (m.weightKg !in 1.0..500.0) return@mapNotNull null
            val fat = m.bodyFatPercent?.takeIf { it in 1.0..70.0 }
            WeightMeasurement(
                timestamp = t,
                weightKg = m.weightKg,
                bodyFatPercent = fat,
                muscleMassKg = m.muscleMassKg?.takeIf { it in 1.0..200.0 },
                boneMassKg = m.boneMassKg?.takeIf { it in 0.1..50.0 },
                basalMetabolismKcal = m.basalMetabolismKcal?.takeIf { it in 200.0..10_000.0 },
            )
        }
            .sortedBy { it.timestamp }
            .associateBy { it.timestamp }
            .values
            .sortedBy { it.timestamp }

    fun normalizeWorkouts(sessions: List<WorkoutSession>): List<WorkoutSession> =
        sessions.mapNotNull { w ->
            val start = toEpochSeconds(w.startTime)
            val end = toEpochSeconds(w.endTime)
            if (!isPlausibleEpochSeconds(start) || end <= start) return@mapNotNull null
            if (end - start > 24 * 3600) return@mapNotNull null
            val cleaned = WorkoutSession(
                startTime = start,
                endTime = end,
                activityType = w.activityType.ifBlank { "workout" },
                distanceMeters = w.distanceMeters?.takeIf { it > 0 },
                caloriesKcal = w.caloriesKcal?.takeIf { it > 0 },
                avgHeartRateBpm = w.avgHeartRateBpm?.takeIf { it in 20..250 },
                maxHeartRateBpm = w.maxHeartRateBpm?.takeIf { it in 20..250 },
                minHeartRateBpm = w.minHeartRateBpm?.takeIf { it in 20..250 },
                totalSteps = w.totalSteps?.takeIf { it > 0 },
                avgPaceSecPerKm = w.avgPaceSecPerKm?.takeIf { it in 60.0..3600.0 },
                maxPaceSecPerKm = w.maxPaceSecPerKm?.takeIf { it in 60.0..3600.0 },
                minPaceSecPerKm = w.minPaceSecPerKm?.takeIf { it in 60.0..3600.0 },
                avgCadenceSpm = w.avgCadenceSpm?.takeIf { it in 20.0..300.0 },
                maxCadenceSpm = w.maxCadenceSpm?.takeIf { it in 20.0..300.0 },
                maxSpeedMps = w.maxSpeedMps?.takeIf { it in 0.1..30.0 },
                avgStrideCm = w.avgStrideCm?.takeIf { it in 20.0..250.0 },
                avgPowerWatts = w.avgPowerWatts?.takeIf { it in 20.0..2000.0 },
                maxPowerWatts = w.maxPowerWatts?.takeIf { it in 20.0..2000.0 },
                avgGroundContactMs = w.avgGroundContactMs?.takeIf { it in 50.0..500.0 },
                avgVerticalOscillationCm = w.avgVerticalOscillationCm?.takeIf { it in 1.0..30.0 },
                elevationGainM = w.elevationGainM?.takeIf { it in 0.0..15_000.0 },
                elevationLossM = w.elevationLossM?.takeIf { it in 0.0..15_000.0 },
                maxElevationM = w.maxElevationM,
                minElevationM = w.minElevationM,
                avgElevationM = w.avgElevationM,
                hrZoneWarmupSec = w.hrZoneWarmupSec?.takeIf { it >= 0 },
                hrZoneFatBurnSec = w.hrZoneFatBurnSec?.takeIf { it >= 0 },
                hrZoneAerobicSec = w.hrZoneAerobicSec?.takeIf { it >= 0 },
                hrZoneAnaerobicSec = w.hrZoneAnaerobicSec?.takeIf { it >= 0 },
                hrZoneExtremeSec = w.hrZoneExtremeSec?.takeIf { it >= 0 },
                trainEffect = w.trainEffect?.takeIf { it in 0.0..10.0 },
                trainLoad = w.trainLoad?.takeIf { it >= 0.0 },
                recoverMinutes = w.recoverMinutes?.takeIf { it in 0..10_000 },
                vo2Max = w.vo2Max?.takeIf { it in 10.0..100.0 },
                tzIn15Min = w.tzIn15Min,
                route = normalizeRoute(w.route, start, end),
                heartRateSeries = normalizeTimed(w.heartRateSeries, start, end, 30.0, 250.0),
                paceSeries = normalizeTimed(w.paceSeries, start, end, 60.0, 3600.0),
                cadenceSeries = normalizeTimed(w.cadenceSeries, start, end, 20.0, 300.0),
                speedSeries = normalizeTimed(w.speedSeries, start, end, 0.1, 30.0),
                elevationSeries = normalizeTimed(w.elevationSeries, start - 60, end + 60, -500.0, 9000.0),
                strideMetersSeries = normalizeTimed(w.strideMetersSeries, start, end, 0.2, 2.5),
                powerWattsSeries = normalizeTimed(w.powerWattsSeries, start, end, 20.0, 2000.0),
                groundContactMsSeries = normalizeTimed(w.groundContactMsSeries, start, end, 50.0, 500.0),
                verticalOscillationCmSeries = normalizeTimed(
                    w.verticalOscillationCmSeries, start, end, 1.0, 30.0,
                ),
                kmSplits = w.kmSplits.filter { it.kilometer > 0 && it.timeSec in start..end + 120 },
                recoverHeartRateSeries = normalizeTimed(
                    w.recoverHeartRateSeries,
                    end,
                    end + 3600,
                    30.0,
                    250.0,
                ),
                gpsDeviceSid = w.gpsDeviceSid,
                gpsTimestampSec = w.gpsTimestampSec,
                gpsTzIn15Min = w.gpsTzIn15Min,
                gpsProtoType = w.gpsProtoType,
            )
            // Fill pace/cadence/stride/speed series for Health Details charts
            WorkoutRunningMetrics.enrich(cleaned)
        }
            .sortedBy { it.startTime }
            .associateBy { it.startTime }
            .values
            .sortedBy { it.startTime }

    private fun normalizeTimed(
        samples: List<WorkoutTimedSample>,
        start: Long,
        end: Long,
        minV: Double,
        maxV: Double,
    ): List<WorkoutTimedSample> =
        samples
            .mapNotNull { s ->
                val t = toEpochSeconds(s.timeSec)
                if (t < start || t > end) return@mapNotNull null
                if (s.value !in minV..maxV) return@mapNotNull null
                WorkoutTimedSample(t, s.value)
            }
            .sortedBy { it.timeSec }
            .distinctBy { it.timeSec }

    /**
     * Drops invalid coords, sorts by time, clamps to session window, de-dupes same second.
     */
    fun normalizeRoute(
        points: List<WorkoutRoutePoint>,
        sessionStart: Long,
        sessionEnd: Long,
    ): List<WorkoutRoutePoint> {
        if (points.isEmpty()) return emptyList()
        val lo = sessionStart - 60
        val hi = sessionEnd + 60
        return points
            .mapNotNull { p ->
                val t = toEpochSeconds(p.timeSec)
                if (!isPlausibleEpochSeconds(t) || t < lo || t > hi) return@mapNotNull null
                if (p.latitude !in -90.0..90.0 || p.longitude !in -180.0..180.0) return@mapNotNull null
                if (p.latitude == 0.0 && p.longitude == 0.0) return@mapNotNull null
                WorkoutRoutePoint(
                    timeSec = t,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    altitudeMeters = p.altitudeMeters?.takeIf { it in -500.0..9000.0 },
                    horizontalAccuracyMeters = p.horizontalAccuracyMeters?.takeIf { it in 0.0..5000.0 },
                )
            }
            .sortedBy { it.timeSec }
            .distinctBy { it.timeSec }
    }

    fun normalizeBloodPressure(samples: List<BloodPressureSample>): List<BloodPressureSample> =
        samples.mapNotNull { s ->
            val t = toEpochSeconds(s.timestamp)
            if (!isPlausibleEpochSeconds(t)) return@mapNotNull null
            if (s.systolicMmhg !in 60..250 || s.diastolicMmhg !in 30..150) return@mapNotNull null
            if (s.diastolicMmhg >= s.systolicMmhg) return@mapNotNull null
            BloodPressureSample(
                timestamp = t,
                systolicMmhg = s.systolicMmhg,
                diastolicMmhg = s.diastolicMmhg,
                pulseBpm = s.pulseBpm?.takeIf { it in 20..250 },
            )
        }
            .sortedBy { it.timestamp }
            .associateBy { it.timestamp }
            .values
            .sortedBy { it.timestamp }

    fun normalizeTemperature(samples: List<TemperatureSample>): List<TemperatureSample> =
        samples.mapNotNull { s ->
            val t = toEpochSeconds(s.timestamp)
            if (!isPlausibleEpochSeconds(t)) return@mapNotNull null
            val body = s.bodyCelsius?.takeIf { it in 30.0..45.0 }
            val skin = s.skinCelsius?.takeIf { it in 20.0..45.0 }
            if (body == null && skin == null) return@mapNotNull null
            TemperatureSample(timestamp = t, bodyCelsius = body, skinCelsius = skin)
        }
            .sortedBy { it.timestamp }
            .associateBy { it.timestamp }
            .values
            .sortedBy { it.timestamp }

    fun normalizeVo2Max(samples: List<Vo2MaxSample>): List<Vo2MaxSample> =
        samples.mapNotNull { s ->
            val t = toEpochSeconds(s.timestamp)
            if (!isPlausibleEpochSeconds(t)) return@mapNotNull null
            if (s.mlPerKgMin !in 5.0..100.0) return@mapNotNull null
            Vo2MaxSample(timestamp = t, mlPerKgMin = s.mlPerKgMin)
        }
            .sortedBy { it.timestamp }
            .associateBy { it.timestamp }
            .values
            .sortedBy { it.timestamp }
}
