package com.bettermifitness.sync.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.platform.getPlainText
import com.bettermifitness.sync.platform.loginKeyboardOptions
import com.bettermifitness.sync.platform.setPlainText
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val viewModel = remember {
        KoinPlatform.getKoin().get<LoginViewModel>()
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loginSucceeded) {
        if (state.loginSucceeded) {
            viewModel.consumeLoginSuccess()
            onLoginSuccess()
        }
    }

    when (state.step) {
        LoginStep.Credentials -> CredentialsStep(
            email = state.email,
            password = state.password,
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onSignIn = viewModel::signIn,
        )

        LoginStep.Otp -> OtpStep(
            maskedTarget = state.otpMaskedTarget,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onVerify = viewModel::verifyOtp,
            onResend = viewModel::resendOtp,
            onTrouble = viewModel::goToBrowserFallback,
            onBack = viewModel::goBackToCredentials,
        )

        LoginStep.BrowserFallback -> BrowserFallbackStep(
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onComplete = viewModel::completeBrowserLogin,
            onBack = viewModel::goBackToCredentials,
        )
    }
}

// ============================================================
// Step 1: Email + Password
// ============================================================

@Composable
private fun CredentialsStep(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            AppIcons.Sync,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Better Mi Fitness Sync",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with your Mi Account",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email or phone") },
            keyboardOptions = loginKeyboardOptions(KeyboardType.Email, ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = loginKeyboardOptions(KeyboardType.Password, ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSignIn,
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }

        ErrorBanner(errorMessage)

        Spacer(Modifier.height(20.dp))
        Text(
            "Your password is only sent to Xiaomi to sign in. " +
                "Account tokens stay on this device and are never shared with third parties.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ============================================================
// Step 2a: OTP Code Input
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpStep(
    maskedTarget: String,
    isLoading: Boolean,
    errorMessage: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onTrouble: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Verify Your Identity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            AppIcon(
                AppIcons.Mail,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Check your email",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "We sent a verification code to\n$maskedTarget",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) code = it },
                label = { Text("Verification Code") },
                keyboardOptions = loginKeyboardOptions(KeyboardType.Number, ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onVerify(code) },
                enabled = !isLoading && code.length >= 4,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Verify", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Didn't get it?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onResend) { Text("Resend") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onTrouble) {
                Text("Having trouble? Use browser login instead")
            }

            ErrorText(errorMessage)
        }
    }
}

// ============================================================
// Step 2b: Browser Fallback
// ============================================================

private const val LOGIN_URL =
    "https://account.xiaomi.com/pass/serviceLogin?sid=miothealth&callback=https%3A%2F%2Fsts-hlth.io.mi.com%2Fhealthapp%2Fsts&_locale=en"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserFallbackStep(
    isLoading: Boolean,
    errorMessage: String?,
    onComplete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var callbackUrl by remember { mutableStateOf("") }
    var urlCopied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val fieldFocus = remember { FocusRequester() }

    fun pasteFromClipboard() {
        scope.launch {
            val text = clipboard.getPlainText()
            if (!text.isNullOrBlank()) callbackUrl = text
        }
    }

    fun submit() {
        focusManager.clearFocus()
        onComplete(callbackUrl.trim())
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Browser Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(
                        onClick = { pasteFromClipboard() },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        AppIcon(AppIcons.ContentPaste, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Paste redirect URL")
                    }
                    Button(
                        onClick = { submit() },
                        enabled = !isLoading && callbackUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Complete Login", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Finish login in the browser, then paste the full redirect URL from the address bar " +
                    "(must start with https://sts-hlth.io.mi.com/). We complete sign-in from that URL — " +
                    "not by asking for another verification code.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StepCard(number = 1, title = "Open & login in Safari") {
                Text(
                    "Use Google, Facebook, or Apple sign-in if available (avoids email OTP limits).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    LOGIN_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            clipboard.setPlainText(LOGIN_URL)
                            urlCopied = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppIcon(AppIcons.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (urlCopied) "Login URL copied" else "Copy login URL")
                }
            }

            StepCard(number = 2, title = "Copy the redirect URL") {
                Text(
                    "After login, the page says “ok”. Copy the full address bar URL (starts with https://sts-hlth.io.mi.com/...).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StepCard(number = 3, title = "Paste redirect URL") {
                OutlinedTextField(
                    value = callbackUrl,
                    onValueChange = { callbackUrl = it },
                    label = { Text("Redirect URL") },
                    placeholder = { Text("https://sts-hlth.io.mi.com/...") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = loginKeyboardOptions(KeyboardType.Uri, ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (callbackUrl.isNotBlank() && !isLoading) submit()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tip: use Paste below — you don’t need to type the URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    LaunchedEffect(callbackUrl) {
        if (callbackUrl.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}

// ============================================================
// Shared components
// ============================================================

@Composable
private fun StepCard(number: Int, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ErrorText(message: String?) {
    ErrorBanner(message)
}

@Composable
private fun ErrorBanner(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(14.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(
                AppIcons.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
