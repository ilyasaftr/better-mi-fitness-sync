package com.bettermifitness.sync.health

import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.TemperatureSample
import com.bettermifitness.sync.data.api.Vo2MaxSample
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutSession

/**
 * ISP: write-only surface for metric samples (used by [com.bettermifitness.sync.data.repository.HealthRepository]).
 */
interface HealthSampleWriter {
    suspend fun writeHeartRate(samples: List<HeartRateSample>)
    suspend fun writeRestingHeartRate(samples: List<HeartRateSample>)
    suspend fun writeSleep(sessions: List<SleepSession>)
    suspend fun writeSteps(records: List<StepsRecord>)
    suspend fun writeDistance(samples: List<DistanceSample>)
    suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>)
    suspend fun writeSpO2(samples: List<SpO2Sample>)
    suspend fun writeWeight(measurements: List<WeightMeasurement>)
    suspend fun writeWorkouts(sessions: List<WorkoutSession>)
    suspend fun writeBloodPressure(samples: List<BloodPressureSample>)
    suspend fun writeTemperature(samples: List<TemperatureSample>)
    suspend fun writeVo2Max(samples: List<Vo2MaxSample>)
}

/**
 * Best-effort readiness of the platform health store for writing our metrics.
 */
data class HealthReadiness(
    val available: Boolean,
    val permissionsGranted: Boolean,
    val serviceName: String,
    val hint: String?,
) {
    val isReady: Boolean get() = available && permissionsGranted

    val statusTitle: String
        get() = when {
            !available -> "Health app missing"
            !permissionsGranted -> "Allow access to Health"
            else -> "Health is ready"
        }

    val statusDetail: String
        get() = when {
            !available ->
                hint
                    ?: "Install or open $serviceName on this phone so we can sync."
            !permissionsGranted ->
                "Allow access to save steps, heart rate, sleep, and more in $serviceName."
            else -> "You’re ready to sync."
        }
}

/**
 * ISP: availability / branding / deep-link into the platform health store.
 */
interface HealthAvailability {
    suspend fun isAvailable(): Boolean
    suspend fun availabilityHint(): String?
    fun healthServiceName(): String
    fun openHealthService()

    /**
     * Best-effort check that write permissions for our metrics are granted.
     * On iOS this is limited by HealthKit privacy (may be false until first grant).
     */
    suspend fun hasWritePermissions(): Boolean

    suspend fun readiness(): HealthReadiness {
        val available = isAvailable()
        val granted = if (available) hasWritePermissions() else false
        return HealthReadiness(
            available = available,
            permissionsGranted = granted,
            serviceName = healthServiceName(),
            hint = availabilityHint(),
        )
    }
}

/**
 * ISP: permission prompts (may show UI on foreground only).
 */
interface HealthPermissionRequester {
    suspend fun requestPermissions()
}

/** Full platform health façade (writes + availability + permissions). */
interface HealthStore : HealthSampleWriter, HealthAvailability, HealthPermissionRequester
