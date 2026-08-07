import Foundation
import ComposeApp
import Combine

@MainActor
final class SettingsStore: ObservableObject {
    @Published private(set) var state = Snapshot()
    private let vm: SettingsViewModel
    private var subscription: FlowSubscription?

    struct Snapshot {
        var enabledMetrics: Set<String> = []
        var rangeDays: Int32 = 7
        var autoSync: Bool = false
        var lastBackgroundSyncLabel: String = "Never"
        var lastBackgroundStatusTitle: String = "No attempt yet"
        var lastBackgroundDetail: String = ""
        var lastBackgroundIsError: Bool = false
        var lastSyncLabel: String = "Never"
        var lastSyncStatusTitle: String = "No sync yet"
        var lastSyncDetail: String = ""
        var lastSyncIsError: Bool = false
        var lastSyncIsWarning: Bool = false
        var bgRefreshLabel: String = ""
        var canTestBgRefresh: Bool = false
        var bgTestStatus: String?
        var bgTestRunning: Bool = false
        var showShortcutsHelp: Bool = false
        var healthServiceName: String = "Apple Health"
        var healthStatusTitle: String = ""
        var healthStatusDetail: String = ""
        var healthNeedsAction: Bool = false
    }

    let metricKeys: [String]
    let metricLabels: [String]

    init() {
        self.vm = IosAppBridge.shared.createSettingsViewModel()
        self.metricKeys = IosAppBridge.shared.allMetricKeys() as? [String] ?? []
        self.metricLabels = IosAppBridge.shared.allMetricLabels() as? [String] ?? []
        subscription = FlowSubscription(
            IosAppBridge.shared.watchSettings(vm: vm) { [weak self] s in
                Task { @MainActor in
                    self?.apply(s)
                }
            }
        )
    }

    deinit { subscription?.cancel() }

    private func apply(_ s: SettingsUiState) {
        var snap = Snapshot()
        // Kotlin Set → Swift
        if let set = s.enabledMetrics as? Set<String> {
            snap.enabledMetrics = set
        } else if let arr = s.enabledMetrics as? NSSet {
            snap.enabledMetrics = Set(arr.compactMap { $0 as? String })
        } else {
            snap.enabledMetrics = Set(metricKeys)
        }
        snap.rangeDays = s.rangeDays
        snap.autoSync = s.autoSync
        snap.lastBackgroundSyncLabel = s.lastBackgroundSyncLabel
        snap.lastBackgroundStatusTitle = s.lastBackgroundStatusTitle
        snap.lastBackgroundDetail = s.lastBackgroundDetail
        snap.lastBackgroundIsError = s.lastBackgroundIsError
        snap.lastSyncLabel = s.lastSyncLabel
        snap.lastSyncStatusTitle = s.lastSyncStatusTitle
        snap.lastSyncDetail = s.lastSyncDetail
        snap.lastSyncIsError = s.lastSyncIsError
        snap.lastSyncIsWarning = s.lastSyncIsWarning
        snap.bgRefreshLabel = s.bgRefreshLabel
        snap.canTestBgRefresh = s.canTestBgRefresh
        snap.bgTestStatus = s.bgTestStatus
        snap.bgTestRunning = s.bgTestRunning
        snap.showShortcutsHelp = s.showShortcutsHelp
        snap.healthServiceName = s.healthServiceName
        snap.healthStatusTitle = s.healthStatusTitle
        snap.healthStatusDetail = s.healthStatusDetail
        snap.healthNeedsAction = s.healthNeedsAction
        state = snap
    }

    func setMetric(_ key: String, enabled: Bool) {
        vm.setMetricEnabled(key: key, enabled: enabled)
    }

    func setRangeDays(_ days: Int) {
        vm.setSyncRangeDays(days: Int32(days))
    }

    func setAutoSync(_ on: Bool) {
        vm.setAutoSync(enabled: on)
    }

    func refreshHealth() { vm.refreshHealth() }
    func openHealth() { vm.openHealthService() }
    func runBgTest() { vm.runBackgroundRefreshTest() }
    func logout() { IosAppBridge.shared.logout() }
}
