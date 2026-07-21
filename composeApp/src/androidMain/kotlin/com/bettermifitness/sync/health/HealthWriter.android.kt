package com.bettermifitness.sync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
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
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Velocity
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
import java.time.Instant
import java.time.ZoneOffset

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
            HealthPermission.getWritePermission(PowerRecord::class),
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(BodyTemperatureRecord::class),
            HealthPermission.getWritePermission(Vo2MaxRecord::class),
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

            HeartRateRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                samples = ordered.map { sample ->
                    HeartRateRecord.Sample(
                        time = Instant.ofEpochSecond(sample.timestamp),
                        beatsPerMinute = sample.bpm.toLong(),
                    )
                },
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.heartRateWindow(windowStart),
                    clientRecordVersion = HealthRecordIds.version(
                        ordered.joinToString("|") { "${it.timestamp}:${it.bpm}" },
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
                zoneOffset = ZoneOffset.UTC,
                beatsPerMinute = sample.bpm.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.restingHeartRate(sample.timestamp),
                    clientRecordVersion = HealthRecordIds.version(sample.timestamp, sample.bpm),
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

            SleepSessionRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
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
        val hcRecords = clean.map { record ->
            val ts = record.date.toLong()
            val start = Instant.ofEpochSecond(ts)
            val end = start.plusSeconds(3599) // One hour bucket

            HcStepsRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                count = record.steps.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.steps(ts),
                    // Higher step counts during the day should replace earlier partial buckets.
                    clientRecordVersion = HealthRecordIds.counterVersion(record.steps.toLong(), ts),
                ),
            )
        }
        client.insertRecords(hcRecords)
    }

    actual override suspend fun writeDistance(samples: List<DistanceSample>) {
        val clean = HealthDataNormalizer.normalizeDistance(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            DistanceRecord(
                startTime = Instant.ofEpochSecond(s.startTime),
                endTime = Instant.ofEpochSecond(s.endTime),
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                distance = Length.meters(s.meters),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.distance(s.startTime),
                    clientRecordVersion = HealthRecordIds.version(s.startTime, s.endTime, s.meters),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeActiveCalories(samples: List<ActiveCaloriesSample>) {
        val clean = HealthDataNormalizer.normalizeActiveCalories(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            ActiveCaloriesBurnedRecord(
                startTime = Instant.ofEpochSecond(s.startTime),
                endTime = Instant.ofEpochSecond(s.endTime),
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                energy = Energy.kilocalories(s.kilocalories),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.activeCalories(s.startTime),
                    clientRecordVersion = HealthRecordIds.version(
                        s.startTime,
                        s.endTime,
                        s.kilocalories,
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
            WeightRecord(
                time = Instant.ofEpochSecond(m.timestamp),
                zoneOffset = ZoneOffset.UTC,
                weight = Mass.kilograms(m.weightKg),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.weight(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, m.weightKg),
                ),
            )
        }
        client.insertRecords(weightRecords)
        val fatRecords = clean.mapNotNull { m ->
            val fat = m.bodyFatPercent ?: return@mapNotNull null
            BodyFatRecord(
                time = Instant.ofEpochSecond(m.timestamp),
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(fat),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyFat(m.timestamp),
                    clientRecordVersion = HealthRecordIds.version(m.timestamp, fat),
                ),
            )
        }
        if (fatRecords.isNotEmpty()) client.insertRecords(fatRecords)
    }

    actual override suspend fun writeWorkouts(sessions: List<WorkoutSession>) {
        val clean = HealthDataNormalizer.normalizeWorkouts(sessions)
        if (clean.isEmpty()) return
        val batch = mutableListOf<androidx.health.connect.client.records.Record>()
        for (w in clean) {
            val zo = zoneOffsetOf(w)
            val start = Instant.ofEpochSecond(w.startTime)
            val end = Instant.ofEpochSecond(w.endTime)
            val mapping = SportTypeMapper.map(w.activityType)
            val notes = buildWorkoutNotes(w)
            val version = HealthRecordIds.version(
                w.startTime, w.endTime, mapping.title, mapping.healthConnectType,
                w.distanceMeters, w.caloriesKcal, w.route.size, w.heartRateSeries.size,
                w.avgPaceSecPerKm, w.avgCadenceSpm, w.trainLoad,
                w.speedSeries.size, w.cadenceSeries.size, w.strideMetersSeries.size,
                w.powerWattsSeries.size,
            )
            val meta = Metadata.manualEntry(
                clientRecordId = HealthRecordIds.workout(w.startTime),
                clientRecordVersion = version,
            )
            val route = exerciseRouteOrNull(w)
            batch += if (route != null) {
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = zo,
                    endTime = end,
                    endZoneOffset = zo,
                    metadata = meta,
                    exerciseType = mapping.healthConnectType,
                    title = mapping.title,
                    notes = notes,
                    exerciseRoute = route,
                )
            } else {
                ExerciseSessionRecord(
                    startTime = start,
                    endTime = end,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    exerciseType = mapping.healthConnectType,
                    title = mapping.title,
                    notes = notes,
                    metadata = meta,
                )
            }
            w.distanceMeters?.takeIf { it > 0 }?.let { d ->
                batch += DistanceRecord(
                    startTime = start,
                    endTime = end,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    distance = Length.meters(d),
                    metadata = Metadata.manualEntry(
                        clientRecordId = HealthRecordIds.workout(w.startTime) + ":dist",
                        clientRecordVersion = version,
                    ),
                )
            }
            w.caloriesKcal?.takeIf { it > 0 }?.let { c ->
                batch += ActiveCaloriesBurnedRecord(
                    startTime = start,
                    endTime = end,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    energy = Energy.kilocalories(c),
                    metadata = Metadata.manualEntry(
                        clientRecordId = HealthRecordIds.workout(w.startTime) + ":kcal",
                        clientRecordVersion = version,
                    ),
                )
            }
            w.totalSteps?.takeIf { it > 0 }?.let { steps ->
                batch += HcStepsRecord(
                    startTime = start,
                    endTime = end,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    count = steps.toLong(),
                    metadata = Metadata.manualEntry(
                        clientRecordId = HealthRecordIds.workout(w.startTime) + ":steps",
                        clientRecordVersion = version,
                    ),
                )
            }
            w.elevationGainM?.takeIf { it > 0 }?.let { gain ->
                try {
                    batch += ElevationGainedRecord(
                        startTime = start,
                        endTime = end,
                        startZoneOffset = zo,
                        endZoneOffset = zo,
                        elevation = Length.meters(gain),
                        metadata = Metadata.manualEntry(
                            clientRecordId = HealthRecordIds.workout(w.startTime) + ":elev",
                            clientRecordVersion = version,
                        ),
                    )
                } catch (_: Exception) { /* type may be unavailable */ }
            }
            if (w.heartRateSeries.size >= 2) {
                val samples = w.heartRateSeries.map {
                    HeartRateRecord.Sample(
                        time = Instant.ofEpochSecond(it.timeSec),
                        beatsPerMinute = it.value.toLong().coerceIn(30, 250),
                    )
                }
                val hrStart = Instant.ofEpochSecond(w.heartRateSeries.first().timeSec)
                val hrEnd = Instant.ofEpochSecond(w.heartRateSeries.last().timeSec).plusSeconds(1)
                if (hrEnd.isAfter(hrStart)) {
                    batch += HeartRateRecord(
                        startTime = hrStart,
                        endTime = hrEnd,
                        startZoneOffset = zo,
                        endZoneOffset = zo,
                        samples = samples,
                        metadata = Metadata.manualEntry(
                            clientRecordId = HealthRecordIds.workout(w.startTime) + ":hr",
                            clientRecordVersion = version,
                        ),
                    )
                }
            }
            // Speed (pace in HC is derived from speed series during exercise)
            addSpeedRecord(batch, w, zo, version)
            addCadenceRecord(batch, w, zo, version)
            addPowerRecord(batch, w, zo, version)
        }
        // Insert in chunks to avoid binder limits on large routes/HR
        batch.chunked(50).forEach { chunk ->
            client.insertRecords(chunk)
        }
    }

    private fun zoneOffsetOf(w: WorkoutSession): ZoneOffset {
        val sec = w.zoneOffsetSeconds()
        return try {
            ZoneOffset.ofTotalSeconds(sec.coerceIn(-18 * 3600, 18 * 3600))
        } catch (_: Exception) {
            ZoneOffset.UTC
        }
    }

    private fun buildWorkoutNotes(w: WorkoutSession): String? {
        val parts = mutableListOf<String>()
        w.avgPaceSecPerKm?.let { parts += "avg pace ${formatPace(it)}/km" }
        w.avgCadenceSpm?.let { parts += "cadence ${it.toInt()} spm" }
        w.avgStrideCm?.let { parts += "stride ${it.toInt()} cm" }
        w.avgPowerWatts?.let { parts += "power ${it.toInt()} W" }
        w.trainEffect?.let { parts += "TE $it" }
        w.trainLoad?.let { parts += "load ${it.toInt()}" }
        w.recoverMinutes?.let { parts += "recover ${it}m" }
        val zones = listOfNotNull(
            w.hrZoneWarmupSec?.let { "WU ${it}s" },
            w.hrZoneFatBurnSec?.let { "FB ${it}s" },
            w.hrZoneAerobicSec?.let { "AE ${it}s" },
            w.hrZoneAnaerobicSec?.let { "AN ${it}s" },
            w.hrZoneExtremeSec?.let { "EX ${it}s" },
        )
        if (zones.isNotEmpty()) parts += "HR zones: ${zones.joinToString(", ")}"
        if (w.kmSplits.isNotEmpty()) parts += "${w.kmSplits.size} km splits"
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun formatPace(secPerKm: Double): String {
        val s = secPerKm.toInt().coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun addSpeedRecord(
        batch: MutableList<androidx.health.connect.client.records.Record>,
        w: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        val series = w.speedSeries
        if (series.size < 2) return
        try {
            val samples = series.map {
                SpeedRecord.Sample(
                    time = Instant.ofEpochSecond(it.timeSec),
                    speed = Velocity.metersPerSecond(it.value.coerceIn(0.0, 15.0)),
                )
            }
            val s0 = Instant.ofEpochSecond(series.first().timeSec)
            val s1 = Instant.ofEpochSecond(series.last().timeSec).plusSeconds(1)
            if (!s1.isAfter(s0)) return
            batch += SpeedRecord(
                startTime = s0,
                endTime = s1,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = samples,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(w.startTime) + ":spd",
                    clientRecordVersion = version,
                ),
            )
        } catch (_: Exception) { /* optional on older HC */ }
    }

    private fun addCadenceRecord(
        batch: MutableList<androidx.health.connect.client.records.Record>,
        w: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        val series = w.cadenceSeries
        if (series.size < 2) return
        try {
            val samples = series.map {
                StepsCadenceRecord.Sample(
                    time = Instant.ofEpochSecond(it.timeSec),
                    rate = it.value.coerceIn(0.0, 300.0),
                )
            }
            val s0 = Instant.ofEpochSecond(series.first().timeSec)
            val s1 = Instant.ofEpochSecond(series.last().timeSec).plusSeconds(1)
            if (!s1.isAfter(s0)) return
            batch += StepsCadenceRecord(
                startTime = s0,
                endTime = s1,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = samples,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(w.startTime) + ":cad",
                    clientRecordVersion = version,
                ),
            )
        } catch (_: Exception) { /* optional */ }
    }

    private fun addPowerRecord(
        batch: MutableList<androidx.health.connect.client.records.Record>,
        w: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        val series = w.powerWattsSeries
        if (series.size < 2) return
        try {
            val samples = series.map {
                PowerRecord.Sample(
                    time = Instant.ofEpochSecond(it.timeSec),
                    power = Power.watts(it.value.coerceIn(0.0, 2000.0)),
                )
            }
            val s0 = Instant.ofEpochSecond(series.first().timeSec)
            val s1 = Instant.ofEpochSecond(series.last().timeSec).plusSeconds(1)
            if (!s1.isAfter(s0)) return
            batch += PowerRecord(
                startTime = s0,
                endTime = s1,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = samples,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(w.startTime) + ":pow",
                    clientRecordVersion = version,
                ),
            )
        } catch (_: Exception) { /* optional */ }
    }

    private fun exerciseRouteOrNull(w: WorkoutSession): ExerciseRoute? {
        if (w.route.size < 2) return null
        return try {
            val locations = w.route.map { p ->
                ExerciseRoute.Location(
                    time = Instant.ofEpochSecond(p.timeSec),
                    latitude = p.latitude,
                    longitude = p.longitude,
                    horizontalAccuracy = p.horizontalAccuracyMeters?.let { Length.meters(it) },
                    verticalAccuracy = null,
                    altitude = p.altitudeMeters?.let { Length.meters(it) },
                )
            }
            ExerciseRoute(locations)
        } catch (_: Exception) {
            null
        }
    }

    actual override suspend fun writeSpO2(samples: List<SpO2Sample>) {
        val clean = HealthDataNormalizer.normalizeSpO2(samples)
        if (clean.isEmpty()) return
        val records = clean.map { sample ->
            val time = Instant.ofEpochSecond(sample.timestamp)
            OxygenSaturationRecord(
                time = time,
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(sample.percentage.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.spo2(sample.timestamp),
                    clientRecordVersion = HealthRecordIds.version(sample.timestamp, sample.percentage),
                ),
            )
        }
        client.insertRecords(records)
    }

    actual override suspend fun writeBloodPressure(samples: List<BloodPressureSample>) {
        val clean = HealthDataNormalizer.normalizeBloodPressure(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            BloodPressureRecord(
                time = Instant.ofEpochSecond(s.timestamp),
                zoneOffset = ZoneOffset.UTC,
                systolic = Pressure.millimetersOfMercury(s.systolicMmhg.toDouble()),
                diastolic = Pressure.millimetersOfMercury(s.diastolicMmhg.toDouble()),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bloodPressure(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(
                        s.timestamp,
                        s.systolicMmhg,
                        s.diastolicMmhg,
                        s.pulseBpm,
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
            BodyTemperatureRecord(
                time = Instant.ofEpochSecond(s.timestamp),
                zoneOffset = ZoneOffset.UTC,
                temperature = Temperature.celsius(body),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.bodyTemperature(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, body),
                ),
            )
        }
        if (bodyRecords.isNotEmpty()) client.insertRecords(bodyRecords)

        // SkinTemperatureRecord needs a baseline + deltas; write as single-point baseline.
        val skinRecords = clean.mapNotNull { s ->
            val skin = s.skinCelsius ?: return@mapNotNull null
            val start = Instant.ofEpochSecond(s.timestamp)
            val end = start.plusSeconds(1)
            SkinTemperatureRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                deltas = listOf(
                    SkinTemperatureRecord.Delta(
                        time = start,
                        delta = TemperatureDelta.celsius(0.0),
                    ),
                ),
                baseline = Temperature.celsius(skin),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.skinTemperature(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, skin),
                ),
            )
        }
        if (skinRecords.isNotEmpty()) client.insertRecords(skinRecords)
    }

    actual override suspend fun writeVo2Max(samples: List<Vo2MaxSample>) {
        val clean = HealthDataNormalizer.normalizeVo2Max(samples)
        if (clean.isEmpty()) return
        val records = clean.map { s ->
            Vo2MaxRecord(
                time = Instant.ofEpochSecond(s.timestamp),
                zoneOffset = ZoneOffset.UTC,
                vo2MillilitersPerMinuteKilogram = s.mlPerKgMin,
                measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.vo2Max(s.timestamp),
                    clientRecordVersion = HealthRecordIds.version(s.timestamp, s.mlPerKgMin),
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
