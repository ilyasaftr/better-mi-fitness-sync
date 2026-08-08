package com.bettermifitness.sync.data.repository
import com.bettermifitness.sync.i18n.L10n


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
        isEmpty -> L10n.text(L10n.outcomeNothingToDo)
        isFullSuccess -> L10n.text(L10n.outcomeSuccessDetail)
        isPartialSuccess ->
            L10n.text(L10n.outcomePartialLabel) +
                errorMessages.firstOrNull()?.let { " — $it" }.orEmpty()
        isTotalFailure ->
            errorMessages.firstOrNull()
                ?: L10n.text(L10n.outcomeCouldNotFinish)
        else -> L10n.text(L10n.outcomeInProgress)
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
    "hrv" -> hrv
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
