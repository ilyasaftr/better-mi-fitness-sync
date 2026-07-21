package com.bettermifitness.sync.health

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord as HcStepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import com.bettermifitness.sync.data.api.WorkoutSession
import java.time.Instant
import java.time.ZoneOffset

/**
 * Builds Health Connect records for one workout (SRP: record construction only).
 */
class AndroidWorkoutRecordFactory {

    fun buildRecords(session: WorkoutSession): List<androidx.health.connect.client.records.Record> {
        val zo = zoneOffsetOf(session)
        val start = Instant.ofEpochSecond(session.startTime)
        val end = Instant.ofEpochSecond(session.endTime)
        val mapping = SportTypeMapper.map(session.activityType)
        val notes = WorkoutNotes.build(session)
        val version = WorkoutFormSeries.contentVersion(
            session,
            mapping.title,
            mapping.healthConnectType,
        )
        val meta = Metadata.manualEntry(
            clientRecordId = HealthRecordIds.workout(session.startTime),
            clientRecordVersion = version,
        )
        val out = ArrayList<androidx.health.connect.client.records.Record>()
        val route = exerciseRouteOrNull(session)
        out += if (route != null) {
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

        session.distanceMeters?.takeIf { it > 0 }?.let { d ->
            out += DistanceRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                distance = Length.meters(d),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(session.startTime) + ":dist",
                    clientRecordVersion = version,
                ),
            )
        }
        session.caloriesKcal?.takeIf { it > 0 }?.let { c ->
            out += ActiveCaloriesBurnedRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                energy = Energy.kilocalories(c),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(session.startTime) + ":kcal",
                    clientRecordVersion = version,
                ),
            )
        }
        session.totalSteps?.takeIf { it > 0 }?.let { steps ->
            out += HcStepsRecord(
                startTime = start,
                endTime = end,
                startZoneOffset = zo,
                endZoneOffset = zo,
                count = steps.toLong(),
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(session.startTime) + ":steps",
                    clientRecordVersion = version,
                ),
            )
        }
        session.elevationGainM?.takeIf { it > 0 }?.let { gain ->
            try {
                out += ElevationGainedRecord(
                    startTime = start,
                    endTime = end,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    elevation = Length.meters(gain),
                    metadata = Metadata.manualEntry(
                        clientRecordId = HealthRecordIds.workout(session.startTime) + ":elev",
                        clientRecordVersion = version,
                    ),
                )
            } catch (_: Exception) { /* optional */ }
        }
        addHeartRate(out, session, zo, version)
        addSpeed(out, session, zo, version)
        addCadence(out, session, zo, version)
        addPower(out, session, zo, version)
        return out
    }

    private fun addHeartRate(
        out: MutableList<androidx.health.connect.client.records.Record>,
        session: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        if (session.heartRateSeries.size < 2) return
        val samples = session.heartRateSeries.map {
            HeartRateRecord.Sample(
                time = Instant.ofEpochSecond(it.timeSec),
                beatsPerMinute = it.value.toLong().coerceIn(30, 250),
            )
        }
        val hrStart = Instant.ofEpochSecond(session.heartRateSeries.first().timeSec)
        val hrEnd = Instant.ofEpochSecond(session.heartRateSeries.last().timeSec).plusSeconds(1)
        if (!hrEnd.isAfter(hrStart)) return
        out += HeartRateRecord(
            startTime = hrStart,
            endTime = hrEnd,
            startZoneOffset = zo,
            endZoneOffset = zo,
            samples = samples,
            metadata = Metadata.manualEntry(
                clientRecordId = HealthRecordIds.workout(session.startTime) + ":hr",
                clientRecordVersion = version,
            ),
        )
    }

    private fun addSpeed(
        out: MutableList<androidx.health.connect.client.records.Record>,
        session: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        val series = WorkoutFormSeries.speed(session)
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
            out += SpeedRecord(
                startTime = s0,
                endTime = s1,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = samples,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(session.startTime) + ":spd",
                    clientRecordVersion = version,
                ),
            )
        } catch (_: Exception) { /* optional */ }
    }

    /**
     * Cadence by sport (Health Connect data types):
     * - Cycling → [CyclingPedalingCadenceRecord] (RPM)
     * - Run / walk / other foot sports → [StepsCadenceRecord] (steps/min)
     * There is no separate "running cadence" type beyond steps cadence.
     */
    private fun addCadence(
        out: MutableList<androidx.health.connect.client.records.Record>,
        session: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        val series = WorkoutFormSeries.cadence(session)
        if (series.size < 2) return
        val s0 = Instant.ofEpochSecond(series.first().timeSec)
        val s1 = Instant.ofEpochSecond(series.last().timeSec).plusSeconds(1)
        if (!s1.isAfter(s0)) return
        val family = WorkoutMetricFamilies.classify(session.activityType)
        try {
            if (family == WorkoutMetricFamily.CYCLING) {
                val samples = series.map {
                    CyclingPedalingCadenceRecord.Sample(
                        time = Instant.ofEpochSecond(it.timeSec),
                        revolutionsPerMinute = it.value.coerceIn(0.0, 300.0),
                    )
                }
                out += CyclingPedalingCadenceRecord(
                    startTime = s0,
                    endTime = s1,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    samples = samples,
                    metadata = Metadata.manualEntry(
                        clientRecordId = HealthRecordIds.workout(session.startTime) + ":pcad",
                        clientRecordVersion = version,
                    ),
                )
            } else {
                val samples = series.map {
                    StepsCadenceRecord.Sample(
                        time = Instant.ofEpochSecond(it.timeSec),
                        rate = it.value.coerceIn(0.0, 300.0),
                    )
                }
                out += StepsCadenceRecord(
                    startTime = s0,
                    endTime = s1,
                    startZoneOffset = zo,
                    endZoneOffset = zo,
                    samples = samples,
                    metadata = Metadata.manualEntry(
                        clientRecordId = HealthRecordIds.workout(session.startTime) + ":cad",
                        clientRecordVersion = version,
                    ),
                )
            }
        } catch (_: Exception) { /* optional on older HC */ }
    }

    private fun addPower(
        out: MutableList<androidx.health.connect.client.records.Record>,
        session: WorkoutSession,
        zo: ZoneOffset,
        version: Long,
    ) {
        val series = session.powerWattsSeries
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
            out += PowerRecord(
                startTime = s0,
                endTime = s1,
                startZoneOffset = zo,
                endZoneOffset = zo,
                samples = samples,
                metadata = Metadata.manualEntry(
                    clientRecordId = HealthRecordIds.workout(session.startTime) + ":pow",
                    clientRecordVersion = version,
                ),
            )
        } catch (_: Exception) { /* optional */ }
    }

    private fun exerciseRouteOrNull(session: WorkoutSession): ExerciseRoute? {
        if (session.route.size < 2) return null
        return try {
            val locations = session.route.map { p ->
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

    private fun zoneOffsetOf(session: WorkoutSession): ZoneOffset {
        val sec = session.zoneOffsetSeconds()
        return try {
            ZoneOffset.ofTotalSeconds(sec.coerceIn(-18 * 3600, 18 * 3600))
        } catch (_: Exception) {
            ZoneOffset.UTC
        }
    }
}
