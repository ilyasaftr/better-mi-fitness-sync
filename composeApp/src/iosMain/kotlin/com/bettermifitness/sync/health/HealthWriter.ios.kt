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
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKCorrelation
import platform.HealthKit.HKCorrelationType
import platform.HealthKit.HKCorrelationTypeIdentifierBloodPressure
import platform.HealthKit.HKHealthStore
import platform.HealthKit.HKQuantity
import platform.HealthKit.HKQuantitySample
import platform.HealthKit.HKQuantityType
import platform.HealthKit.HKQuantityTypeIdentifierActiveEnergyBurned
import platform.HealthKit.HKQuantityTypeIdentifierBloodPressureDiastolic
import platform.HealthKit.HKQuantityTypeIdentifierBloodPressureSystolic
import platform.HealthKit.HKQuantityTypeIdentifierBodyFatPercentage
import platform.HealthKit.HKQuantityTypeIdentifierBodyMass
import platform.HealthKit.HKQuantityTypeIdentifierBodyTemperature
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierOxygenSaturation
import platform.HealthKit.HKQuantityTypeIdentifierRestingHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierStepCount
import platform.HealthKit.HKQuantityTypeIdentifierVO2Max
import platform.HealthKit.HKMetadataKeySyncIdentifier
import platform.HealthKit.HKMetadataKeySyncVersion
import platform.HealthKit.HKUnit
import platform.HealthKit.HKCategorySample
import platform.HealthKit.HKCategoryType
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepDeep
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepREM
import platform.HealthKit.HKCategoryValueSleepAnalysisAsleepCore
import platform.HealthKit.HKCategoryValueSleepAnalysisAwake
import platform.HealthKit.HKCategoryValueSleepAnalysisInBed
import platform.HealthKit.HKWorkout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalForeignApi::class)
actual class HealthWriter : HealthStore {
    private val healthStore = HKHealthStore()

    actual override suspend fun writeHeartRate(samples: List<HeartRateSample>) {
        val clean = HealthDataNormalizer.normalizeHeartRate(samples)
        if (clean.isEmpty()) return
        val hrType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate) ?: return
        val unit = HKUnit.unitFromString("count/min")

        val hkSamples = clean.map { sample ->
            val date = NSDate.dateWithTimeIntervalSince1970(sample.timestamp.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = hrType,
                quantity = HKQuantity.quantityWithUnit(unit, sample.bpm.toDouble()),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.heartRateSample(sample.timestamp),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(sample.timestamp, sample.bpm),
                ),
            )
        }

        saveSamples(hkSamples)
    }

    actual override suspend fun writeRestingHeartRate(samples: List<HeartRateSample>) {
        val clean = HealthDataNormalizer.normalizeHeartRate(samples)
        if (clean.isEmpty()) return
        val rhrType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRestingHeartRate) ?: return
        val unit = HKUnit.unitFromString("count/min")

        val hkSamples = clean.map { sample ->
            val date = NSDate.dateWithTimeIntervalSince1970(sample.timestamp.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = rhrType,
                quantity = HKQuantity.quantityWithUnit(unit, sample.bpm.toDouble()),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.restingHeartRate(sample.timestamp),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(sample.timestamp, sample.bpm),
                ),
            )
        }

        saveSamples(hkSamples)
    }

    actual override suspend fun writeSleep(sessions: List<SleepSession>) {
        val clean = HealthDataNormalizer.normalizeSleep(sessions)
        if (clean.isEmpty()) return
        val sleepType = HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis) ?: return

        val hkSamples = clean.flatMap { session ->
            val bedStartSec = if (session.inBedStart > 0) session.inBedStart else session.startTime
            val bedEndSec = if (session.inBedEnd > 0) session.inBedEnd else session.endTime
            val bedStart = NSDate.dateWithTimeIntervalSince1970(bedStartSec.toDouble())
            val bedEnd = NSDate.dateWithTimeIntervalSince1970(bedEndSec.toDouble())

            // "In Bed" sample spans the full bed period — Apple Health "Time in Bed".
            val inBed = HKCategorySample.categorySampleWithType(
                type = sleepType,
                value = HKCategoryValueSleepAnalysisInBed,
                startDate = bedStart,
                endDate = bedEnd,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.sleepInBed(bedStartSec),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(
                        bedStartSec,
                        bedEndSec,
                        session.startTime,
                    ),
                ),
            )

            val stageSamples = session.stages.map { stage ->
                val start = NSDate.dateWithTimeIntervalSince1970(stage.startTime.toDouble())
                val end = NSDate.dateWithTimeIntervalSince1970(stage.endTime.toDouble())
                val value = when (stage.stage) {
                    // Mi Fitness sleep states: 2=light/core, 3=deep, 4=REM, 5=awake
                    5 -> HKCategoryValueSleepAnalysisAwake
                    3 -> HKCategoryValueSleepAnalysisAsleepDeep
                    4 -> HKCategoryValueSleepAnalysisAsleepREM
                    2 -> HKCategoryValueSleepAnalysisAsleepCore
                    else -> HKCategoryValueSleepAnalysisAsleepCore
                }
                HKCategorySample.categorySampleWithType(
                    type = sleepType,
                    value = value,
                    startDate = start,
                    endDate = end,
                    metadata = mapOf<Any?, Any?>(
                        HKMetadataKeySyncIdentifier to HealthRecordIds.sleepStage(stage.startTime),
                        HKMetadataKeySyncVersion to HealthRecordIds.version(
                            stage.startTime,
                            stage.endTime,
                            stage.stage,
                        ),
                    ),
                )
            }

            listOf(inBed) + stageSamples
        }

        saveSamples(hkSamples)
    }

    actual override suspend fun writeSteps(records: List<StepsRecord>) {
        val clean = HealthDataNormalizer.normalizeSteps(records)
        if (clean.isEmpty()) return
        val stepsType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount) ?: return
        val unit = HKUnit.unitFromString("count")

        val hkSamples = clean.map { record ->
            val timestamp = record.date.toLong()
            val start = NSDate.dateWithTimeIntervalSince1970(timestamp.toDouble())
            val end = NSDate.dateWithTimeIntervalSince1970((timestamp + 3600 - 1).toDouble())
            // Same syncIdentifier + version on re-sync → HealthKit replaces, no double-count.
            val metadata = mapOf<Any?, Any?>(
                HKMetadataKeySyncIdentifier to HealthRecordIds.steps(timestamp),
                HKMetadataKeySyncVersion to HealthRecordIds.counterVersion(
                    record.steps.toLong(),
                    timestamp,
                ),
            )
            HKQuantitySample.quantitySampleWithType(
                quantityType = stepsType,
                quantity = HKQuantity.quantityWithUnit(unit, record.steps.toDouble()),
                startDate = start,
                endDate = end,
                metadata = metadata,
            )
        }

        saveSamples(hkSamples)
    }

    actual override suspend fun writeSpO2(samples: List<SpO2Sample>) {
        val clean = HealthDataNormalizer.normalizeSpO2(samples)
        if (clean.isEmpty()) return
        val spo2Type = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierOxygenSaturation) ?: return
        val unit = HKUnit.unitFromString("%")

        val hkSamples = clean.map { sample ->
            val date = NSDate.dateWithTimeIntervalSince1970(sample.timestamp.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = spo2Type,
                quantity = HKQuantity.quantityWithUnit(unit, sample.percentage.toDouble() / 100.0),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.spo2(sample.timestamp),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(
                        sample.timestamp,
                        sample.percentage,
                    ),
                ),
            )
        }

        saveSamples(hkSamples)
    }

    actual override suspend fun writeDistance(samples: List<DistanceSample>) {
        val clean = HealthDataNormalizer.normalizeDistance(samples)
        if (clean.isEmpty()) return
        val type = HKQuantityType.quantityTypeForIdentifier(
            HKQuantityTypeIdentifierDistanceWalkingRunning,
        ) ?: return
        val unit = HKUnit.unitFromString("m")
        val hkSamples = clean.map { s ->
            HKQuantitySample.quantitySampleWithType(
                quantityType = type,
                quantity = HKQuantity.quantityWithUnit(unit, s.meters),
                startDate = NSDate.dateWithTimeIntervalSince1970(s.startTime.toDouble()),
                endDate = NSDate.dateWithTimeIntervalSince1970(s.endTime.toDouble()),
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.distance(s.startTime),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(
                        s.startTime,
                        s.endTime,
                        s.meters,
                    ),
                ),
            )
        }
        saveSamples(hkSamples)
    }

    actual override suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>) {
        val clean = HealthDataNormalizer.normalizeActiveCalories(samples)
        if (clean.isEmpty()) return
        val type = HKQuantityType.quantityTypeForIdentifier(
            HKQuantityTypeIdentifierActiveEnergyBurned,
        ) ?: return
        val unit = HKUnit.unitFromString("kcal")
        val hkSamples = clean.map { s ->
            HKQuantitySample.quantitySampleWithType(
                quantityType = type,
                quantity = HKQuantity.quantityWithUnit(unit, s.kilocalories),
                startDate = NSDate.dateWithTimeIntervalSince1970(s.startTime.toDouble()),
                endDate = NSDate.dateWithTimeIntervalSince1970(s.endTime.toDouble()),
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.activeCalories(s.startTime),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(
                        s.startTime,
                        s.endTime,
                        s.kilocalories,
                    ),
                ),
            )
        }
        saveSamples(hkSamples)
    }

    actual override suspend fun writeWeight(measurements: List<WeightMeasurement>) {
        val clean = HealthDataNormalizer.normalizeWeight(measurements)
        if (clean.isEmpty()) return
        val massType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierBodyMass)
            ?: return
        val kg = HKUnit.unitFromString("kg")
        val massSamples = clean.map { m ->
            val date = NSDate.dateWithTimeIntervalSince1970(m.timestamp.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = massType,
                quantity = HKQuantity.quantityWithUnit(kg, m.weightKg),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.weight(m.timestamp),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(m.timestamp, m.weightKg),
                ),
            )
        }
        saveSamples(massSamples)

        val fatType = HKQuantityType.quantityTypeForIdentifier(
            HKQuantityTypeIdentifierBodyFatPercentage,
        ) ?: return
        val percent = HKUnit.unitFromString("%")
        val fatSamples = clean.mapNotNull { m ->
            val fat = m.bodyFatPercent ?: return@mapNotNull null
            val date = NSDate.dateWithTimeIntervalSince1970(m.timestamp.toDouble())
            // bodyFatPercentage uses percent unit as 0–1 fraction.
            HKQuantitySample.quantitySampleWithType(
                quantityType = fatType,
                quantity = HKQuantity.quantityWithUnit(percent, fat / 100.0),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.bodyFat(m.timestamp),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(m.timestamp, fat),
                ),
            )
        }
        if (fatSamples.isNotEmpty()) saveSamples(fatSamples)
    }

    actual override suspend fun writeWorkouts(sessions: List<WorkoutSession>) {
        val clean = HealthDataNormalizer.normalizeWorkouts(sessions)
        if (clean.isEmpty()) return
        val kcal = HKUnit.unitFromString("kcal")
        val meters = HKUnit.unitFromString("m")
        val workouts = clean.map { w ->
            val mapping = SportTypeMapper.map(w.activityType)
            HKWorkout.workoutWithActivityType(
                workoutActivityType = mapping.healthKitType.toULong(),
                startDate = NSDate.dateWithTimeIntervalSince1970(w.startTime.toDouble()),
                endDate = NSDate.dateWithTimeIntervalSince1970(w.endTime.toDouble()),
                workoutEvents = null,
                totalEnergyBurned = w.caloriesKcal?.let {
                    HKQuantity.quantityWithUnit(kcal, it)
                },
                totalDistance = w.distanceMeters?.let {
                    HKQuantity.quantityWithUnit(meters, it)
                },
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.workout(w.startTime),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(
                        w.startTime,
                        w.endTime,
                        mapping.title,
                        w.distanceMeters,
                        w.caloriesKcal,
                    ),
                ),
            )
        }
        saveSamples(workouts)
    }

    actual override suspend fun writeBloodPressure(samples: List<BloodPressureSample>) {
        val clean = HealthDataNormalizer.normalizeBloodPressure(samples)
        if (clean.isEmpty()) return
        val corrType = HKCorrelationType.correlationTypeForIdentifier(
            HKCorrelationTypeIdentifierBloodPressure,
        ) ?: return
        val sysType = HKQuantityType.quantityTypeForIdentifier(
            HKQuantityTypeIdentifierBloodPressureSystolic,
        ) ?: return
        val diaType = HKQuantityType.quantityTypeForIdentifier(
            HKQuantityTypeIdentifierBloodPressureDiastolic,
        ) ?: return
        val mmHg = HKUnit.unitFromString("mmHg")
        val objects = clean.map { s ->
            val date = NSDate.dateWithTimeIntervalSince1970(s.timestamp.toDouble())
            val meta = mapOf<Any?, Any?>(
                HKMetadataKeySyncIdentifier to HealthRecordIds.bloodPressure(s.timestamp),
                HKMetadataKeySyncVersion to HealthRecordIds.version(
                    s.timestamp,
                    s.systolicMmhg,
                    s.diastolicMmhg,
                ),
            )
            val sys = HKQuantitySample.quantitySampleWithType(
                quantityType = sysType,
                quantity = HKQuantity.quantityWithUnit(mmHg, s.systolicMmhg.toDouble()),
                startDate = date,
                endDate = date,
                metadata = meta,
            )
            val dia = HKQuantitySample.quantitySampleWithType(
                quantityType = diaType,
                quantity = HKQuantity.quantityWithUnit(mmHg, s.diastolicMmhg.toDouble()),
                startDate = date,
                endDate = date,
                metadata = meta,
            )
            HKCorrelation.correlationWithType(
                correlationType = corrType,
                startDate = date,
                endDate = date,
                objects = setOf(sys, dia),
                metadata = meta,
            )
        }
        saveSamples(objects)
    }

    actual override suspend fun writeTemperature(samples: List<TemperatureSample>) {
        val clean = HealthDataNormalizer.normalizeTemperature(samples)
        if (clean.isEmpty()) return
        val type = HKQuantityType.quantityTypeForIdentifier(
            HKQuantityTypeIdentifierBodyTemperature,
        ) ?: return
        val celsius = HKUnit.unitFromString("degC")
        // Prefer body °C; fall back to skin (still better than dropping data).
        val hkSamples = clean.mapNotNull { s ->
            val value = s.bodyCelsius ?: s.skinCelsius ?: return@mapNotNull null
            val date = NSDate.dateWithTimeIntervalSince1970(s.timestamp.toDouble())
            val id = if (s.bodyCelsius != null) {
                HealthRecordIds.bodyTemperature(s.timestamp)
            } else {
                HealthRecordIds.skinTemperature(s.timestamp)
            }
            HKQuantitySample.quantitySampleWithType(
                quantityType = type,
                quantity = HKQuantity.quantityWithUnit(celsius, value),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to id,
                    HKMetadataKeySyncVersion to HealthRecordIds.version(s.timestamp, value),
                ),
            )
        }
        saveSamples(hkSamples)
    }

    actual override suspend fun writeVo2Max(samples: List<Vo2MaxSample>) {
        val clean = HealthDataNormalizer.normalizeVo2Max(samples)
        if (clean.isEmpty()) return
        val type = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierVO2Max) ?: return
        val unit = HKUnit.unitFromString("ml/kg*min")
        val hkSamples = clean.map { s ->
            val date = NSDate.dateWithTimeIntervalSince1970(s.timestamp.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = type,
                quantity = HKQuantity.quantityWithUnit(unit, s.mlPerKgMin),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to HealthRecordIds.vo2Max(s.timestamp),
                    HKMetadataKeySyncVersion to HealthRecordIds.version(s.timestamp, s.mlPerKgMin),
                ),
            )
        }
        saveSamples(hkSamples)
    }

    actual override suspend fun isAvailable(): Boolean {
        return HKHealthStore.isHealthDataAvailable()
    }

    actual override suspend fun hasWritePermissions(): Boolean {
        if (!isAvailable()) return false
        // HealthKit only exposes share-authorization status (not a full ACL dump).
        // Treat as granted when every type we care about is SharingAuthorized.
        val types = shareTypes()
        if (types.isEmpty()) return false
        return types.all { type ->
            healthStore.authorizationStatusForType(type) ==
                platform.HealthKit.HKAuthorizationStatusSharingAuthorized
        }
    }

    actual override suspend fun requestPermissions() {
        // Share set must be sample types only. Including HKCorrelationType (e.g. blood pressure)
        // makes HealthKit throw NSException via _throwIfAuthorizationDisallowedForSharing —
        // that aborts the process and is not catchable as a Kotlin Exception.
        val types = shareTypes()
        if (types.isEmpty()) {
            throw Exception("No HealthKit types available to request")
        }

        suspendCoroutine { continuation ->
            healthStore.requestAuthorizationToShareTypes(
                typesToShare = types,
                readTypes = emptySet<platform.HealthKit.HKObjectType>(),
            ) { success, error ->
                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        Exception(error?.localizedDescription ?: "Permission denied")
                    )
                }
            }
        }
    }

    actual override fun healthServiceName(): String = "Apple Health"

    actual override suspend fun availabilityHint(): String? {
        return when {
            !HKHealthStore.isHealthDataAvailable() ->
                "Apple Health isn’t available on this device."
            !hasWritePermissions() ->
                "Please allow this app to save data in Apple Health. Tap Sync to review access."
            else -> null
        }
    }

    actual override fun openHealthService() {
        // HealthKit has no public install/settings deep-link; permissions are system dialogs.
    }

    /**
     * Types we may *write*. Blood pressure is authorized via systolic + diastolic quantity
     * types only — not [HKCorrelationTypeIdentifierBloodPressure] (that crashes auth).
     */
    private fun shareTypes(): Set<platform.HealthKit.HKSampleType> = setOfNotNull(
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
        HKCategoryType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis),
        platform.HealthKit.HKObjectType.workoutType(),
    )

    private suspend fun saveSamples(samples: List<Any>) {
        if (samples.isEmpty()) return
        suspendCoroutine { continuation ->
            @Suppress("UNCHECKED_CAST")
            healthStore.saveObjects(samples as List<platform.HealthKit.HKObject>) { success, error ->
                if (success) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        Exception(error?.localizedDescription ?: "Failed to save health data")
                    )
                }
            }
        }
    }
}
