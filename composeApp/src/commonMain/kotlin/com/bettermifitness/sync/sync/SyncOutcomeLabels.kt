package com.bettermifitness.sync.sync

import com.bettermifitness.sync.i18n.L10n

/** Friendly localized labels for persisted SyncOutcome status codes. */
object SyncOutcomeLabels {
    fun title(status: String?): String = when (status) {
        SyncOutcome.STATUS_SUCCESS -> L10n.text(L10n.outcomeUpToDate)
        SyncOutcome.STATUS_PARTIAL_SUCCESS -> L10n.text(L10n.outcomeAlmostDone)
        SyncOutcome.STATUS_FAILED -> L10n.text(L10n.outcomeCouldNotFinish)
        SyncOutcome.STATUS_NOT_LOGGED_IN -> L10n.text(L10n.outcomePleaseSignIn)
        SyncOutcome.STATUS_HEALTH_UNAVAILABLE -> L10n.text(L10n.outcomeHealthNeeded)
        SyncOutcome.STATUS_SKIPPED -> L10n.text(L10n.outcomeNothingToDo)
        SyncOutcome.STATUS_CANCELLED -> L10n.text(L10n.outcomeStopped)
        SyncOutcome.STATUS_ALREADY_RUNNING -> L10n.text(L10n.outcomeInProgress)
        null, "" -> L10n.text(L10n.outcomeNotSynced)
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun detail(status: String?, message: String?): String {
        val msg = message?.takeIf { it.isNotBlank() }
        return when (status) {
            SyncOutcome.STATUS_SUCCESS -> msg ?: L10n.text(L10n.outcomeSuccessDetail)
            SyncOutcome.STATUS_PARTIAL_SUCCESS -> msg ?: L10n.text(L10n.outcomePartialDetail)
            SyncOutcome.STATUS_FAILED -> msg ?: L10n.text(L10n.outcomeFailedDetail)
            SyncOutcome.STATUS_NOT_LOGGED_IN -> msg ?: L10n.text(L10n.outcomeLoginDetail)
            SyncOutcome.STATUS_HEALTH_UNAVAILABLE -> msg ?: L10n.text(L10n.outcomeHealthDetail)
            SyncOutcome.STATUS_SKIPPED -> msg ?: L10n.text(L10n.outcomeSkippedDetail)
            SyncOutcome.STATUS_CANCELLED -> msg ?: L10n.text(L10n.outcomeCancelledDetail)
            SyncOutcome.STATUS_ALREADY_RUNNING -> msg ?: L10n.text(L10n.outcomeRunningDetail)
            null, "" -> L10n.text(L10n.outcomeIdleDetail)
            else -> msg ?: title(status)
        }
    }

    fun isWarning(status: String?): Boolean =
        status == SyncOutcome.STATUS_PARTIAL_SUCCESS || status == SyncOutcome.STATUS_SKIPPED

    fun isError(status: String?): Boolean =
        status == SyncOutcome.STATUS_FAILED ||
            status == SyncOutcome.STATUS_NOT_LOGGED_IN ||
            status == SyncOutcome.STATUS_HEALTH_UNAVAILABLE ||
            status == SyncOutcome.STATUS_CANCELLED
}

fun SyncOutcome.userMessage(): String? = when (this) {
    SyncOutcome.Success -> L10n.text(L10n.outcomeSuccessDetail)
    is SyncOutcome.PartialSuccess -> summary
    SyncOutcome.Skipped -> L10n.text(L10n.outcomeNothingNeeded)
    SyncOutcome.NotLoggedIn -> L10n.text(L10n.outcomeSignInAgain)
    SyncOutcome.HealthUnavailable -> L10n.text(L10n.outcomeHealthUnavailable)
    SyncOutcome.AlreadyRunning -> L10n.text(L10n.outcomeRunningDetail)
    is SyncOutcome.Failed -> message ?: L10n.text(L10n.outcomeCouldNotFinish)
}
