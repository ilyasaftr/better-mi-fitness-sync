package com.bettermifitness.sync.ui.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginViewModelHelpersTest {

    @Test
    fun extractDeviceId_findsDParam() {
        assertEquals(
            "deviceXYZ",
            LoginViewModel.extractDeviceId(
                "https://sts-hlth.io.mi.com/healthapp/sts?foo=1&d=deviceXYZ&p_ur=ID",
            ),
        )
    }

    @Test
    fun extractDeviceId_trimsAndFindsDeviceIdAlias() {
        assertEquals(
            "wb_abc",
            LoginViewModel.extractDeviceId(
                "  https://sts-hlth.io.mi.com/healthapp/sts?deviceId=wb_abc&p_ur=SG  \n",
            ),
        )
    }

    @Test
    fun extractDeviceId_missingReturnsEmpty() {
        assertEquals("", LoginViewModel.extractDeviceId("https://example.com/?x=1"))
    }

    @Test
    fun shouldFallbackToBrowser_detectsKnownHints() {
        assertTrue(
            LoginViewModel.shouldFallbackToBrowser(
                "OTP_ACCEPTED_NEEDS_BROWSER: use browser",
            ),
        )
        assertTrue(LoginViewModel.shouldFallbackToBrowser("still require OTP"))
        assertFalse(LoginViewModel.shouldFallbackToBrowser("Invalid code"))
    }
}
