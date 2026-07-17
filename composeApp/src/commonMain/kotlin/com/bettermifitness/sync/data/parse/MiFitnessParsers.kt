package com.bettermifitness.sync.data.parse

import com.bettermifitness.sync.data.api.ActiveCaloriesSample
import com.bettermifitness.sync.data.api.BloodPressureSample
import com.bettermifitness.sync.data.api.DistanceSample
import com.bettermifitness.sync.data.api.HeartRateEntry
import com.bettermifitness.sync.data.api.HeartRateSample
import com.bettermifitness.sync.data.api.SleepEntry
import com.bettermifitness.sync.data.api.SleepSession
import com.bettermifitness.sync.data.api.SleepStage
import com.bettermifitness.sync.data.api.SpO2Sample
import com.bettermifitness.sync.data.api.SportRecordEntry
import com.bettermifitness.sync.data.api.StepsRecord
import com.bettermifitness.sync.data.api.TemperatureSample
import com.bettermifitness.sync.data.api.Vo2MaxSample
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * Normalized raw fitness row from Mi list/latest endpoints.
 * Decouples parsers from DTO type aliases (HeartRateEntry / SleepEntry).
 */
data class RawFitnessEntry(
    val key: String? = null,
    val time: Long = 0,
    val value: String = "",
)

fun HeartRateEntry.toRaw(): RawFitnessEntry = RawFitnessEntry(key, time, value)
fun SleepEntry.toRaw(): RawFitnessEntry = RawFitnessEntry(key, time, value)

/**
 * Pure Mi API JSON → domain sample converters.
 * No I/O, no platform APIs — safe for unit tests on both Android and iOS.
 */
