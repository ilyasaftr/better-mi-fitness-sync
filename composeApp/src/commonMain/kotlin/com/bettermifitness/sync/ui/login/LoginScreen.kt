package com.bettermifitness.sync.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.platform.getPlainText
import com.bettermifitness.sync.platform.loginKeyboardOptions
import com.bettermifitness.sync.platform.setPlainText
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.ui.components.PrimaryButton
import com.bettermifitness.sync.ui.components.StickyCtaBar
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

    // Leave login before clearing the flag so Back cannot land on OTP/browser again.
    LaunchedEffect(state.loginSucceeded) {
        if (state.loginSucceeded) {
            onLoginSuccess()
            viewModel.consumeLoginSuccess()
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
            onBack = {
                if (!state.loginSucceeded) viewModel.goBackToCredentials()
            },
        )

        LoginStep.BrowserFallback -> BrowserFallbackStep(
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            onComplete = viewModel::completeBrowserLogin,
            onBack = {
                // OTP if left from OTP; credentials if OTP was skipped (rate limit).
                if (!state.loginSucceeded) viewModel.goBackFromBrowser()
            },
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
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppIcon(
                    AppIcons.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Better Mi Fitness Sync",
            style = MaterialTheme.typography.headlineMedium,
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

private const val OTP_LENGTH = 6

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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Verify your identity", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppIcon(
                        AppIcons.Mail,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Check your email",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (maskedTarget.isBlank()) {
                    "We sent a verification code to your Mi Account."
                } else {
                    "We sent a verification code to\n$maskedTarget"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            // Six visual boxes + one real field (keyboard, paste, autofill) — iOS parity
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable { focusRequester.requestFocus() },
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(OTP_LENGTH)
                        code = digits
                        if (digits.length == OTP_LENGTH && !isLoading) onVerify(digits)
                    },
                    singleLine = true,
                    keyboardOptions = loginKeyboardOptions(KeyboardType.Number, ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (code.length == OTP_LENGTH && !isLoading) onVerify(code)
                        },
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.02f)
                        .focusRequester(focusRequester),
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(OTP_LENGTH) { index ->
                        val digit = code.getOrNull(index)?.toString() ?: ""
                        val isActive = index == code.length.coerceAtMost(OTP_LENGTH - 1) &&
                            code.length < OTP_LENGTH
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (digit.isNotEmpty()) {
                                    Text(
                                        digit,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else if (isActive) {
                                    Box(
                                        Modifier
                                            .width(2.dp)
                                            .height(22.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                RoundedCornerShape(1.dp),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onVerify(code) },
                enabled = !isLoading && code.length == OTP_LENGTH,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = BrandShapes.Button,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onResend, enabled = !isLoading) { Text("Resend") }
            }
            TextButton(onClick = onTrouble, enabled = !isLoading) {
                Text("Having trouble? Use browser login instead", textAlign = TextAlign.Center)
            }

            ErrorText(errorMessage)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============================================================
// Step 2b: Browser Fallback (parity with iOS LoginView)
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
    var pasteHint by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val trimmed = callbackUrl.trim()
    val hasValidUrl = isValidStsRedirectUrl(trimmed)

    fun pasteFromClipboard() {
        scope.launch {
            val text = clipboard.getPlainText()?.let { firstNonEmptyLine(it) }
            if (text.isNullOrBlank()) {
                callbackUrl = ""
                pasteHint = "Nothing to paste yet. In the browser, copy the page link, then tap here again."
                return@launch
            }
            if (!isValidStsRedirectUrl(text)) {
                callbackUrl = ""
                pasteHint = invalidRedirectMessage(text)
                return@launch
            }
            callbackUrl = text
            pasteHint = null
        }
    }

    fun submit() {
        if (!hasValidUrl || isLoading) return
        focusManager.clearFocus()
        onComplete(trimmed)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Browser login", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            StickyCtaBar(
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                PrimaryButton(
                    text = "Complete login",
                    onClick = { submit() },
                    enabled = hasValidUrl && !isLoading,
                    loading = isLoading,
                    icon = AppIcons.CheckCircle,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PhaseCard(title = "Step 1 · Sign in") {
                Text(
                    "Sign in with Xiaomi in the browser, then return here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        runCatching { uriHandler.openUri(LOGIN_URL) }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = BrandShapes.Button,
                ) {
                    Text("Open Xiaomi login")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            clipboard.setPlainText(LOGIN_URL)
                            urlCopied = true
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = BrandShapes.Button,
                ) {
                    AppIcon(AppIcons.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (urlCopied) "Link copied" else "Copy login link")
                }
            }

            PhaseCard(title = "Step 2 · Finish here") {
                Text(
                    "In the browser, copy the page link after you see “ok”. Then tap below to paste it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                RedirectPasteControl(
                    hasValidUrl = hasValidUrl,
                    summary = redirectUrlSummary(trimmed),
                    pasteHint = pasteHint,
                    isLoading = isLoading,
                    onPaste = { pasteFromClipboard() },
                    onClear = {
                        callbackUrl = ""
                        pasteHint = null
                    },
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                ErrorBanner(errorMessage)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PhaseCard(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun RedirectPasteControl(
    hasValidUrl: Boolean,
    summary: String,
    pasteHint: String?,
    isLoading: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasValidUrl) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(
                        AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Link pasted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onPaste, enabled = !isLoading) {
                        AppIcon(AppIcons.ContentPaste, contentDescription = "Paste a different link")
                    }
                    IconButton(onClick = onClear, enabled = !isLoading) {
                        AppIcon(
                            AppIcons.ErrorOutline,
                            contentDescription = "Remove pasted link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            Surface(
                onClick = onPaste,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (pasteHint != null) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(
                        AppIcons.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Tap to paste the link",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Copy it from the browser first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppIcon(
                        AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Text(
            when {
                !pasteHint.isNullOrBlank() -> pasteHint
                hasValidUrl -> "You’re set — tap Complete login below."
                else -> "Tip: the link should come from the address bar after Xiaomi shows “ok”."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!pasteHint.isNullOrBlank()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun firstNonEmptyLine(raw: String): String =
    raw.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: raw.trim()

/** Xiaomi STS redirect: https://sts-hlth.io.mi.com/… */
private fun isValidStsRedirectUrl(raw: String): Boolean {
    val trimmed = firstNonEmptyLine(raw)
    if (trimmed.isEmpty()) return false
    val lower = trimmed.lowercase()
    if (lower.startsWith("https://sts-hlth.io.mi.com/") ||
        lower.startsWith("http://sts-hlth.io.mi.com/")
    ) {
        return true
    }
    // Host-style parse without android.net.Uri (KMP-safe)
    val withoutScheme = when {
        lower.startsWith("https://") -> trimmed.drop(8)
        lower.startsWith("http://") -> trimmed.drop(7)
        else -> return false
    }
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        .substringBefore(':')
        .lowercase()
    return host == "sts-hlth.io.mi.com" || host.endsWith(".sts-hlth.io.mi.com")
}

private fun invalidRedirectMessage(pasted: String): String {
    val lower = pasted.lowercase()
    return if (lower.startsWith("http://") || lower.startsWith("https://")) {
        "That link isn’t the right one. After Xiaomi shows “ok”, copy the link from the address bar and try again."
    } else {
        "That isn’t a web link. Copy the full page link from the browser’s address bar, then tap paste again."
    }
}

private fun redirectUrlSummary(raw: String): String {
    val trimmed = firstNonEmptyLine(raw)
    val withoutScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed.drop(8)
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed.drop(7)
        else -> return "sts-hlth.io.mi.com"
    }
    val host = withoutScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        .substringBefore(':')
    return host.ifBlank { "sts-hlth.io.mi.com" }
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
