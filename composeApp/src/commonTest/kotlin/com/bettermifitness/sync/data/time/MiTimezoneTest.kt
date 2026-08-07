package com.bettermifitness.sync.data.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MiTimezoneTest {

    @Test
    fun units28_isUtcPlus7() {
        val tz = MiTimezone.fromPayloadOrNull(28)
        assertNotNull(tz)
        assertEquals(28 * 15 * 60, tz.offsetSeconds())
        assertEquals(7 * 3600, tz.offsetSeconds())
    }

    @Test
    fun units32_isUtcPlus8() {
        val tz = MiTimezone.fromPayloadOrNull(32)
        assertNotNull(tz)
        assertEquals(8 * 3600, tz.offsetSeconds())
    }

    @Test
    fun units0_isUtc() {
        val tz = MiTimezone.fromPayloadOrNull(0)
        assertNotNull(tz)
        assertEquals(0, tz.offsetSeconds())
    }

    @Test
    fun negativeUnits_withinRange() {
        // UTC-5 → -20 units of 15 min
        val tz = MiTimezone.fromPayloadOrNull(-20)
        assertNotNull(tz)
        assertEquals(-5 * 3600, tz.offsetSeconds())
    }

    @Test
    fun nullPayload_returnsNull() {
        assertNull(MiTimezone.fromPayloadOrNull(null))
    }

    @Test
    fun outOfRange_returnsNull() {
        // ±19 hours in 15-min units
        assertNull(MiTimezone.fromPayloadOrNull(19 * 4 + 1)) // 77
        assertNull(MiTimezone.fromPayloadOrNull(-(19 * 4 + 1)))
    }

    @Test
    fun resolve_prefersMiOverFallback() {
        val mi = MiTimezone.fromPayloadOrNull(32)
        assertEquals(8 * 3600, resolveOffsetSeconds(mi, fallbackSeconds = 3600))
    }

    @Test
    fun resolve_usesFallbackWhenMiMissing() {
        assertEquals(3600, resolveOffsetSeconds(null, fallbackSeconds = 3600))
        assertEquals(3600, resolveOffsetSecondsFromMiUnits(null, fallbackSeconds = 3600))
    }

    @Test
    fun resolve_clampsExtremeFallback() {
        assertEquals(
            MiTimezone.MAX_OFFSET_SECONDS,
            resolveOffsetSeconds(null, fallbackSeconds = 100_000),
        )
        assertEquals(
            MiTimezone.MIN_OFFSET_SECONDS,
            resolveOffsetSeconds(null, fallbackSeconds = -100_000),
        )
    }
}
