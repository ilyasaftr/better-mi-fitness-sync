package com.bettermifitness.sync.health

/**
 * Stable Health Connect [clientRecordId] / HealthKit [HKMetadataKeySyncIdentifier] values.
 *
 * Same logical Mi sample → same id on every sync so platforms **upsert** instead of duplicating.
 * Prefix `mifit-` is kept for backward compatibility with records already written.
 *
 * [version] must be a positive Long. Prefer content-derived versions so identical re-syncs
 * are no-ops; when source values change, a different version triggers an update (HC requires
 * greater-or-equal; HK prefers higher version — we always use a positive content fingerprint).
 */
object HealthRecordIds {

    /** 5-minute windows for batched HR series on Android. */
    const val HEART_RATE_WINDOW_SEC = 5 * 60L

    fun heartRateWindow(windowStartEpochSec: Long): String =
        "mifit-hr-$windowStartEpochSec"

    fun heartRateSample(timestampSec: Long): String =
        "mifit-hr-$timestampSec"

    fun restingHeartRate(timestampSec: Long): String =
        "mifit-rhr-$timestampSec"

    fun sleepSession(startSec: Long): String =
        "mifit-sleep-$startSec"

    fun sleepInBed(inBedStartSec: Long): String =
        "mifit-sleep-inbed-$inBedStartSec"

    fun sleepStage(startSec: Long): String =
        "mifit-sleep-stage-$startSec"

    fun steps(hourStartSec: Long): String =
        "mifit-steps-$hourStartSec"

    fun distance(startSec: Long): String =
        "mifit-dist-$startSec"

    fun activeCalories(startSec: Long): String =
        "mifit-kcal-$startSec"

    fun spo2(timestampSec: Long): String =
        "mifit-spo2-$timestampSec"

    fun weight(timestampSec: Long): String =
        "mifit-weight-$timestampSec"

    fun bodyFat(timestampSec: Long): String =
        "mifit-bodyfat-$timestampSec"

    fun workout(startSec: Long): String =
        "mifit-workout-$startSec"

    fun bloodPressure(timestampSec: Long): String =
        "mifit-bp-$timestampSec"

    fun bodyTemperature(timestampSec: Long): String =
        "mifit-bodytemp-$timestampSec"

    fun skinTemperature(timestampSec: Long): String =
        "mifit-skintemp-$timestampSec"

    fun vo2Max(timestampSec: Long): String =
        "mifit-vo2max-$timestampSec"

    /** Floor epoch seconds to the HR batch window start. */
    fun heartRateWindowStart(timestampSec: Long): Long =
        (timestampSec / HEART_RATE_WINDOW_SEC) * HEART_RATE_WINDOW_SEC

    /**
     * Stable positive content version for upsert metadata.
     * Identical inputs → identical version (idempotent re-sync).
     */
    fun version(vararg parts: Any?): Long {
        var h = 0xcbf29ce484222325uL // FNV-1a 64 offset
        val prime = 0x100000001b3uL
        for (part in parts) {
            val s = part?.toString() ?: "null"
            for (ch in s) {
                h = h xor ch.code.toULong()
                h *= prime
            }
            h = h xor 0x9euL
            h *= prime
        }
        // Health platforms want signed positive longs.
        val signed = (h and 0x7FFFFFFFFFFFFFFFuL).toLong()
        return if (signed == 0L) 1L else signed
    }

    /**
     * Version that grows with a natural counter (e.g. step count) so mid-day updates
     * usually win over earlier smaller values. Falls back to [version] mix-in.
     */
    fun counterVersion(counter: Long, vararg salt: Any?): Long {
        val base = counter.coerceAtLeast(0L)
        val mix = version(*salt) % 1_000_000L
        // Keep in positive range; prioritize counter in high bits-ish without overflow.
        val combined = base * 1_000_000L + mix
        return if (combined > 0) combined else version(base, *salt)
    }
}
