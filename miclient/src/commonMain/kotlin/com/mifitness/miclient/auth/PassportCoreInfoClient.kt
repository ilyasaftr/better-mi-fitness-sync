package com.mifitness.miclient.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Passport safe-API coreInfo fetch for the Mi account avatar
 * (APK `XMPassport.getXiaomiUserCoreInfo`, sid `passportapi`).
 *
 * Single responsibility: mint a passportapi token, issue the signed GET,
 * decrypt, and parse the avatar address. Never persists tokens.
 */
class PassportCoreInfoClient(
    private val miAuth: MiAuth,
    private val userAgent: String = PassportAuthUtils.DEFAULT_USER_AGENT,
) {
    suspend fun avatarAddress(credentials: MiCredentials): String? {
        val passport = try {
            miAuth.refreshWithPassToken(
                credentials,
                sid = PASSPORT_SID,
                callback = PASSPORT_STS_CALLBACK,
            )
        } catch (_: Exception) {
            return null
        }
        return try {
            val params = PassportSecureRequest.encryptParams(
                mapOf(
                    "userId" to passport.userId,
                    "sid" to PASSPORT_SID,
                    "transId" to transId(),
                    "flags" to CORE_INFO_FLAGS,
                ),
                passport.ssecurity,
            )
            val query = params.entries.joinToString("&") { (name, value) ->
                "${name.encodeURLParameter()}=${value.encodeURLParameter()}"
            }
            val storage = AcceptAllCookiesStorage()
            val client = PassportHttpSession.buildClient(storage)
            try {
                PassportHttpSession.seedDeviceIdCookie(storage, passport.deviceId)
                storage.addCookie(
                    Url("https://api.account.xiaomi.com/"),
                    Cookie(
                        name = "serviceToken",
                        value = passport.serviceToken,
                        domain = ".xiaomi.com",
                        path = "/",
                    ),
                )
                storage.addCookie(
                    Url("https://api.account.xiaomi.com/"),
                    Cookie(
                        name = "userId",
                        value = passport.userId,
                        domain = ".xiaomi.com",
                        path = "/",
                    ),
                )
                val response: String = client.get("$CORE_INFO_URL?$query") {
                    header("User-Agent", userAgent)
                }.bodyAsText()
                val decrypted = PassportSecureRequest.decryptResponse(
                    PassportAuthUtils.stripJsonPrefix(response),
                    passport.ssecurity,
                ).decodeToString()
                parseAvatarAddress(decrypted)
            } finally {
                client.close()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PASSPORT_SID = "passportapi"
        const val PASSPORT_STS_CALLBACK = "https://api.account.xiaomi.com/sts?sid=passportapi"
        const val CORE_INFO_FLAGS = "9"
        const val CORE_INFO_URL =
            "https://api.account.xiaomi.com/pass/v2/safe/user/coreInfo"

        /** Rewrites `xxx.jpg` to the `_320` thumbnail (APK `ICON_SIZE_SUFFIX_320`). */
        fun parseAvatarAddress(dataJson: String): String? {
            val icon = try {
                val obj = PassportAuthUtils.json.parseToJsonElement(dataJson).jsonObject
                val data = obj["data"]?.jsonObject ?: obj
                data["icon"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                return null
            }?.takeIf { it.isNotBlank() } ?: return null
            val dot = icon.lastIndexOf('.')
            if (dot <= 0) return icon
            return icon.substring(0, dot) + "_320" + icon.substring(dot)
        }

        /** coreInfo avatar primary, fitness-profile icon secondary, else empty. */
        fun resolveAvatarUrl(coreInfoAvatar: String?, fitnessIcon: String?): String {
            if (!coreInfoAvatar.isNullOrBlank()) return coreInfoAvatar
            if (!fitnessIcon.isNullOrBlank()) return fitnessIcon
            return ""
        }

        private fun transId(): String {
            val hex = "0123456789abcdef"
            return buildString(15) { repeat(15) { append(hex[Random.nextInt(16)]) } }
        }
    }
}
