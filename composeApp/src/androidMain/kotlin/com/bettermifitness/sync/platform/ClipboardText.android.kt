package com.bettermifitness.sync.platform

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipData.newPlainText("text", text).toClipEntry())
}

actual suspend fun Clipboard.getPlainText(): String? {
    val entry = getClipEntry() ?: return null
    val data = entry.clipData
    if (data.itemCount <= 0) return null
    return data.getItemAt(0)?.coerceToText(null)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
