package com.mifitness.miclient.auth

import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Pure helpers for Xiaomi passport auth (no HTTP).
 * Shared by [MiAuth] login, OTP, and STS paths.
 */
object PassportAuthUtils {
    val json: Json = Json { ignoreUnknownKeys = true }

    const val DEFAULT_SID = "miothealth"
    const val DEFAULT_STS_CALLBACK = "https://sts-hlth.io.mi.com/healthapp/sts"
    const val DEFAULT_USER_AGENT = "APP/com.xiaomi.miwatch.pro APPV/3.49.1 " +
        "iosPassportSDK/4.2.64 iOS/18.7.8 MK/aVBob25lMTQsMw== " +
        "DEVT/aVBob25l DEVS/aU9T BRA/QXBwbGU= L/en_US miHSTS"

    fun stripJsonPrefix(body: String): String = body.removePrefix("&&&START&&&").trim()

    fun absUrl(url: String): String {
        if (url.isEmpty()) return ""
        return if (url.startsWith("http")) url else "https://account.xiaomi.com$url"
    }

    fun resolveRegion(countryCode: String): String = when (countryCode.uppercase()) {
        "CN" -> "cn"
        "IN" -> "i2"
        "RU" -> "ru"
        "US", "BR", "CA", "MX" -> "us"
        "DE", "GB", "FR", "IT", "ES", "NL", "PL", "SE", "NO", "DK", "FI", "AT", "CH", "BE" -> "de"
        else -> "sg"
    }

    fun inferMaskedEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2 || parts[0].length < 3) return email
        return "${parts[0].take(3)}***@${parts[1]}"
    }

    fun friendlyLoginError(code: Int, desc: String): String = when (code) {
        70016 -> "Incorrect email or password, or too many attempts. Wait a few minutes and try again."
        70022 -> "Login rate-limited by Xiaomi. Wait a few minutes, or use browser login."
        81003 -> "Captcha required — use the browser login flow instead."
        87001 -> "Two-factor authentication required — use the browser login flow."
        else -> "Login failed (code $code): $desc"
    }

    fun friendlySendOtpError(code: Int, desc: String): String = when (code) {
        70022 -> "Email OTP is rate-limited by Xiaomi. Wait a while or use browser login."
        else -> "Could not send verification email (code $code): $desc"
    }

    fun extractCookieValue(setCookieHeaders: List<String>?, name: String): String? {
        return setCookieHeaders
            ?.firstOrNull { it.startsWith("$name=") || it.contains("$name=") }
            ?.substringAfter("$name=")
            ?.substringBefore(";")
    }

    fun parseJsonField(jsonString: String, field: String): String {
        return try {
            json.parseToJsonElement(jsonString).jsonObject[field]?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun jsonUserId(obj: JsonObject): String {
        val el = obj["userId"] ?: return ""
        return try {
            el.jsonPrimitive.long.toString()
        } catch (_: Exception) {
            el.jsonPrimitive.content
        }
    }

    /** Matches Mi browser `d=` style ids: wb_ + 32 hex chars. */
    fun generateDeviceId(): String {
        val hex = buildString(32) {
            repeat(32) {
                append("0123456789abcdef"[Random.nextInt(16)])
            }
        }
        return "wb_$hex"
    }

    fun parseContext(url: String): String {
        val regex = Regex("[?&]context=([^&]+)")
        return regex.find(url)?.groupValues?.get(1) ?: ""
    }
}
