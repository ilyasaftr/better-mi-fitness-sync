package com.bettermifitness.sync.data.time

/**
 * Mi Fitness `timezone` field: offset in units of **15 minutes**.
 *
 * Matches APK `FitnessDateUtils.getTzOffsetInSecBy15min`:
 * - 28 → UTC+7 (28 × 15 min)
 * - 32 → UTC+8
 *
 * Pure domain type — no platform time APIs (safe for commonMain / unit tests).
 * (Regular class, not value class: KMP needs `@JvmInline` on JVM but that is unavailable on Native.)
 */
class MiTimezone private constructor(val unitsOf15Min: Int) {

    /** Offset from UTC in seconds (e.g. 28 → 25200). */
    fun offsetSeconds(): Int = unitsOf15Min * MINUTES_PER_UNIT * SECONDS_PER_MINUTE

    fun isWithinHealthConnectRange(): Boolean =
        offsetSeconds() in MIN_OFFSET_SECONDS..MAX_OFFSET_SECONDS

    override fun equals(other: Any?): Boolean =
        other is MiTimezone && unitsOf15Min == other.unitsOf15Min

    override fun hashCode(): Int = unitsOf15Min

    override fun toString(): String = "MiTimezone(unitsOf15Min=$unitsOf15Min)"

    companion object {
        const val MINUTES_PER_UNIT = 15
        const val SECONDS_PER_MINUTE = 60

        /** Health Connect accepts offsets roughly in ±18 hours. */
        const val MIN_OFFSET_SECONDS = -18 * 3600
        const val MAX_OFFSET_SECONDS = 18 * 3600

        /**
         * @return valid [MiTimezone], or null if [units] is null or outside HC range.
         */
        fun fromPayloadOrNull(units: Int?): MiTimezone? {
            if (units == null) return null
            val candidate = MiTimezone(units)
            return candidate.takeIf { it.isWithinHealthConnectRange() }
        }
    }
}

/**
 * Prefer Mi session/sample timezone; otherwise [fallbackSeconds] (typically device offset).
 * Result is always clamped to Health Connect’s accepted range.
 */
fun resolveOffsetSeconds(
    mi: MiTimezone?,
    fallbackSeconds: Int = 0,
): Int {
    val raw = mi?.offsetSeconds() ?: fallbackSeconds
    return raw.coerceIn(MiTimezone.MIN_OFFSET_SECONDS, MiTimezone.MAX_OFFSET_SECONDS)
}

/**
 * Convenience when only the raw Mi int is available.
 */
fun resolveOffsetSecondsFromMiUnits(
    tzIn15Min: Int?,
    fallbackSeconds: Int = 0,
): Int = resolveOffsetSeconds(MiTimezone.fromPayloadOrNull(tzIn15Min), fallbackSeconds)
