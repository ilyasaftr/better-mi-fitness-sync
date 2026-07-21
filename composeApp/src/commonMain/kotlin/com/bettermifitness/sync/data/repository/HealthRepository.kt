package com.bettermifitness.sync.data.repository

import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.api.HeartRateEntry
import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.data.parse.MiFitnessParsers
import com.bettermifitness.sync.data.parse.toRaw
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.health.HealthSampleWriter
import com.mifitness.miclient.api.MiApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncProgress(
    val heartRate: SyncState = SyncState.Idle,
    val restingHeartRate: SyncState = SyncState.Idle,
    val sleep: SyncState = SyncState.Idle,
    val steps: SyncState = SyncState.Idle,
    val distance: SyncState = SyncState.Idle,
    val activeCalories: SyncState = SyncState.Idle,
    val spo2: SyncState = SyncState.Idle,
    val weight: SyncState = SyncState.Idle,
    val workouts: SyncState = SyncState.Idle,
    val bloodPressure: SyncState = SyncState.Idle,
    val temperature: SyncState = SyncState.Idle,
    val vo2Max: SyncState = SyncState.Idle,
)

sealed class SyncState {
    data object Idle : SyncState()
    data object InProgress : SyncState()
    data class Success(val count: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * Orchestrates Mi fetch → parse → platform health write.
 * Each metric fails independently; one bad metric does not abort the rest.
 * On [MiApiException.AuthExpired], attempts a single passToken refresh for the whole run.
 */
class HealthRepository(
    private val session: MiSessionManager,
    private val healthWriter: HealthSampleWriter,
) : HealthSyncRunner {
    private val _syncProgress = MutableStateFlow(SyncProgress())
    override val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    private val api get() = session.api

    private var authRefreshTried = false
    private var sawAuthFailure = false
    private val retryableFailures = mutableListOf<Boolean>()

    override suspend fun syncAll(
        from: String,
        to: String,
        enabled: Set<String>,
    ): SyncRunResult {
        authRefreshTried = false
        sawAuthFailure = false
        retryableFailures.clear()
        _syncProgress.value = SyncProgress()

        if ("heart_rate" in enabled) syncHeartRate(from, to)
        if ("resting_heart_rate" in enabled) syncRestingHeartRate(from, to)
        if ("sleep" in enabled) syncSleep(from, to)
        if ("steps" in enabled) syncSteps(from, to)
        if ("distance" in enabled) syncDistance(from, to)
        if ("active_calories" in enabled) syncActiveCalories(from, to)
        if ("spo2" in enabled) syncSpO2(from, to)
        if ("weight" in enabled) syncWeight(from, to)
        if ("workouts" in enabled) syncWorkouts(from, to)
        if ("blood_pressure" in enabled) syncBloodPressure(from, to)
        if ("temperature" in enabled) syncTemperature(from, to)
        if ("vo2_max" in enabled) syncVo2Max(from, to)

        return SyncRunResult.from(
            progress = _syncProgress.value,
            metricKeys = enabled,
            retryableFlags = retryableFailures.toList(),
            authFailure = sawAuthFailure,
        )
    }

    suspend fun syncRestingHeartRate(from: String, to: String) {
        runMetric("restingHeartRate") {
            val response = api.getLatest("resting_heart_rate", limit = 30)
            val samples = MiFitnessParsers.parseRestingHeartRateSamples(
                response.result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeRestingHeartRate(samples)
            samples.size
        }
    }

    suspend fun syncHeartRate(from: String, to: String) {
        runMetric("heartRate") {
            val samples = MiFitnessParsers.parseHeartRateSamples(
                fetchAllByTime("heart_rate", from, to).map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeHeartRate(samples)
            samples.size
        }
    }

    suspend fun syncSleep(from: String, to: String) {
        runMetric("sleep") {
            val response = api.getLatest("sleep", limit = 30)
            val sessions = MiFitnessParsers.parseSleepSessions(
                response.result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (sessions.isNotEmpty()) healthWriter.writeSleep(sessions)
            sessions.size
        }
    }

    suspend fun syncSteps(from: String, to: String) {
        runMetric("steps") {
            val records = MiFitnessParsers.parseHourlySteps(
                fetchAllByTime("steps", from, to).map { it.toRaw() },
            )
            if (records.isNotEmpty()) healthWriter.writeSteps(records)
            records.size
        }
    }

    suspend fun syncDistance(from: String, to: String) {
        runMetric("distance") {
            // Distance is embedded on the steps minute stream (bridge parity).
            val samples = MiFitnessParsers.parseHourlyDistanceFromSteps(
                fetchAllByTime("steps", from, to).map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeDistance(samples)
            samples.size
        }
    }

    suspend fun syncActiveCalories(from: String, to: String) {
        runMetric("activeCalories") {
            val samples = MiFitnessParsers.parseHourlyActiveCalories(
                fetchAllByTime("calories", from, to).map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeActiveCalories(samples)
            samples.size
        }
    }

    suspend fun syncSpO2(from: String, to: String) {
        runMetric("spo2") {
            val samples = MiFitnessParsers.parseSpO2Samples(
                fetchAllByTime("spo2", from, to).map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeSpO2(samples)
            samples.size
        }
    }

    suspend fun syncWeight(from: String, to: String) {
        runMetric("weight") {
            val measurements = MiFitnessParsers.parseWeightMeasurements(
                fetchAllByTime("weight", from, to).map { it.toRaw() },
            )
            if (measurements.isNotEmpty()) healthWriter.writeWeight(measurements)
            measurements.size
        }
    }

    suspend fun syncWorkouts(from: String, to: String) {
        runMetric("workouts") {
            val parsed = MiFitnessParsers.parseWorkouts(
                api.getSportRecordsByTime(from, to),
            )
            // HR samples in range — attach as series when FDS record is sparse
            val hrRaw = try {
                fetchAllByTime("heart_rate", from, to).map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val hrSamples = MiFitnessParsers.parseHeartRateSamples(hrRaw)
            val sessions = enrichWorkouts(parsed, hrSamples)
            if (sessions.isNotEmpty()) healthWriter.writeWorkouts(sessions)
            sessions.size
        }
    }

    /**
     * FDS GPS/record/recover + cloud HR overlap. Failures leave partial detail so summary still syncs.
     */
    private suspend fun enrichWorkouts(
        sessions: List<WorkoutSession>,
        hrSamples: List<com.bettermifitness.sync.data.api.HeartRateSample>,
    ): List<WorkoutSession> {
        return sessions.map { session ->
            var out = session
            // Cloud HR overlapping the workout window
            val fromCloud = MiFitnessParsers.heartRateInWindow(
                hrSamples,
                session.startTime,
                session.endTime,
            )
            if (fromCloud.size > out.heartRateSeries.size) {
                out = out.copy(heartRateSeries = fromCloud)
            }
            // FDS GPS + record + recover
            if (!session.gpsDeviceSid.isNullOrBlank()) {
                out = try {
                    api.enrichWorkoutDetails(out)
                } catch (_: Exception) {
                    out
                }
            }
            // Prefer denser HR after FDS
            if (fromCloud.size > out.heartRateSeries.size) {
                out = out.copy(heartRateSeries = fromCloud)
            }
            out
        }
    }

    suspend fun syncBloodPressure(from: String, to: String) {
        runMetric("bloodPressure") {
            val samples = MiFitnessParsers.parseBloodPressureSamples(
                fetchAllByTime("blood_pressure", from, to).map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeBloodPressure(samples)
            samples.size
        }
    }

    suspend fun syncTemperature(from: String, to: String) {
        runMetric("temperature") {
            // Prefer by-time series; also merge manual single_temperature latest if present.
            val byTime = fetchAllByTime("temperature_characteristic", from, to).map { it.toRaw() }
            val manual = try {
                api.getLatest("single_temperature", limit = 30).result?.dataList.orEmpty()
                    .map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val samples = MiFitnessParsers.parseTemperatureSamples(byTime + manual)
            if (samples.isNotEmpty()) healthWriter.writeTemperature(samples)
            samples.size
        }
    }

    suspend fun syncVo2Max(from: String, to: String) {
        runMetric("vo2Max") {
            val byTime = fetchAllByTime("vo2_max", from, to).map { it.toRaw() }
            val latest = try {
                api.getLatest("vo2_max", limit = 30).result?.dataList.orEmpty().map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val samples = MiFitnessParsers.parseVo2MaxSamples(byTime + latest)
            if (samples.isNotEmpty()) healthWriter.writeVo2Max(samples)
            samples.size
        }
    }

    override fun resetProgress() {
        _syncProgress.value = SyncProgress()
    }

    private suspend fun fetchAllByTime(
        key: String,
        from: String,
        to: String,
        maxPages: Int = 60,
    ): List<HeartRateEntry> {
        val all = mutableListOf<HeartRateEntry>()
        var next: String? = null
        var pages = 0
        while (pages < maxPages) {
            val res = api.getDataByTime(key, from, to, next).result ?: break
            all += res.dataList
            pages++
            if (!res.hasMore || res.nextKey.isNullOrEmpty()) break
            next = res.nextKey
        }
        return all
    }

    private suspend fun runMetric(metric: String, block: suspend () -> Int) {
        setState(metric, SyncState.InProgress)
        try {
            setState(metric, SyncState.Success(executeWithAuthRetry(block)))
        } catch (e: Exception) {
            recordFailure(e)
            setState(metric, SyncState.Error(friendlyMessage(e)))
        }
    }

    private suspend fun executeWithAuthRetry(block: suspend () -> Int): Int {
        return try {
            block()
        } catch (e: MiApiException.AuthExpired) {
            sawAuthFailure = true
            if (!authRefreshTried) {
                authRefreshTried = true
                if (session.refreshSession()) {
                    return block()
                }
            }
            throw e
        }
    }

    private fun recordFailure(e: Exception) {
        when (e) {
            is MiApiException.AuthExpired -> {
                sawAuthFailure = true
                retryableFailures += false
            }
            is MiApiException -> retryableFailures += e.isRetryable
            else -> retryableFailures += true
        }
    }

    private fun friendlyMessage(e: Exception): String {
        return when (e) {
            is MiApiException.AuthExpired ->
                e.message?.takeIf { it.isNotBlank() } ?: "Session expired — sign in again"
            is MiApiException.Network ->
                e.message?.takeIf { it.isNotBlank() } ?: "Network error"
            is MiApiException.RateLimited ->
                "Mi cloud rate limited — try again later"
            is MiApiException.Server ->
                e.message?.takeIf { it.isNotBlank() } ?: "Mi cloud error (${e.httpOrBusinessCode})"
            is MiApiException.Unexpected ->
                e.message?.takeIf { it.isNotBlank() } ?: "Unexpected Mi API error"
            else -> e.message ?: e::class.simpleName ?: "Unknown error"
        }
    }

    private fun setState(metric: String, state: SyncState) {
        _syncProgress.value = when (metric) {
            "heartRate" -> _syncProgress.value.copy(heartRate = state)
            "restingHeartRate" -> _syncProgress.value.copy(restingHeartRate = state)
            "sleep" -> _syncProgress.value.copy(sleep = state)
            "steps" -> _syncProgress.value.copy(steps = state)
            "distance" -> _syncProgress.value.copy(distance = state)
            "activeCalories" -> _syncProgress.value.copy(activeCalories = state)
            "spo2" -> _syncProgress.value.copy(spo2 = state)
            "weight" -> _syncProgress.value.copy(weight = state)
            "workouts" -> _syncProgress.value.copy(workouts = state)
            "bloodPressure" -> _syncProgress.value.copy(bloodPressure = state)
            "temperature" -> _syncProgress.value.copy(temperature = state)
            "vo2Max" -> _syncProgress.value.copy(vo2Max = state)
            else -> _syncProgress.value
        }
    }
}
