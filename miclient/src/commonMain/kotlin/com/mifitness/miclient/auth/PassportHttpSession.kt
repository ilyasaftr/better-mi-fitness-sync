package com.mifitness.miclient.auth

import com.mifitness.miclient.createPlatformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.Cookie
import io.ktor.http.Url

/**
 * Builds a cookie-aware Ktor client for Xiaomi passport and seeds deviceId.
 */
object PassportHttpSession {
    fun buildClient(cookieStorage: AcceptAllCookiesStorage): HttpClient {
        return createPlatformHttpClient().config {
            followRedirects = false
            install(HttpCookies) {
                storage = cookieStorage
            }
        }
    }

    suspend fun seedDeviceIdCookie(storage: AcceptAllCookiesStorage, deviceId: String) {
        if (deviceId.isEmpty()) return
        for (host in listOf("https://account.xiaomi.com/", "https://sts-hlth.io.mi.com/")) {
            storage.addCookie(
                Url(host),
                Cookie(
                    name = "deviceId",
                    value = deviceId,
                    domain = host.removePrefix("https://").removeSuffix("/").let {
                        if (it.contains("xiaomi")) ".xiaomi.com" else it
                    },
                    path = "/",
                ),
            )
        }
    }
}
