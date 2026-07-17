package com.bettermifitness.sync.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    @Test
    fun format_nullIsNever() {
        assertEquals("Never", RelativeTime.format(null))
    }

    @Test
    fun format_emptyIsNever() {
        assertEquals("Never", RelativeTime.format(""))
    }

    @Test
    fun format_invalidIsUnknown() {
        assertEquals("Unknown", RelativeTime.format("not-an-instant"))
    }
}
