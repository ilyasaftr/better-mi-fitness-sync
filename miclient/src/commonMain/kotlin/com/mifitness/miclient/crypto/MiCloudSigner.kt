package com.mifitness.miclient.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Signs and encrypts HTTP requests for Mi Cloud / Mi Fitness data endpoints.
 *
 * Protocol (reverse-engineered from the Mi Fitness APK and python-miio):
 * ```
 * nonce         = 8 random bytes + bigEndian(unix_sec / 60)  →  base64
 * signedNonce   = base64( SHA-256( decode(ssecurity) + decode(nonce) ) )
 * rc4Key        = decode(signedNonce)
 *
 * rc4_hash__    = base64( rc4( SHA-1("POST&path&data=plain&signedNonce") ) )
 * data          = base64( rc4( plaintext ) )
 * signature     = base64( SHA-1("POST&path&data=data&rc4_hash__=hash&signedNonce") )
 * ```
 *
 * All RC4 operations use the Xiaomi variant: 1024-byte keystream skip.
 */
@OptIn(ExperimentalEncodingApi::class)
object MiCloudSigner {

    /**
     * Builds the four encrypted form fields for a POST request.
     *
     * @param path      the URL path used for signing (e.g. "/user/get_miot_user_profile")
     * @param ssecurity the base64 ssecurity from Mi Account login
     * @param plaintext the JSON payload to encrypt
     * @return [EncryptedRequest] containing the form fields and the signedNonce
     *         needed to decrypt the response.
     */
    fun buildEncryptedRequest(
        path: String,
        ssecurity: String,
        plaintext: String,
    ): EncryptedRequest {
        val nonce = generateNonce()
        val signedNonce = computeSignedNonce(ssecurity, nonce)

        val preHashInput = "POST&$path&data=$plaintext&$signedNonce"
        val rc4HashPlain = base64Encode(Hash.sha1(preHashInput.encodeToByteArray()))
        val rc4HashEncrypted = base64Encode(rc4Encrypt(signedNonce, rc4HashPlain.encodeToByteArray()))

        val encryptedData = base64Encode(rc4Encrypt(signedNonce, plaintext.encodeToByteArray()))

        val signatureInput = "POST&$path&data=$encryptedData&rc4_hash__=$rc4HashEncrypted&$signedNonce"
        val signature = base64Encode(Hash.sha1(signatureInput.encodeToByteArray()))

        return EncryptedRequest(
            nonce = nonce,
            data = encryptedData,
            rc4Hash = rc4HashEncrypted,
            signature = signature,
            signedNonce = signedNonce,
        )
    }

    /** Decrypts a base64-encoded response body using the signedNonce from the request. */
    fun decryptResponse(signedNonce: String, responseBase64: String): ByteArray {
        val ciphertext = base64Decode(responseBase64)
        return rc4Encrypt(signedNonce, ciphertext)
    }

    /** Computes UPPER(hex(MD5(password))) as Mi's login requires. */
    fun hashPassword(password: String): String {
        return Hash.md5(password.encodeToByteArray())
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            .uppercase()
    }

    // --- Internal helpers (kept small and single-purpose) ---

    internal fun generateNonce(): String {
        val buffer = ByteArray(12)
        Random.nextBytes(buffer, 0, 8)
        val minutesSinceEpoch = (currentUnixSeconds() / 60).toInt()
        buffer[8] = (minutesSinceEpoch shr 24).toByte()
        buffer[9] = (minutesSinceEpoch shr 16).toByte()
        buffer[10] = (minutesSinceEpoch shr 8).toByte()
        buffer[11] = minutesSinceEpoch.toByte()
        return base64Encode(buffer)
    }

    internal fun computeSignedNonce(ssecurityBase64: String, nonceBase64: String): String {
        val ssecurity = base64Decode(ssecurityBase64)
        val nonce = base64Decode(nonceBase64)
        return base64Encode(Hash.sha256(ssecurity + nonce))
    }

    private fun rc4Encrypt(signedNonceBase64: String, plaintext: ByteArray): ByteArray {
        val key = base64Decode(signedNonceBase64)
        return SkippedRC4(key).process(plaintext)
    }

    private fun base64Encode(bytes: ByteArray): String = Base64.encode(bytes)
    private fun base64Decode(encoded: String): ByteArray = Base64.decode(encoded)
}

/** The four form fields + the signedNonce needed to decrypt the response. */
data class EncryptedRequest(
    val nonce: String,
    val data: String,
    val rc4Hash: String,
    val signature: String,
    /** Retain this to pass to [MiCloudSigner.decryptResponse]. */
    val signedNonce: String,
)

// Platform-agnostic current time in seconds. Kotlin stdlib Clock isn't available
// in all KMP targets without kotlinx-datetime, so we use expect/actual if needed
// later. For now, epochSeconds via kotlin.time.
internal expect fun currentUnixSeconds(): Long
