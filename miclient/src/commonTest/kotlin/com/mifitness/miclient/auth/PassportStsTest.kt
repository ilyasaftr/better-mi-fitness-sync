package com.mifitness.miclient.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PassportStsTest {

    @Test
    fun clientSign_matchesApkSha1Base64NoWrap() {
        // APK: Base64(SHA1("nonce=<n>&" + ssecurity), NO_WRAP)
        val expected = "4V5zHfsiqQGpDuc8sofWmSUlPIo="
        assertEquals(expected, PassportSts.clientSign("12345", "test-ssecurity-value"))
    }

    @Test
    fun signedLocationUrl_appendsQueryWhenNoExistingParams() {
        val url = PassportSts.signedLocationUrl(
            location = "https://sts-hlth.io.mi.com/healthapp/sts",
            nonce = "12345",
            ssecurity = "test-ssecurity-value",
        )
        assertTrue(url.startsWith("https://sts-hlth.io.mi.com/healthapp/sts?"))
        assertTrue(url.contains("clientSign="))
        assertTrue(url.contains("_userIdNeedEncrypt=true"))
        assertTrue(url.contains("clientSign=4V5zHfsiqQGpDuc8sofWmSUlPIo%3D") || url.contains("clientSign=4V5zHfsiqQGpDuc8sofWmSUlPIo="))
    }

    @Test
    fun signedLocationUrl_appendsWithAmpersandWhenQueryExists() {
        val url = PassportSts.signedLocationUrl(
            location = "https://sts-hlth.io.mi.com/healthapp/sts?foo=1",
            nonce = "12345",
            ssecurity = "test-ssecurity-value",
        )
        assertTrue(url.contains("foo=1&clientSign="))
        assertFalse(url.contains("?clientSign="))
    }

    @Test
    fun passTokenMd5Upper_matchesApk() {
        assertEquals(
            "B6B00ADDF1314F2A49E8821A179584BA",
            PassportSts.passTokenMd5Upper("oldPassToken"),
        )
    }

    @Test
    fun preferRotatedPassToken_acceptsNewWhenNoHeader() {
        assertEquals(
            "newToken",
            PassportSts.preferRotatedPassToken("old", "newToken", rePassTokenHeader = null),
        )
    }

    @Test
    fun preferRotatedPassToken_acceptsWhenReHeaderMatchesMd5Old() {
        val old = "oldPassToken"
        val md5 = PassportSts.passTokenMd5Upper(old)
        assertEquals(
            "rotated",
            PassportSts.preferRotatedPassToken(old, "rotated", rePassTokenHeader = md5),
        )
    }

    @Test
    fun preferRotatedPassToken_rejectsWhenReHeaderMismatches() {
        assertEquals(
            "old",
            PassportSts.preferRotatedPassToken("old", "rotated", rePassTokenHeader = "DEADBEEF"),
        )
    }

    @Test
    fun extractServiceTokenFromCookieHeader_plainAndSidPrefixed() {
        assertEquals(
            "abc",
            PassportSts.extractServiceTokenFromCookieHeader("serviceToken=abc; Path=/"),
        )
        assertEquals(
            "xyz",
            PassportSts.extractServiceTokenFromCookieHeader(
                "miothealth_serviceToken=xyz; Domain=.mi.com",
            ),
        )
    }
}
