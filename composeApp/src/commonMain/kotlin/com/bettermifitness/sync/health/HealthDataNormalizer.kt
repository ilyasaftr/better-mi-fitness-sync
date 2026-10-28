package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.HrvSample
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
import kotlin.math.roundToInt

/**
 * Pure helpers that sanitize Mi fitness payloads before platform health writes.
 * Shared across Android / iOS so invalid records fail in unit tests, not only on device.
 *
 * Also **dedupes** by stable logical key (last write wins after time sort) so a single
 * sync batch never inserts the same clientRecordId twice.
 */
object HealthDataNormalizer {

    private const val HOUR_SEC = 3600L
    private const val MIN_SLEEP_STAGE_GAP_SECONDS = 1L

    /**
     * Floor for buckets reduced by workout-overlap subtraction: keeps the record alive
     * through normalize (which drops non-positive values) so the corrected value
     * overwrites previously synced inflated history instead of leaving it stale.
     */
    private const val MIN_ADJUSTED_STEPS = 1
    private const val MIN_ADJUSTED_VALUE = 0.01

    /** Mi sometimes returns ms; HealthKit/HC expect seconds. */
    fun toEpochSeconds(raw: Long): Long {
        // Heuristic: timestamps in ms are ~1e12; seconds ~1e9.
        return if (raw > 10_000_000_000L) raw / 1000L else raw
    }

    fun isPlausibleEpochSeconds(seconds: Long): Boolean {
        // 2000-01-01 .. 2100-01-01
        return seconds in 946_684_800L..4_102_444_800L
    }

    private fun usablePoint(epochSec: Long, nowSec: Long): Boolean =
        isPlausibleEpochSeconds(epochSec) && HealthTimePolicy.isNotFuture(epochSec, nowSec)

