package com.bettermifitness.sync.platform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions

@OptIn(ExperimentalComposeUiApi::class)
actual fun loginPlatformImeOptions(): PlatformImeOptions? = PlatformImeOptions {
    usingNativeTextInput(true)
}
