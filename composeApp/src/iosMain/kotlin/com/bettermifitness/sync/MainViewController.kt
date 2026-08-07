package com.bettermifitness.sync

/**
 * iOS entry helpers. UI is native SwiftUI ([IosAppBridge]); this only boots DI.
 * Compose is no longer hosted on iOS.
 */
private var koinStarted = false

/**
 * Starts Koin exactly once. Safe to call from Swift app launch and background sync.
 */
fun doInitKoin() {
    if (koinStarted) return
    koinStarted = true
    com.bettermifitness.sync.di.initKoin()
}
