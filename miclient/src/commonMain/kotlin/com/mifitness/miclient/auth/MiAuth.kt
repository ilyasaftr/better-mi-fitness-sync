package com.mifitness.miclient.auth

import com.mifitness.miclient.crypto.MiCloudSigner
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Mi Account authentication façade (password, OTP handoff, STS browser callback).
 *
 * Internals are split across [PassportAuthUtils], [PassportHttpSession], and [LoginResult.OtpRequired].
 */
class MiAuth(
    private val userAgent: String = PassportAuthUtils.DEFAULT_USER_AGENT,
) : MiAuthHost {
    private val json = PassportAuthUtils.json

    suspend fun login(
        email: String,
        password: String,
        deviceId: String = "",
        sid: String = PassportAuthUtils.DEFAULT_SID,
        callback: String = PassportAuthUtils.DEFAULT_STS_CALLBACK,
    ): LoginResult {
        val effectiveDeviceId = deviceId.ifEmpty { PassportAuthUtils.generateDeviceId() }
        val cookieStorage = AcceptAllCookiesStorage()
        val client = PassportHttpSession.buildClient(cookieStorage)
        PassportHttpSession.seedDeviceIdCookie(cookieStorage, effectiveDeviceId)

        return try {
            passwordLoginStep(
                client = client,
                cookieStorage = cookieStorage,
                email = email,
                password = password,
                deviceId = effectiveDeviceId,
                sid = sid,
                callback = callback,
                closeClientOnSuccess = true,
            )
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }

    /**
     * Completes browser login from the final STS redirect URL (paste from Safari/Chrome).
     * Follows redirects and collects serviceToken — does **not** re-run password/OTP login
     * (which would ask for verification again).
     */
    suspend fun completeFromCallbackUrl(
        stsCallbackUrl: String,
        sid: String = PassportAuthUtils.DEFAULT_SID,
    ): MiCredentials {
        val cleaned = stsCallbackUrl.trim().lines().firstOrNull { it.isNotBlank() }?.trim()
            ?: throw MiAuthException("Empty redirect URL")
        val parsedUrl = Url(cleaned)
        val params = parseQueryString(parsedUrl.encodedQuery)
        val deviceId = params["d"]
            ?: params["deviceId"]
            ?: throw MiAuthException("Missing device id (d=…) in redirect URL — copy the full address bar URL")
        val regionFromUrl = params["p_ur"] ?: ""

        val cookieStorage = AcceptAllCookiesStorage()
        val client = PassportHttpSession.buildClient(cookieStorage)
        PassportHttpSession.seedDeviceIdCookie(cookieStorage, deviceId)

        try {
            // One-shot GET is not enough — STS often 302s while setting serviceToken.
            val (serviceToken, regionFromRedirects) = followRedirectsCollectingServiceToken(
                client,
                cleaned,
            )
            if (serviceToken.isEmpty()) {
                throw MiAuthException(
                    "Could not get a session from that redirect URL. " +
                        "Open the login page again, finish sign-in, then paste the new full URL " +
                        "(it should start with https://sts-hlth.io.mi.com/).",
                )
            }

            val accountCookies = cookieStorage.get(Url("https://account.xiaomi.com/"))
            val stsCookies = cookieStorage.get(Url("https://sts-hlth.io.mi.com/"))
            fun cookie(name: String): String =
                stsCookies.firstOrNull { it.name == name }?.value
                    ?: accountCookies.firstOrNull { it.name == name }?.value
                    ?: ""

            val userId = cookie("userId")
            val passToken = cookie("passToken")
            val ssecurity = if (userId.isNotEmpty() && passToken.isNotEmpty()) {
                harvestSsecurity(client, userId, passToken, deviceId, sid)
            } else {
                ""
            }
            if (ssecurity.isEmpty()) {
                throw MiAuthException(
                    "Got a service token but not full session details. " +
                        "Try browser login again and paste the URL as soon as the page says “ok”.",
                )
            }

            return MiCredentials(
                userId = userId,
                ssecurity = ssecurity,
                serviceToken = serviceToken,
                passToken = passToken,
                deviceId = deviceId,
                region = PassportAuthUtils.resolveRegion(
                    regionFromUrl.ifBlank { regionFromRedirects },
                ),
            )
        } finally {
            client.close()
        }
    }

    fun buildLoginUrl(
        sid: String = PassportAuthUtils.DEFAULT_SID,
        callback: String = PassportAuthUtils.DEFAULT_STS_CALLBACK,
    ): String {
        return "https://account.xiaomi.com/pass/serviceLogin?sid=$sid&callback=$callback&_locale=en"
    }

    /**
     * Re-mint serviceToken (and ssecurity when needed) using a stored [passToken].
     * Used when Mi health APIs reject an expired session without forcing a full password login.
     */
    suspend fun refreshWithPassToken(
        credentials: MiCredentials,
        sid: String = PassportAuthUtils.DEFAULT_SID,
        callback: String = PassportAuthUtils.DEFAULT_STS_CALLBACK,
    ): MiCredentials {
        if (credentials.passToken.isBlank()) {
            throw MiAuthException("No passToken saved — sign in again")
        }
        if (credentials.userId.isBlank()) {
            throw MiAuthException("No userId saved — sign in again")
        }

        val deviceId = credentials.deviceId.ifBlank { PassportAuthUtils.generateDeviceId() }
        val cookieStorage = AcceptAllCookiesStorage()
        val client = PassportHttpSession.buildClient(cookieStorage)
        PassportHttpSession.seedDeviceIdCookie(cookieStorage, deviceId)
        cookieStorage.addCookie(
            Url("https://account.xiaomi.com/"),
            Cookie(name = "userId", value = credentials.userId, domain = ".xiaomi.com", path = "/"),
        )
        cookieStorage.addCookie(
            Url("https://account.xiaomi.com/"),
            Cookie(name = "passToken", value = credentials.passToken, domain = ".xiaomi.com", path = "/"),
        )

        return try {
            val refreshed = tryLoginWithPassTokenCookies(
                client = client,
                cookieStorage = cookieStorage,
                deviceId = deviceId,
                sid = sid,
                callback = callback,
            ) ?: throw MiAuthException("Session refresh failed — sign in again")

            // STS may omit region; keep the previously stored health region when blank.
            val region = refreshed.region.takeIf { it.isNotBlank() }
                ?: credentials.region.takeIf { it.isNotBlank() }
                ?: "sg"
            refreshed.copy(
                deviceId = deviceId,
                region = region,
                passToken = refreshed.passToken.ifBlank { credentials.passToken },
            )
        } finally {
            client.close()
        }
    }

    private suspend fun passwordLoginStep(
        client: HttpClient,
        cookieStorage: AcceptAllCookiesStorage,
        email: String,
        password: String,
        deviceId: String,
        sid: String,
        callback: String,
        closeClientOnSuccess: Boolean,
    ): LoginResult {
        val sign = fetchSign(client, sid)
        val authResponse = postServiceLoginAuth2(client, email, password, sid, callback, sign)

        val code = authResponse["code"]?.jsonPrimitive?.int ?: -1
        if (code != 0) {
            val desc = authResponse["desc"]?.jsonPrimitive?.content
                ?: authResponse["description"]?.jsonPrimitive?.content
                ?: "Login failed"
            throw MiAuthException(PassportAuthUtils.friendlyLoginError(code, desc))
        }

        val securityStatus = authResponse["securityStatus"]?.jsonPrimitive?.int ?: 0
        val notification = authResponse["notificationUrl"]?.jsonPrimitive?.content ?: ""

        if (securityStatus != 0 || notification.isNotEmpty()) {
            return LoginResult.OtpRequired(
                host = this,
                client = client,
                cookieStorage = cookieStorage,
                email = email,
                password = password,
                sid = sid,
                callback = callback,
                notificationUrl = PassportAuthUtils.absUrl(notification),
                maskedTarget = PassportAuthUtils.inferMaskedEmail(email),
                deviceId = deviceId,
            )
        }

        val credentials = exchangeLocationForCredentials(client, authResponse, deviceId)
        if (closeClientOnSuccess) client.close()
        return LoginResult.Success(credentials)
    }

    override suspend fun finishLoginAfterOtp(
        client: HttpClient,
        cookieStorage: AcceptAllCookiesStorage,
        email: String,
        password: String,
        deviceId: String,
        sid: String,
        callback: String,
    ): MiCredentials {
        PassportHttpSession.seedDeviceIdCookie(cookieStorage, deviceId)

        val result = passwordLoginStep(
            client = client,
            cookieStorage = cookieStorage,
            email = email,
            password = password,
            deviceId = deviceId,
            sid = sid,
            callback = callback,
            closeClientOnSuccess = false,
        )
        when (result) {
            is LoginResult.Success -> {
                client.close()
                return result.credentials
            }
            is LoginResult.OtpRequired -> {
                // Same client reused; do not open a nested OTP challenge.
            }
        }

        val passTokenCreds = tryLoginWithPassTokenCookies(
            client, cookieStorage, deviceId, sid, callback,
        )
        if (passTokenCreds != null) {
            client.close()
            return passTokenCreds
        }

        client.close()
        throw MiAuthException(
            "OTP_ACCEPTED_NEEDS_BROWSER: Email code was accepted, but Xiaomi still " +
                "won't finish app login for this session. Use browser login once to trust this device.",
        )
    }

    private suspend fun tryLoginWithPassTokenCookies(
        client: HttpClient,
        cookieStorage: AcceptAllCookiesStorage,
        deviceId: String,
        sid: String,
        callback: String,
    ): MiCredentials? {
        val accountCookies = cookieStorage.get(Url("https://account.xiaomi.com/"))
        val userId = accountCookies.firstOrNull { it.name == "userId" }?.value?.takeIf { it.isNotBlank() }
        val passToken = accountCookies.firstOrNull { it.name == "passToken" }?.value?.takeIf { it.isNotBlank() }
        if (userId.isNullOrEmpty() || passToken.isNullOrEmpty()) return null

        PassportHttpSession.seedDeviceIdCookie(cookieStorage, deviceId)
        cookieStorage.addCookie(
            Url("https://account.xiaomi.com/"),
            Cookie(name = "userId", value = userId, domain = ".xiaomi.com", path = "/"),
        )
        cookieStorage.addCookie(
            Url("https://account.xiaomi.com/"),
            Cookie(name = "passToken", value = passToken, domain = ".xiaomi.com", path = "/"),
        )

        val loginUrl =
            "https://account.xiaomi.com/pass/serviceLogin?sid=$sid&_json=true&callback=${callback.encodeURLParameter()}"
        val response = client.get(loginUrl) {
            header("User-Agent", userAgent)
        }
        val body = PassportAuthUtils.stripJsonPrefix(response.bodyAsText())
        val obj = try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return null
        }
        val code = obj["code"]?.jsonPrimitive?.int ?: -1
        if (code != 0) return null
        val securityStatus = obj["securityStatus"]?.jsonPrimitive?.int ?: 0
        if (securityStatus != 0) return null

        val ssecurity = obj["ssecurity"]?.jsonPrimitive?.content
            ?: harvestSsecurity(client, userId, passToken, deviceId, sid)
        val location = obj["location"]?.jsonPrimitive?.content
        val passFromBody = obj["passToken"]?.jsonPrimitive?.content ?: passToken
        val uid = PassportAuthUtils.jsonUserId(obj).ifEmpty { userId }

        val serviceToken: String
        val region: String
        if (!location.isNullOrEmpty()) {
            val pair = followRedirectsCollectingServiceToken(
                client,
                PassportAuthUtils.absUrl(location),
            )
            serviceToken = pair.first
            region = pair.second
        } else {
            serviceToken = cookieStorage.get(Url("https://sts-hlth.io.mi.com/"))
                .firstOrNull { it.name == "serviceToken" }?.value
                ?: cookieStorage.get(Url("https://account.xiaomi.com/"))
                    .firstOrNull { it.name == "serviceToken" }?.value
                ?: ""
            region = ""
        }
        if (serviceToken.isEmpty() || ssecurity.isEmpty()) return null

        return MiCredentials(
            userId = uid,
            ssecurity = ssecurity,
            serviceToken = serviceToken,
            passToken = passFromBody,
            deviceId = deviceId,
            region = PassportAuthUtils.resolveRegion(region),
        )
    }

    private suspend fun fetchSign(client: HttpClient, sid: String): String {
        val response = client.get("https://account.xiaomi.com/pass/serviceLogin?sid=$sid&_json=true") {
            header("User-Agent", userAgent)
        }
        val body = PassportAuthUtils.stripJsonPrefix(response.bodyAsText())
        val obj = json.parseToJsonElement(body).jsonObject
        return obj["_sign"]?.jsonPrimitive?.content ?: ""
    }

    private suspend fun postServiceLoginAuth2(
        client: HttpClient,
        email: String,
        password: String,
        sid: String,
        callback: String,
        sign: String,
    ): JsonObject {
        val hash = MiCloudSigner.hashPassword(password)
        val response = client.submitForm(
            url = "https://account.xiaomi.com/pass/serviceLoginAuth2",
            formParameters = Parameters.build {
                append("sid", sid)
                append("hash", hash)
                append("callback", callback)
                append("qs", "?sid=$sid&_json=true")
                append("user", email)
                append("_json", "true")
                if (sign.isNotEmpty()) append("_sign", sign)
            },
        ) {
            header("User-Agent", userAgent)
        }
        val body = PassportAuthUtils.stripJsonPrefix(response.bodyAsText())
        return json.parseToJsonElement(body).jsonObject
    }

    private suspend fun exchangeLocationForCredentials(
        client: HttpClient,
        authResponse: JsonObject,
        deviceId: String,
    ): MiCredentials {
        val location = authResponse["location"]?.jsonPrimitive?.content
            ?: throw MiAuthException("Login succeeded but no location URL (can't get serviceToken)")
        val userId = PassportAuthUtils.jsonUserId(authResponse)
        val ssecurity = authResponse["ssecurity"]?.jsonPrimitive?.content ?: ""
        val passToken = authResponse["passToken"]?.jsonPrimitive?.content ?: ""

        val (serviceToken, region) = followRedirectsCollectingServiceToken(
            client,
            PassportAuthUtils.absUrl(location),
        )
        if (serviceToken.isEmpty()) {
            throw MiAuthException("STS did not set serviceToken — location follow failed")
        }

        return MiCredentials(
            userId = userId,
            ssecurity = ssecurity,
            serviceToken = serviceToken,
            passToken = passToken,
            deviceId = deviceId,
            region = PassportAuthUtils.resolveRegion(region),
        )
    }

    override suspend fun followRedirectsCollectingServiceToken(
        client: HttpClient,
        startUrl: String,
    ): Pair<String, String> {
        var serviceToken = ""
        var region = ""
        var currentUrl = startUrl
        repeat(12) {
            val response = client.get(currentUrl) {
                header("User-Agent", userAgent)
            }
            val cookieHeaders = (response.headers.getAll("Set-Cookie") ?: emptyList()) +
                (response.headers.getAll("set-cookie") ?: emptyList())
            cookieHeaders.forEach { cookie ->
                if (cookie.contains("serviceToken=")) {
                    serviceToken = cookie.substringAfter("serviceToken=").substringBefore(";")
                }
            }
            if (currentUrl.contains("p_ur=")) {
                region = try {
                    parseQueryString(Url(currentUrl).encodedQuery)["p_ur"] ?: region
                } catch (_: Exception) {
                    region
                }
            }
            val redirectUrl = response.headers["Location"]
            if (redirectUrl.isNullOrEmpty() || response.status.value !in 300..399) {
                return@repeat
            }
            currentUrl = PassportAuthUtils.absUrl(redirectUrl)
        }
        return serviceToken to region
    }

    private suspend fun harvestSsecurity(
        client: HttpClient,
        userId: String,
        passToken: String,
        deviceId: String,
        sid: String,
    ): String {
        val response = client.get("https://account.xiaomi.com/pass/serviceLogin?sid=$sid&_json=true") {
            header("User-Agent", userAgent)
            header("Cookie", "userId=$userId; passToken=$passToken; deviceId=$deviceId")
        }
        val pragma = response.headers["Extension-Pragma"] ?: response.headers["extension-pragma"]
        if (pragma != null) {
            val ssec = PassportAuthUtils.parseJsonField(pragma, "ssecurity")
            if (ssec.isNotEmpty()) return ssec
        }
        return PassportAuthUtils.parseJsonField(
            PassportAuthUtils.stripJsonPrefix(response.bodyAsText()),
            "ssecurity",
        )
    }

    companion object {
        const val DEFAULT_SID = PassportAuthUtils.DEFAULT_SID
        const val DEFAULT_STS_CALLBACK = PassportAuthUtils.DEFAULT_STS_CALLBACK
        const val DEFAULT_USER_AGENT = PassportAuthUtils.DEFAULT_USER_AGENT

        fun generateDeviceId(): String = PassportAuthUtils.generateDeviceId()
    }
}