object MiFitnessParsers {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseHeartRateSamples(entries: List<RawFitnessEntry>): List<HeartRateSample> =
        entries.mapNotNull { entry ->
            try {
                val obj = json.parseToJsonElement(entry.value).jsonObject
                HeartRateSample(
                    timestamp = obj["time"]?.jsonPrimitive?.long ?: entry.time,
                    bpm = obj["bpm"]?.jsonPrimitive?.int ?: return@mapNotNull null,
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Resting HR lives on the "latest" endpoint; payload uses `date_time` not `time`.
     */
    fun parseRestingHeartRateSamples(entries: List<RawFitnessEntry>): List<HeartRateSample> =
        entries.mapNotNull { entry ->
            try {
                val obj = json.parseToJsonElement(entry.value).jsonObject
                HeartRateSample(
                    timestamp = obj["date_time"]?.jsonPrimitive?.long ?: entry.time,
                    bpm = obj["bpm"]?.jsonPrimitive?.int ?: return@mapNotNull null,
                )
            } catch (_: Exception) {
                null
            }
        }

    fun parseSpO2Samples(entries: List<RawFitnessEntry>): List<SpO2Sample> =
        entries.mapNotNull { entry ->
            try {
                val obj = json.parseToJsonElement(entry.value).jsonObject
                SpO2Sample(
                    timestamp = obj["time"]?.jsonPrimitive?.long ?: entry.time,
                    percentage = obj["spo2"]?.jsonPrimitive?.int ?: return@mapNotNull null,
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Mi records per-minute step deltas; bucket into hourly samples to match Mi Fitness UI.
     */
    fun parseHourlySteps(entries: List<RawFitnessEntry>): List<StepsRecord> {
        val byHour = HashMap<Long, Int>()
        for (entry in entries) {
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val t = o["time"]?.jsonPrimitive?.long ?: continue
                val steps = o["steps"]?.jsonPrimitive?.int ?: continue
                val hourStart = (t / 3600L) * 3600L
                byHour[hourStart] = (byHour[hourStart] ?: 0) + steps
            } catch (_: Exception) {
                /* skip bad entry */
            }
        }
        return byHour.entries
            .filter { it.value > 0 }
            .map { StepsRecord(date = it.key.toString(), steps = it.value) }
    }

    /**
     * Distance often rides on the same per-minute `steps` stream (`distance` field, meters).
     * Bucket into hourly intervals for Health writes.
     */
    fun parseHourlyDistanceFromSteps(entries: List<RawFitnessEntry>): List<DistanceSample> {
        val byHour = HashMap<Long, Double>()
        for (entry in entries) {
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val t = o["time"]?.jsonPrimitive?.long ?: continue
                val meters = o.doubleField("distance") ?: continue
                if (meters <= 0) continue
                val hourStart = (t / 3600L) * 3600L
                byHour[hourStart] = (byHour[hourStart] ?: 0.0) + meters
            } catch (_: Exception) {
            }
        }
        return byHour.entries
            .filter { it.value > 0 }
            .map { (hour, meters) ->
                DistanceSample(startTime = hour, endTime = hour + 3599, meters = meters)
            }
    }

    /** Active energy from the dedicated `calories` key (kcal). */
    fun parseHourlyActiveCalories(entries: List<RawFitnessEntry>): List<ActiveCaloriesSample> {
        val byHour = HashMap<Long, Double>()
        for (entry in entries) {
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val t = o["time"]?.jsonPrimitive?.long ?: entry.time
                val kcal = o.doubleField("calories") ?: continue
                if (kcal <= 0) continue
                val hourStart = (t / 3600L) * 3600L
                byHour[hourStart] = (byHour[hourStart] ?: 0.0) + kcal
            } catch (_: Exception) {
            }
        }
        return byHour.entries
            .filter { it.value > 0 }
            .map { (hour, kcal) ->
                ActiveCaloriesSample(startTime = hour, endTime = hour + 3599, kilocalories = kcal)
            }
    }

    fun parseWeightMeasurements(entries: List<RawFitnessEntry>): List<WeightMeasurement> =
        entries.mapNotNull { entry ->
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val kg = o.doubleField("weight") ?: return@mapNotNull null
                if (kg <= 0 || kg > 500) return@mapNotNull null
                WeightMeasurement(
                    timestamp = entry.time.takeIf { it > 0 } ?: o["time"]?.jsonPrimitive?.long ?: 0L,
                    weightKg = kg,
                    bodyFatPercent = o.doubleField("body_fat_rate"),
                    muscleMassKg = o.doubleField("muscle_rate")?.takeIf { it in 1.0..200.0 },
                    boneMassKg = o.doubleField("bone_mass"),
                    basalMetabolismKcal = o.doubleField("basal_metabolism"),
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Mi [BloodPressureItem]: systolic_pressure / diastolic_pressure (mmHg).
     * Also accepts high_pressure/low_pressure aliases if present.
     */
    fun parseBloodPressureSamples(entries: List<RawFitnessEntry>): List<BloodPressureSample> =
        entries.mapNotNull { entry ->
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val systolic = o["systolic_pressure"]?.jsonPrimitive?.intOrNull
                    ?: o["high_pressure"]?.jsonPrimitive?.intOrNull
                    ?: return@mapNotNull null
                val diastolic = o["diastolic_pressure"]?.jsonPrimitive?.intOrNull
                    ?: o["low_pressure"]?.jsonPrimitive?.intOrNull
                    ?: return@mapNotNull null
                BloodPressureSample(
                    timestamp = o["time"]?.jsonPrimitive?.longOrNull
                        ?: entry.time.takeIf { it > 0 }
                        ?: return@mapNotNull null,
                    systolicMmhg = systolic,
                    diastolicMmhg = diastolic,
                    pulseBpm = o["pulse"]?.jsonPrimitive?.intOrNull,
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Mi [TemperatureItem]: body_temperature / skin_temperature (°C).
     * At least one temperature field required.
     */
    fun parseTemperatureSamples(entries: List<RawFitnessEntry>): List<TemperatureSample> =
        entries.mapNotNull { entry ->
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val body = o.doubleField("body_temperature")
                val skin = o.doubleField("skin_temperature")
                if (body == null && skin == null) return@mapNotNull null
                TemperatureSample(
                    timestamp = o["time"]?.jsonPrimitive?.longOrNull
                        ?: entry.time.takeIf { it > 0 }
                        ?: return@mapNotNull null,
                    bodyCelsius = body,
                    skinCelsius = skin,
                )
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Mi [Vo2MaxItem]: field `vo2_max` (integer mL·kg⁻¹·min⁻¹ in APK).
     */
    fun parseVo2MaxSamples(entries: List<RawFitnessEntry>): List<Vo2MaxSample> =
        entries.mapNotNull { entry ->
            try {
                val o = json.parseToJsonElement(entry.value).jsonObject
                val vo2 = o.doubleField("vo2_max")
                    ?: o["vo2Max"]?.jsonPrimitive?.doubleOrNull
                    ?: return@mapNotNull null
                if (vo2 <= 0 || vo2 > 100) return@mapNotNull null
                Vo2MaxSample(
                    timestamp = o["time"]?.jsonPrimitive?.longOrNull
                        ?: entry.time.takeIf { it > 0 }
                        ?: return@mapNotNull null,
                    mlPerKgMin = vo2,
                )
            } catch (_: Exception) {
                null
            }
        }

    fun parseWorkouts(entries: List<SportRecordEntry>): List<WorkoutSession> =
        entries.mapNotNull { entry -> parseWorkout(entry) }

    fun parseWorkout(entry: SportRecordEntry): WorkoutSession? {
        return try {
            val payload = json.parseToJsonElement(entry.value).jsonObject
            val startTs = payload["start_time"]?.jsonPrimitive?.longOrNull
                ?: entry.time.takeIf { it > 0 }
                ?: return null
            val durationSec = payload["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            val endTs = payload["end_time"]?.jsonPrimitive?.longOrNull
                ?: (startTs + durationSec).takeIf { durationSec > 0 }
                ?: return null
            if (endTs <= startTs) return null
            // Prefer string category / key from APK sport reports; sport_type may be int or string.
            val sportTypeEl = payload["sport_type"]
            val sportTypeStr = try {
                sportTypeEl?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it.toIntOrNull() == null }
            } catch (_: Exception) {
                null
            }
            val activity = entry.category?.takeIf { it.isNotBlank() }
                ?: entry.key?.takeIf { it.isNotBlank() && it != "sport" }
                ?: sportTypeStr
                ?: payload["type"]?.jsonPrimitive?.content
                ?: payload["sport_name"]?.jsonPrimitive?.content
                ?: "workout"
            WorkoutSession(
                startTime = startTs,
                endTime = endTs,
                activityType = activity,
                distanceMeters = payload.doubleField("distance"),
                caloriesKcal = payload.doubleField("calories")
                    ?: payload.doubleField("total_cal"),
                avgHeartRateBpm = payload["avg_hrm"]?.jsonPrimitive?.intOrNull,
                maxHeartRateBpm = payload["max_hrm"]?.jsonPrimitive?.intOrNull,
                totalSteps = payload["steps"]?.jsonPrimitive?.intOrNull
                    ?: payload["total_steps"]?.jsonPrimitive?.intOrNull,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.doubleField(name: String): Double? {
        val el = this[name] ?: return null
        return try {
            el.jsonPrimitive.doubleOrNull
                ?: el.jsonPrimitive.content.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun parseSleepSessions(entries: List<RawFitnessEntry>): List<SleepSession> =
        entries
            .filter { it.key == "sleep" }
            .mapNotNull { entry -> parseSleepSession(entry) }

    fun parseSleepSession(entry: RawFitnessEntry): SleepSession? {
        return try {
            val obj = json.parseToJsonElement(entry.value).jsonObject
            val items = obj["items"]?.jsonArray
            val rawStages = items?.map { item ->
                val stageObj = item.jsonObject
                SleepStage(
                    startTime = stageObj["start_time"]?.jsonPrimitive?.long ?: 0L,
                    endTime = stageObj["end_time"]?.jsonPrimitive?.long ?: 0L,
                    stage = stageObj["state"]?.jsonPrimitive?.int ?: 0,
                )
            }?.sortedBy { it.startTime } ?: emptyList()

            val stages = fillAwakeGaps(rawStages)
            val awakeDuration = obj["sleep_awake_duration"]?.jsonPrimitive?.long ?: 0L
            val hasAwake = stages.any { it.stage == 5 }
            if (!hasAwake && awakeDuration > 0 && stages.isNotEmpty()) {
                val wakeUp = obj["wake_up_time"]?.jsonPrimitive?.long ?: entry.time
                stages.add(
                    SleepStage(
                        startTime = wakeUp - (awakeDuration * 60),
                        endTime = wakeUp,
                        stage = 5,
                    ),
                )
            }

            SleepSession(
                startTime = obj["bedtime"]?.jsonPrimitive?.long ?: entry.time,
                endTime = obj["wake_up_time"]?.jsonPrimitive?.long ?: entry.time,
                inBedStart = obj["bed_timestamp"]?.jsonPrimitive?.long
                    ?: obj["bedtime"]?.jsonPrimitive?.long ?: entry.time,
                inBedEnd = obj["out_bed_timestamp"]?.jsonPrimitive?.long
                    ?: obj["wake_up_time"]?.jsonPrimitive?.long ?: entry.time,
                stages = stages,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Insert explicit awake stages for gaps ≥ 60s between reported sleep stages.
     */
    internal fun fillAwakeGaps(rawStages: List<SleepStage>): MutableList<SleepStage> {
        val stages = mutableListOf<SleepStage>()
        for (i in rawStages.indices) {
            if (i > 0) {
                val gap = rawStages[i].startTime - rawStages[i - 1].endTime
                if (gap >= 60) {
                    stages.add(
                        SleepStage(
                            startTime = rawStages[i - 1].endTime,
                            endTime = rawStages[i].startTime,
                            stage = 5,
                        ),
                    )
                }
            }
            stages.add(rawStages[i])
        }
        return stages
    }
}
