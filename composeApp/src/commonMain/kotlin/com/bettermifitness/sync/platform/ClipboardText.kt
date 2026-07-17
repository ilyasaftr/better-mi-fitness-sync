package com.bettermifitness.sync.platform

import androidx.compose.ui.platform.Clipboard

/** Cross-platform plain-text helpers for the CMP 1.8+ [Clipboard] API. */
expect suspend fun Clipboard.setPlainText(text: String)

expect suspend fun Clipboard.getPlainText(): String?
