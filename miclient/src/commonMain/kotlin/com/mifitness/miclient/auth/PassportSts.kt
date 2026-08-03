package com.mifitness.miclient.auth

import com.mifitness.miclient.crypto.Hash
import io.ktor.http.encodeURLParameter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * STS helpers matching Xiaomi passport SDK ([XMPassport.getClientSign] / getServiceTokenByStsUrl).
 */
@OptIn(ExperimentalEncodingApi::class)
object PassportSts {
    /**
     * APK: `CloudCoder.generateSignature(null, null, {nonce=…}, ssecurity)`
     * → Base64(SHA1("nonce=<nonce>&" + ssecurity)) with Android Base64.NO_WRAP.
     */
    fun clientSign(nonce: String, ssecurity: String): String {
        require(nonce.isNotEmpty()) { "nonce required for clientSign" }
        require(ssecurity.isNotEmpty()) { "ssecurity required for clientSign" }
        val payload = "nonce=$nonce&$ssecurity"
        return Base64.encode(Hash.sha1(payload.encodeToByteArray()))
    }

    /**
     * Appends `clientSign` and `_userIdNeedEncrypt=true` to the STS location URL.
     */
    fun signedLocationUrl(location: String, nonce: String, ssecurity: String): String {
        val abs = PassportAuthUtils.absUrl(location)
        val sign = clientSign(nonce, ssecurity).encodeURLParameter()
        val sep = if (abs.contains('?')) '&' else '?'
        return "$abs${sep}clientSign=$sign&_userIdNeedEncrypt=true"
    }

    /** MD5 hex of [passToken] uppercase — used with `re-pass-token` response header. */
    fun passTokenMd5Upper(passToken: String): String =
        Hash.md5(passToken.encodeToByteArray())
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
            .uppercase()

    /**
     * Whether the server is rotating passToken (APK [AMPassTokenUpdateUtil] intent).
     * Prefer body [newPassToken] when non-blank and different from old.
     * If [rePassTokenHeader] is present, require it equals MD5(old).
     */
    fun preferRotatedPassToken(
        oldPassToken: String,
        newPassToken: String?,
        rePassTokenHeader: String?,
    ): String {
        val candidate = newPassToken?.takeIf { it.isNotBlank() } ?: return oldPassToken
        if (candidate == oldPassToken) return oldPassToken
        val re = rePassTokenHeader?.trim().orEmpty()
        if (re.isNotEmpty() && re.uppercase() != passTokenMd5Upper(oldPassToken)) {
            return oldPassToken
        }
        return candidate
    }

    fun extractServiceTokenFromCookieHeader(cookie: String, sid: String = PassportAuthUtils.DEFAULT_SID): String? {
        val prefixed = "${sid}_serviceToken="
        return when {
            cookie.contains(prefixed) ->
                cookie.substringAfter(prefixed).substringBefore(";").takeIf { it.isNotBlank() }
            cookie.contains("serviceToken=") ->
                cookie.substringAfter("serviceToken=").substringBefore(";").takeIf { it.isNotBlank() }
            else -> null
        }
    }
}