    fun normalizeHeartRate(
        samples: List<HeartRateSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<HeartRateSample> {
        // Single pass: validate, normalize, last-wins dedupe; single final sort.
        val byTime = LinkedHashMap<Long, HeartRateSample>(samples.size)
        for (s in samples) {
            val t = toEpochSeconds(s.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            if (s.bpm !in 20..250) continue
            byTime[t] = HeartRateSample(timestamp = t, bpm = s.bpm, tzIn15Min = s.tzIn15Min)
        }
        return byTime.values.sortedBy { it.timestamp }
    }

    fun normalizeSpO2(
        samples: List<SpO2Sample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<SpO2Sample> {
        val byTime = LinkedHashMap<Long, SpO2Sample>(samples.size)
        for (s in samples) {
            val t = toEpochSeconds(s.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            if (s.percentage !in 50..100) continue
            byTime[t] = SpO2Sample(timestamp = t, percentage = s.percentage, tzIn15Min = s.tzIn15Min)
        }
        return byTime.values.sortedBy { it.timestamp }
    }

    fun normalizeSteps(
        records: List<StepsRecord>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<StepsRecord> {
        return records.mapNotNull { r ->
            val ts = r.date.toLongOrNull()?.let { toEpochSeconds(it) } ?: return@mapNotNull null
            // Hour bucket start must not be in the future (HC interval start).
            if (!usablePoint(ts, nowEpochSeconds)) return@mapNotNull null
            if (r.steps <= 0 || r.steps > 200_000) return@mapNotNull null
            StepsRecord(
                date = ts.toString(),
                steps = r.steps,
                distance = r.distance,
                calories = r.calories,
                tzIn15Min = r.tzIn15Min,
                workoutAdjusted = r.workoutAdjusted,
            )
        }
            .sortedBy { it.date.toLong() }
            // Prefer higher step count for the same hour bucket (partial day re-sync).
            .groupBy { it.date }
            .map { (_, group) -> group.maxBy { it.steps } }
            .sortedBy { it.date.toLong() }
    }

    /**
     * Mi's per-minute `steps` stream already contains workout-period steps, while each
     * workout also carries its own `totalSteps`, written as a separate record over the
     * session window. Health platforms sum both, so workout days read high (issue #51).
     * Subtract each session's steps from the hour buckets it overlaps (split
     * proportionally to overlapped seconds). Per-workout records are untouched, so no
     * workout detail is lost and hourly + workout totals equal the Mi total.
     */
    fun subtractWorkoutSteps(
        records: List<StepsRecord>,
        workouts: List<WorkoutSession>,
    ): List<StepsRecord> {
        if (records.isEmpty() || workouts.isEmpty()) return records
        val buckets = OverlapBuckets(
            starts = LongArray(records.size) { i ->
                records[i].date.toLongOrNull()?.let { toEpochSeconds(it) } ?: 0L
            },
            values = DoubleArray(records.size) { i -> records[i].steps.toDouble() },
        )
        allocateWorkoutOverlap(buckets, workouts) { it.totalSteps?.toDouble() }
        return records.mapIndexed { i, r ->
            if (!buckets.adjusted[i]) {
                r
            } else {
                r.copy(
                    steps = buckets.values[i].roundToInt().coerceAtLeast(MIN_ADJUSTED_STEPS),
                    workoutAdjusted = true,
                )
            }
        }
    }

    /**
     * Same overlap correction as [subtractWorkoutSteps] for hourly distance, which rides
     * on the same per-minute stream and overlaps per-workout `DistanceRecord`s.
     */
    fun subtractWorkoutDistance(
        samples: List<DistanceSample>,
        workouts: List<WorkoutSession>,
    ): List<DistanceSample> {
        if (samples.isEmpty() || workouts.isEmpty()) return samples
        val buckets = OverlapBuckets(
            starts = LongArray(samples.size) { i -> toEpochSeconds(samples[i].startTime) },
            ends = LongArray(samples.size) { i -> toEpochSeconds(samples[i].endTime) + 1 },
            values = DoubleArray(samples.size) { i -> samples[i].meters },
        )
        allocateWorkoutOverlap(buckets, workouts) { it.distanceMeters }
        return samples.mapIndexed { i, s ->
            if (!buckets.adjusted[i]) {
                s
            } else {
                s.copy(
                    meters = buckets.values[i].coerceAtLeast(MIN_ADJUSTED_VALUE),
                    workoutAdjusted = true,
                )
            }
        }
    }

    /**
     * Same overlap correction as [subtractWorkoutSteps] for hourly active calories,
     * which overlap per-workout `ActiveCaloriesBurnedRecord`s.
     */
    fun subtractWorkoutCalories(
        samples: List<ActiveCaloriesSample>,
        workouts: List<WorkoutSession>,
    ): List<ActiveCaloriesSample> {
        if (samples.isEmpty() || workouts.isEmpty()) return samples
        val buckets = OverlapBuckets(
            starts = LongArray(samples.size) { i -> toEpochSeconds(samples[i].startTime) },
            ends = LongArray(samples.size) { i -> toEpochSeconds(samples[i].endTime) + 1 },
            values = DoubleArray(samples.size) { i -> samples[i].kilocalories },
        )
        allocateWorkoutOverlap(buckets, workouts) { it.caloriesKcal }
        return samples.mapIndexed { i, s ->
            if (!buckets.adjusted[i]) {
                s
            } else {
                s.copy(
                    kilocalories = buckets.values[i].coerceAtLeast(MIN_ADJUSTED_VALUE),
                    workoutAdjusted = true,
                )
            }
        }
    }

    /** Hour-bucket intervals for steps default to `[start, start + 3600)`. */
    private class OverlapBuckets(
        val starts: LongArray,
        val ends: LongArray = LongArray(starts.size) { i -> starts[i] + HOUR_SEC },
        val values: DoubleArray,
        val adjusted: BooleanArray = BooleanArray(starts.size),
    )

    private fun allocateWorkoutOverlap(
        buckets: OverlapBuckets,
        workouts: List<WorkoutSession>,
        amountOf: (WorkoutSession) -> Double?,
    ) {
        for (w in workouts) {
            val amount = amountOf(w)?.takeIf { it > 0 } ?: continue
            val start = toEpochSeconds(w.startTime)
            val end = toEpochSeconds(w.endTime)
            if (end <= start) continue
            val duration = (end - start).toDouble()
            for (i in buckets.starts.indices) {
                val overlap = minOf(end, buckets.ends[i]) - maxOf(start, buckets.starts[i])
                if (overlap <= 0) continue
                buckets.values[i] -= amount * overlap / duration
                buckets.adjusted[i] = true
            }
        }
    }

    fun normalizeSleep(
        sessions: List<SleepSession>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<SleepSession> {
        val byStart = LinkedHashMap<Long, SleepSession>(sessions.size)
        for (session in sessions) {
            val cleaned = cleanSleepSession(session, nowEpochSeconds) ?: continue
            val existing = byStart[cleaned.startTime]
            byStart[cleaned.startTime] =
                if (existing == null) cleaned else mergeSleepSessions(existing, cleaned)
        }
        // Merging complete and legacy/truncated rows can introduce overlapping stages
        // with different start times. Repair the merged timeline before constructing
        // platform sleep records, whose stage intervals must be strictly ordered.
        return byStart.values
            .sortedBy { it.startTime }
            .mapNotNull { session ->
                val repairedStages = repairSleepStages(
                    sessionStart = session.startTime,
                    sessionEnd = session.endTime,
                    stages = session.stages,
                )
                if (repairedStages.isEmpty()) {
                    null
                } else {
                    session.copy(stages = repairedStages)
                }
            }
    }

    /**
     * Makes a merged sleep timeline acceptable to Health Connect / HealthKit.
     *
     * Stages are ordered by their source start time. Each stage after the first
     * starts at least one second after the previous retained stage ends. We move
     * only the start boundary, preserving the source end; if that would leave no
     * positive duration, the stage is discarded instead of inventing an end time.
     */
    internal fun repairSleepStages(
        sessionStart: Long,
        sessionEnd: Long,
        stages: List<SleepStage>,
    ): List<SleepStage> {
        var previousEnd: Long? = null

        return stages
            .asSequence()
            .filter { it.endTime > it.startTime }
            .sortedWith(
                compareBy<SleepStage> { it.startTime }
                    .thenBy { it.endTime },
            )
            .mapNotNull { stage ->
                val end = minOf(stage.endTime, sessionEnd)
                val minimumStart = previousEnd
                    ?.plus(MIN_SLEEP_STAGE_GAP_SECONDS)
                    ?: sessionStart
                val start = maxOf(stage.startTime, minimumStart)

                if (start >= end) {
                    null
                } else {
                    stage.copy(startTime = start, endTime = end).also {
                        previousEnd = end
                    }
                }
            }
            .toList()
    }

    private fun cleanSleepSession(
        session: SleepSession,
        nowEpochSeconds: Long,
    ): SleepSession? {
        val start = toEpochSeconds(session.startTime)
        val endRaw = toEpochSeconds(session.endTime)
        if (!isPlausibleEpochSeconds(start)) return null
        val clamped = HealthTimePolicy.clampInterval(start, endRaw, nowEpochSeconds)
            ?: return null
        val (clampedStart, end) = clamped
        // Trim invalid stages (1.0.2 semantics); only drop the session when
        // nothing valid remains.
        val stageByStart = LinkedHashMap<Long, SleepStage>()
        for (stage in session.stages) {
            val s = toEpochSeconds(stage.startTime)
            val e = toEpochSeconds(stage.endTime)
            if (e <= s) continue
            if (s < clampedStart || e > end) continue
            if (!HealthTimePolicy.isNotFuture(e, nowEpochSeconds)) continue
            stageByStart[s] = SleepStage(startTime = s, endTime = e, stage = stage.stage)
        }
        if (stageByStart.isEmpty()) return null
        val stages = stageByStart.values.sortedBy { it.startTime }
        val inBedStart = toEpochSeconds(session.inBedStart)
            .takeIf { isPlausibleEpochSeconds(it) && it <= end } ?: clampedStart
        val inBedEnd = toEpochSeconds(session.inBedEnd)
            .takeIf { isPlausibleEpochSeconds(it) }
            ?.let { minOf(it, end) }
            ?: end
        return SleepSession(
            startTime = clampedStart,
            endTime = end,
            inBedStart = inBedStart,
            inBedEnd = maxOf(inBedStart + 1, inBedEnd),
            stages = stages,
            avgHrvMs = session.avgHrvMs?.takeIf { it in 5..300 },
            minHrvMs = session.minHrvMs?.takeIf { it in 5..300 },
            maxHrvMs = session.maxHrvMs?.takeIf { it in 5..300 },
            hrvAnalysisTimeSec = session.hrvAnalysisTimeSec
                ?.let { toEpochSeconds(it) }
                ?.takeIf { usablePoint(it, nowEpochSeconds) },
            tzIn15Min = session.tzIn15Min,
        )
    }

    /**
     * Unions two sessions sharing the same start (e.g. full `sleep` row plus a
     * first-block-only legacy watch report): stages merged by start, end
     * extended to the latest wake, HRV kept from the first non-null source.
     */
    internal fun mergeSleepSessions(a: SleepSession, b: SleepSession): SleepSession {
        val stages = (a.stages + b.stages)
            .associateBy { it.startTime }
            .values
            .sortedBy { it.startTime }
        val end = maxOf(a.endTime, b.endTime)
        val inBedStart = minOf(a.inBedStart, b.inBedStart)
        val inBedEnd = maxOf(a.inBedEnd, b.inBedEnd)
        return SleepSession(
            startTime = a.startTime,
            endTime = end,
            inBedStart = inBedStart,
            inBedEnd = maxOf(inBedStart + 1, inBedEnd),
            stages = stages,
            avgHrvMs = a.avgHrvMs ?: b.avgHrvMs,
            minHrvMs = a.minHrvMs ?: b.minHrvMs,
            maxHrvMs = a.maxHrvMs ?: b.maxHrvMs,
            hrvAnalysisTimeSec = a.hrvAnalysisTimeSec ?: b.hrvAnalysisTimeSec,
            tzIn15Min = a.tzIn15Min ?: b.tzIn15Min,
        )
    }

    fun normalizeHrv(
        samples: List<HrvSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<HrvSample> {
        val byTime = LinkedHashMap<Long, HrvSample>(samples.size)
        for (s in samples) {
            val t = toEpochSeconds(s.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            if (s.hrvMs !in 5.0..300.0) continue
            byTime[t] = HrvSample(timestamp = t, hrvMs = s.hrvMs, tzIn15Min = s.tzIn15Min)
        }
        return byTime.values.sortedBy { it.timestamp }
    }

    /**
     * Raw Mi Fitness stages, verified against its aggregate duration fields:
     * state 2=deep, 3=light/core, 4=REM, and 5 (or legacy 1)=awake.
     */
    enum class MiSleepStageKind { AWAKE, DEEP, LIGHT, REM, UNKNOWN }

    fun miSleepStageKind(stage: Int): MiSleepStageKind = when (stage) {
        5, 1 -> MiSleepStageKind.AWAKE
        2 -> MiSleepStageKind.DEEP
        3 -> MiSleepStageKind.LIGHT
        4 -> MiSleepStageKind.REM
        else -> MiSleepStageKind.UNKNOWN
    }

    fun miSleepStageLabel(stage: Int): String = when (miSleepStageKind(stage)) {
        MiSleepStageKind.AWAKE -> "awake"
        MiSleepStageKind.DEEP -> "deep"
        MiSleepStageKind.LIGHT -> "light"
        MiSleepStageKind.REM -> "rem"
        MiSleepStageKind.UNKNOWN -> "unknown"
    }

    fun normalizeDistance(
        samples: List<DistanceSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<DistanceSample> =
        samples.mapNotNull { s ->
            val start = toEpochSeconds(s.startTime)
            val endRaw = toEpochSeconds(s.endTime)
            val clamped = HealthTimePolicy.clampInterval(start, endRaw, nowEpochSeconds)
                ?: return@mapNotNull null
            if (s.meters <= 0 || s.meters > 500_000) return@mapNotNull null
            DistanceSample(
                startTime = clamped.first,
                endTime = clamped.second,
                meters = s.meters,
                tzIn15Min = s.tzIn15Min,
                workoutAdjusted = s.workoutAdjusted,
            )
        }
            .sortedBy { it.startTime }
            .groupBy { it.startTime }
            .map { (_, group) -> group.maxBy { it.meters } }
            .sortedBy { it.startTime }

    fun normalizeActiveCalories(
        samples: List<ActiveCaloriesSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<ActiveCaloriesSample> =
        samples.mapNotNull { s ->
            val start = toEpochSeconds(s.startTime)
            val endRaw = toEpochSeconds(s.endTime)
            val clamped = HealthTimePolicy.clampInterval(start, endRaw, nowEpochSeconds)
                ?: return@mapNotNull null
            if (s.kilocalories <= 0 || s.kilocalories > 50_000) return@mapNotNull null
            ActiveCaloriesSample(
                startTime = clamped.first,
                endTime = clamped.second,
                kilocalories = s.kilocalories,
                tzIn15Min = s.tzIn15Min,
                workoutAdjusted = s.workoutAdjusted,
            )
        }
            .sortedBy { it.startTime }
            .groupBy { it.startTime }
            .map { (_, group) -> group.maxBy { it.kilocalories } }
            .sortedBy { it.startTime }

    fun normalizeWeight(
        measurements: List<WeightMeasurement>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<WeightMeasurement> {
        val byTime = LinkedHashMap<Long, WeightMeasurement>(measurements.size)
        for (m in measurements) {
            val t = toEpochSeconds(m.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            if (m.weightKg !in 1.0..500.0) continue
            val fat = m.bodyFatPercent?.takeIf { it in 1.0..70.0 }
            byTime[t] = WeightMeasurement(
                timestamp = t,
                weightKg = m.weightKg,
                bodyFatPercent = fat,
                muscleMassKg = m.muscleMassKg?.takeIf { it in 1.0..200.0 },
                boneMassKg = m.boneMassKg?.takeIf { it in 0.1..50.0 },
                basalMetabolismKcal = m.basalMetabolismKcal?.takeIf { it in 200.0..10_000.0 },
                tzIn15Min = m.tzIn15Min,
            )
        }
        return byTime.values.sortedBy { it.timestamp }
    }

    fun normalizeWorkouts(
        sessions: List<WorkoutSession>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<WorkoutSession> {
        val byStart = LinkedHashMap<Long, WorkoutSession>(sessions.size)
        for (w in sessions) {
            val start = toEpochSeconds(w.startTime)
            val endRaw = toEpochSeconds(w.endTime)
            val clamped = HealthTimePolicy.clampInterval(start, endRaw, nowEpochSeconds)
                ?: continue
            val end = clamped.second
            if (end - start > 24 * 3600) continue
            val cleaned = WorkoutSession(
                startTime = clamped.first,
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
            byStart[cleaned.startTime] = WorkoutRunningMetrics.enrich(cleaned)
        }
        return byStart.values.sortedBy { it.startTime }
    }

    private fun normalizeTimed(
        samples: List<WorkoutTimedSample>,
        start: Long,
        end: Long,
        minV: Double,
        maxV: Double,
    ): List<WorkoutTimedSample> {
        if (samples.isEmpty()) return emptyList()
        // Sort first so later duplicates overwrite earlier ones (last-wins), then single dedupe.
        val ordered = samples.sortedBy { toEpochSeconds(it.timeSec) }
        val byTime = LinkedHashMap<Long, WorkoutTimedSample>(ordered.size)
        for (s in ordered) {
            val t = toEpochSeconds(s.timeSec)
            if (t < start || t > end) continue
            if (s.value !in minV..maxV) continue
            byTime[t] = WorkoutTimedSample(t, s.value)
        }
        return byTime.values.toList()
    }

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
        val ordered = points.sortedBy { toEpochSeconds(it.timeSec) }
        val byTime = LinkedHashMap<Long, WorkoutRoutePoint>(ordered.size)
        for (p in ordered) {
            val t = toEpochSeconds(p.timeSec)
            if (!isPlausibleEpochSeconds(t) || t < lo || t > hi) continue
            if (p.latitude !in -90.0..90.0 || p.longitude !in -180.0..180.0) continue
            if (p.latitude == 0.0 && p.longitude == 0.0) continue
            byTime[t] = WorkoutRoutePoint(
                timeSec = t,
                latitude = p.latitude,
                longitude = p.longitude,
                altitudeMeters = p.altitudeMeters?.takeIf { it in -500.0..9000.0 },
                horizontalAccuracyMeters = p.horizontalAccuracyMeters?.takeIf { it in 0.0..5000.0 },
            )
        }
        return byTime.values.toList()
    }

    fun normalizeBloodPressure(
        samples: List<BloodPressureSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<BloodPressureSample> {
        val byTime = LinkedHashMap<Long, BloodPressureSample>(samples.size)
        for (s in samples) {
            val t = toEpochSeconds(s.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            if (s.systolicMmhg !in 60..250 || s.diastolicMmhg !in 30..150) continue
            if (s.diastolicMmhg >= s.systolicMmhg) continue
            byTime[t] = BloodPressureSample(
                timestamp = t,
                systolicMmhg = s.systolicMmhg,
                diastolicMmhg = s.diastolicMmhg,
                pulseBpm = s.pulseBpm?.takeIf { it in 20..250 },
                tzIn15Min = s.tzIn15Min,
            )
        }
        return byTime.values.sortedBy { it.timestamp }
    }

    fun normalizeTemperature(
        samples: List<TemperatureSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<TemperatureSample> {
        val byTime = LinkedHashMap<Long, TemperatureSample>(samples.size)
        for (s in samples) {
            val t = toEpochSeconds(s.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            val body = s.bodyCelsius?.takeIf { it in 30.0..45.0 }
            val skin = s.skinCelsius?.takeIf { it in 20.0..45.0 }
            if (body == null && skin == null) continue
            byTime[t] = TemperatureSample(
                timestamp = t,
                bodyCelsius = body,
                skinCelsius = skin,
                tzIn15Min = s.tzIn15Min,
            )
        }
        return byTime.values.sortedBy { it.timestamp }
    }

    fun normalizeVo2Max(
        samples: List<Vo2MaxSample>,
        nowEpochSeconds: Long = HealthTimePolicy.nowEpochSeconds(),
    ): List<Vo2MaxSample> {
        val byTime = LinkedHashMap<Long, Vo2MaxSample>(samples.size)
        for (s in samples) {
            val t = toEpochSeconds(s.timestamp)
            if (!usablePoint(t, nowEpochSeconds)) continue
            if (s.mlPerKgMin !in 5.0..100.0) continue
            byTime[t] = Vo2MaxSample(timestamp = t, mlPerKgMin = s.mlPerKgMin, tzIn15Min = s.tzIn15Min)
        }
        return byTime.values.sortedBy { it.timestamp }
    }
}
