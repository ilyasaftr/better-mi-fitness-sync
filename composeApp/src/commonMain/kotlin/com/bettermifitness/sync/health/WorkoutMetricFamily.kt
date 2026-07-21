package com.bettermifitness.sync.health

/**
 * Maps Mi activity strings to the family of **public** HealthKit / Health Connect
 * quantity types we may write for that sport.
 *
 * Cadence (Apple HealthKit, iOS 17+ headers):
 * - There is **no** `HKQuantityTypeIdentifierRunningCadence`.
 * - Official cadence type is only
 *   [HKQuantityTypeIdentifierCyclingCadence] (`count/min`) for **cycling**.
 * - Running cadence therefore stays in workout notes on iOS; Android uses
 *   [StepsCadenceRecord] for walk/run step rate.
 *
 * Sources: `HKTypeIdentifiers.h` (iOS SDK), Apple Developer docs for
 * RunningSpeed / Stride / Power / GCT / VO, WalkingSpeed / StepLength,
 * CyclingCadence / Speed / Power, DistanceCycling / Swimming.
 */
enum class WorkoutMetricFamily {
    RUNNING,
    WALKING,
    CYCLING,
    SWIMMING,
    OTHER,
}

object WorkoutMetricFamilies {

    fun classify(activityType: String): WorkoutMetricFamily {
        val a = activityType.lowercase()
        return when {
            a.contains("swim") || a.contains("pool") || a.contains("open_water") ->
                WorkoutMetricFamily.SWIMMING
            a.contains("cycl") || a.contains("riding") || a.contains("bike") ||
                a.contains("spinning") || a.contains("biking") ->
                WorkoutMetricFamily.CYCLING
            a.contains("walk") || a.contains("hik") || a.contains("stroll") ->
                WorkoutMetricFamily.WALKING
            a.contains("run") || a.contains("jog") || a.contains("treadmill") ||
                a.contains("trail") || a.contains("marathon") ->
                WorkoutMetricFamily.RUNNING
            else -> WorkoutMetricFamily.OTHER
        }
    }
}
