package com.bettermifitness.sync.data.api

import com.bettermifitness.sync.data.time.resolveOffsetSecondsFromMiUnits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- User Profile ---

@Serializable
data class MeResponse(
    val code: Int? = null,
    val message: String? = null,
    val result: UserProfile? = null,
)

@Serializable
data class UserProfile(
    val name: String? = null,
    val sex: String? = null,
    val age: Int? = null,
    val height: Int? = null,
    val birth: String? = null,
    @SerialName("daily_step_goal") val dailyStepGoal: Int? = null,
    @SerialName("daily_cal_goal") val dailyCalGoal: Int? = null,
)

// --- Fitness Data ---

@Serializable
data class FitnessResponse<T>(
    val result: FitnessResult<T>? = null,
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
data class FitnessResult<T>(
    @SerialName("data_list") val dataList: List<T> = emptyList(),
    val watermark: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_key") val nextKey: String? = null,
)

// --- Heart Rate / generic by-time row ---

@Serializable
data class HeartRateEntry(
    val key: String? = null,
    val time: Long = 0,
    val value: String = "",
)

@Serializable
data class HeartRateSample(
    val timestamp: Long,
    val bpm: Int,
    /** Mi `timezone` field: offset in units of 15 minutes (28 → UTC+7). */
    val tzIn15Min: Int? = null,
)

// --- Sleep ---

@Serializable
data class SleepEntry(
    val key: String? = null,
    val time: Long = 0,
    val value: String = "",
)

@Serializable
data class SleepSession(
    val startTime: Long,
    val endTime: Long,
    val inBedStart: Long = 0,
    val inBedEnd: Long = 0,
    val stages: List<SleepStage> = emptyList(),
    /**
     * Overnight HRV from Mi sleep payload (ms). Present only on capable devices
     * (e.g. Band 10 Pro); Band 10 and similar omit these fields.
     */
    val avgHrvMs: Int? = null,
    val minHrvMs: Int? = null,
    val maxHrvMs: Int? = null,
    /** Epoch seconds when Mi analyzed HRV; falls back to wake time when writing. */
    val hrvAnalysisTimeSec: Long? = null,
    /** Mi `timezone` field: offset in units of 15 minutes (28 → UTC+7). */
    val tzIn15Min: Int? = null,
)

@Serializable
data class SleepStage(
    val startTime: Long,
    val endTime: Long,
    val stage: Int, // 2=light/core, 3=deep, 4=REM, 5=awake (Mi codes)
)

/**
 * Overnight HRV sample derived from Mi sleep JSON (`avg_hrv` in ms).
 * Not a separate Mi cloud key — only written when sleep payload includes HRV.
 */
@Serializable
data class HrvSample(
    val timestamp: Long,
    /** Heart-rate variability in milliseconds (Mi UI unit). */
    val hrvMs: Double,
    /** Mi sleep `timezone` (15-min units), when derived from a sleep session. */
    val tzIn15Min: Int? = null,
)

// --- Steps ---

@Serializable
data class StepsRecord(
    val date: String,
    val steps: Int,
    val distance: Double = 0.0,
    val calories: Double = 0.0,
    /** Mi `timezone` from first sample in the hour bucket, if present. */
    val tzIn15Min: Int? = null,
)

// --- SpO2 ---

@Serializable
data class SpO2Sample(
    val timestamp: Long,
    val percentage: Int,
    /** Mi `timezone` field: offset in units of 15 minutes. */
    val tzIn15Min: Int? = null,
)

// --- Distance (meters), interval start = epoch seconds ---

@Serializable
data class DistanceSample(
    val startTime: Long,
    val endTime: Long,
    val meters: Double,
    /** Mi `timezone` from first sample in the hour bucket, if present. */
    val tzIn15Min: Int? = null,
)

// --- Active energy (kcal) ---

@Serializable
data class ActiveCaloriesSample(
    val startTime: Long,
    val endTime: Long,
    val kilocalories: Double,
    /** Mi `timezone` from first sample in the hour bucket, if present. */
    val tzIn15Min: Int? = null,
)

// --- Weight / body composition ---

@Serializable
data class WeightMeasurement(
    val timestamp: Long,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val basalMetabolismKcal: Double? = null,
    /** Mi `timezone` field: offset in units of 15 minutes. */
    val tzIn15Min: Int? = null,
)

// --- Workouts ---

/** One GPS track point for HealthKit / Health Connect workout routes. */
@Serializable
data class WorkoutRoutePoint(
    val timeSec: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val horizontalAccuracyMeters: Double? = null,
)

/** Scalar sample during a workout (HR, pace, cadence, …). */
@Serializable
data class WorkoutTimedSample(
    val timeSec: Long,
    val value: Double,
)

/** Whole-km split from Mi record series (or derived). */
@Serializable
data class WorkoutKmSplit(
    val kilometer: Int,
    val timeSec: Long,
    val paceSecPerKm: Double? = null,
)

@Serializable
data class WorkoutSession(
    val startTime: Long,
    val endTime: Long,
    /** Mi category / sport type string (e.g. running, cycling). */
    val activityType: String,
    val distanceMeters: Double? = null,
    val caloriesKcal: Double? = null,
    val avgHeartRateBpm: Int? = null,
    val maxHeartRateBpm: Int? = null,
    val minHeartRateBpm: Int? = null,
    val totalSteps: Int? = null,
    /** Pace in seconds per kilometer (Mi avg_pace / max_pace / min_pace). */
    val avgPaceSecPerKm: Double? = null,
    val maxPaceSecPerKm: Double? = null,
    val minPaceSecPerKm: Double? = null,
    val avgCadenceSpm: Double? = null,
    val maxCadenceSpm: Double? = null,
    val maxSpeedMps: Double? = null,
    val avgStrideCm: Double? = null,
    /** Running power watts (Mi rarely provides on phone GPS). */
    val avgPowerWatts: Double? = null,
    val maxPowerWatts: Double? = null,
    /** Form metrics when Mi/watch provides them (ms / cm). */
    val avgGroundContactMs: Double? = null,
    val avgVerticalOscillationCm: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val maxElevationM: Double? = null,
    val minElevationM: Double? = null,
    val avgElevationM: Double? = null,
    /** Seconds spent in Mi HR zones (warm-up → extreme). */
    val hrZoneWarmupSec: Int? = null,
    val hrZoneFatBurnSec: Int? = null,
    val hrZoneAerobicSec: Int? = null,
    val hrZoneAnaerobicSec: Int? = null,
    val hrZoneExtremeSec: Int? = null,
    val trainEffect: Double? = null,
    val trainLoad: Double? = null,
    val recoverMinutes: Int? = null,
    val vo2Max: Double? = null,
    /** Mi `timezone` field: offset in units of 15 minutes (28 → UTC+7). */
    val tzIn15Min: Int? = null,
    /** GPS route points (empty when indoor / download failed / no GPS file). */
    val route: List<WorkoutRoutePoint> = emptyList(),
    /** In-workout time series (from FDS record +/or Mi HR by time). */
    val heartRateSeries: List<WorkoutTimedSample> = emptyList(),
    val paceSeries: List<WorkoutTimedSample> = emptyList(),
    val cadenceSeries: List<WorkoutTimedSample> = emptyList(),
    val speedSeries: List<WorkoutTimedSample> = emptyList(),
    val elevationSeries: List<WorkoutTimedSample> = emptyList(),
    /** Stride length in **meters** over time (for HK RunningStrideLength / notes). */
    val strideMetersSeries: List<WorkoutTimedSample> = emptyList(),
    val powerWattsSeries: List<WorkoutTimedSample> = emptyList(),
    val groundContactMsSeries: List<WorkoutTimedSample> = emptyList(),
    val verticalOscillationCmSeries: List<WorkoutTimedSample> = emptyList(),
    val kmSplits: List<WorkoutKmSplit> = emptyList(),
    val recoverHeartRateSeries: List<WorkoutTimedSample> = emptyList(),
    /**
     * FDS download keys from the sport report (not written to Health).
     * Set when report `version > 0` so sync can fetch GPS/record/recover files.
     */
    val gpsDeviceSid: String? = null,
    val gpsTimestampSec: Long? = null,
    val gpsTzIn15Min: Int? = null,
    val gpsProtoType: Int? = null,
) {
    /**
     * Zone offset seconds from Mi timezone fields.
     * Prefer [tzIn15Min], then [gpsTzIn15Min]; missing → 0 (callers may apply device fallback).
     */
    fun zoneOffsetSeconds(): Int =
        resolveOffsetSecondsFromMiUnits(tzIn15Min ?: gpsTzIn15Min, fallbackSeconds = 0)
}

@Serializable
data class SportRecordEntry(
    val key: String? = null,
    val time: Long = 0,
    val value: String = "",
    val category: String? = null,
)

// --- P2: from Mi FitnessPersistKey + item models (decompiled APK) ---

/** Mi BloodPressureItem: systolic_pressure / diastolic_pressure (mmHg). */
@Serializable
data class BloodPressureSample(
    val timestamp: Long,
    val systolicMmhg: Int,
    val diastolicMmhg: Int,
    val pulseBpm: Int? = null,
    /** Mi `timezone` field: offset in units of 15 minutes. */
    val tzIn15Min: Int? = null,
)

/** Mi TemperatureItem: body_temperature / skin_temperature (°C). */
@Serializable
data class TemperatureSample(
    val timestamp: Long,
    val bodyCelsius: Double? = null,
    val skinCelsius: Double? = null,
    /** Mi `timezone` field: offset in units of 15 minutes. */
    val tzIn15Min: Int? = null,
)

/** Mi Vo2MaxItem: vo2_max (mL/kg/min as integer in APK). */
@Serializable
data class Vo2MaxSample(
    val timestamp: Long,
    val mlPerKgMin: Double,
    /** Mi `timezone` field: offset in units of 15 minutes. */
    val tzIn15Min: Int? = null,
)
