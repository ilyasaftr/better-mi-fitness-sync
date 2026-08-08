import SwiftUI

/// Compact single-column metric list + sticky bottom Sync CTA (consistent row height).
struct SyncView: View {
    @StateObject private var store = SyncStore()

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(L10n.syncLast(Int(store.state.rangeDays)) )
                        .font(.subheadline)
                        .foregroundStyle(Brand.secondaryLabel)
                        .padding(.horizontal, 4)

                    if let err = store.state.permissionError {
                        statusBanner(err, color: Brand.danger)
                    }
                    if let hint = store.state.availabilityHint {
                        statusBanner(hint, color: Brand.caution)
                    }
                    // Skip plain success banners ("All enabled metrics synced") — rows already show Done.
                    // Keep partial-failure summaries so issues stay visible.
                    if let msg = store.state.outcomeMessage, store.state.outcomeIsWarning {
                        statusBanner(msg, color: Brand.caution)
                    }

                    if store.state.metrics.isEmpty {
                        emptyMetrics
                    } else {
                        VStack(spacing: 0) {
                            ForEach(Array(store.state.metrics.enumerated()), id: \.element.id) { index, row in
                                metricRow(row)
                                if index < store.state.metrics.count - 1 {
                                    Divider()
                                        .padding(.leading, 48)
                                }
                            }
                        }
                        .background(Brand.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 16)
            }

            // Sticky primary — same chrome as Home (`StickyPrimaryBar`).
            StickyPrimaryBar(
                primaryEnabled: !store.state.isSyncing && store.state.healthAvailable,
                primaryAction: { store.startSync() },
                label: {
                    if store.state.isSyncing {
                        SyncingPrimaryLabel()
                    } else {
                        Label(L10n.syncNow, systemImage: "arrow.triangle.2.circlepath")
                    }
                },
                footer: {
                    if !store.state.healthAvailable {
                        Button(L10n.openHealth(store.state.healthServiceName)) {
                            store.openHealth()
                        }
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Brand.primary)
                        .frame(maxWidth: .infinity)
                    }
                }
            )
        }
        .background(Brand.pageBackground.ignoresSafeArea())
        .navigationTitle(L10n.syncTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var emptyMetrics: some View {
        Text(store.state.readinessChecked ? L10n.syncNoMetrics : L10n.syncPreparing)
            .font(.subheadline)
            .foregroundStyle(Brand.secondaryLabel)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
    }

    private func statusBanner(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(color.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    /// Fixed-height row: icon | title (1 line) | trailing status
    private func metricRow(_ row: SyncStore.MetricRow) -> some View {
        HStack(spacing: 12) {
            Image(systemName: MetricSymbol.name(for: row.id))
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Brand.primary)
                .frame(width: 28, height: 28)

            Text(row.label)
                .font(.body)
                .foregroundStyle(Brand.label)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel(row.label)

            HStack(spacing: 6) {
                statusIcon(for: row.kind)
                Text(shortStatus(row))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(color(for: row.kind))
                    .lineLimit(1)
            }
            .frame(minWidth: 88, alignment: .trailing)
        }
        .padding(.horizontal, 14)
        .frame(height: 52)
        .contentShape(Rectangle())
    }

    /// Trailing labels must stay consistent — raw sample counts differ by metric type
    /// (HR samples vs sleep sessions vs hourly steps) and read as random numbers.
    private func shortStatus(_ row: SyncStore.MetricRow) -> String {
        switch row.kind {
        case "progress":
            return L10n.syncStatusSyncing
        case "success":
            return L10n.syncStatusDone
        case "error":
            return L10n.syncStatusFailed
        default:
            return L10n.syncStatusWaiting
        }
    }

    @ViewBuilder
    private func statusIcon(for kind: String) -> some View {
        switch kind {
        case "progress":
            ProgressView().controlSize(.small)
        case "success":
            Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.success)
        case "error":
            Image(systemName: "xmark.circle.fill").foregroundStyle(Brand.danger)
        default:
            Image(systemName: "circle").foregroundStyle(Brand.secondaryLabel.opacity(0.35))
        }
    }

    private func color(for kind: String) -> Color {
        switch kind {
        case "success": return Brand.success
        case "error": return Brand.danger
        case "progress": return Brand.primary
        default: return Brand.secondaryLabel
        }
    }
}
