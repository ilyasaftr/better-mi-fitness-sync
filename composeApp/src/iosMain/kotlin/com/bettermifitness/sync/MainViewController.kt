package com.bettermifitness.sync

import androidx.compose.ui.window.ComposeUIViewController
import com.bettermifitness.sync.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = { doInitKoin() },
) {
    App()
}

private var koinStarted = false

/**
 * Starts Koin exactly once. Safe to call from both the Compose entry point and
 * the Swift app launch (so dependencies are available for background sync too).
 */
fun doInitKoin() {
    if (koinStarted) return
    koinStarted = true
    initKoin()
}
