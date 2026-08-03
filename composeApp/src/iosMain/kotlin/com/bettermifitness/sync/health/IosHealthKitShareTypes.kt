package com.bettermifitness.sync.health

import kotlinx.cinterop.ExperimentalForeignApi
import platform.HealthKit.HKCategoryType
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKObjectType
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBloodPressureDiastolic
import platform.HealthKit.HKQuantityTypeIdentifierBloodPressureSystolic
import platform.HealthKit.HKQuantityTypeIdentifierBodyFatPercentage
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierBodyTemperature
import platform.HealthKit.HKQuantityTypeIdentifierCyclingCadence
import platform.HealthKit.HKQuantityTypeIdentifierCyclingPower
import platform.HealthKit.HKQuantityTypeIdentifierCyclingSpeed
import platform.HealthKit.HKQuantityTypeIdentifierDistanceCycling
import platform.HealthKit.HKQuantityTypeIdentifierDistanceSwimming
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierFlightsClimbed
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierHeartRateRecoveryOneMinute
import platform.HealthKit.HKQuantityTypeIdentifierHeartRateVariabilitySDNN
import platform.HealthKit.HKQuantityTypeIdentifierOxygenSaturation
import platform.HealthKit.HKQuantityTypeIdentifierRestingHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierRunningGroundContactTime
import platform.HealthKit.HKQuantityTypeIdentifierRunningPower
import platform.HealthKit.HKQuantityTypeIdentifierRunningSpeed
import platform.HealthKit.HKQuantityTypeIdentifierRunningStrideLength
import platform.HealthKit.HKQuantityTypeIdentifierRunningVerticalOscillation
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuantityTypeIdentifierSwimmingStrokeCount
import platform.HealthKit.HKQuantityTypeIdentifierVO2Max
import platform.HealthKit.HKQuantityTypeIdentifierWalkingSpeed
import platform.HealthKit.HKQuantityTypeIdentifierWalkingStepLength
import platform.HealthKit.HKSampleType
import platform.HealthKit.HKSeriesType

/**
 * HealthKit share authorization sets.
 * Blood pressure uses systolic+diastolic only (not HKCorrelation — that aborts auth).
 */
@OptIn(ExperimentalForeignApi::class)
object IosHealthKitShareTypes {

    fun core(): Set<HKSampleType> = setOfNotNull(
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRestingHeartRate),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceWalkingRunning),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierActiveEnergyBurned),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierOxygenSaturation),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyFatPercentage),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyTemperature),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureSystolic),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBloodPressureDiastolic),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierVO2Max),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRateVariabilitySDNN),
        HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis),
        HKObjectType.workoutType(),
        HKSeriesType.workoutRouteType(),
    )

    /** Optional sport metrics (Apple public identifiers only). */
    fun activityMetrics(): Set<HKSampleType> = setOfNotNull(
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningSpeed),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningStrideLength),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningPower),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningGroundContactTime),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningVerticalOscillation),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingSpeed),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingStepLength),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingCadence),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingSpeed),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingPower),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceCycling),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceSwimming),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierSwimmingStrokeCount),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRateRecoveryOneMinute),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierFlightsClimbed),
    )

    fun allShareable(): Set<HKSampleType> = core() + activityMetrics()
}
