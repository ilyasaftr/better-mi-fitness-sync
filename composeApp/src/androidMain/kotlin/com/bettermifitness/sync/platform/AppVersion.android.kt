package com.bettermifitness.sync.platform

import android.content.pm.PackageManager
import android.os.Build
import com.bettermifitness.sync.di.androidAppContext

actual fun appVersionLabel(): String {
    return try {
        val context = androidAppContext()
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        val name = info.versionName?.takeIf { it.isNotBlank() } ?: "—"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "$name ($code)"
    } catch (_: Exception) {
        "—"
    }
}
