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
 * Platform health store (Health Connect / HealthKit).
 * Implements [HealthStore] so callers can depend on the narrow ISP surface they need.
 */
expect class HealthWriter : HealthStore {
    override suspend fun writeHeartRate(samples: List<HeartRateSample>)
    override suspend fun writeRestingHeartRate(samples: List<HeartRateSample>)
    override suspend fun writeSleep(sessions: List<SleepSession>)
    override suspend fun writeSteps(records: List<StepsRecord>)
    override suspend fun writeDistance(samples: List<DistanceSample>)
    override suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>)
    override suspend fun writeSpO2(samples: List<SpO2Sample>)
    override suspend fun writeWeight(measurements: List<WeightMeasurement>)
    override suspend fun writeWorkouts(sessions: List<WorkoutSession>)
    override suspend fun writeBloodPressure(samples: List<BloodPressureSample>)
    override suspend fun writeTemperature(samples: List<TemperatureSample>)
    override suspend fun writeVo2Max(samples: List<Vo2MaxSample>)
    override suspend fun isAvailable(): Boolean
    override suspend fun hasWritePermissions(): Boolean
    override suspend fun requestPermissions()
    override fun healthServiceName(): String
    override suspend fun availabilityHint(): String?
    override fun openHealthService()
}
