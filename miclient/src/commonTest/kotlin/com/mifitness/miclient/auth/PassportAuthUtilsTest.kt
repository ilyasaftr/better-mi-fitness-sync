package com.mifitness.miclient.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PassportAuthUtilsTest {

    @Test
    fun stripJsonPrefix_removesMiPrefix() {
        assertEquals(
            """{"code":0}""",
            PassportAuthUtils.stripJsonPrefix("""&&&START&&&{"code":0}"""),
        )
    }

    @Test
    fun absUrl_prefixesRelativePaths() {
        assertEquals(
            "https://account.xiaomi.com/identity/foo",
            PassportAuthUtils.absUrl("/identity/foo"),
        )
        assertEquals(
            "https://example.com/x",
            PassportAuthUtils.absUrl("https://example.com/x"),
        )
    }

    @Test
    fun resolveRegion_mapsKnownCountries() {
        assertEquals("cn", PassportAuthUtils.resolveRegion("CN"))
        assertEquals("us", PassportAuthUtils.resolveRegion("US"))
        assertEquals("de", PassportAuthUtils.resolveRegion("DE"))
        assertEquals("sg", PassportAuthUtils.resolveRegion("ID"))
    }

    @Test
    fun inferMaskedEmail_masksLocalPart() {
        assertEquals("tes***@example.com", PassportAuthUtils.inferMaskedEmail("test@example.com"))
        assertEquals("ab@x.com", PassportAuthUtils.inferMaskedEmail("ab@x.com"))
    }

    @Test
    fun friendlyLoginError_mapsKnownCodes() {
        assertTrue(PassportAuthUtils.friendlyLoginError(70016, "x").contains("password", ignoreCase = true))
        assertTrue(PassportAuthUtils.friendlyLoginError(70022, "x").contains("rate", ignoreCase = true))
    }

    @Test
    fun generateDeviceId_matchesWbHexPattern() {
        val id = PassportAuthUtils.generateDeviceId()
        assertTrue(id.startsWith("wb_"))
        assertEquals(35, id.length) // wb_ + 32 hex
        assertTrue(id.drop(3).all { it in "0123456789abcdef" })
    }

    @Test
    fun parseContext_extractsQueryParam() {
        assertEquals(
            "abc",
            PassportAuthUtils.parseContext("https://account.xiaomi.com/x?context=abc&y=1"),
        )
    }
}
