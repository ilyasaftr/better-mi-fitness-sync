package com.bettermifitness.sync.data.repository

/**
 * Aggregate result of a multi-metric sync pass (each metric fails independently).
 */
data class SyncRunResult(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val totalRecords: Int,
    val hadRetryableFailure: Boolean,
    val hadAuthFailure: Boolean,
    val errorMessages: List<String>,
) {
    val isFullSuccess: Boolean get() = attempted > 0 && failed == 0
    val isPartialSuccess: Boolean get() = succeeded > 0 && failed > 0
    val isTotalFailure: Boolean get() = attempted > 0 && succeeded == 0 && failed > 0
    val isEmpty: Boolean get() = attempted == 0

    fun summary(): String = when {
        isEmpty -> "Nothing to sync"
        isFullSuccess -> "Synced $succeeded metric${if (succeeded == 1) "" else "s"} ($totalRecords records)"
        isPartialSuccess ->
            "Partial: $succeeded ok, $failed failed" +
                errorMessages.firstOrNull()?.let { " — $it" }.orEmpty()
        isTotalFailure ->
            errorMessages.firstOrNull()
                ?: "All $failed metrics failed"
        else -> "Sync finished"
    }

    companion object {
        fun from(
            progress: SyncProgress,
            metricKeys: Collection<String>,
            retryableFlags: List<Boolean>,
            authFailure: Boolean,
        ): SyncRunResult {
            val states = metricKeys.mapNotNull { key -> progress.stateForKey(key) }
            var succeeded = 0
            var failed = 0
            var records = 0
            val errors = mutableListOf<String>()
            for (state in states) {
                when (state) {
                    is SyncState.Success -> {
                        succeeded++
                        records += state.count
                    }
                    is SyncState.Error -> {
                        failed++
                        errors += state.message
                    }
                    else -> Unit
                }
            }
            return SyncRunResult(
                attempted = succeeded + failed,
                succeeded = succeeded,
                failed = failed,
                totalRecords = records,
                hadRetryableFailure = retryableFlags.any { it },
                hadAuthFailure = authFailure,
                errorMessages = errors,
            )
        }
    }
}

fun SyncProgress.stateForKey(key: String): SyncState? = when (key) {
    "heart_rate" -> heartRate
    "resting_heart_rate" -> restingHeartRate
    "sleep" -> sleep
    "steps" -> steps
    "distance" -> distance
    "active_calories" -> activeCalories
    "spo2" -> spo2
    "weight" -> weight
    "workouts" -> workouts
    "blood_pressure" -> bloodPressure
    "temperature" -> temperature
    "vo2_max" -> vo2Max
    else -> null
}
