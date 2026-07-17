import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Start Koin so dependencies exist for both UI and background sync.
        MainViewControllerKt.doInitKoin()
        // Register BG handler + Kotlin↔Swift schedule bridge.
        BackgroundSyncManager.register()
        // Only keep BGAppRefresh queued when Auto-sync is enabled.
        AutoSyncSchedule.shared.restore()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // Debug/Simulator only (no-op in Release device builds)
                    BackgroundSyncManager.handleDebugURL(url)
                }
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .background:
                // Re-queue only if Auto-sync is still on (OS decides when to fire).
                AutoSyncSchedule.shared.rescheduleIfEnabled()
            case .active:
                // Foreground auto-sync: full user-configured range when toggle is on.
                // Joins any in-flight Shortcuts sync instead of cancelling it.
                BackgroundSync.shared.runForegroundAutoSync { status in
                    print("[BGSync] foreground auto-sync status=\(status)")
                }
            default:
                break
            }
        }
    }
}
