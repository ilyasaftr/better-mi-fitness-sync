import SwiftUI

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
                        Button("Open \(store.state.healthServiceName)") {
                            store.openHealth()
                        }
                    }
                }
            }

            Section("Sync range") {
                Picker("Days", selection: Binding(
                    get: { Int(store.state.rangeDays) },
                    set: { store.setRangeDays($0) }
                )) {
                    ForEach(rangeOptions, id: \.self) { d in
                        Text("\(d) day\(d == 1 ? "" : "s")").tag(d)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section("Auto-sync") {
                Toggle("Background auto-sync", isOn: Binding(
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
                            Text("Test background refresh")
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

            Section("Metrics") {
                ForEach(Array(zip(store.metricKeys, store.metricLabels)), id: \.0) { key, label in
                    Toggle(isOn: Binding(
                        get: { store.state.enabledMetrics.contains(key) },
                        set: { store.setMetric(key, enabled: $0) }
                    )) {
                        // Match Sync screen: brand orange metric icons (not system list tint).
                        Label {
                            Text(label)
                        } icon: {
                            Image(systemName: MetricSymbol.name(for: key))
                                .foregroundStyle(Brand.primary)
                        }
                    }
                }
            }

            Section("Status") {
                // Keep rows minimal — titles/details live on Home / Sync.
                LabeledContent("Last sync", value: store.state.lastSyncLabel)
                LabeledContent("Last background", value: store.state.lastBackgroundSyncLabel)
            }

            if store.state.showShortcutsHelp {
                Section("Shortcuts") {
                    Text("Use the “Sync Better Mi Fitness” App Intent or Shortcuts for manual background runs.")
                        .font(.caption)
                        .foregroundStyle(Brand.secondaryLabel)
                }
            }

            Section("Account") {
                Button("Log out", role: .destructive) {
                    store.logout()
                    onLogout?()
                }
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { store.refreshHealth() }
    }
}
