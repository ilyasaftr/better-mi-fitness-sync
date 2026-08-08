package com.bettermifitness.sync.util

import com.bettermifitness.sync.i18n.L10n
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Human-readable time-ago labels shared by Home and Settings. */
object RelativeTime {
    fun format(iso: String?, neverLabel: String = L10n.text(L10n.homeNever)): String {
        if (iso.isNullOrEmpty()) return neverLabel
        return try {
            val then = Instant.parse(iso)
            val diff = Clock.System.now() - then
            when {
                diff < 1.minutes -> L10n.text(L10n.timeJustNow)
                diff < 60.minutes -> L10n.textFmt(L10n.timeMinuteAgo, diff.inWholeMinutes)
                diff < 24.hours -> {
                    val h = diff.inWholeHours
                    if (h == 1L) L10n.text(L10n.timeHourOneAgo)
                    else L10n.textFmt(L10n.timeHourManyAgo, h)
                }
                diff < 48.hours -> L10n.text(L10n.timeYesterday)
                else -> {
                    val d = diff.inWholeDays
                    if (d == 1L) L10n.text(L10n.timeDayOneAgo)
                    else L10n.textFmt(L10n.timeDayManyAgo, d)
                }
            }
        } catch (_: Exception) {
            L10n.text(L10n.timeUnknown)
        }
    }
}
