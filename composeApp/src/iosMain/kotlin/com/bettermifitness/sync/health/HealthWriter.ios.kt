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
import platform.HealthKit.HKQuantityTypeIdentifierCyclingCadence
import platform.HealthKit.HKQuantityTypeIdentifierCyclingPower
import platform.HealthKit.HKQuantityTypeIdentifierCyclingSpeed
import platform.HealthKit.HKQuantityTypeIdentifierDistanceCycling
import platform.HealthKit.HKQuantityTypeIdentifierDistanceSwimming
import platform.HealthKit.HKQuantityTypeIdentifierDistanceWalkingRunning
import platform.HealthKit.HKQuantityTypeIdentifierFlightsClimbed
import platform.HealthKit.HKQuantityTypeIdentifierHeartRate
import platform.HealthKit.HKQuantityTypeIdentifierHeartRateRecoveryOneMinute
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
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.HealthKit.HKSample
import platform.HealthKit.HKWorkout
import platform.HealthKit.HKWorkoutBuilder
import platform.HealthKit.HKWorkoutConfiguration
import platform.HealthKit.HKWorkoutRouteBuilder
import platform.HealthKit.HKWorkoutSessionLocationTypeIndoor
import platform.HealthKit.HKWorkoutSessionLocationTypeOutdoor
import platform.Foundation.NSLog
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
        for (w in clean) {
            try {
                writeOneWorkoutWithBuilder(w)
            } catch (e: Exception) {
                NSLog("BetterMi: workout builder failed (${w.startTime}): ${e.message}; legacy fallback")
                try {
                    writeOneWorkoutLegacy(w)
                } catch (e2: Exception) {
                    NSLog("BetterMi: workout write failed (${w.startTime}): ${e2.message}")
                }
            }
        }
    }

    /**
     * Historical import via [HKWorkoutBuilder] (Apple's supported path).
     *
     * Per WWDC22 / HKWorkoutBuilder docs, workout statistics and Health "Related Samples"
     * for Running Speed / Stride / Power / GCT / VO are only associated when samples are
     * **added to the builder** — not merely saved with overlapping timestamps.
     * Standalone saves of Running* samples succeed but do not show under the workout.
     */
    private suspend fun writeOneWorkoutWithBuilder(w: WorkoutSession) {
        val mapping = SportTypeMapper.map(w.activityType)
        val version = workoutVersion(w, mapping)
        val meta = workoutMetadata(w, version)
        val start = NSDate.dateWithTimeIntervalSince1970(w.startTime.toDouble())
        val end = NSDate.dateWithTimeIntervalSince1970(w.endTime.toDouble())

        val config = HKWorkoutConfiguration()
        config.activityType = mapping.healthKitType.toULong()
        config.locationType = workoutLocationType(w.activityType)

        val builder = HKWorkoutBuilder(
            healthStore = healthStore,
            configuration = config,
            device = null,
        )

        suspendCoroutine { cont ->
            builder.beginCollectionWithStartDate(start) { ok, error ->
                if (ok) cont.resume(Unit)
                else cont.resumeWithException(
                    Exception(error?.localizedDescription ?: "beginCollection failed"),
                )
            }
        }

        // Metadata (sync id/version, notes, HR summaries)
        @Suppress("UNCHECKED_CAST")
        suspendCoroutine { cont ->
            builder.addMetadata(meta as Map<Any?, *>) { ok, error ->
                if (ok) cont.resume(Unit)
                else cont.resumeWithException(
                    Exception(error?.localizedDescription ?: "addMetadata failed"),
                )
            }
        }

        // Core samples first (energy / distance / HR / steps), then form metrics per-type.
        // A single unauthorized form type must NOT fail the whole builder (that used to drop
        // us into legacy path → Pace/HR only, grey Stride in Fitness).
        val core = buildCoreWorkoutSamples(w, version)
        val form = buildSportFormSamples(w, version)
        addSamplesToBuilder(builder, core)
        var formAdded = 0
        form.groupBy { it.quantityType.identifier }.forEach { (typeId, samples) ->
            try {
                addSamplesToBuilder(builder, samples)
                formAdded += samples.size
            } catch (e: Exception) {
                NSLog("BetterMi: skip form type $typeId: ${e.message}")
            }
        }
        if (form.isNotEmpty()) {
            NSLog(
                "BetterMi: workout ${w.startTime} builder core=${core.size} " +
                    "form=$formAdded/${form.size} stride=${w.strideMetersSeries.size}",
            )
        }

        suspendCoroutine { cont ->
            builder.endCollectionWithEndDate(end) { ok, error ->
                if (ok) cont.resume(Unit)
                else cont.resumeWithException(
                    Exception(error?.localizedDescription ?: "endCollection failed"),
                )
            }
        }

        val workout = suspendCoroutine { cont ->
            builder.finishWorkoutWithCompletion { finished, error ->
                if (finished != null) cont.resume(finished)
                else cont.resumeWithException(
                    Exception(error?.localizedDescription ?: "finishWorkout failed"),
                )
            }
        }

        if (w.route.size >= 2) {
            try {
                saveWorkoutRoute(workout, w.route)
            } catch (e: Exception) {
                NSLog("BetterMi: route failed: ${e.message}")
            }
        }
        // Recover HR is after end — not part of the workout collection window
        writeRecoverHeartRate(w, version)
    }

    /** Legacy HKWorkout factory — energy/distance totals only; form samples not associated. */
    private suspend fun writeOneWorkoutLegacy(w: WorkoutSession) {
        val mapping = SportTypeMapper.map(w.activityType)
        val version = workoutVersion(w, mapping)
        val meta = workoutMetadata(w, version)
        val kcal = HKUnit.unitFromString("kcal")
        val meters = HKUnit.unitFromString("m")
        val workout = HKWorkout.workoutWithActivityType(
            workoutActivityType = mapping.healthKitType.toULong(),
            startDate = NSDate.dateWithTimeIntervalSince1970(w.startTime.toDouble()),
            endDate = NSDate.dateWithTimeIntervalSince1970(w.endTime.toDouble()),
            workoutEvents = null,
            totalEnergyBurned = w.caloriesKcal?.let { HKQuantity.quantityWithUnit(kcal, it) },
            totalDistance = w.distanceMeters?.let { HKQuantity.quantityWithUnit(meters, it) },
            metadata = meta,
        )
        saveSamples(listOf(workout))
        if (w.route.size >= 2) {
            try {
                saveWorkoutRoute(workout, w.route)
            } catch (_: Exception) { /* best-effort */ }
        }
        // Still save samples (may appear as standalone Browse data, not Related Samples)
        val samples = buildAssociatedWorkoutSamples(w, version)
        samples.chunked(200).forEach { chunk ->
            try {
                saveSamples(chunk)
            } catch (_: Exception) { /* optional */ }
        }
        writeRecoverHeartRate(w, version)
    }

    private fun workoutVersion(
        w: WorkoutSession,
        mapping: SportTypeMapper.Mapping,
    ): Long = HealthRecordIds.version(
        w.startTime, w.endTime, mapping.title,
        w.distanceMeters, w.caloriesKcal, w.route.size, w.heartRateSeries.size,
        w.avgPaceSecPerKm, w.avgCadenceSpm, w.trainLoad,
        w.speedSeries.size, w.cadenceSeries.size, w.strideMetersSeries.size,
        w.powerWattsSeries.size, w.groundContactMsSeries.size,
        w.verticalOscillationCmSeries.size,
        // Bump forces new sample sync-ids association for form metrics (stride etc.)
        "builder-v3-stride",
    )

    private fun workoutMetadata(w: WorkoutSession, version: Long): MutableMap<Any?, Any?> {
        val meta = mutableMapOf<Any?, Any?>(
            HKMetadataKeySyncIdentifier to HealthRecordIds.workout(w.startTime),
            HKMetadataKeySyncVersion to version,
        )
        w.avgHeartRateBpm?.let { meta["MiAvgHeartRate"] = it }
        w.maxHeartRateBpm?.let { meta["MiMaxHeartRate"] = it }
        w.minHeartRateBpm?.let { meta["MiMinHeartRate"] = it }
        buildIosWorkoutNotes(w)?.let { meta["MiFitnessNotes"] = it }
        return meta
    }

    private fun workoutLocationType(activityType: String): Long {
        val a = activityType.lowercase()
        return when {
            a.contains("indoor") || a.contains("treadmill") || a.contains("spinning") ->
                HKWorkoutSessionLocationTypeIndoor
            a.contains("outdoor") || a.contains("running") || a.contains("walking") ||
                a.contains("riding") || a.contains("cycling") || a.contains("hiking") ->
                HKWorkoutSessionLocationTypeOutdoor
            else -> HKWorkoutSessionLocationTypeOutdoor
        }
    }

    private suspend fun addSamplesToBuilder(
        builder: HKWorkoutBuilder,
        samples: List<HKQuantitySample>,
    ) {
        if (samples.isEmpty()) return
        samples.chunked(100).forEach { chunk ->
            suspendCoroutine { cont ->
                @Suppress("UNCHECKED_CAST")
                builder.addSamples(chunk as List<HKSample>) { ok, error ->
                    if (ok) cont.resume(Unit)
                    else cont.resumeWithException(
                        Exception(error?.localizedDescription ?: "addSamples failed"),
                    )
                }
            }
        }
    }

    /** Energy / distance / HR / steps — required for a usable workout. */
    private fun buildCoreWorkoutSamples(
        w: WorkoutSession,
        version: Long,
    ): List<HKQuantitySample> {
        val out = ArrayList<HKQuantitySample>()
        val idPrefix = HealthRecordIds.workout(w.startTime)
        val start = NSDate.dateWithTimeIntervalSince1970(w.startTime.toDouble())
        val end = NSDate.dateWithTimeIntervalSince1970(w.endTime.toDouble())
        val family = WorkoutMetricFamilies.classify(w.activityType)
        val bpm = HKUnit.unitFromString("count/min")

        w.caloriesKcal?.takeIf { it > 0 }?.let { cals ->
            singleQuantitySample(
                typeId = HKQuantityTypeIdentifierActiveEnergyBurned
                    ?: "HKQuantityTypeIdentifierActiveEnergyBurned",
                unitStr = "kcal",
                value = cals,
                start = start,
                end = end,
                syncId = "$idPrefix:v3:energy",
                version = version,
            )?.let { out += it }
        }

        w.distanceMeters?.takeIf { it > 0 }?.let { dist ->
            val distTypeId = when (family) {
                WorkoutMetricFamily.CYCLING ->
                    HKQuantityTypeIdentifierDistanceCycling
                        ?: "HKQuantityTypeIdentifierDistanceCycling"
                WorkoutMetricFamily.SWIMMING ->
                    HKQuantityTypeIdentifierDistanceSwimming
                        ?: "HKQuantityTypeIdentifierDistanceSwimming"
                else ->
                    HKQuantityTypeIdentifierDistanceWalkingRunning
                        ?: "HKQuantityTypeIdentifierDistanceWalkingRunning"
            }
            singleQuantitySample(
                typeId = distTypeId,
                unitStr = "m",
                value = dist,
                start = start,
                end = end,
                syncId = "$idPrefix:v3:dist",
                version = version,
            )?.let { out += it }
        }

        val hrType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
        if (hrType != null) {
            for (s in w.heartRateSeries) {
                val date = NSDate.dateWithTimeIntervalSince1970(s.timeSec.toDouble())
                out += HKQuantitySample.quantitySampleWithType(
                    quantityType = hrType,
                    quantity = HKQuantity.quantityWithUnit(bpm, s.value),
                    startDate = date,
                    endDate = date,
                    metadata = mapOf<Any?, Any?>(
                        HKMetadataKeySyncIdentifier to "$idPrefix:v3:hr:${s.timeSec}",
                        HKMetadataKeySyncVersion to version,
                    ),
                )
            }
        }

        if (family == WorkoutMetricFamily.RUNNING ||
            family == WorkoutMetricFamily.WALKING ||
            family == WorkoutMetricFamily.OTHER
        ) {
            val stepType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierStepCount)
            if (stepType != null && (w.totalSteps ?: 0) > 0) {
                out += HKQuantitySample.quantitySampleWithType(
                    quantityType = stepType,
                    quantity = HKQuantity.quantityWithUnit(
                        HKUnit.unitFromString("count"),
                        w.totalSteps!!.toDouble(),
                    ),
                    startDate = start,
                    endDate = end,
                    metadata = mapOf<Any?, Any?>(
                        HKMetadataKeySyncIdentifier to "$idPrefix:v3:steps",
                        HKMetadataKeySyncVersion to version,
                    ),
                )
            }
        }
        return out
    }

    /**
     * Sport form metrics (Apple public types only).
     * Running stride → [HKQuantityTypeIdentifierRunningStrideLength] (m).
     * No RunningCadence exists in HealthKit.
     */
    private fun buildSportFormSamples(
        w: WorkoutSession,
        version: Long,
    ): List<HKQuantitySample> {
        val out = ArrayList<HKQuantitySample>()
        val idPrefix = HealthRecordIds.workout(w.startTime)
        val start = NSDate.dateWithTimeIntervalSince1970(w.startTime.toDouble())
        val end = NSDate.dateWithTimeIntervalSince1970(w.endTime.toDouble())
        val family = WorkoutMetricFamilies.classify(w.activityType)

        // Ensure stride series exists when Mi only sent avg_stride (enrich may already have)
        val strideSeries = w.strideMetersSeries.ifEmpty {
            w.avgStrideCm?.takeIf { it in 20.0..250.0 }?.let {
                WorkoutRunningMetrics.flatSeries(w.startTime, w.endTime, it / 100.0)
            } ?: emptyList()
        }
        val speedSeries = w.speedSeries.ifEmpty {
            val mps = w.maxSpeedMps?.takeIf { it > 0.3 }
                ?: w.avgPaceSecPerKm?.takeIf { it in 60.0..3600.0 }?.let { 1000.0 / it }
            if (mps != null) WorkoutRunningMetrics.flatSeries(w.startTime, w.endTime, mps)
            else emptyList()
        }

        when (family) {
            WorkoutMetricFamily.RUNNING -> {
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierRunningSpeed
                        ?: "HKQuantityTypeIdentifierRunningSpeed",
                    unitStr = "m/s",
                    samples = speedSeries,
                    idPrefix = "$idPrefix:v3:rspeed",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierRunningStrideLength
                        ?: "HKQuantityTypeIdentifierRunningStrideLength",
                    unitStr = "m",
                    samples = strideSeries,
                    idPrefix = "$idPrefix:v3:stride",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierRunningPower
                        ?: "HKQuantityTypeIdentifierRunningPower",
                    unitStr = "W",
                    samples = w.powerWattsSeries,
                    idPrefix = "$idPrefix:v3:rpow",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierRunningGroundContactTime
                        ?: "HKQuantityTypeIdentifierRunningGroundContactTime",
                    unitStr = "ms",
                    samples = w.groundContactMsSeries,
                    idPrefix = "$idPrefix:v3:gct",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierRunningVerticalOscillation
                        ?: "HKQuantityTypeIdentifierRunningVerticalOscillation",
                    unitStr = "cm",
                    samples = w.verticalOscillationCmSeries,
                    idPrefix = "$idPrefix:v3:vo",
                    version = version,
                )
            }
            WorkoutMetricFamily.WALKING -> {
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierWalkingSpeed
                        ?: "HKQuantityTypeIdentifierWalkingSpeed",
                    unitStr = "m/s",
                    samples = speedSeries,
                    idPrefix = "$idPrefix:v3:wspeed",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierWalkingStepLength
                        ?: "HKQuantityTypeIdentifierWalkingStepLength",
                    unitStr = "m",
                    samples = strideSeries,
                    idPrefix = "$idPrefix:v3:wstep",
                    version = version,
                )
            }
            WorkoutMetricFamily.CYCLING -> {
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierCyclingCadence
                        ?: "HKQuantityTypeIdentifierCyclingCadence",
                    unitStr = "count/min",
                    samples = w.cadenceSeries,
                    idPrefix = "$idPrefix:v3:ccad",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierCyclingSpeed
                        ?: "HKQuantityTypeIdentifierCyclingSpeed",
                    unitStr = "m/s",
                    samples = speedSeries,
                    idPrefix = "$idPrefix:v3:cspd",
                    version = version,
                )
                out += formSeries(
                    typeId = HKQuantityTypeIdentifierCyclingPower
                        ?: "HKQuantityTypeIdentifierCyclingPower",
                    unitStr = "W",
                    samples = w.powerWattsSeries,
                    idPrefix = "$idPrefix:v3:cpow",
                    version = version,
                )
            }
            WorkoutMetricFamily.SWIMMING, WorkoutMetricFamily.OTHER -> Unit
        }

        w.vo2Max?.takeIf { it in 10.0..100.0 }?.let { vo2 ->
            singleQuantitySample(
                typeId = HKQuantityTypeIdentifierVO2Max
                    ?: "HKQuantityTypeIdentifierVO2Max",
                unitStr = "ml/(kg*min)",
                value = vo2,
                start = end,
                end = end,
                syncId = "$idPrefix:v3:vo2",
                version = version,
            )?.let { out += it }
        }

        recoverOneMinuteBpm(w)?.let { bpm1 ->
            val t1 = NSDate.dateWithTimeIntervalSince1970((w.endTime + 60).toDouble())
            singleQuantitySample(
                typeId = HKQuantityTypeIdentifierHeartRateRecoveryOneMinute
                    ?: "HKQuantityTypeIdentifierHeartRateRecoveryOneMinute",
                unitStr = "count/min",
                value = bpm1,
                start = t1,
                end = t1,
                syncId = "$idPrefix:v3:hrr1",
                version = version,
            )?.let { out += it }
        }

        w.elevationGainM?.takeIf { it >= 3.0 }?.let { gain ->
            val flights = (gain / 3.0).toInt().coerceAtLeast(1)
            singleQuantitySample(
                typeId = HKQuantityTypeIdentifierFlightsClimbed
                    ?: "HKQuantityTypeIdentifierFlightsClimbed",
                unitStr = "count",
                value = flights.toDouble(),
                start = start,
                end = end,
                syncId = "$idPrefix:v3:flights",
                version = version,
            )?.let { out += it }
        }

        return out
    }

    /** Full set for legacy fallback path. */
    private fun buildAssociatedWorkoutSamples(
        w: WorkoutSession,
        version: Long,
    ): List<HKQuantitySample> =
        buildCoreWorkoutSamples(w, version) + buildSportFormSamples(w, version)

    private fun recoverOneMinuteBpm(w: WorkoutSession): Double? {
        val series = w.recoverHeartRateSeries
        if (series.isEmpty()) return null
        val target = w.endTime + 60
        // Nearest recover sample within 90s of the 1-minute mark
        val nearest = series.minByOrNull { kotlin.math.abs(it.timeSec - target) } ?: return null
        if (kotlin.math.abs(nearest.timeSec - target) > 90) return null
        return nearest.value.takeIf { it in 30.0..250.0 }
    }

    private fun singleQuantitySample(
        typeId: String,
        unitStr: String,
        value: Double,
        start: NSDate,
        end: NSDate,
        syncId: String,
        version: Long,
    ): HKQuantitySample? {
        val type = HKQuantityType.quantityTypeForIdentifier(typeId) ?: return null
        if (healthStore.authorizationStatusForType(type) !=
            platform.HealthKit.HKAuthorizationStatusSharingAuthorized
        ) {
            return null
        }
        val unit = HKUnit.unitFromString(unitStr)
        return HKQuantitySample.quantitySampleWithType(
            quantityType = type,
            quantity = HKQuantity.quantityWithUnit(unit, value),
            startDate = start,
            endDate = end,
            metadata = mapOf<Any?, Any?>(
                HKMetadataKeySyncIdentifier to syncId,
                HKMetadataKeySyncVersion to version,
            ),
        )
    }

    /**
     * Optional form/sport series for the workout builder.
     * Skips types that are unavailable or explicitly denied; otherwise lets HealthKit
     * accept samples after the share request (status is often not yet Authorized).
     */
    private fun formSeries(
        typeId: String,
        unitStr: String,
        samples: List<com.bettermifitness.sync.data.api.WorkoutTimedSample>,
        idPrefix: String,
        version: Long,
    ): List<HKQuantitySample> {
        if (samples.isEmpty()) return emptyList()
        val type = HKQuantityType.quantityTypeForIdentifier(typeId) ?: return emptyList()
        if (healthStore.authorizationStatusForType(type) ==
            platform.HealthKit.HKAuthorizationStatusSharingDenied
        ) {
            return emptyList()
        }
        val unit = HKUnit.unitFromString(unitStr)
        return samples.map { s ->
            val date = NSDate.dateWithTimeIntervalSince1970(s.timeSec.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = type,
                quantity = HKQuantity.quantityWithUnit(unit, s.value),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to "$idPrefix:${s.timeSec}",
                    HKMetadataKeySyncVersion to version,
                ),
            )
        }
    }

    private suspend fun writeRecoverHeartRate(w: WorkoutSession, version: Long) {
        val hrType = HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRate)
            ?: return
        if (w.recoverHeartRateSeries.isEmpty()) return
        val bpm = HKUnit.unitFromString("count/min")
        val samples = w.recoverHeartRateSeries.map { s ->
            val date = NSDate.dateWithTimeIntervalSince1970(s.timeSec.toDouble())
            HKQuantitySample.quantitySampleWithType(
                quantityType = hrType,
                quantity = HKQuantity.quantityWithUnit(bpm, s.value),
                startDate = date,
                endDate = date,
                metadata = mapOf<Any?, Any?>(
                    HKMetadataKeySyncIdentifier to
                        HealthRecordIds.workout(w.startTime) + ":rhr:" + s.timeSec,
                    HKMetadataKeySyncVersion to version,
                ),
            )
        }
        try {
            saveSamples(samples)
        } catch (_: Exception) { /* optional */ }
    }

    private fun buildIosWorkoutNotes(w: WorkoutSession): String? {
        val parts = mutableListOf<String>()
        w.avgPaceSecPerKm?.let {
            val s = it.toInt()
            parts += "avg pace ${s / 60}:${(s % 60).toString().padStart(2, '0')}/km"
        }
        // Running cadence: notes only (no public HK RunningCadence). Cycling → CyclingCadence samples.
        w.avgCadenceSpm?.let {
            val family = WorkoutMetricFamilies.classify(w.activityType)
            parts += when (family) {
                WorkoutMetricFamily.CYCLING -> "cadence ${it.toInt()} rpm"
                else -> "cadence ${it.toInt()} spm"
            }
        }
        w.avgStrideCm?.let { parts += "stride ${it.toInt()} cm" }
            ?: w.strideMetersSeries.firstOrNull()?.let {
                parts += "stride ${(it.value * 100).toInt()} cm"
            }
        w.maxSpeedMps?.let {
            val kmh = (it * 3.6 * 10).toInt() / 10.0
            parts += "max $kmh km/h"
        }
        w.avgPowerWatts?.let { parts += "power ${it.toInt()} W" }
        w.avgGroundContactMs?.let { parts += "GCT ${it.toInt()} ms" }
        w.avgVerticalOscillationCm?.let {
            val tenths = ((it * 10).toInt()) / 10.0
            parts += "VO $tenths cm"
        }
        w.trainEffect?.let { parts += "TE $it" }
        w.trainLoad?.let { parts += "load ${it.toInt()}" }
        w.recoverMinutes?.let { parts += "recover ${it}m" }
        w.vo2Max?.let { parts += "VO2 $it" }
        val zones = listOfNotNull(
            w.hrZoneWarmupSec?.let { "WU${it}s" },
            w.hrZoneFatBurnSec?.let { "FB${it}s" },
            w.hrZoneAerobicSec?.let { "AE${it}s" },
            w.hrZoneAnaerobicSec?.let { "AN${it}s" },
            w.hrZoneExtremeSec?.let { "EX${it}s" },
        )
        if (zones.isNotEmpty()) parts += zones.joinToString("/")
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Attaches an [HKWorkoutRoute] via [HKWorkoutRouteBuilder] (requires CLLocation points).
     */
    private suspend fun saveWorkoutRoute(
        workout: HKWorkout,
        route: List<com.bettermifitness.sync.data.api.WorkoutRoutePoint>,
    ) {
        val locations = route.map { p ->
            val date = NSDate.dateWithTimeIntervalSince1970(p.timeSec.toDouble())
            val hAcc = p.horizontalAccuracyMeters ?: 10.0
            val alt = p.altitudeMeters ?: 0.0
            CLLocation(
                coordinate = CLLocationCoordinate2DMake(p.latitude, p.longitude),
                altitude = alt,
                horizontalAccuracy = hAcc,
                verticalAccuracy = if (p.altitudeMeters != null) 10.0 else -1.0,
                timestamp = date,
            )
        }
        val builder = HKWorkoutRouteBuilder(healthStore = healthStore, device = null)
        suspendCoroutine { cont ->
            builder.insertRouteData(locations) { success, error ->
                if (!success) {
                    cont.resumeWithException(
                        Exception(error?.localizedDescription ?: "insertRouteData failed"),
                    )
                    return@insertRouteData
                }
                builder.finishRouteWithWorkout(workout, metadata = null) { _, finishError ->
                    if (finishError != null) {
                        cont.resumeWithException(
                            Exception(finishError.localizedDescription),
                        )
                    } else {
                        cont.resume(Unit)
                    }
                }
            }
        }
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
        // Core types must be authorized to sync. Running-form types are optional —
        // if missing, we still sync workouts and skip form samples with a log.
        val core = coreShareTypes()
        if (core.isEmpty()) return false
        return core.all { type ->
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
    private fun coreShareTypes(): Set<platform.HealthKit.HKSampleType> = setOfNotNull(
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
        platform.HealthKit.HKSeriesType.workoutRouteType(),
    )

    /**
     * Optional sport metrics (Apple public identifiers only).
     * Requested at auth time so [HKWorkoutBuilder] can associate them.
     */
    private fun activityMetricShareTypes(): Set<platform.HealthKit.HKSampleType> = setOfNotNull(
        // Running (iOS 16+)
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningSpeed),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningStrideLength),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningPower),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningGroundContactTime),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierRunningVerticalOscillation),
        // Walking (iOS 14+)
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingSpeed),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierWalkingStepLength),
        // Cycling (iOS 17+) — only public cadence type in HealthKit
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingCadence),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingSpeed),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierCyclingPower),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceCycling),
        // Swimming
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierDistanceSwimming),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierSwimmingStrokeCount),
        // Other workout-related
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierHeartRateRecoveryOneMinute),
        HKQuantityType.quantityTypeForIdentifier(HKQuantityTypeIdentifierFlightsClimbed),
    )

    private fun shareTypes(): Set<platform.HealthKit.HKSampleType> =
        coreShareTypes() + activityMetricShareTypes()

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
