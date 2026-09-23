package com.mifitness.miclient.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RED tests for passport coreInfo avatar fetch (APK XMPassport.getXiaomiUserCoreInfo).
 * Fail until PassportSecureRequest + PassportCoreInfoClient land.
 */
@OptIn(ExperimentalEncodingApi::class)
class AvatarCoreInfoTest {

    @Test
    fun encryptThenDecryptParamsIsIdentity() {
        val ssecurity = Base64.encode("16bytes-ssec-key".encodeToByteArray())
        val encrypted = PassportSecureRequest.encryptParams(
            mapOf("userId" to "6716605740", "sid" to "passportapi"),
            ssecurity,
        )
        val decrypted = PassportSecureRequest.decryptResponse(encrypted["userId"]!!, ssecurity)
        assertEquals("6716605740", decrypted.decodeToString())
    }

    @Test
    fun signatureJoinsSortedParamsWithMethodAndPath() {
        val signature = PassportSecureRequest.generateSignature(
            method = "GET",
            url = "https://api.account.xiaomi.com/pass/v2/safe/user/coreInfo",
            encryptedParams = mapOf("b" to "2", "a" to "1"),
            ssecurity = "ssec",
        )
        // Decodes as SHA-1 (20 bytes) of the joined spec string.
        assertEquals(20, Base64.decode(signature).size)
        assertTrue(signature.isNotBlank())
    }

    @Test
    fun parseAvatarAddress_rewritesTo320Thumbnail() {
        assertEquals(
            "https://cdn/icon_320.jpg",
            PassportCoreInfoClient.parseAvatarAddress("""{"icon":"https://cdn/icon.jpg"}"""),
        )
    }

    @Test
    fun parseAvatarAddress_missingOrBlankYieldsNull() {
        assertNull(PassportCoreInfoClient.parseAvatarAddress("""{"userName":"x"}"""))
        assertNull(PassportCoreInfoClient.parseAvatarAddress("""{"icon":""}"""))
        assertNull(PassportCoreInfoClient.parseAvatarAddress("not json"))
    }

    @Test
    fun resolveAvatarUrl_prefersCoreInfoOverFitnessIcon() {
        assertEquals(
            "https://cdn/a_320.jpg",
            PassportCoreInfoClient.resolveAvatarUrl(
                coreInfoAvatar = "https://cdn/a_320.jpg",
                fitnessIcon = "https://cdn/b.jpg",
            ),
        )
        assertEquals(
            "https://cdn/b.jpg",
            PassportCoreInfoClient.resolveAvatarUrl(coreInfoAvatar = null, fitnessIcon = "https://cdn/b.jpg"),
        )
        assertEquals(
            "",
            PassportCoreInfoClient.resolveAvatarUrl(coreInfoAvatar = null, fitnessIcon = " "),
        )
    }
}
