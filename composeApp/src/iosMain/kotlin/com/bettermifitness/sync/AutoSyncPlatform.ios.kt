package com.bettermifitness.sync

/**
 * Swift registers handlers via [AutoSyncBridge.setHandlers] at app launch so
 * Kotlin Settings can schedule/cancel BGAppRefresh and read status.
 */
actual object AutoSyncPlatform {
    actual fun scheduleBackgroundRefresh() {
        AutoSyncBridge.invokeSchedule()
    }

    actual fun cancelBackgroundRefresh() {
        AutoSyncBridge.invokeCancel()
    }

    actual fun backgroundRefreshStatusLabel(): String {
        return AutoSyncBridge.invokeStatus()
    }

    /** Debug test control removed from Settings UI; keep false so it never appears. */
    actual fun supportsOpportunisticRefreshTest(): Boolean = false

    actual fun runOpportunisticRefreshTest(onDone: (String) -> Unit) {
        if (!supportsOpportunisticRefreshTest()) {
            onDone("skipped")
            return
        }
        // Same path as BGAppRefreshTask (last 1 day, requires auto-sync ON).
        BackgroundSync.runOpportunisticBackgroundSync(onDone)
    }

    actual fun supportsShortcutsHelp(): Boolean = true
}

/**
 * Filled from Swift (BackgroundSyncManager.register) so commonMain can drive
 * BGTaskScheduler without importing Swift types into Kotlin.
 */
object AutoSyncBridge {
    private var onSchedule: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var statusProvider: (() -> String)? = null
    private var supportsDebugTest: (() -> Boolean)? = null

    fun setHandlers(
        onSchedule: (() -> Unit)?,
        onCancel: (() -> Unit)?,
        statusProvider: (() -> String)?,
        supportsDebugTest: (() -> Boolean)?,
    ) {
        this.onSchedule = onSchedule
        this.onCancel = onCancel
        this.statusProvider = statusProvider
        this.supportsDebugTest = supportsDebugTest
    }

    fun invokeSchedule() {
        onSchedule?.invoke()
    }

    fun invokeCancel() {
        onCancel?.invoke()
    }

    fun invokeStatus(): String {
        return statusProvider?.invoke() ?: "Unknown"
    }

    fun invokeSupportsDebugTest(): Boolean {
        return supportsDebugTest?.invoke() ?: false
    }
}
