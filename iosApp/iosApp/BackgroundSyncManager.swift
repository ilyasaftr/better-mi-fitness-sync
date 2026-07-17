import BackgroundTasks
import ComposeApp
import UIKit

/// Schedules and runs opportunistic background auto-sync via BGAppRefreshTask.
///
/// These tasks are short (~30s). The Kotlin side only syncs the **last 1 day**
/// so the work can finish inside that budget. Full-range sync is for:
/// - Manual "Sync Now"
/// - Foreground auto-sync when the app is opened
/// - Shortcuts / App Intent (`SyncBetterMiFitnessIntent`)
///
/// Simulator: tasks won't fire alone — use:
///   e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"com.bettermifitness.sync.refresh"]
enum BackgroundSyncManager {
    static let taskIdentifier = "com.bettermifitness.sync.refresh"

    /// Registers the task handler and wires Kotlin → Swift scheduling bridges.
    /// Must be called during app launch.
    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            handle(task: task as! BGAppRefreshTask)
        }

        AutoSyncBridge.shared.setHandlers(
            onSchedule: { schedule() },
            onCancel: { cancel() },
            statusProvider: { backgroundRefreshStatusLabel() },
            supportsDebugTest: { KotlinBoolean(bool: isDebugRefreshEnabled) }
        )
    }

    /// Debug / Simulator only — never shown or callable in App Store Release.
    static var isDebugRefreshEnabled: Bool {
        #if DEBUG
        return true
        #elseif targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
    }

    /// Asks iOS to schedule the next opportunistic refresh (earliest ~15 min).
    /// iOS treats earliestBeginDate as a floor, not a guarantee.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do {
            try BGTaskScheduler.shared.submit(request)
            print("[BGSync] scheduled \(taskIdentifier)")
        } catch {
            print("[BGSync] schedule failed: \(error)")
        }
    }

    /// Cancels a pending refresh request (e.g. auto-sync turned off).
    static func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        print("[BGSync] cancelled \(taskIdentifier)")
    }

    static func backgroundRefreshStatusLabel() -> String {
        if isDebugRefreshEnabled {
            #if targetEnvironment(simulator)
            return "Simulator: system never auto-fires BGAppRefresh. Use “Test 1-day refresh” or bettermifitness://debug/bg-refresh"
            #endif
        }
        switch UIApplication.shared.backgroundRefreshStatus {
        case .available:
            return "Background App Refresh is on"
        case .denied:
            return "Background App Refresh is off — enable it in Settings → General → Background App Refresh"
        case .restricted:
            return "Background App Refresh is restricted on this device"
        @unknown default:
            return "Background App Refresh status unknown"
        }
    }

    /// Runs the same 1-day path as BGAppRefresh (Debug / Simulator only).
    /// URL: `bettermifitness://debug/bg-refresh`
    static func handleDebugURL(_ url: URL) {
        guard isDebugRefreshEnabled else { return }
        guard url.scheme == "bettermifitness" else { return }
        let host = url.host?.lowercased() ?? ""
        let path = url.path.lowercased()
        guard host == "debug", path.contains("bg-refresh") else { return }
        print("[BGSync] debug URL trigger — running opportunistic 1-day sync")
        BackgroundSync.shared.runOpportunisticBackgroundSync { status in
            let code = (status as? String) ?? "?"
            print("[BGSync] debug opportunistic finished status=\(code)")
        }
    }

    private static func handle(task: BGAppRefreshTask) {
        // Line up the next run only if Auto-sync is still enabled.
        AutoSyncSchedule.shared.rescheduleIfEnabled()

        var finished = false
        func finish(success: Bool) {
            guard !finished else { return }
            finished = true
            task.setTaskCompleted(success: success)
        }

        task.expirationHandler = {
            print("[BGSync] task expired — cancelling in-flight sync")
            BackgroundSync.shared.cancel()
            finish(success: false)
        }

        // Last 1 day only — see BackgroundSync.runOpportunisticBackgroundSync
        BackgroundSync.shared.runOpportunisticBackgroundSync { status in
            let code = (status as? String) ?? BackgroundSync.shared.STATUS_FAILED
            // success or quiet skip both count as a completed task for the OS
            let ok = code == BackgroundSync.shared.STATUS_SUCCESS
                || code == BackgroundSync.shared.STATUS_SKIPPED
            print("[BGSync] opportunistic sync finished status=\(code)")
            finish(success: ok)
        }
    }
}
