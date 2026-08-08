import SwiftUI
import ComposeApp

struct SettingsView: View {
    @StateObject private var store = SettingsStore()
    /// Called after logout so RootView can return to login.
    var onLogout: (() -> Void)? = nil

    private let rangeOptions = [1, 7, 14, 30]

    var body: some View {
        List {
            if store.state.healthNeedsAction {
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(store.state.healthStatusTitle)
                            .font(.headline)
                        Text(store.state.healthStatusDetail)
                            .font(.subheadline)
                            .foregroundStyle(Brand.secondaryLabel)
                        Button(L10n.openHealth(store.state.healthServiceName)) {
                            store.openHealth()
                        }
                    }
                }
            }

            Section(L10n.settingsSectionSyncRange) {
                Picker(L10n.settingsDaysPicker, selection: Binding(
                    get: { Int(store.state.rangeDays) },
                    set: { store.setRangeDays($0) }
                )) {
                    ForEach(rangeOptions, id: \.self) { d in
                        Text(d == 1 ? L10n.settingsRange1Day : L10n.settingsRangeNDays(d)).tag(d)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section(L10n.settingsSectionAutoSync) {
                Toggle(L10n.settingsBackgroundAutoSync, isOn: Binding(
                    get: { store.state.autoSync },
                    set: { store.setAutoSync($0) }
                ))
                if !store.state.bgRefreshLabel.isEmpty {
                    Text(store.state.bgRefreshLabel)
                        .font(.caption)
                        .foregroundStyle(Brand.secondaryLabel)
                }
                if store.state.canTestBgRefresh {
                    Button {
                        store.runBgTest()
                    } label: {
                        if store.state.bgTestRunning {
                            ProgressView()
                        } else {
                            Text(L10n.settingsTestBackgroundRefresh)
                        }
                    }
                    .disabled(!store.state.autoSync || store.state.bgTestRunning)
                }
                if let status = store.state.bgTestStatus {
                    Text(status)
                        .font(.caption)
                        .foregroundStyle(Brand.secondaryLabel)
                }
            }

            Section(L10n.settingsSectionMetrics) {
                ForEach(Array(zip(store.metricKeys, store.metricLabels)), id: \.0) { key, label in
                    Toggle(isOn: Binding(
                        get: { store.state.enabledMetrics.contains(key) },
                        set: { store.setMetric(key, enabled: $0) }
                    )) {
                        Label {
                            Text(label)
                        } icon: {
                            Image(systemName: MetricSymbol.name(for: key))
                                .foregroundStyle(Brand.primary)
                        }
                    }
                }
            }

            Section(L10n.settingsSectionStatus) {
                LabeledContent(L10n.settingsLastSync, value: store.state.lastSyncLabel)
                LabeledContent(L10n.settingsLastBackgroundSync, value: store.state.lastBackgroundSyncLabel)
            }

            if store.state.showShortcutsHelp {
                Section(L10n.settingsSectionShortcuts) {
                    Text(L10n.settingsShortcutsHelp)
                        .font(.caption)
                        .foregroundStyle(Brand.secondaryLabel)
                }
            }

            Section(L10n.settingsSectionAccount) {
                Button(L10n.settingsLogOut, role: .destructive) {
                    store.logout()
                    onLogout?()
                }
            }

            Section(L10n.settingsSectionAbout) {
                LabeledContent(L10n.settingsVersion, value: appVersionLabel)
                Link(destination: creditURL) {
                    HStack {
                        Text(L10n.settingsCredit)
                            .foregroundStyle(Brand.label)
                        Spacer(minLength: 0)
                        Text(L10n.settingsCreditName)
                            .foregroundStyle(Brand.primary)
                    }
                }
            }
        }
        .navigationTitle(L10n.settingsTitle)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { store.refreshHealth() }
    }

    private var appVersionLabel: String {
        let info = Bundle.main.infoDictionary
        let name = (info?["CFBundleShortVersionString"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let build = (info?["CFBundleVersion"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let n = (name?.isEmpty == false) ? name! : "—"
        let b = (build?.isEmpty == false) ? build! : "—"
        return "\(n) (\(b))"
    }

    private var creditURL: URL {
        URL(string: "https://github.com/ilyasaftr")!
    }
}
