package com.mifitness.miclient.auth

import com.mifitness.miclient.crypto.Hash
import com.mifitness.miclient.fds.Aes128Ecb
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * AES param encryption + signature for passport safe-API calls
 * (APK `SecureRequest.encryptParams` + `Coder.generateSignature`).
 *
 * Params whose key starts with `_` ride plaintext; the rest are AES-encrypted
 * (`AES/CBC/PKCS5Padding`, key = base64(ssecurity), IV = `0102030405060708`).
 * `Aes128Ecb` is reused as the CBC block primitive. Signature is
 * base64(SHA-1(`METHOD&encodedPath&sorted-k=v&ssecurity`)).
 */
@OptIn(ExperimentalEncodingApi::class)
object PassportSecureRequest {
    val defaultIv: ByteArray get() = "0102030405060708".encodeToByteArray()

    fun encryptParams(params: Map<String, String>, ssecurity: String): Map<String, String> {
        val key = Base64.decode(ssecurity)
        val out = LinkedHashMap<String, String>(params.size + 1)
        for ((name, value) in params) {
            out[name] = if (name.startsWith("_")) value else encrypt(value, key)
        }
        out["signature"] = generateSignature("GET", CORE_INFO_URL, out, ssecurity)
        return out
    }

    fun decryptResponse(ciphertextBase64: String, ssecurity: String): ByteArray {
        val key = Base64.decode(ssecurity)
        return cbcDecrypt(key, Base64.decode(ciphertextBase64))
    }

    fun generateSignature(
        method: String,
        url: String,
        encryptedParams: Map<String, String>,
        ssecurity: String,
    ): String {
        val path = url.substringAfter("://").substringAfter("/", "")
            .let { if (url.contains("//") && "/" in url.substringAfter("://")) "/$it" else "" }
        val parts = ArrayList<String>(encryptedParams.size + 3)
        parts += method.uppercase()
        parts += path.substringBefore("?")
        for ((name, value) in encryptedParams.toSortedMap()) {
            parts += "$name=$value"
        }
        parts += ssecurity
        return Base64.encode(Hash.sha1(parts.joinToString("&").encodeToByteArray()))
    }

    internal fun encrypt(plaintext: String, key: ByteArray): String =
        Base64.encode(cbcEncrypt(key, plaintext.encodeToByteArray()))

    private fun cbcEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 16)
        val padded = pkcs7Pad(plaintext)
        val aes = Aes128Ecb(key)
        val out = ByteArray(padded.size)
        var prev = defaultIv.copyOf()
        var offset = 0
        while (offset < padded.size) {
            val block = ByteArray(16)
            for (i in 0 until 16) {
                block[i] = (padded[offset + i].toInt() xor prev[i].toInt()).toByte()
            }
            val enc = aes.encryptBlock(block)
            enc.copyInto(out, offset)
            prev = enc
            offset += 16
        }
        return out
    }

    private fun cbcDecrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(key.size == 16)
        require(ciphertext.isNotEmpty() && ciphertext.size % 16 == 0)
        val aes = Aes128Ecb(key)
        val plain = ByteArray(ciphertext.size)
        var prev = defaultIv.copyOf()
        var offset = 0
        while (offset < ciphertext.size) {
            val block = ciphertext.copyOfRange(offset, offset + 16)
            val dec = aes.decryptBlock(block)
            for (i in 0 until 16) {
                plain[offset + i] = (dec[i].toInt() xor prev[i].toInt()).toByte()
            }
            prev = block
            offset += 16
        }
        return pkcs7Unpad(plain)
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        return data + ByteArray(pad) { pad.toByte() }
    }

    private fun pkcs7Unpad(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val pad = data.last().toInt() and 0xFF
        require(pad in 1..16)
        return data.copyOf(data.size - pad)
    }

    internal const val CORE_INFO_URL =
        "https://api.account.xiaomi.com/pass/v2/safe/user/coreInfo"
}
