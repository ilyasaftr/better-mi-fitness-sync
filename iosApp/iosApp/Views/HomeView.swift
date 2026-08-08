import SwiftUI

/// Account + bridge hub. No health metric values — data lives in Apple Health.
struct HomeView: View {
    @StateObject private var store = HomeStore()
    var onSync: () -> Void
    var onSettings: () -> Void
    var onLogout: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    accountHero

                    if let banner = blockingBanner {
                        blockingBannerView(banner)
                    }

                    bridgeSection
                    activityFootnote
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 20)
            }
            .refreshable {
                store.refresh()
            }

            stickyCta
        }
        .background(Brand.pageBackground.ignoresSafeArea())
        // Account hero is the real header — no competing large title.
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    onSettings()
                } label: {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel(L10n.homeSettingsAccessibility)
            }
        }
        .onAppear { store.refresh() }
        .onChange(of: store.state.loggedOut) { loggedOut in
            if loggedOut {
                store.consumeLoggedOut()
                onLogout()
            }
        }
    }

    // MARK: - Account hero (primary story)

    private var accountHero: some View {
        VStack(spacing: 14) {
            avatar
            VStack(spacing: 4) {
                Text(store.state.profileName)
                    .font(.title2.bold())
                    .foregroundStyle(Brand.label)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                Text(profileSubtitle)
                    .font(.subheadline)
                    .foregroundStyle(
                        store.state.profileError != nil ? Brand.danger : Brand.secondaryLabel
                    )
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                // Sync progress stays on Activity row + sticky CTA — not under the name
                // (avoids layout jump when that line appears/disappears).
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }

    private var avatar: some View {
        let letter = store.state.profileName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .first
            .map { String($0).uppercased() } ?? "?"
        return Text(letter)
            .font(.title2.bold())
            .foregroundStyle(.white)
            .frame(width: 72, height: 72)
            .background(Brand.mark)
            .clipShape(Circle())
            .accessibilityHidden(true)
    }

    private var profileSubtitle: String {
        if let err = store.state.profileError, !err.isEmpty {
            return err
        }
        if store.state.profileName != "Mi account" {
            return L10n.homeConnected
        }
        return L10n.homeLoadingProfile
    }

    // MARK: - Blocking banner (permission / empty plan only)

    private struct BlockingBanner {
        var title: String
        var detail: String
        var accent: Color
    }

    private var blockingBanner: BlockingBanner? {
        let s = store.state
        if s.healthNeedsAction {
            return BlockingBanner(
                title: s.healthStatusTitle.isEmpty
                    ? L10n.openHealth(s.healthServiceName)
                    : s.healthStatusTitle,
                detail: L10n.homeAllowAccessDetail(s.healthServiceName),
                accent: Brand.danger
            )
        }
        if s.enabledMetricsCount == 0 {
            return BlockingBanner(
                title: L10n.homeNothingSelectedTitle,
                detail: L10n.homeNothingSelectedDetail(s.healthServiceName),
                accent: Brand.caution
            )
        }
        return nil
    }

    private func blockingBannerView(_ banner: BlockingBanner) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(banner.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(banner.accent)
            Text(banner.detail)
                .font(.caption)
                .foregroundStyle(Brand.secondaryLabel)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(banner.accent.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - Bridge section (setup, not data)

    private var bridgeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Plain setup label — avoid product jargon like "Bridge".
            sectionLabel(L10n.homeSetup)

            VStack(spacing: 0) {
                healthRow
                Divider().padding(.leading, 52)
                planRow
            }
            .background(Brand.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    /// Health identity badge; systemPink adapts and reads as “Health”.
    private var healthBadgeColor: Color {
        store.state.healthNeedsAction ? Brand.danger : Color(uiColor: .systemPink)
    }

    private var syncOptionsBadgeColor: Color {
        store.state.enabledMetricsCount == 0 ? Brand.caution : Brand.mark
    }

    private var healthRow: some View {
        Button {
            if store.state.healthNeedsAction {
                store.openHealth()
            }
        } label: {
            bridgeRowLabel(
                // Simple heart matches Apple Health; same badge treatment as Sync options.
                systemImage: "heart.fill",
                badgeColor: healthBadgeColor,
                title: store.state.healthServiceName,
                trailing: healthRowValue,
                trailingColor: store.state.healthNeedsAction ? Brand.danger : Brand.secondaryLabel,
                showChevron: store.state.healthNeedsAction
            )
        }
        .buttonStyle(.plain)
        .disabled(!store.state.healthNeedsAction)
        .accessibilityHint(
            store.state.healthNeedsAction
                ? L10n.homeHealthAccessibility(store.state.healthServiceName)
                : L10n.homeHealthReadyAccessibility
        )
    }

    private var healthRowValue: String {
        if store.state.healthNeedsAction {
            return L10n.healthAllowAccess
        }
        if store.state.healthReady {
            return L10n.healthReady
        }
        return store.state.healthStatusTitle.isEmpty ? L10n.healthChecking : store.state.healthStatusTitle
    }

    private var planRow: some View {
        Button(action: onSettings) {
            bridgeRowLabel(
                // iOS Settings-style “options” control; pairs with heart as a matched set.
                systemImage: "slider.horizontal.3",
                badgeColor: syncOptionsBadgeColor,
                title: L10n.homeSyncOptions,
                detail: planSummary,
                detailColor: store.state.enabledMetricsCount == 0 ? Brand.caution : Brand.secondaryLabel,
                trailing: nil,
                showChevron: true
            )
        }
        .buttonStyle(.plain)
        .accessibilityHint(L10n.homeSettingsHint)
    }

    private var planSummary: String {
        let s = store.state
        if s.enabledMetricsCount == 0 {
            return L10n.homeNoneSelected
        }
        let metrics = s.enabledMetricsCount == 1
            ? L10n.homeMetricOne
            : L10n.homeMetricMany(Int(s.enabledMetricsCount))
        let days = s.rangeDays == 1 ? L10n.homeDayOne : L10n.homeDayMany(Int(s.rangeDays))
        let auto = s.autoSync ? L10n.homeAutoOn : L10n.homeManual
        return "\(metrics) · \(days) · \(auto)"
    }

    /// Settings-style row: matched rounded icon badges + title/detail stack.
    private func bridgeRowLabel(
        systemImage: String,
        badgeColor: Color,
        title: String,
        detail: String? = nil,
        detailColor: Color = Brand.secondaryLabel,
        trailing: String?,
        trailingColor: Color = Brand.secondaryLabel,
        showChevron: Bool
    ) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Image(systemName: systemImage)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(badgeColor)
                .clipShape(RoundedRectangle(cornerRadius: 7, style: .continuous))
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.body)
                    .foregroundStyle(Brand.label)
                    .lineLimit(1)

                if let detail, !detail.isEmpty {
                    Text(detail)
                        .font(.subheadline)
                        .foregroundStyle(detailColor)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if let trailing, !trailing.isEmpty {
                Text(trailing)
                    .font(.subheadline)
                    .foregroundStyle(trailingColor)
                    .lineLimit(1)
                    .layoutPriority(1)
            }

            if showChevron {
                Image(systemName: "chevron.forward")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Brand.secondaryLabel.opacity(0.7))
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    // MARK: - Activity footnote (bridge job history, not health data)

    private var activityFootnote: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionLabel(L10n.homeActivity)

            VStack(spacing: 0) {
                footnoteRow(
                    title: L10n.homeLastSync,
                    value: lastSyncValue,
                    valueColor: lastSyncColor
                )
                Divider().padding(.leading, 14)
                footnoteRow(
                    title: L10n.homeBackgroundSync,
                    value: backgroundValue,
                    valueColor: store.state.lastBackgroundIsError ? Brand.danger : Brand.secondaryLabel
                )
            }
            .background(Brand.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    /// Time when OK; only append a status word when something needs attention.
    private var lastSyncValue: String {
        let s = store.state
        if s.isSyncing {
            return L10n.homeSyncing
        }
        let when = s.lastSyncLabel
        if when == L10n.homeNever {
            return L10n.homeNever
        }
        // Success: time alone — "Just now · OK" is redundant.
        if s.lastSyncIsError {
            return L10n.homeFailed(when)
        }
        if s.lastSyncIsWarning {
            return L10n.homePartial(when)
        }
        return when
    }

    private var lastSyncColor: Color {
        let s = store.state
        if s.isSyncing { return Brand.primary }
        if s.lastSyncIsError { return Brand.danger }
        if s.lastSyncIsWarning { return Brand.caution }
        return Brand.secondaryLabel
    }

    private var backgroundValue: String {
        let label = store.state.lastBackgroundLabel
        if store.state.lastBackgroundIsError {
            return L10n.homeFailed(label)
        }
        return label
    }

    private func footnoteRow(title: String, value: String, valueColor: Color) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(Brand.secondaryLabel)
                .layoutPriority(1)

            Spacer(minLength: 8)

            Text(value)
                .font(.subheadline)
                .foregroundStyle(valueColor)
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(Brand.secondaryLabel)
            .textCase(.uppercase)
            .padding(.leading, 4)
    }

    // MARK: - Sticky primary CTA (shared chrome with Sync)

    private var stickyCta: some View {
        StickyPrimaryBar(primaryEnabled: stickyPrimaryEnabled, primaryAction: performPrimaryAction) {
            if store.state.isSyncing {
                SyncingPrimaryLabel()
            } else if store.state.healthNeedsAction {
                Text(L10n.healthAllowAccess)
            } else if store.state.enabledMetricsCount == 0 {
                Text(L10n.homeOpenSettings)
            } else {
                Label(L10n.homeSyncNow, systemImage: "arrow.triangle.2.circlepath")
            }
        }
    }

    /// Keep tappable while syncing so user can open the Sync screen.
    private var stickyPrimaryEnabled: Bool {
        store.state.isSyncing
            || store.state.healthNeedsAction
            || store.state.enabledMetricsCount == 0
            || store.state.canSync
    }

    private func performPrimaryAction() {
        if store.state.healthNeedsAction {
            store.openHealth()
            return
        }
        if store.state.enabledMetricsCount == 0 {
            onSettings()
            return
        }
        if store.state.canSync {
            if !store.state.isSyncing {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                store.startSyncIfIdle()
            }
            onSync()
        } else if store.state.isSyncing {
            onSync()
        }
    }
}
