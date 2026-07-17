package com.bettermifitness.sync.sync

/**
 * Friendly labels for persisted [SyncOutcome] status codes (shown in the UI).
 * Keep details short so status cards stay one clean block (avoid awkward wraps).
 */
object SyncOutcomeLabels {

    fun title(status: String?): String = when (status) {
        SyncOutcome.STATUS_SUCCESS -> "You're up to date"
        SyncOutcome.STATUS_PARTIAL_SUCCESS -> "Almost done"
        SyncOutcome.STATUS_FAILED -> "Couldn't finish"
        SyncOutcome.STATUS_NOT_LOGGED_IN -> "Please sign in"
        SyncOutcome.STATUS_HEALTH_UNAVAILABLE -> "Health app needed"
        SyncOutcome.STATUS_SKIPPED -> "Nothing to do"
        SyncOutcome.STATUS_CANCELLED -> "Stopped"
        null, "" -> "Not synced yet"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun detail(status: String?, message: String?): String {
        val msg = message?.takeIf { it.isNotBlank() }
        return when (status) {
            // Short on purpose — long lines wrap badly in the status card
            SyncOutcome.STATUS_SUCCESS ->
                msg ?: "Mi Fitness synced to Health"
            SyncOutcome.STATUS_PARTIAL_SUCCESS ->
                msg ?: "Some items failed — try Sync again"
            SyncOutcome.STATUS_FAILED ->
                msg ?: "Check your connection and try again"
            SyncOutcome.STATUS_NOT_LOGGED_IN ->
                msg ?: "Sign in with your Mi Account"
            SyncOutcome.STATUS_HEALTH_UNAVAILABLE ->
                msg ?: "Open or install the Health app first"
            SyncOutcome.STATUS_SKIPPED ->
                msg ?: "Turn on items in Settings, then Update"
            SyncOutcome.STATUS_CANCELLED ->
                msg ?: "Sync was stopped"
            null, "" -> "Tap Sync for your latest activity"
            else -> msg ?: title(status)
        }
    }

    fun isWarning(status: String?): Boolean =
        status == SyncOutcome.STATUS_PARTIAL_SUCCESS ||
            status == SyncOutcome.STATUS_SKIPPED

    fun isError(status: String?): Boolean =
        status == SyncOutcome.STATUS_FAILED ||
            status == SyncOutcome.STATUS_NOT_LOGGED_IN ||
            status == SyncOutcome.STATUS_HEALTH_UNAVAILABLE ||
            status == SyncOutcome.STATUS_CANCELLED
}

fun SyncOutcome.userMessage(): String? = when (this) {
    SyncOutcome.Success -> "Mi Fitness synced to Health"
    is SyncOutcome.PartialSuccess -> summary
    SyncOutcome.Skipped -> "Nothing needed syncing"
    SyncOutcome.NotLoggedIn -> "Please sign in again"
    SyncOutcome.HealthUnavailable -> "Health app isn't available"
    is SyncOutcome.Failed -> message ?: "Couldn't finish sync"
}
