package com.bettermifitness.sync.platform

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions

/**
 * Platform IME tweaks for login fields.
 * iOS enables CMP 1.11 native text input (caret, selection, Autofill, system menus).
 */
expect fun loginPlatformImeOptions(): PlatformImeOptions?

fun loginKeyboardOptions(
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Default,
): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboardType,
    imeAction = imeAction,
    platformImeOptions = loginPlatformImeOptions(),
)
