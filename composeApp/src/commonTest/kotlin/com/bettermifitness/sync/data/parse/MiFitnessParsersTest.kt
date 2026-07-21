package com.bettermifitness.sync.data.parse

import com.bettermifitness.sync.data.api.SleepStage
import com.bettermifitness.sync.data.api.SportRecordEntry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private fun assertNear(expected: Double, actual: Double, tol: Double = 0.01) {
    assertTrue(abs(expected - actual) <= tol, "expected $expected but was $actual")
}

class MiFitnessParsersTest {

    @Test
    fun parseBloodPressure_fromApkFieldNames() {
        val raw = listOf(
            RawFitnessEntry(
                key = "blood_pressure",
                time = 1_700_000_000L,
                value = """{"time":1700000000,"systolic_pressure":120,"diastolic_pressure":80,"pulse":70}""",
            ),
        )
        val samples = MiFitnessParsers.parseBloodPressureSamples(raw)
        assertEquals(1, samples.size)
        assertEquals(120, samples[0].systolicMmhg)
        assertEquals(80, samples[0].diastolicMmhg)
        assertEquals(70, samples[0].pulseBpm)
    }

    @Test
    fun parseTemperature_bodyAndSkin() {
        val raw = listOf(
            RawFitnessEntry(
                time = 1_700_000_000L,
                value = """{"time":1700000000,"body_temperature":36.5,"skin_temperature":33.2}""",
            ),
        )
        val samples = MiFitnessParsers.parseTemperatureSamples(raw)
        assertEquals(1, samples.size)
        assertEquals(36.5, samples[0].bodyCelsius)
        assertEquals(33.2, samples[0].skinCelsius)
    }

    @Test
    fun parseVo2Max_fromApkField() {
        val raw = listOf(
            RawFitnessEntry(
                time = 1_700_000_000L,
                value = """{"time":1700000000,"vo2_max":48,"vo2_max_level":3}""",
            ),
        )
        val samples = MiFitnessParsers.parseVo2MaxSamples(raw)
        assertEquals(1, samples.size)
        assertEquals(48.0, samples[0].mlPerKgMin)
    }

    @Test
    fun parseHeartRateSamples_readsTimeAndBpm() {
        val entries = listOf(
            RawFitnessEntry(
                time = 1_700_000_000L,
                value = """{"time":1700000010,"bpm":72}""",
            ),
            RawFitnessEntry(
                time = 1_700_000_020L,
                value = """{"bpm":"bad"}""", // invalid
            ),
        )
        val samples = MiFitnessParsers.parseHeartRateSamples(entries)
        assertEquals(1, samples.size)
        assertEquals(1_700_000_010L, samples[0].timestamp)
        assertEquals(72, samples[0].bpm)
    }

    @Test
    fun parseRestingHeartRateSamples_usesDateTimeField() {
        val entries = listOf(
            RawFitnessEntry(
                time = 1L,
                value = """{"date_time":1700000000,"bpm":55}""",
            ),
        )
        val samples = MiFitnessParsers.parseRestingHeartRateSamples(entries)
        assertEquals(1, samples.size)
        assertEquals(1_700_000_000L, samples[0].timestamp)
        assertEquals(55, samples[0].bpm)
    }

    @Test
    fun parseSpO2Samples_readsPercentage() {
        val entries = listOf(
            RawFitnessEntry(value = """{"time":1700000000,"spo2":98}"""),
            RawFitnessEntry(value = """{"time":1700000001}"""),
        )
        val samples = MiFitnessParsers.parseSpO2Samples(entries)
        assertEquals(1, samples.size)
        assertEquals(98, samples[0].percentage)
    }

    @Test
    fun parseHourlySteps_bucketsByHour() {
        // Floor to hour start: (t / 3600) * 3600
        val t1 = 1_700_000_100L
        val t2 = 1_700_000_200L
        val t3 = 1_700_003_700L
        val hour1 = (t1 / 3600L) * 3600L
        val hour2 = (t3 / 3600L) * 3600L
        val entries = listOf(
            RawFitnessEntry(value = """{"time":$t1,"steps":10}"""),
            RawFitnessEntry(value = """{"time":$t2,"steps":5}"""),
            RawFitnessEntry(value = """{"time":$t3,"steps":20}"""),
        )
        val records = MiFitnessParsers.parseHourlySteps(entries)
        assertEquals(2, records.size)
        val byHour = records.associate { it.date to it.steps }
        assertEquals(15, byHour[hour1.toString()])
        assertEquals(20, byHour[hour2.toString()])
    }

    @Test
    fun fillAwakeGaps_insertsStage5ForGapsAtLeast60s() {
        val raw = listOf(
            SleepStage(1_000L, 1_100L, 2),
            SleepStage(1_200L, 1_300L, 3), // 100s gap → awake
        )
        val stages = MiFitnessParsers.fillAwakeGaps(raw)
        assertEquals(3, stages.size)
        assertEquals(5, stages[1].stage)
        assertEquals(1_100L, stages[1].startTime)
        assertEquals(1_200L, stages[1].endTime)
    }

