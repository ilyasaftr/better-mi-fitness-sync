package com.bettermifitness.sync

/**
 * iOS bridges for system background refresh and status. No-ops on Android.
 */
expect object AutoSyncPlatform {
    /** Ask the OS to schedule the next opportunistic background refresh. */
    fun scheduleBackgroundRefresh()

    /** Cancel any pending opportunistic background refresh. */
    fun cancelBackgroundRefresh()

    /**
     * Human-readable Background App Refresh status for Settings.
     * Empty string when the platform has no equivalent (Android).
     */
    fun backgroundRefreshStatusLabel(): String

    /**
     * Whether Settings can show a “test 1-day refresh” control (iOS).
     * Simulator never auto-fires BGAppRefresh; this runs the same code path.
     */
    fun supportsOpportunisticRefreshTest(): Boolean

    /**
     * Runs the opportunistic (last 1 day) sync immediately for debugging.
     * Invokes [onDone] with a status code string (success / skipped / failed / …).
     */
    fun runOpportunisticRefreshTest(onDone: (String) -> Unit)

    /**
     * Whether Settings should show Apple Shortcuts / Siri setup help.
     * iOS only — Android has no Shortcuts App Intent.
     */
    fun supportsShortcutsHelp(): Boolean
}
