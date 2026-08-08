package com.bettermifitness.sync.platform

import platform.Foundation.NSBundle

/** Used if Compose UI ever reads version on iOS; SwiftUI reads Bundle directly. */
actual fun appVersionLabel(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val name = (info?.get("CFBundleShortVersionString") as? String)?.takeIf { it.isNotBlank() } ?: "—"
    val build = (info?.get("CFBundleVersion") as? String)?.takeIf { it.isNotBlank() } ?: "—"
    return "$name ($build)"
}