    @Test
    fun parseHourlyDistanceFromSteps_sumsMeters() {
        val entries = listOf(
            RawFitnessEntry(value = """{"time":1700000100,"steps":10,"distance":5.5}"""),
            RawFitnessEntry(value = """{"time":1700000200,"steps":5,"distance":2.5}"""),
        )
        val samples = MiFitnessParsers.parseHourlyDistanceFromSteps(entries)
        assertEquals(1, samples.size)
        assertNear(8.0, samples[0].meters)
    }

    @Test
    fun parseHourlyActiveCalories_bucketsKcal() {
        val t = 1_700_000_100L
        val hour = (t / 3600L) * 3600L
        val samples = MiFitnessParsers.parseHourlyActiveCalories(
            listOf(RawFitnessEntry(value = """{"time":$t,"calories":12.5}""")),
        )
        assertEquals(1, samples.size)
        assertEquals(hour, samples[0].startTime)
        assertNear(12.5, samples[0].kilocalories)
    }

    @Test
    fun parseWeightMeasurements_readsBodyFat() {
        val m = MiFitnessParsers.parseWeightMeasurements(
            listOf(
                RawFitnessEntry(
                    time = 1_700_000_000L,
                    value = """{"weight":70.5,"body_fat_rate":18.2,"bone_mass":3.1}""",
                ),
            ),
        )
        assertEquals(1, m.size)
        assertNear(70.5, m[0].weightKg)
        assertNear(18.2, m[0].bodyFatPercent!!)
    }

    @Test
    fun parseWorkout_fromSportPayload() {
        val w = MiFitnessParsers.parseWorkout(
            SportRecordEntry(
                key = "outdoor_running",
                category = "running",
                time = 1_700_000_000L,
                value = """{"start_time":1700000000,"end_time":1700003600,"duration":3600,"distance":5000,"calories":320,"avg_hrm":145}""",
            ),
        )
        assertNotNull(w)
        assertEquals("running", w.activityType)
        assertNear(5000.0, w.distanceMeters!!, tol = 0.1)
        assertEquals(145, w.avgHeartRateBpm)
        // no version/did → no FDS GPS keys
        assertEquals(null, w.gpsDeviceSid)
    }

    @Test
    fun parseWorkout_extractsFdsGpsKeysWhenVersionPositive() {
        val w = MiFitnessParsers.parseWorkout(
            SportRecordEntry(
                key = "outdoor_running",
                category = "running",
                time = 1_784_453_950L,
                value = """
                    {
                      "start_time":1784453950,"end_time":1784457222,"duration":3272,
                      "distance":5210,"calories":1000,"avg_hrm":150,"max_hrm":178,"min_hrm":125,
                      "avg_pace":673,"max_pace":483,"min_pace":2238,"avg_cadence":133,"max_cadence":168,
                      "max_speed":7.45,"avg_stride":65,"steps":8261,
                      "hrm_warm_up_duration":0,"hrm_fat_burning_duration":80,
                      "hrm_aerobic_duration":2274,"hrm_anaerobic_duration":1206,"hrm_extreme_duration":5,
                      "version":9,"proto_type":22,"timezone":28,"did":"xiaomiwear_app","time":1784453950
                    }
                """.trimIndent(),
            ),
        )
        assertNotNull(w)
        assertEquals("xiaomiwear_app", w.gpsDeviceSid)
        assertEquals(1_784_453_950L, w.gpsTimestampSec)
        assertEquals(28, w.gpsTzIn15Min)
        assertEquals(22, w.gpsProtoType)
        assertEquals(150, w.avgHeartRateBpm)
        assertEquals(178, w.maxHeartRateBpm)
        assertEquals(125, w.minHeartRateBpm)
        assertEquals(673.0, w.avgPaceSecPerKm)
        assertEquals(133.0, w.avgCadenceSpm)
        assertEquals(8261, w.totalSteps)
        assertEquals(2274, w.hrZoneAerobicSec)
        assertEquals(28 * 15 * 60, w.zoneOffsetSeconds())
    }

    @Test
    fun parseSleepSession_buildsSessionWithStages() {
        val entry = RawFitnessEntry(
            key = "sleep",
            time = 1_700_003_600L,
            value = """
                {
                  "bedtime": 1700000000,
                  "wake_up_time": 1700036000,
                  "bed_timestamp": 1699999900,
                  "out_bed_timestamp": 1700036100,
                  "sleep_awake_duration": 0,
                  "items": [
                    {"start_time": 1700000000, "end_time": 1700018000, "state": 2},
                    {"start_time": 1700018000, "end_time": 1700036000, "state": 3}
                  ]
                }
            """.trimIndent(),
        )
        val session = MiFitnessParsers.parseSleepSession(entry)
        assertNotNull(session)
        assertEquals(1_700_000_000L, session.startTime)
        assertEquals(1_700_036_000L, session.endTime)
        assertEquals(2, session.stages.size)
        assertTrue(session.stages.all { it.stage in 2..3 })
    }
}
