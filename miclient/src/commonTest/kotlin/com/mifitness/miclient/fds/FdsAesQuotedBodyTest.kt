package com.mifitness.miclient.fds

import kotlin.test.Test
import kotlin.test.assertContentEquals

class FdsAesQuotedBodyTest {

    private val key = ByteArray(16) { (it * 7 + 3).toByte() }
    private val objectKey = FdsKeys.encodeUrlSafeNoPad(key)
    private val plain = "strength record bytes".encodeToByteArray()
    private val body = FdsKeys.encodeUrlSafeNoPad(FdsAes.encrypt(key, plain))

    @Test
    fun plainBodyDecrypts() {
        assertContentEquals(plain, FdsAes.decryptBase64Ciphertext(objectKey, body))
    }

    @Test
    fun bodySentAsJsonStringDecrypts() {
        // Indoor workout records (strength training) come back wrapped in quotes
        assertContentEquals(plain, FdsAes.decryptBase64Ciphertext(objectKey, "\"$body\"\n"))
    }
}
