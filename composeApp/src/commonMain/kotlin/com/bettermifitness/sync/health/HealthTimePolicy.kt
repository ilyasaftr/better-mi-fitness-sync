package com.bettermifitness.sync.health

import kotlin.time.Clock

/**
 * Health Connect / HealthKit absolute-time rules:
 * - Zone offset is for civil display only.
 * - Record Instant must not be after "now" (HC rejects with TIME_IN_FUTURE).
 *
 * See issue #10: daily resting HR at UTC midnight can still be in the future for
 * users east of UTC early in their local morning.
 */
object HealthTimePolicy {

    /** Allow small device/Mi clock skew before treating a sample as future. */
    const val CLOCK_SKEW_SECONDS: Long = 120L

    fun nowEpochSeconds(clock: Clock = Clock.System): Long = clock.now().epochSeconds

    /** True if [epochSec] is acceptable for a point-in-time health write. */
    fun isNotFuture(
        epochSec: Long,
        nowSec: Long,
        skewSec: Long = CLOCK_SKEW_SECONDS,
    ): Boolean = epochSec <= nowSec + skewSec

    /**
     * Clamps [end] to [nowSec]+skew when the interval is still open / slightly future.
     * Drops the interval if [start] is in the future or end would not be after start.
     */
    fun clampInterval(
        start: Long,
        end: Long,
        nowSec: Long,
        skewSec: Long = CLOCK_SKEW_SECONDS,
    ): Pair<Long, Long>? {
        val maxAllowed = nowSec + skewSec
        if (start > maxAllowed) return null
        val clampedEnd = minOf(end, maxAllowed)
        if (clampedEnd <= start) return null
        return start to clampedEnd
    }
}
