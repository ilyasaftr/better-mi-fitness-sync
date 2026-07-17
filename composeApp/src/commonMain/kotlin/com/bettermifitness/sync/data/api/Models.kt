package com.bettermifitness.sync.data.api

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
)

@Serializable
data class SleepStage(
    val startTime: Long,
    val endTime: Long,
    val stage: Int, // 2=light/core, 3=deep, 4=REM, 5=awake (Mi codes)
)

// --- Steps ---

@Serializable
data class StepsRecord(
    val date: String,
    val steps: Int,
    val distance: Double = 0.0,
    val calories: Double = 0.0,
)

// --- SpO2 ---

@Serializable
data class SpO2Sample(
    val timestamp: Long,
    val percentage: Int,
)

// --- Distance (meters), interval start = epoch seconds ---

@Serializable
data class DistanceSample(
    val startTime: Long,
    val endTime: Long,
    val meters: Double,
)

// --- Active energy (kcal) ---

@Serializable
data class ActiveCaloriesSample(
    val startTime: Long,
    val endTime: Long,
    val kilocalories: Double,
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
)

// --- Workouts ---

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
    val totalSteps: Int? = null,
)

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
)

/** Mi TemperatureItem: body_temperature / skin_temperature (°C). */
@Serializable
data class TemperatureSample(
    val timestamp: Long,
    val bodyCelsius: Double? = null,
    val skinCelsius: Double? = null,
)

/** Mi Vo2MaxItem: vo2_max (mL/kg/min as integer in APK). */
@Serializable
data class Vo2MaxSample(
    val timestamp: Long,
    val mlPerKgMin: Double,
)
