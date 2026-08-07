import Foundation
import ComposeApp
import Combine

@MainActor
final class LoginStore: ObservableObject {
    @Published private(set) var step: String = "Credentials"
    @Published var email: String = ""
    @Published var password: String = ""
    @Published private(set) var isLoading: Bool = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var otpMaskedTarget: String = ""
    @Published private(set) var loginSucceeded: Bool = false

    private let vm: LoginViewModel
    private var subscription: FlowSubscription?

    init() {
        self.vm = IosAppBridge.shared.createLoginViewModel()
        subscription = FlowSubscription(
            IosAppBridge.shared.watchLogin(vm: vm) { [weak self] state in
                Task { @MainActor in
                    self?.apply(state)
                }
            }
        )
    }

    deinit {
        subscription?.cancel()
    }

    private func apply(_ state: LoginUiState) {
        step = state.step.name
        email = state.email
        password = state.password
        isLoading = state.isLoading
        errorMessage = state.errorMessage
        otpMaskedTarget = state.otpMaskedTarget
        loginSucceeded = state.loginSucceeded
    }

    func onEmailChange(_ value: String) {
        email = value
        vm.onEmailChange(value: value)
    }

    func onPasswordChange(_ value: String) {
        password = value
        vm.onPasswordChange(value: value)
    }

    func signIn() { vm.signIn() }
    func verifyOtp(_ code: String) { vm.verifyOtp(code: code) }
    func resendOtp() { vm.resendOtp() }
    func completeBrowserLogin(_ url: String) { vm.completeBrowserLogin(callbackUrl: url) }
    func goToBrowserFallback() { vm.goToBrowserFallback() }
    func goBackToCredentials() { vm.goBackToCredentials() }
    /// Browser back → OTP when a code challenge is active, else credentials.
    func goBackFromBrowser() { vm.goBackFromBrowser() }
    func consumeLoginSuccess() { vm.consumeLoginSuccess() }
}
