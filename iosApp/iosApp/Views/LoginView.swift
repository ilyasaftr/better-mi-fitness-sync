import SwiftUI

/// Native login aligned with KMP `LoginScreen` flow:
/// Credentials → OTP (optional browser via “trouble”) → success.
struct LoginView: View {
    @StateObject private var store = LoginStore()
    @State private var otpCode = ""
    @State private var browserUrl = ""
    @State private var loginUrlCopied = false
    /// Shown when Paste is tapped but clipboard has nothing usable.
    @State private var pasteHint: String?
    @FocusState private var focusedField: Field?

    var onSuccess: () -> Void

    private enum Field {
        case email, password, otp, browserUrl
    }

    /// Same Xiaomi login URL as Compose `LoginScreen.LOGIN_URL`.
    private static let xiaomiLoginURL =
        "https://account.xiaomi.com/pass/serviceLogin?sid=miothealth&callback=https%3A%2F%2Fsts-hlth.io.mi.com%2Fhealthapp%2Fsts&_locale=en"

    var body: some View {
        NavigationStack {
            Group {
                switch store.step {
                case "Otp":
                    otpStep
                case "BrowserFallback":
                    browserStep
                default:
                    credentialsStep
                }
            }
            .background(Brand.pageBackground.ignoresSafeArea())
        }
        .onChange(of: store.loginSucceeded) { ok in
            if ok {
                store.consumeLoginSuccess()
                onSuccess()
            }
        }
        .onChange(of: store.step) { newStep in
            // Reset local fields when leaving a step so back/forward stays clean.
            if newStep != "Otp" { otpCode = "" }
            if newStep != "BrowserFallback" {
                browserUrl = ""
                loginUrlCopied = false
                pasteHint = nil
            }
            focusedField = nil
        }
    }

    // MARK: - Step 1: Credentials

    private var credentialsStep: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer(minLength: 24)

