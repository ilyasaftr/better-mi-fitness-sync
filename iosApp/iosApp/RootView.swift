import SwiftUI
import ComposeApp

/// Native SwiftUI shell: auth bootstrap + navigation (no Compose UI).
struct RootView: View {
    enum AuthPhase: Equatable {
        case loading
        case loggedOut
        case loggedIn
    }

    enum MainRoute: Hashable {
        case home
        case settings
        case sync
    }

    @State private var auth: AuthPhase = .loading
    @State private var path = NavigationPath()

    var body: some View {
        Group {
            switch auth {
            case .loading:
                ZStack {
                    Brand.pageBackground.ignoresSafeArea()
                    ProgressView(L10n.homeLoadingProfile)
                        .tint(Brand.primary)
                }
            case .loggedOut:
                LoginView {
                    auth = .loggedIn
                    path = NavigationPath()
                }
            case .loggedIn:
                NavigationStack(path: $path) {
                    HomeView(
                        onSync: { path.append(MainRoute.sync) },
                        onSettings: { path.append(MainRoute.settings) },
                        onLogout: {
                            path = NavigationPath()
                            auth = .loggedOut
                        }
                    )
                    .navigationDestination(for: MainRoute.self) { route in
                        switch route {
                        case .home:
                            EmptyView()
                        case .settings:
                            SettingsView(onLogout: {
                                path = NavigationPath()
                                auth = .loggedOut
                            })
                        case .sync:
                            SyncView()
                        }
                    }
                }
            }
        }
        .tint(Brand.primary)
        .task {
            await restoreSession()
        }
    }

    private func restoreSession() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            IosAppBridge.shared.restoreAuth { result in
                Task { @MainActor in
                    auth = (result == "logged_in") ? .loggedIn : .loggedOut
                    cont.resume()
                }
            }
        }
    }
}
