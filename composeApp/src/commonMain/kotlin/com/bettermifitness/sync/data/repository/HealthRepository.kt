package com.bettermifitness.sync.data.repository

import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.SessionRefreshResult
import com.bettermifitness.sync.data.api.HeartRateEntry
import com.bettermifitness.sync.data.api.FitnessResponse
import com.bettermifitness.sync.data.api.SleepEntry
import com.bettermifitness.sync.data.api.WeightMeasurement
import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.data.parse.MiFitnessParsers
import com.bettermifitness.sync.data.parse.toRaw
import com.bettermifitness.sync.data.preferences.SyncPreferences
import com.bettermifitness.sync.health.HealthDataNormalizer
import com.bettermifitness.sync.health.HealthStore
import com.bettermifitness.sync.health.HealthTimePolicy
import com.mifitness.miclient.api.MiApiException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class SyncProgress(
    val heartRate: SyncState = SyncState.Idle,
    val restingHeartRate: SyncState = SyncState.Idle,
    val sleep: SyncState = SyncState.Idle,
    val hrv: SyncState = SyncState.Idle,
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

private fun SyncProgress.withMetric(metric: String, state: SyncState): SyncProgress = when (metric) {
    "heartRate" -> copy(heartRate = state)
    "restingHeartRate" -> copy(restingHeartRate = state)
    "sleep" -> copy(sleep = state)
    "hrv" -> copy(hrv = state)
    "steps" -> copy(steps = state)
    "distance" -> copy(distance = state)
    "activeCalories" -> copy(activeCalories = state)
    "spo2" -> copy(spo2 = state)
    "weight" -> copy(weight = state)
    "workouts" -> copy(workouts = state)
    "bloodPressure" -> copy(bloodPressure = state)
    "temperature" -> copy(temperature = state)
    "vo2Max" -> copy(vo2Max = state)
    else -> this
}

/**
 * Orchestrates Mi fetch → parse → platform health write.
 * Each metric fails independently; one bad metric does not abort the rest.
 * On [MiApiException.AuthExpired], attempts a single passToken refresh for the whole run.
 *
 * Weight is bidirectional latest-only (sparse weigh-ins) — newest timestamp wins
 * regardless of sync window. Body-fat rides with weight when present but is not
 * a separate bidirectional metric (Mi has no standalone body-fat input). Other metrics remain window-based.
 */
class HealthRepository(
    private val session: MiSessionManager,
    private val healthWriter: HealthStore,
) : HealthSyncRunner {
    override val syncProgress: StateFlow<SyncProgress>
        field = MutableStateFlow(SyncProgress())
    private val api get() = session.api

    private var sawAuthFailure = false
    private var lastRefreshUserMessage: String? = null
    private var lastRefreshRetryable: Boolean = false
    private val retryableFailures = mutableListOf<Boolean>()
    private val stateMutex = Mutex()
    private val sharedRefresh = SharedAuthRefresh { session.refreshSessionDetailed() }
    // Bound concurrent Mi requests so parallel metrics cannot trigger rate limits.
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)

    override suspend fun syncAll(
        from: String,
        to: String,
        enabled: Set<String>,
    ): SyncRunResult {
        sharedRefresh.reset()
        sawAuthFailure = false
        lastRefreshUserMessage = null
        lastRefreshRetryable = false
        retryableFailures.clear()
        syncProgress.value = SyncProgress()

        supervisorScope {
            val resting = if ("resting_heart_rate" in enabled) {
                async { syncRestingHeartRate(from, to) }
            } else null
            // Sleep rows are fetched once and shared with HRV (derived from sleep payloads).
            // Modern segments plus legacy watch reports (APK FitnessPersistKey).
            val sleepRows = if ("sleep" in enabled || "hrv" in enabled) {
                async {
                    fetchSemaphore.withPermit {
                        api.getLatest("sleep,watch_night_sleep,watch_daytime_sleep", limit = 30)
                    }
                }
            } else null
            // Steps rows are fetched once and shared with distance (same minute stream).
            val stepsRows = if ("steps" in enabled || "distance" in enabled) {
                async {
                    fetchSemaphore.withPermit { fetchAllByTime("steps", from, to) }
                }
            } else null
            // Workouts parsed once for the steps/distance/calories overlap correction
            // (issue #51): the hourly streams already contain workout-period values.
            // Isolated failure: a fetch error only skips the correction, and the
            // workouts metric still fetches independently below.
            val overlapWorkouts = if ("workouts" in enabled &&
                ("steps" in enabled || "distance" in enabled || "active_calories" in enabled)
            ) {
                async {
                    try {
                        fetchSemaphore.withPermit {
                            MiFitnessParsers.parseWorkouts(api.getSportRecordsByTime(from, to))
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            } else null
            // HR rows (continuous plus manual spot checks) fetched once, shared with workouts.
            val hrRows = if ("heart_rate" in enabled || "workouts" in enabled) {
                async {
                    fetchSemaphore.withPermit {
                        try {
                            fetchAllByTime("heart_rate", from, to)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            } else null
            val hrManualRows = if ("heart_rate" in enabled || "workouts" in enabled) {
                async {
                    fetchSemaphore.withPermit {
                        try {
                            fetchAllByTime("single_heart_rate", from, to)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            } else null
            val hrManualLatest = if ("heart_rate" in enabled) {
                async {
                    try {
                        fetchSemaphore.withPermit {
                            api.getLatest("single_heart_rate", limit = 30)
                        }.result?.dataList.orEmpty()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            } else null
            val heartRate = if ("heart_rate" in enabled) {
                async { syncHeartRate(hrRows!!, hrManualRows!!, hrManualLatest!!) }
            } else null
            val sleep = if ("sleep" in enabled) {
                async { syncSleep(sleepRows!!) }
            } else null
            // HRV is derived from sleep payloads (no separate Mi key); empty = non-capable device.
            val hrv = if ("hrv" in enabled) {
                async { syncHrv(sleepRows!!) }
            } else null
            val steps = if ("steps" in enabled) {
                async { syncSteps(stepsRows!!, overlapWorkouts) }
            } else null
            val distance = if ("distance" in enabled) {
                async { syncDistance(stepsRows!!, overlapWorkouts) }
            } else null
            val calories = if ("active_calories" in enabled) {
                async { syncActiveCalories(from, to, overlapWorkouts) }
            } else null
            val spo2 = if ("spo2" in enabled) {
                async { syncSpO2(from, to) }
            } else null
            val weight = if ("weight" in enabled) {
                async { syncWeightBidirectional() }
            } else null
            val workouts = if ("workouts" in enabled) {
                async { syncWorkouts(from, to, hrRows!!, hrManualRows!!) }
            } else null
            val bloodPressure = if ("blood_pressure" in enabled) {
                async { syncBloodPressure(from, to) }
            } else null
            val temperature = if ("temperature" in enabled) {
                async { syncTemperature(from, to) }
            } else null
            val vo2Max = if ("vo2_max" in enabled) {
                async { syncVo2Max(from, to) }
            } else null
            listOfNotNull(
                resting,
                heartRate,
                sleep,
                hrv,
                steps,
                distance,
                calories,
                spo2,
                weight,
                workouts,
                bloodPressure,
                temperature,
                vo2Max,
            ).forEach { it.await() }
            sleepRows?.await()
            stepsRows?.await()
            overlapWorkouts?.await()
            hrRows?.await()
            hrManualRows?.await()
            hrManualLatest?.await()
        }

        return SyncRunResult.from(
            progress = syncProgress.value,
            metricKeys = enabled,
            retryableFlags = retryableFailures.toList(),
            authFailure = sawAuthFailure,
        )
    }

    suspend fun syncRestingHeartRate(from: String, to: String) {
        runMetric("restingHeartRate") {
            val response = fetchSemaphore.withPermit { api.getLatest("resting_heart_rate", limit = 30) }
            val samples = MiFitnessParsers.parseRestingHeartRateSamples(
                response.result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeRestingHeartRate(samples)
            samples.size
        }
    }

    suspend fun syncHeartRate(from: String, to: String) {
        runMetric("heartRate") {
            // Continuous stream plus manual spot checks (APK ManualHr = single_heart_rate,
            // same HrItem shape, merged in FitnessSummaryConnector.TABLE_HEART_RATE).
            val byTime = fetchAllByTime("heart_rate", from, to).map { it.toRaw() }
            val manualByTime = try {
                fetchAllByTime("single_heart_rate", from, to).map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val manualLatest = try {
                api.getLatest("single_heart_rate", limit = 30).result?.dataList.orEmpty()
                    .map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val samples = MiFitnessParsers.parseHeartRateSamples(byTime + manualByTime + manualLatest)
            if (samples.isNotEmpty()) healthWriter.writeHeartRate(samples)
            samples.size
        }
    }

    private suspend fun syncHeartRate(
        hrRows: kotlinx.coroutines.Deferred<List<HeartRateEntry>>,
        hrManualRows: kotlinx.coroutines.Deferred<List<HeartRateEntry>>,
        hrManualLatest: kotlinx.coroutines.Deferred<List<SleepEntry>>,
    ) {
        runMetric("heartRate") {
            val samples = MiFitnessParsers.parseHeartRateSamples(
                hrRows.await().map { it.toRaw() } +
                    hrManualRows.await().map { it.toRaw() } +
                    hrManualLatest.await().map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeHeartRate(samples)
            samples.size
        }
    }

    suspend fun syncSleep(from: String, to: String) {
        runMetric("sleep") {
            // Modern segments plus legacy watch reports (APK FitnessPersistKey).
            val response = api.getLatest("sleep,watch_night_sleep,watch_daytime_sleep", limit = 30)
            val sessions = MiFitnessParsers.parseSleepSessions(
                response.result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (sessions.isNotEmpty()) healthWriter.writeSleep(sessions)
            sessions.size
        }
    }

    private suspend fun syncSleep(
        sleepRows: kotlinx.coroutines.Deferred<FitnessResponse<SleepEntry>>,
    ) {
        runMetric("sleep") {
            val sessions = MiFitnessParsers.parseSleepSessions(
                sleepRows.await().result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (sessions.isNotEmpty()) healthWriter.writeSleep(sessions)
            sessions.size
        }
    }

    /**
     * Overnight HRV from sleep JSON (`avg_hrv`). Queries the same merged sleep
     * keys — devices without HRV simply yield 0 samples (success).
     */
    suspend fun syncHrv(from: String, to: String) {
        runMetric("hrv") {
            val response = api.getLatest("sleep,watch_night_sleep,watch_daytime_sleep", limit = 30)
            val samples = MiFitnessParsers.parseHrvSamples(
                response.result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeHrv(samples)
            samples.size
        }
    }

    private suspend fun syncHrv(
        sleepRows: kotlinx.coroutines.Deferred<FitnessResponse<SleepEntry>>,
    ) {
        runMetric("hrv") {
            val samples = MiFitnessParsers.parseHrvSamples(
                sleepRows.await().result?.dataList.orEmpty().map { it.toRaw() },
            )
            if (samples.isNotEmpty()) healthWriter.writeHrv(samples)
            samples.size
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

    private suspend fun syncSteps(
        stepsRows: kotlinx.coroutines.Deferred<List<HeartRateEntry>>,
        overlapWorkouts: kotlinx.coroutines.Deferred<List<WorkoutSession>>? = null,
    ) {
        runMetric("steps") {
            val records = MiFitnessParsers.parseHourlySteps(
                stepsRows.await().map { it.toRaw() },
            )
            // Hourly stream already contains workout-period steps; subtract the overlap
            // so Health totals don't double-count the per-workout step records (issue #51).
            val adjusted = HealthDataNormalizer.subtractWorkoutSteps(
                records,
                overlapWorkouts?.await().orEmpty(),
            )
            if (adjusted.isNotEmpty()) healthWriter.writeSteps(adjusted)
            adjusted.size
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

    private suspend fun syncDistance(
        stepsRows: kotlinx.coroutines.Deferred<List<HeartRateEntry>>,
        overlapWorkouts: kotlinx.coroutines.Deferred<List<WorkoutSession>>? = null,
    ) {
        runMetric("distance") {
            // Distance is embedded on the steps minute stream (bridge parity).
            val samples = MiFitnessParsers.parseHourlyDistanceFromSteps(
                stepsRows.await().map { it.toRaw() },
            )
            val adjusted = HealthDataNormalizer.subtractWorkoutDistance(
                samples,
                overlapWorkouts?.await().orEmpty(),
            )
            if (adjusted.isNotEmpty()) healthWriter.writeDistance(adjusted)
            adjusted.size
        }
    }

    private suspend fun syncActiveCalories(
        from: String,
        to: String,
        overlapWorkouts: kotlinx.coroutines.Deferred<List<WorkoutSession>>? = null,
    ) {
        runMetric("activeCalories") {
            val samples = MiFitnessParsers.parseHourlyActiveCalories(
                fetchSemaphore.withPermit { fetchAllByTime("calories", from, to) }
                    .map { it.toRaw() },
            )
            val adjusted = HealthDataNormalizer.subtractWorkoutCalories(
                samples,
                overlapWorkouts?.await().orEmpty(),
            )
            if (adjusted.isNotEmpty()) healthWriter.writeActiveCalories(adjusted)
            adjusted.size
        }
    }

    suspend fun syncSpO2(from: String, to: String) {
        runMetric("spo2") {
            // Continuous stream plus legacy manual spot checks (APK ManualSpo2).
            val byTime = fetchSemaphore.withPermit { fetchAllByTime("spo2", from, to) }
                .map { it.toRaw() }
            val manualByTime = try {
                fetchSemaphore.withPermit { fetchAllByTime("single_spo2", from, to) }
                    .map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val manualLatest = try {
                fetchSemaphore.withPermit { api.getLatest("single_spo2", limit = 30) }
                    .result?.dataList.orEmpty().map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val samples = MiFitnessParsers.parseSpO2Samples(byTime + manualByTime + manualLatest)
            if (samples.isNotEmpty()) healthWriter.writeSpO2(samples)
            samples.size
        }
    }

    private suspend fun syncWeightBidirectional() {
        runMetric("weight") {
            val miLatest = getMiLatestWeight()
            val hcLatest = try { healthWriter.readLatestWeight() } catch (_: Exception) { null }
            // LWW comparison below; HealthStore filters self-written loop records.
            // Both empty
            if (miLatest == null && hcLatest == null) return@runMetric 0

            // Only one side has data
            if (miLatest == null && hcLatest != null) {
                // HC newer -> Mi
                val ok = api.uploadWeight(hcLatest)
                return@runMetric if (ok) 1 else throw IllegalStateException("Mi weight upload failed")
            }
            if (miLatest != null && hcLatest == null) {
                healthWriter.writeWeight(listOf(miLatest))
                return@runMetric 1
            }

            // Both present — LWW
            val mi = miLatest!!
            val hc = hcLatest!!
            val deltaSec = hc.timestamp - mi.timestamp
            val weightDiff = kotlin.math.abs(hc.weightKg - mi.weightKg)
            // Same instant and effectively same weight -> already synced
            if (kotlin.math.abs(deltaSec) < 60 && weightDiff < 0.05) return@runMetric 0
            if (deltaSec > 60) {
                // HC newer -> Mi (preserve timestamp)
                val ok = api.uploadWeight(hc)
                if (!ok) throw IllegalStateException("Mi weight upload failed")
                1
            } else if (deltaSec < -60) {
                // Mi newer -> HC
                healthWriter.writeWeight(listOf(mi))
                1
            } else {
                // Within 60s threshold but different weight: treat larger timestamp as winner
                if (hc.timestamp > mi.timestamp) {
                    val ok = api.uploadWeight(hc)
                    if (!ok) throw IllegalStateException("Mi weight upload failed")
                    1
                } else if (mi.timestamp > hc.timestamp) {
                    healthWriter.writeWeight(listOf(mi))
                    1
                } else {
                    0
                }
            }
        }
    }

    /** Legacy window-based weight (kept for tests); now delegates to bidirectional. */
    suspend fun syncWeight(from: String, to: String) { syncWeightBidirectional() }

    private suspend fun getMiLatestWeight(): WeightMeasurement? {
        // Primary: getLatest (server-side latest regardless of time)
        val latestViaLatest = try {
            val res = fetchSemaphore.withPermit { api.getLatest("weight", limit = 5) }
            val list = MiFitnessParsers.parseWeightMeasurements(res.result?.dataList.orEmpty().map { it.toRaw() })
            HealthDataNormalizer.normalizeWeight(list).maxByOrNull { it.timestamp }
        } catch (_: Exception) { null }
        if (latestViaLatest != null) return latestViaLatest
        // Fallback: broad by-time window (covers legacy / CN quirks)
        return try {
            val nowSec = HealthTimePolicy.nowEpochSeconds()
            val from = "1970-01-01T00:00:00Z"
            val to = Clock.System.now().toString()
            val list = MiFitnessParsers.parseWeightMeasurements(
                fetchSemaphore.withPermit { fetchAllByTime("weight", from, to) }.map { it.toRaw() },
            )
            HealthDataNormalizer.normalizeWeight(list).maxByOrNull { it.timestamp }
        } catch (_: Exception) { null }
    }

    suspend fun syncWorkouts(from: String, to: String) {
        runMetric("workouts") {
            val parsed = MiFitnessParsers.parseWorkouts(
                api.getSportRecordsByTime(from, to),
            )
            // HR samples in range — attach as series when FDS record is sparse.
            // Include manual spot checks (single_heart_rate) like the main HR sync.
            val hrRaw = try {
                fetchAllByTime("heart_rate", from, to).map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val hrManualRaw = try {
                fetchAllByTime("single_heart_rate", from, to).map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val hrSamples = MiFitnessParsers.parseHeartRateSamples(hrRaw + hrManualRaw)
            val sessions = enrichWorkouts(parsed, hrSamples)
            if (sessions.isNotEmpty()) healthWriter.writeWorkouts(sessions)
            sessions.size
        }
    }

    private suspend fun syncWorkouts(
        from: String,
        to: String,
        hrRows: kotlinx.coroutines.Deferred<List<HeartRateEntry>>,
        hrManualRows: kotlinx.coroutines.Deferred<List<HeartRateEntry>>,
    ) {
        runMetric("workouts") {
            val parsed = MiFitnessParsers.parseWorkouts(
                fetchSemaphore.withPermit { api.getSportRecordsByTime(from, to) },
            )
            // Shared HR rows including manual spot checks — attach when FDS is sparse.
            val hrSamples = MiFitnessParsers.parseHeartRateSamples(
                hrRows.await().map { it.toRaw() } + hrManualRows.await().map { it.toRaw() },
            )
            val sessions = enrichWorkouts(parsed, hrSamples)
            if (sessions.isNotEmpty()) healthWriter.writeWorkouts(sessions)
            sessions.size
        }
    }

    /**
     * FDS GPS/record/recover + cloud HR overlap. Failures leave partial detail so summary still syncs.
     * One shared FDS client per sync (engine/TLS setup once, not per workout).
     */
    private suspend fun enrichWorkouts(
        sessions: List<WorkoutSession>,
        hrSamples: List<com.bettermifitness.sync.data.api.HeartRateSample>,
    ): List<WorkoutSession> {
        if (sessions.isEmpty()) return sessions
        val fds = com.mifitness.miclient.fds.FdsClient(api.dataClient())
        try {
            return sessions.map { session ->
                enrichOneWorkout(session, hrSamples, fds)
            }
        } finally {
            fds.close()
        }
    }

    private suspend fun enrichOneWorkout(
        session: WorkoutSession,
        hrSamples: List<com.bettermifitness.sync.data.api.HeartRateSample>,
        fds: com.mifitness.miclient.fds.FdsClient,
    ): WorkoutSession {
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
                api.enrichWorkoutDetails(out, fds, closeFds = false)
            } catch (_: Exception) {
                out
            }
        }
        // Prefer denser HR after FDS
        if (fromCloud.size > out.heartRateSeries.size) {
            out = out.copy(heartRateSeries = fromCloud)
        }
        return out
    }

    suspend fun syncBloodPressure(from: String, to: String) {
        runMetric("bloodPressure") {
            // Fitness key plus medical key (APK BloodPressureBiz merges both; medical rows
            // live behind get_medical_data_by_time / get_latest_medical_data, same
            // BloodPressureItem shape). Medical endpoints may 404 for regions/users
            // without medical data — fall back to fitness-only.
            val fitness = fetchSemaphore.withPermit { fetchAllByTime("blood_pressure", from, to) }
                .map { it.toRaw() }
            val medicalByTime = try {
                fetchSemaphore.withPermit { fetchAllMedicalByTime("mc_blood_pressure", from, to) }
                    .map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val medicalLatest = try {
                fetchSemaphore.withPermit { api.getLatestMedical("mc_blood_pressure", limit = 30) }
                    .result?.dataList.orEmpty().map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val samples = MiFitnessParsers.parseBloodPressureSamples(fitness + medicalByTime + medicalLatest)
            if (samples.isNotEmpty()) healthWriter.writeBloodPressure(samples)
            samples.size
        }
    }

    suspend fun syncTemperature(from: String, to: String) {
        runMetric("temperature") {
            // Prefer by-time series; also merge manual single_temperature latest if present.
            val byTime = fetchSemaphore.withPermit {
                fetchAllByTime("temperature_characteristic", from, to)
            }.map { it.toRaw() }
            val manual = try {
                fetchSemaphore.withPermit {
                    api.getLatest("single_temperature", limit = 30)
                }.result?.dataList.orEmpty().map { it.toRaw() }
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
            val byTime = fetchSemaphore.withPermit {
                fetchAllByTime("vo2_max", from, to)
            }.map { it.toRaw() }
            val latest = try {
                fetchSemaphore.withPermit {
                    api.getLatest("vo2_max", limit = 30)
                }.result?.dataList.orEmpty().map { it.toRaw() }
            } catch (_: Exception) {
                emptyList()
            }
            val samples = MiFitnessParsers.parseVo2MaxSamples(byTime + latest)
            if (samples.isNotEmpty()) healthWriter.writeVo2Max(samples)
            samples.size
        }
    }

    override fun resetProgress() {
        syncProgress.value = SyncProgress()
    }

    private suspend fun fetchAllByTime(
        key: String,
        from: String,
        to: String,
        maxPages: Int = 60,
    ): List<HeartRateEntry> {
        // Fetch the complete oldest hour before replacing legacy hourly activity records.
        val activity = key == "steps" || key == "calories"
        val queryFrom = if (activity) {
            val start = Instant.parse(from).epochSeconds
            Instant.fromEpochSeconds(start - start.mod(3600L)).toString()
        } else from
        val all = mutableListOf<HeartRateEntry>()
        var next: String? = null
        var pages = 0
        while (pages < maxPages) {
            val res = api.getDataByTime(key, queryFrom, to, next).result
            if (res == null) {
                check(!activity) { "Missing $key data page" }
                return all
            }
            all += res.dataList
            pages++
            if (!res.hasMore) return all
            if (res.nextKey.isNullOrEmpty()) {
                check(!activity) { "Missing $key page cursor" }
                return all
            }
            next = res.nextKey
        }
        check(!activity) { "Incomplete $key data after $maxPages pages" }
        return all
    }

    private suspend fun fetchAllMedicalByTime(
        key: String,
        from: String,
        to: String,
        maxPages: Int = 60,
    ): List<HeartRateEntry> {
        val all = mutableListOf<HeartRateEntry>()
        var next: String? = null
        var pages = 0
        while (pages < maxPages) {
            val res = api.getMedicalDataByTime(key, from, to, next).result ?: break
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(e)
            setState(metric, SyncState.Error(friendlyMessage(e)))
        }
    }

    /**
     * Runs [block], healing an expired session once per sync run.
     *
     * Every metric that observes [MiApiException.AuthExpired] joins the same shared
     * refresh: on success each of them retries once against the renewed session instead
     * of only the first failure recovering while the rest report auth errors.
     */
    private suspend fun executeWithAuthRetry(block: suspend () -> Int): Int {
        return try {
            block()
        } catch (e: MiApiException.AuthExpired) {
            stateMutex.withLock { sawAuthFailure = true }
            when (val refresh = sharedRefresh.refreshOnce()) {
                SessionRefreshResult.Success -> {
                    stateMutex.withLock {
                        lastRefreshUserMessage = null
                        lastRefreshRetryable = false
                    }
                    return block()
                }
                else -> {
                    noteRefreshOutcome(refresh)
                    throw e
                }
            }
        }
    }

    private suspend fun noteRefreshOutcome(refresh: SessionRefreshResult) {
        val message = if (refresh is SessionRefreshResult.NeedsVerification) {
            // Include verification URL so UI can open Xiaomi WebView directly,
            // matching Mi Fitness behavior where 70016 shows “Verify” link.
            val url = refresh.notificationUrl?.takeIf { it.isNotBlank() }
            if (url != null) "${refresh.reason} — Verify at: $url" else refresh.userMessage
        } else {
            refresh.userMessage
        }
        val retryable = refresh is SessionRefreshResult.TransientFailure
        stateMutex.withLock {
            lastRefreshUserMessage = message
            lastRefreshRetryable = retryable
        }
    }

    private suspend fun recordFailure(e: Exception) {
        stateMutex.withLock {
            when (e) {
                is MiApiException.AuthExpired -> {
                    sawAuthFailure = true
                    retryableFailures += lastRefreshRetryable
                }
                is MiApiException -> retryableFailures += e.isRetryable
                else -> retryableFailures += true
            }
        }
    }

    private fun friendlyMessage(e: Exception): String {
        return when (e) {
            is MiApiException.AuthExpired ->
                lastRefreshUserMessage
                    ?: e.message?.takeIf { it.isNotBlank() }
                    ?: "Session expired — sign in again"
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

    private suspend fun setState(metric: String, state: SyncState) {
        stateMutex.withLock {
            syncProgress.value = syncProgress.value.withMetric(metric, state)
        }
    }

    companion object {
        /** Max concurrent Mi cloud requests during parallel sync (rate-limit safety). */
        const val MAX_CONCURRENT_FETCHES = 3
    }
}