                VStack(spacing: 16) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 40, weight: .semibold))
                        .foregroundStyle(Brand.primary)
                        .frame(width: 72, height: 72)
                        .background(Brand.primary.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))

                    Text("Better Mi Fitness Sync")
                        .font(.title.bold())
                        .foregroundStyle(Brand.label)
                        .multilineTextAlignment(.center)

                    Text("Sign in with your Mi Account")
                        .font(.body)
                        .foregroundStyle(Brand.secondaryLabel)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 28)

                VStack(spacing: 14) {
                    loginField(title: "Email or phone") {
                        TextField(
                            "",
                            text: Binding(
                                get: { store.email },
                                set: { store.onEmailChange($0) }
                            ),
                            prompt: Text("Email or phone")
                                .foregroundColor(Brand.placeholder)
                        )
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .foregroundStyle(Brand.label)
                        .tint(Brand.primary)
                        .submitLabel(.next)
                        .focused($focusedField, equals: .email)
                        .onSubmit { focusedField = .password }
                    }

                    loginField(title: "Password") {
                        SecureField(
                            "",
                            text: Binding(
                                get: { store.password },
                                set: { store.onPasswordChange($0) }
                            ),
                            prompt: Text("Password")
                                .foregroundColor(Brand.placeholder)
                        )
                        .textContentType(.password)
                        .foregroundStyle(Brand.label)
                        .tint(Brand.primary)
                        .submitLabel(.go)
                        .focused($focusedField, equals: .password)
                        .onSubmit { attemptSignIn() }
                    }

                    Button {
                        attemptSignIn()
                    } label: {
                        if store.isLoading {
                            ProgressView().tint(.white)
                        } else {
                            Text("Sign in")
                        }
                    }
                    .buttonStyle(PrimaryButtonStyle(enabled: canSignIn))
                    .disabled(!canSignIn)
                    .padding(.top, 8)

                    if let err = store.errorMessage, !err.isEmpty {
                        errorBanner(err)
                    }
                }

                Text(
                    "Your password is only sent to Xiaomi to sign in. " +
                        "Account tokens stay on this device and are never shared with third parties."
                )
                .font(.caption)
                .foregroundStyle(Brand.secondaryLabel)
                .multilineTextAlignment(.center)
                .padding(.top, 24)
                .padding(.horizontal, 8)

                Spacer(minLength: 40)
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 16)
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationBarHidden(true)
    }

    private var canSignIn: Bool {
        !store.isLoading
            && !store.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !store.password.isEmpty
    }

    private func attemptSignIn() {
        guard canSignIn else { return }
        focusedField = nil
        store.signIn()
    }

    // MARK: - Step 2: OTP

    private static let otpLength = 6

    private var otpStep: some View {
        ScrollView {
            VStack(spacing: 20) {
                Image(systemName: "envelope.fill")
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(Brand.primary)
                    .frame(width: 64, height: 64)
                    .background(Brand.primary.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .padding(.top, 12)

                Text("Check your email")
                    .font(.title2.bold())
                    .foregroundStyle(Brand.label)

                Group {
                    if store.otpMaskedTarget.isEmpty {
                        Text("We sent a verification code to your Mi Account.")
                    } else {
                        Text("We sent a verification code to\n\(store.otpMaskedTarget)")
                    }
                }
                .font(.subheadline)
                .foregroundStyle(Brand.secondaryLabel)
                .multilineTextAlignment(.center)

                otpDigitBoxes
                    .padding(.top, 12)

                Button {
                    attemptVerifyOtp()
                } label: {
                    if store.isLoading {
                        ProgressView().tint(.white)
                    } else {
                        Text("Verify")
                    }
                }
                .buttonStyle(PrimaryButtonStyle(enabled: canVerifyOtp))
                .disabled(!canVerifyOtp)
                .padding(.top, 4)

                HStack(spacing: 4) {
                    Text("Didn't get it?")
                        .font(.subheadline)
                        .foregroundStyle(Brand.secondaryLabel)
                    Button("Resend") {
                        store.resendOtp()
                    }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Brand.primary)
                    .disabled(store.isLoading)
                }
                .padding(.top, 4)

                Button("Having trouble? Use browser login instead") {
                    store.goToBrowserFallback()
                }
                .font(.subheadline)
                .foregroundStyle(Brand.primary)
                .multilineTextAlignment(.center)
                .disabled(store.isLoading)

                if let err = store.errorMessage, !err.isEmpty {
                    errorBanner(err)
                }

                Spacer(minLength: 24)
            }
            .padding(.horizontal, 24)
            .frame(maxWidth: .infinity)
        }
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Verify your identity")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    store.goBackToCredentials()
                } label: {
                    Image(systemName: "chevron.left")
                }
                .accessibilityLabel("Back")
                .disabled(store.isLoading)
            }
        }
        .onAppear {
            // Focus code entry as soon as OTP step shows.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                focusedField = .otp
            }
        }
    }

    /// Six visual boxes + one real field for keyboard, paste, and SMS OTP autofill.
    private var otpDigitBoxes: some View {
        ZStack {
            TextField("", text: $otpCode)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focusedField, equals: .otp)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .opacity(0.02)
                .accessibilityLabel("Verification code")
                .accessibilityValue(otpCode.isEmpty ? "Empty" : "\(otpCode.count) of \(Self.otpLength) digits")
                .onChange(of: otpCode) { value in
                    let digits = String(value.filter(\.isNumber).prefix(Self.otpLength))
                    if digits != value {
                        otpCode = digits
                        return
                    }
                    if digits.count == Self.otpLength, !store.isLoading {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        // Auto-submit once the 6th digit is entered.
                        attemptVerifyOtp()
                    }
                }

            HStack(spacing: 8) {
                ForEach(0..<Self.otpLength, id: \.self) { index in
                    otpBox(
                        digit: otpDigit(at: index),
                        isActive: focusedField == .otp
                            && index == min(otpCode.count, Self.otpLength - 1)
                            && (otpCode.count < Self.otpLength || index == Self.otpLength - 1)
                    )
                }
            }
            .allowsHitTesting(false)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            focusedField = .otp
        }
    }

    private func otpDigit(at index: Int) -> Character? {
        guard index < otpCode.count else { return nil }
        return otpCode[otpCode.index(otpCode.startIndex, offsetBy: index)]
    }

    private func otpBox(digit: Character?, isActive: Bool) -> some View {
        return Text(digit.map(String.init) ?? "")
            .font(.title2.weight(.semibold))
            .foregroundStyle(Brand.label)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(Brand.fieldBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .strokeBorder(
                        isActive ? Brand.primary : Brand.separator,
                        lineWidth: isActive ? 2 : 1
                    )
            )
            .overlay(alignment: .center) {
                if digit == nil, isActive {
                    // Soft caret in the active empty box
                    RoundedRectangle(cornerRadius: 1)
                        .fill(Brand.primary)
                        .frame(width: 2, height: 22)
                        .opacity(0.85)
                }
            }
    }

    private var canVerifyOtp: Bool {
        !store.isLoading && otpCode.count == Self.otpLength
    }

    private func attemptVerifyOtp() {
        guard canVerifyOtp else { return }
        focusedField = nil
        store.verifyOtp(otpCode)
    }

    // MARK: - Step 3: Browser fallback (two phases + icon actions)

    private var browserStep: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    phaseBlock(title: "Step 1 · Sign in") {
                        Text("Sign in with Xiaomi in the browser, then return here.")
                            .font(.subheadline)
                            .foregroundStyle(Brand.secondaryLabel)
                            .fixedSize(horizontal: false, vertical: true)

                        VStack(spacing: 10) {
                            Button {
                                openXiaomiLogin()
                            } label: {
                                Label("Open Xiaomi login", systemImage: "arrow.up.right.square")
                            }
                            .buttonStyle(PrimaryButtonStyle(enabled: !store.isLoading))
                            .disabled(store.isLoading)

                            Button {
                                UIPasteboard.general.string = Self.xiaomiLoginURL
                                loginUrlCopied = true
                            } label: {
                                Label(
                                    loginUrlCopied ? "Link copied" : "Copy login link",
                                    systemImage: loginUrlCopied ? "checkmark" : "link"
                                )
                            }
                            .buttonStyle(SecondaryButtonStyle(enabled: !store.isLoading))
                            .disabled(store.isLoading)
                        }
                        .padding(.top, 4)
                    }

                    phaseBlock(title: "Step 2 · Finish here") {
                        Text("In Safari, copy the page link after you see “ok”. Then tap the button below to paste it.")
                            .font(.subheadline)
                            .foregroundStyle(Brand.secondaryLabel)
                            .fixedSize(horizontal: false, vertical: true)

                        // Compact paste control — never show the full long redirect URL.
                        redirectUrlPasteControl
                            .padding(.top, 4)
                    }

                    if let err = store.errorMessage, !err.isEmpty {
                        errorBanner(err)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 16)
            }
            .scrollDismissesKeyboard(.interactively)

            VStack(spacing: 0) {
                Divider()
                Button {
                    attemptCompleteBrowser()
                } label: {
                    if store.isLoading {
                        ProgressView().tint(.white)
                    } else {
                        Label("Complete login", systemImage: "checkmark.circle.fill")
                    }
                }
                .buttonStyle(PrimaryButtonStyle(enabled: canCompleteBrowser))
                .disabled(!canCompleteBrowser)
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 12)
            }
            .background(.bar)
        }
        .navigationTitle("Browser login")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    store.goBackFromBrowser()
                } label: {
                    Image(systemName: "chevron.left")
                }
                .accessibilityLabel("Back")
                .disabled(store.isLoading)
            }
        }
    }

    private func phaseBlock<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Brand.secondaryLabel)
                .textCase(.uppercase)

            VStack(alignment: .leading, spacing: 14) {
                content()
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
        }
    }

    private var trimmedBrowserUrl: String {
        browserUrl.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Only a validated Xiaomi STS redirect counts as “ready”.
    private var hasValidBrowserUrl: Bool {
        Self.isValidStsRedirectUrl(trimmedBrowserUrl)
    }

    private var canCompleteBrowser: Bool {
        !store.isLoading && hasValidBrowserUrl
    }

    /// Empty: paste target. Valid: ready chip. Invalid paste: immediate error (no Complete needed).
    private var redirectUrlPasteControl: some View {
        VStack(alignment: .leading, spacing: 8) {
            if hasValidBrowserUrl {
                HStack(spacing: 12) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title3)
                        .foregroundStyle(Brand.success)
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Link pasted")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.label)
                        Text(redirectUrlSummary(trimmedBrowserUrl))
                            .font(.caption)
                            .foregroundStyle(Brand.secondaryLabel)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 8)

                    Button {
                        pasteRedirectFromClipboard()
                    } label: {
                        Image(systemName: "doc.on.clipboard")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Brand.primary)
                            .frame(minWidth: 36, minHeight: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Paste a different link")
                    .disabled(store.isLoading)

                    Button {
                        browserUrl = ""
                        pasteHint = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body)
                            .foregroundStyle(Brand.secondaryLabel.opacity(0.55))
                            .frame(minWidth: 36, minHeight: 36)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Remove pasted link")
                    .disabled(store.isLoading)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Brand.success.opacity(0.08))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Brand.success.opacity(0.25), lineWidth: 1)
                        .allowsHitTesting(false)
                }
            } else {
                Button {
                    pasteRedirectFromClipboard()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "doc.on.clipboard")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(Brand.primary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Tap to paste the link")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(Brand.label)
                            Text("Copy it from Safari first")
                                .font(.caption)
                                .foregroundStyle(Brand.secondaryLabel)
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Brand.secondaryLabel.opacity(0.6))
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .background(Brand.fieldBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .strokeBorder(
                                pasteHint != nil ? Brand.danger.opacity(0.45) : Brand.separator,
                                style: StrokeStyle(lineWidth: 1, dash: [5, 4])
                            )
                            .allowsHitTesting(false)
                    }
                }
                .buttonStyle(.plain)
                .disabled(store.isLoading)
                .accessibilityLabel("Tap to paste the link from Safari")
            }

            if let pasteHint, !pasteHint.isEmpty {
                Text(pasteHint)
                    .font(.caption)
                    .foregroundStyle(Brand.danger)
                    .fixedSize(horizontal: false, vertical: true)
            } else if hasValidBrowserUrl {
                Text("You’re set — tap Complete login below.")
                    .font(.caption)
                    .foregroundStyle(Brand.secondaryLabel)
            } else {
                Text("Tip: the link should come from the address bar after Xiaomi shows “ok”.")
                    .font(.caption)
                    .foregroundStyle(Brand.secondaryLabel)
            }
        }
    }

    /// Reads clipboard and validates immediately — invalid text never becomes “ready”.
    private func pasteRedirectFromClipboard() {
        let board = UIPasteboard.general
        let candidates: [String?] = [
            board.url?.absoluteString,
            board.string,
            board.strings?.first,
        ]
        let pasted = candidates
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }

        guard let pasted else {
            browserUrl = ""
            pasteHint = "Nothing to paste yet. In Safari, copy the page link, then tap here again."
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
            return
        }

        let normalized = Self.firstNonEmptyLine(of: pasted)
        guard Self.isValidStsRedirectUrl(normalized) else {
            browserUrl = ""
            pasteHint = Self.invalidRedirectMessage(for: normalized)
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            return
        }

        browserUrl = normalized
        pasteHint = nil
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    /// Xiaomi STS redirect: https://sts-hlth.io.mi.com/… (same rule as auth docs).
    private static func isValidStsRedirectUrl(_ raw: String) -> Bool {
        let trimmed = firstNonEmptyLine(of: raw)
        guard !trimmed.isEmpty else { return false }

        let lower = trimmed.lowercased()
        if lower.hasPrefix("https://sts-hlth.io.mi.com/")
            || lower.hasPrefix("http://sts-hlth.io.mi.com/") {
            return true
        }

        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              let host = url.host?.lowercased()
        else {
            return false
        }
        return host == "sts-hlth.io.mi.com" || host.hasSuffix(".sts-hlth.io.mi.com")
    }

    private static func firstNonEmptyLine(of raw: String) -> String {
        raw
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .first { !$0.isEmpty }
            ?? raw.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func invalidRedirectMessage(for pasted: String) -> String {
        let lower = pasted.lowercased()
        if lower.hasPrefix("http://") || lower.hasPrefix("https://") {
            return "That link isn’t the right one. After Xiaomi shows “ok”, copy the link from the address bar and try again."
        }
        return "That isn’t a web link. Copy the full page link from Safari’s address bar, then tap paste again."
    }

    /// Short host-style summary — never the full query string.
    private func redirectUrlSummary(_ raw: String) -> String {
        let trimmed = Self.firstNonEmptyLine(of: raw)
        if let url = URL(string: trimmed), let host = url.host, !host.isEmpty {
            return host
        }
        return "sts-hlth.io.mi.com"
    }

    private func openXiaomiLogin() {
        if let url = URL(string: Self.xiaomiLoginURL) {
            UIApplication.shared.open(url)
        }
    }

    private func attemptCompleteBrowser() {
        guard canCompleteBrowser else { return }
        focusedField = nil
        store.completeBrowserLogin(trimmedBrowserUrl)
    }

    // MARK: - Shared chrome

    private func loginField<Content: View>(
        title: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Brand.label)
            content()
                .padding(.horizontal, 14)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                .background(Brand.fieldBackground)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(Brand.separator, lineWidth: 1)
                )
        }
    }

    private func errorBanner(_ message: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundStyle(Brand.danger)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(Brand.danger)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.danger.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}
