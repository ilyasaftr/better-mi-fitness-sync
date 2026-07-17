package com.bettermifitness.sync.util

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Human-readable "time ago" labels shared by Home and Settings.
 */
object RelativeTime {
    fun format(iso: String?, neverLabel: String = "Never"): String {
        if (iso.isNullOrEmpty()) return neverLabel
        return try {
            val then = Instant.parse(iso)
            val diff = Clock.System.now() - then
            when {
                diff < 1.minutes -> "Just now"
                diff < 60.minutes -> "${diff.inWholeMinutes} min ago"
                diff < 24.hours -> {
                    val h = diff.inWholeHours
                    if (h == 1L) "1 hour ago" else "$h hours ago"
                }
                diff < 48.hours -> "Yesterday"
                else -> "${diff.inWholeDays} days ago"
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }
}
