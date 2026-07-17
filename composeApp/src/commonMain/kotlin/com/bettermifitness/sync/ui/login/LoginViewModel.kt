package com.bettermifitness.sync.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.preferences.CredentialsStore
import com.mifitness.miclient.auth.LoginResult
import com.mifitness.miclient.auth.MiAuth
import com.mifitness.miclient.auth.MiCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginStep {
    Credentials,
    Otp,
    BrowserFallback,
}

data class LoginUiState(
    val step: LoginStep = LoginStep.Credentials,
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val otpMaskedTarget: String = "",
    val loginSucceeded: Boolean = false,
)

class LoginViewModel(
    private val miAuth: MiAuth,
    private val sessionManager: MiSessionManager,
    private val credentialsStore: CredentialsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var otpChallenge: LoginResult.OtpRequired? = null

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun signIn() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        if (email.isBlank() || password.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                when (val result = miAuth.login(email = email, password = password)) {
                    is LoginResult.Success -> persistAndSucceed(result.credentials)
                    is LoginResult.OtpRequired -> {
                        otpChallenge = result
                        try {
                            result.sendOtp()
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    step = LoginStep.Otp,
                                    otpMaskedTarget = result.maskedTarget,
                                    errorMessage = null,
                                )
                            }
                        } catch (e: Exception) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    step = LoginStep.BrowserFallback,
                                    errorMessage = e.message
                                        ?: "Could not send verification email. Use browser login.",
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Login failed")
                }
            }
        }
    }

    fun verifyOtp(code: String) {
        val challenge = otpChallenge ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val credentials = challenge.verifyOtp(code)
                persistAndSucceed(credentials)
            } catch (e: Exception) {
                val msg = e.message ?: "Verification failed"
                if (shouldFallbackToBrowser(msg)) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            step = LoginStep.BrowserFallback,
                            errorMessage =
                                "Email code was accepted, but Xiaomi still needs a one-time browser login " +
                                    "to trust this device. Paste the redirect URL below.",
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
                }
            }
        }
    }

    fun resendOtp() {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                otpChallenge?.sendOtp()
                _uiState.update { it.copy(errorMessage = "Code resent!") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Resend failed: ${e.message}")
                }
            }
        }
    }

    fun completeBrowserLogin(callbackUrl: String) {
        val cleaned = callbackUrl.trim()
        if (cleaned.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Paste the full redirect URL from the address bar first.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Correct path: finish STS session from the pasted URL.
                // Re-running password login with only deviceId still hits OTP and shows
                // "Still requires verification" even with a valid redirect.
                val credentials = miAuth.completeFromCallbackUrl(cleaned)
                persistAndSucceed(credentials)
            } catch (e: Exception) {
                // Optional fallback: trusted deviceId + password (rarely works after OTP).
                val deviceId = extractDeviceId(cleaned)
                val email = _uiState.value.email.trim()
                val password = _uiState.value.password
                if (deviceId.isNotEmpty() && email.isNotBlank() && password.isNotBlank()) {
                    try {
                        when (
                            val result = miAuth.login(
                                email = email,
                                password = password,
                                deviceId = deviceId,
                            )
                        ) {
                            is LoginResult.Success -> {
                                persistAndSucceed(result.credentials)
                                return@launch
                            }
                            is LoginResult.OtpRequired -> {
                                // Fall through to user-facing error from STS attempt.
                            }
                        }
                    } catch (_: Exception) {
                        // Prefer the STS error message below.
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                            ?: "Could not finish browser login from that URL. Paste the full sts-hlth URL.",
                    )
                }
            }
        }
    }

    fun goToBrowserFallback() {
        _uiState.update {
            it.copy(step = LoginStep.BrowserFallback, errorMessage = null)
        }
    }

    fun goBackToCredentials() {
        otpChallenge = null
        _uiState.update {
            it.copy(
                step = LoginStep.Credentials,
                errorMessage = null,
                otpMaskedTarget = "",
            )
        }
    }

    fun consumeLoginSuccess() {
        _uiState.update { it.copy(loginSucceeded = false) }
    }

    private suspend fun persistAndSucceed(credentials: MiCredentials) {
        sessionManager.activate(credentials)
        credentialsStore.saveCredentials(credentials)
        _uiState.update {
            it.copy(isLoading = false, loginSucceeded = true, errorMessage = null)
        }
    }

    companion object {
        private val OTP_BROWSER_HINTS = listOf(
            "OTP_ACCEPTED_NEEDS_BROWSER",
            "still require",
            "still won't",
            "browser login",
        )

        fun shouldFallbackToBrowser(message: String): Boolean =
            OTP_BROWSER_HINTS.any { message.contains(it, ignoreCase = true) }

        fun extractDeviceId(url: String): String {
            val cleaned = url.trim().lines().firstOrNull { it.isNotBlank() }?.trim() ?: return ""
            // Xiaomi uses d= on STS; some pages also use deviceId=
            val patterns = listOf(
                Regex("[?&]d=([^&]+)"),
                Regex("[?&]deviceId=([^&]+)"),
                Regex("[?&]device_id=([^&]+)"),
            )
            for (regex in patterns) {
                val value = regex.find(cleaned)?.groupValues?.get(1)
                if (!value.isNullOrBlank()) return value
            }
            return ""
        }
    }
}
