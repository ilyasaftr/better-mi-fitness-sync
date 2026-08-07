import Foundation
import ComposeApp
import Combine

@MainActor
final class HomeStore: ObservableObject {
    @Published private(set) var state = HomeSnapshot()
    private let vm: HomeViewModel
    private var subscription: FlowSubscription?

    struct HomeSnapshot {
        var profileName: String = "Mi account"
        var profileError: String?
        var lastSyncLabel: String = "Never"
        var lastSyncStatusTitle: String = "No sync yet"
        var lastSyncDetail: String = "Run a sync to see results here"
        var lastSyncIsError: Bool = false
        var lastSyncIsWarning: Bool = false
        var lastBackgroundLabel: String = "Never"
        var lastBackgroundDetail: String = "Background sync has not run yet"
        var lastBackgroundIsError: Bool = false
        var enabledMetricsCount: Int32 = 0
        var totalMetricsCount: Int32 = 0
        var rangeDays: Int32 = 7
        var autoSync: Bool = false
        var canSync: Bool = true
        var isSyncing: Bool = false
        var healthServiceName: String = "Apple Health"
        var healthReady: Bool = true
        var healthStatusTitle: String = ""
        var healthStatusDetail: String = ""
        var healthNeedsAction: Bool = false
        var loggedOut: Bool = false
    }

    init() {
        self.vm = IosAppBridge.shared.createHomeViewModel()
        subscription = FlowSubscription(
            IosAppBridge.shared.watchHome(vm: vm) { [weak self] s in
                Task { @MainActor in
                    self?.apply(s)
                }
            }
        )
    }

    deinit { subscription?.cancel() }

    private func apply(_ s: HomeUiState) {
        var snap = HomeSnapshot()
        snap.profileName = IosAppBridge.shared.profileName(state: s)
        snap.profileError = s.profileError
        snap.lastSyncLabel = s.lastSyncLabel
        snap.lastSyncStatusTitle = s.lastSyncStatusTitle
        snap.lastSyncDetail = s.lastSyncDetail
        snap.lastSyncIsError = s.lastSyncIsError
        snap.lastSyncIsWarning = s.lastSyncIsWarning
        snap.lastBackgroundLabel = s.lastBackgroundLabel
        snap.lastBackgroundDetail = s.lastBackgroundDetail
        snap.lastBackgroundIsError = s.lastBackgroundIsError
        snap.enabledMetricsCount = s.enabledMetricsCount
        snap.totalMetricsCount = s.totalMetricsCount
        snap.rangeDays = s.rangeDays
        snap.autoSync = s.autoSync
        snap.canSync = s.canSync
        snap.isSyncing = s.isSyncing
        snap.healthServiceName = s.healthServiceName
        snap.healthReady = s.healthReady
        snap.healthStatusTitle = s.healthStatusTitle
        snap.healthStatusDetail = s.healthStatusDetail
        snap.healthNeedsAction = s.healthNeedsAction
        snap.loggedOut = s.loggedOut
        state = snap
    }

    func refresh() {
        vm.loadProfile()
        vm.refreshHealthReadiness()
    }

    func openHealth() { vm.openHealthService() }
    /// Starts sync if idle, then caller should navigate to Sync to observe.
    func startSyncIfIdle() { vm.startSyncIfIdle() }
    func logout() { vm.logout() }
    func consumeLoggedOut() { vm.consumeLoggedOut() }
}
