package com.bettermifitness.sync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord as HcStepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.HrvSample
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.TemperatureSample
import com.bettermifitness.sync.data.api.Vo2MaxSample
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutSession
import java.time.Instant

actual class HealthWriter(private val context: Context) : HealthStore {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /**
     * Write permissions for types this HC install supports.
     * Older phones (e.g. Galaxy S8 + old Health Connect) may not expose skin temperature.
     */
    private fun availableWritePermissions(): Set<String> {
        val perms = linkedSetOf(
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(RestingHeartRateRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(HcStepsRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
            HealthPermission.getWritePermission(ElevationGainedRecord::class),
            HealthPermission.getWritePermission(SpeedRecord::class),
            HealthPermission.getWritePermission(StepsCadenceRecord::class),
            HealthPermission.getWritePermission(CyclingPedalingCadenceRecord::class),
            HealthPermission.getWritePermission(PowerRecord::class),
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(BodyTemperatureRecord::class),
            HealthPermission.getWritePermission(Vo2MaxRecord::class),
            HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        )
        val skinOk = try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (_: Exception) {
            false
        }
        if (skinOk) {
            perms += HealthPermission.getWritePermission(SkinTemperatureRecord::class)
        }
        return perms
    }

    actual override suspend fun writeHeartRate(samples: List<HeartRateSample>) {
        val sortedSamples = HealthDataNormalizer.normalizeHeartRate(samples)
        if (sortedSamples.isEmpty()) return

        // Group into fixed windows so clientRecordId stays stable across re-syncs.
        val groups = sortedSamples.groupBy { HealthRecordIds.heartRateWindowStart(it.timestamp) }

        val records = groups.mapNotNull { (windowStart, group) ->
            val ordered = group.sortedBy { it.timestamp }
            val start = Instant.ofEpochSecond(ordered.first().timestamp)
            val end = Instant.ofEpochSecond(ordered.last().timestamp).plusSeconds(1)
            if (!end.isAfter(start)) return@mapNotNull null
            val zo = ZoneOffsetResolver.fromMiOrSystem(
                ordered.firstNotNullOfOrNull { it.tzIn15Min },
                start,
            )

            HeartRateRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = ordered.map { sample ->
                    HeartRateRecord.Sample(
                        time = Instant.ofEpochSecond(sample.timestamp),
                        beatsPerMinute = sample.bpm.toLong(),
                    )
                },
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.heartRateWindow(windowStart),
                    clientRecordVersion = HealthRecordIds.version(
                        ordered.joinToString("|") { "${it.timestamp}:${it.bpm}:${it.tzIn15Min}" },
                    ),
                ),
            )
        }

        if (records.isNotEmpty()) client.insertRecords(records)
    }

    actual override suspend fun writeRestingHeartRate(samples: List<HeartRateSample>) {
        val clean = HealthDataNormalizer.normalizeHeartRate(samples)
        if (clean.isEmpty()) return
        val records = clean.map { sample ->
            val time = Instant.ofEpochSecond(sample.timestamp)
            RestingHeartRateRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(sample.tzIn15Min, time),
                beatsPerMinute = sample.bpm.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.restingHeartRate(sample.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        sample.timestamp,
                        sample.bpm,
                        sample.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeSleep(sessions: List<SleepSession>) {
        val clean = HealthDataNormalizer.normalizeSleep(sessions)
        if (clean.isEmpty()) return
        val records = clean.mapNotNull { session ->
            val start = Instant.ofEpochSecond(session.startTime)
            val end = Instant.ofEpochSecond(session.endTime)
            if (!end.isAfter(start)) return@mapNotNull null
            val zo = ZoneOffsetResolver.fromMiOrSystem(session.tzIn15Min, start)

            SleepSessionRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                stages = session.stages.mapNotNull { stage ->
                    val s = Instant.ofEpochSecond(stage.startTime)
                    val e = Instant.ofEpochSecond(stage.endTime)
                    if (!e.isAfter(s)) return@mapNotNull null
                    SleepSessionRecord.Stage(
                        startTime = s,
                        endTime = e,
                        stage = mapSleepStage(stage.stage),
                    )
                },
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.sleepSession(session.startTime),
                    clientRecordVersion = HealthRecordIds.version(
                        session.startTime,
                        session.endTime,
                        session.tzIn15Min,
                        session.stages.joinToString { "${it.startTime}:${it.stage}" },
                    ),
                ),
            )
        }
        if (records.isNotEmpty()) client.insertRecords(records)
    }

    actual override suspend fun writeSteps(records: List<StepsRecord>) {
        val clean = HealthDataNormalizer.normalizeSteps(records)
        if (clean.isEmpty()) return
        val hcRecords = clean.mapNotNull { record ->
            val ts = record.date.toLong()
            val start = Instant.ofEpochSecond(ts)
            // Clamp open hour end so HC never sees endTime in the future (issue #10 class).
            val end = minOf(start.plusSeconds(3599), Instant.now())
            if (!end.isAfter(start)) return@mapNotNull null
            val zo = ZoneOffsetResolver.fromMiOrSystem(record.tzIn15Min, start)

            HcStepsRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                count = record.steps.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.steps(ts),
                    // Higher step counts during the day should replace earlier partial buckets.
                    clientRecordVersion = HealthRecordIds.counterVersion(record.steps.toLong(), ts),
                ),
            )
        }
        if (hcRecords.isNotEmpty()) client.insertRecords(hcRecords)
    }

    actual override suspend fun writeDistance(samples: List<DistanceSample>) {
        val clean = HealthDataNormalizer.normalizeDistance(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val start = Instant.ofEpochSecond(s.startTime)
            val zo = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, start)
            DistanceRecord(
                startTime = start,
                endTime = Instant.ofEpochSecond(s.endTime),
                startZoneOffset = zo,
                endZoneOffset = zo,
                distance = Length.meters(s.meters),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.distance(s.startTime),
                    clientRecordVersion = HealthRecordIds.version(
                        s.startTime,
                        s.endTime,
                        s.meters,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>) {
        val clean = HealthDataNormalizer.normalizeActiveCalories(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val start = Instant.ofEpochSecond(s.startTime)
            val zo = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, start)
            ActiveCaloriesBurnedRecord(
                startTime = start,
                endTime = Instant.ofEpochSecond(s.endTime),
                startZoneOffset = zo,
                endZoneOffset = zo,
                energy = Energy.kilocalories(s.kilocalories),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.activeCalories(s.startTime),
                    clientRecordVersion = HealthRecordIds.version(
                        s.startTime,
                        s.endTime,
                        s.kilocalories,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeWeight(measurements: List<WeightMeasurement>) {
        val clean = HealthDataNormalizer.normalizeWeight(measurements)
        if (clean.isEmpty()) return
        val weightRecords = clean.map { m ->
            val time = Instant.ofEpochSecond(m.timestamp)
            WeightRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                weight = Mass.kilograms(m.weightKg),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.weight(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        m.timestamp,
                        m.weightKg,
                        m.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(weightRecords)
        val fatRecords = clean.mapNotNull { m ->
            val fat = m.bodyFatPercent ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(m.timestamp)
            BodyFatRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(m.tzIn15Min, time),
                percentage = Percentage(fat),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyFat(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, fat, m.tzIn15Min),
                ),
            )
        }
        if (fatRecords.isNotEmpty()) client.insertRecords(fatRecords)
    }

    actual override suspend fun writeWorkouts(sessions: List<WorkoutSession>) {
        val clean = HealthDataNormalizer.normalizeWorkouts(sessions)
        if (clean.isEmpty()) return
        val factory = AndroidWorkoutRecordFactory()
        val batch = clean.flatMap { factory.buildRecords(it) }
        // Insert in chunks to avoid binder limits on large routes/HR
        batch.chunked(50).forEach { chunk ->
            client.insertRecords(chunk)
        }
    }

    actual override suspend fun writeSpO2(samples: List<SpO2Sample>) {
        val clean = HealthDataNormalizer.normalizeSpO2(samples)
        if (clean.isEmpty()) return
        val records = clean.map { sample ->
            val time = Instant.ofEpochSecond(sample.timestamp)
            OxygenSaturationRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(sample.tzIn15Min, time),
                percentage = Percentage(sample.percentage.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.spo2(sample.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        sample.timestamp,
                        sample.percentage,
                        sample.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeBloodPressure(samples: List<BloodPressureSample>) {
        val clean = HealthDataNormalizer.normalizeBloodPressure(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val time = Instant.ofEpochSecond(s.timestamp)
            BloodPressureRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                systolic = Pressure.millimetersOfMercury(s.systolicMmhg.toDouble()),
                diastolic = Pressure.millimetersOfMercury(s.diastolicMmhg.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bloodPressure(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.systolicMmhg,
                        s.diastolicMmhg,
                        s.pulseBpm,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeTemperature(samples: List<TemperatureSample>) {
        val clean = HealthDataNormalizer.normalizeTemperature(samples)
        if (clean.isEmpty()) return
        val bodyRecords = clean.mapNotNull { s ->
            val body = s.bodyCelsius ?: return@mapNotNull null
            val time = Instant.ofEpochSecond(s.timestamp)
            BodyTemperatureRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                temperature = Temperature.celsius(body),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyTemperature(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, body, s.tzIn15Min),
                ),
            )
        }
        if (bodyRecords.isNotEmpty()) client.insertRecords(bodyRecords)

        // SkinTemperatureRecord needs a baseline + deltas; write as single-point baseline.
        val skinRecords = clean.mapNotNull { s ->
            val skin = s.skinCelsius ?: return@mapNotNull null
            val start = Instant.ofEpochSecond(s.timestamp)
            val end = start.plusSeconds(1)
            val zo = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, start)
            SkinTemperatureRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                deltas = listOf(
                    SkinTemperatureRecord.Delta(
                        time = start,
                        delta = TemperatureDelta.celsius(0.0),
                    ),
                ),
                baseline = Temperature.celsius(skin),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.skinTemperature(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, skin, s.tzIn15Min),
                ),
            )
        }
        if (skinRecords.isNotEmpty()) client.insertRecords(skinRecords)
    }

    actual override suspend fun writeVo2Max(samples: List<Vo2MaxSample>) {
        val clean = HealthDataNormalizer.normalizeVo2Max(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            val time = Instant.ofEpochSecond(s.timestamp)
            Vo2MaxRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                vo2MillilitersPerMinuteKilogram = s.mlPerKgMin,
                measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.vo2Max(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.mlPerKgMin,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeHrv(samples: List<HrvSample>) {
        val clean = HealthDataNormalizer.normalizeHrv(samples)
        if (clean.isEmpty()) return
        // Mi overnight HRV is in ms; Health Connect stores RMSSD milliseconds.
        val records = clean.map { s ->
            val time = Instant.ofEpochSecond(s.timestamp)
            HeartRateVariabilityRmssdRecord(
                time = time,
                zoneOffset = ZoneOffsetResolver.fromMiOrSystem(s.tzIn15Min, time),
                heartRateVariabilityMillis = s.hrvMs,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.hrv(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.hrvMs,
                        s.tzIn15Min,
                    ),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    actual override suspend fun hasWritePermissions(): Boolean {
        if (!isAvailable()) return false
        return try {
            val needed = availableWritePermissions()
            if (needed.isEmpty()) return false
            val granted = client.permissionController.getGrantedPermissions()
            needed.all { it in granted }
        } catch (_: Exception) {
            false
        }
    }

    actual override suspend fun requestPermissions() {
        val launcher = HealthConnectPermissionBridge.requestPermissions
            ?: throw IllegalStateException("Health Connect permission launcher not ready")
        val needed = availableWritePermissions()
        if (needed.isEmpty()) {
            throw Exception("Health Connect has no writable data types available on this phone.")
        }
        val granted = launcher(needed)
        val missing = needed - granted
        if (missing.isNotEmpty()) {
            throw Exception(
                "Health Connect write permission not granted. " +
                    "Tap Allow access in this app, then enable the toggles.",
            )
        }
    }

    actual override fun healthServiceName(): String = "Health Connect"

    actual override suspend fun availabilityHint(): String? {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE ->
                if (hasWritePermissions()) {
                    null
                } else {
                    "Tap Allow access so Health Connect can show the permission screen. " +
                        "This app appears under App permissions after that request."
                }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "Update Health Connect from the Play Store, then try again."
            else ->
                "Install Health Connect from the Play Store so we can save your activity."
        }
    }

    actual override fun openHealthService() {
        HealthConnectPermissionBridge.openHealthConnect?.invoke()
    }

    /**
     * Mi Fitness sleep states: 2=light/core, 3=deep, 4=REM, 5=awake
     * (same mapping as iOS HealthWriter).
     */
    private fun mapSleepStage(stage: Int): Int {
        return when (stage) {
            5, 1 -> SleepSessionRecord.STAGE_TYPE_AWAKE
            2 -> SleepSessionRecord.STAGE_TYPE_LIGHT
            3 -> SleepSessionRecord.STAGE_TYPE_DEEP
            4 -> SleepSessionRecord.STAGE_TYPE_REM
            else -> SleepSessionRecord.STAGE_TYPE_UNKNOWN
        }
    }

}
