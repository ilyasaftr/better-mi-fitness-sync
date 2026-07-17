import AppIntents
import ComposeApp

/// Shortcuts / Siri entry point for a user-initiated full-range sync
/// (uses the range configured in Settings; does not require Auto-sync ON).
struct SyncBetterMiFitnessIntent: AppIntent {
    static var title: LocalizedStringResource = "Sync Better Mi Fitness Sync"
    static var description = IntentDescription(
        "Download the latest Mi Fitness health data and write it to Apple Health."
    )

    /// Run in the background so time-based Shortcuts automations work without
    /// bringing the app to the foreground.
    static var openAppWhenRun: Bool = false

    func perform() async throws -> some IntentResult & ProvidesDialog {
        // Ensure Koin / DataStore exist even if the process was launched only for the intent.
        MainViewControllerKt.doInitKoin()

        let status: String = try await withCheckedThrowingContinuation { continuation in
            var resumed = false
            BackgroundSync.shared.runUserInitiatedSync { result in
                guard !resumed else { return }
                resumed = true
                let code = (result as? String) ?? BackgroundSync.shared.STATUS_FAILED
                continuation.resume(returning: code)
            }
        }

        return .result(dialog: dialog(for: status))
    }

    private func dialog(for status: String) -> IntentDialog {
        switch status {
        case BackgroundSync.shared.STATUS_SUCCESS:
            return IntentDialog("Better Mi Fitness Sync finished.")
        case BackgroundSync.shared.STATUS_PARTIAL_SUCCESS:
            return IntentDialog("Sync finished with some metrics failed. Open the app for details.")
        case BackgroundSync.shared.STATUS_SKIPPED:
            return IntentDialog("Nothing to sync (no data types enabled).")
        case BackgroundSync.shared.STATUS_NOT_LOGGED_IN:
            return IntentDialog("You’re not signed in. Open Better Mi Fitness Sync, sign in, then try again.")
        case BackgroundSync.shared.STATUS_HEALTH_UNAVAILABLE:
            return IntentDialog("Apple Health isn’t available on this device.")
        case BackgroundSync.shared.STATUS_CANCELLED:
            return IntentDialog("Sync was interrupted. Wait a moment and try once.")
        default:
            return IntentDialog("Sync failed. Wait for any running sync to finish, then try once.")
        }
    }
}

/// Publishes the intent to the Shortcuts app and Siri suggestions.
struct BetterMiFitnessSyncShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SyncBetterMiFitnessIntent(),
            phrases: [
                "Sync \(.applicationName)",
                "Sync Better Mi Fitness Sync with \(.applicationName)",
                "Update Apple Health from \(.applicationName)",
            ],
            shortTitle: "Sync Better Mi Fitness Sync",
            systemImageName: "heart.text.square"
        )
    }
}
