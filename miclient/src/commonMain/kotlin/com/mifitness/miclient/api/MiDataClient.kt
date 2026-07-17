package com.mifitness.miclient.api

import com.mifitness.miclient.auth.MiCredentials
import com.mifitness.miclient.createPlatformHttpClient
import com.mifitness.miclient.crypto.MiCloudSigner
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Encrypted HTTP client for Mi Fitness data endpoints.
 *
 * Each request is signed + RC4-encrypted per Mi's protocol (see [MiCloudSigner]),
 * and the response is decrypted transparently. Callers work with plaintext JSON.
 *
 * Single responsibility: transport. No business logic or data parsing.
 */
class MiDataClient(
    private val credentials: MiCredentials,
    private val httpClient: HttpClient = createPlatformHttpClient(),
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The regional data host for this user (e.g. "sg.hlth.io.mi.com"). */
    val healthHost: String = buildHealthHost(credentials.region)

    /**
     * Sends an encrypted POST request and returns the decrypted JSON response.
     *
     * @param host the backend host (e.g. [healthHost])
     * @param path the URL path for signing (e.g. "/app/v1/data/get_latest_fitness_data")
     * @param signPath optional separate signing path (for endpoints with a pathPrefix like "/healthapp/...")
     * @param payload the plaintext JSON payload to encrypt and send
     * @return parsed JSON element of the decrypted response
     */
    suspend fun encryptedPost(
        host: String = healthHost,
        path: String,
        signPath: String = path,
        payload: String,
    ): JsonElement {
        val encrypted = MiCloudSigner.buildEncryptedRequest(
            path = signPath,
            ssecurity = credentials.ssecurity,
            plaintext = payload,
        )

        val formBody = listOf(
            "_nonce" to encrypted.nonce,
            "data" to encrypted.data,
            "rc4_hash__" to encrypted.rc4Hash,
            "signature" to encrypted.signature,
        ).formUrlEncode()

        val response = try {
            httpClient.post("https://$host$path") {
                contentType(ContentType.Application.FormUrlEncoded)
                header("User-Agent", userAgent)
                header("Cookie", buildCookieHeader())
                setBody(formBody)
            }
        } catch (e: Exception) {
            throw mapTransportException(e)
        }

        val status = response.status.value
        val responseBase64 = response.bodyAsText().trim()

        when {
            status == 401 || status == 403 ->
                throw MiApiException.AuthExpired("Mi cloud rejected session (HTTP $status)")
            status == 429 ->
                throw MiApiException.RateLimited("Mi cloud rate limited (HTTP 429)")
            status !in 200..299 ->
                throw MiApiException.Server(
                    httpOrBusinessCode = status,
                    message = "Mi cloud HTTP $status: ${responseBase64.take(160)}",
                )
        }

        val decrypted = try {
            MiCloudSigner.decryptResponse(encrypted.signedNonce, responseBase64)
        } catch (e: Exception) {
            // Expired sessions often return non-RC4 garbage or HTML login pages.
            if (responseBase64.startsWith("<") || responseBase64.contains("login", ignoreCase = true)) {
                throw MiApiException.AuthExpired("Mi cloud returned undecryptable body (session may be expired)", e)
            }
            throw MiApiException.Unexpected("Failed to decrypt Mi response", e)
        }

        val element = try {
            json.parseToJsonElement(decrypted.decodeToString())
        } catch (e: Exception) {
            throw MiApiException.Unexpected("Invalid JSON from Mi cloud", e)
        }

        throwIfBusinessAuthFailure(element)
        return element
    }

    private fun throwIfBusinessAuthFailure(element: JsonElement) {
        val obj = try {
            element.jsonObject
        } catch (_: Exception) {
            return
        }
        val code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull()
        val message = obj["message"]?.jsonPrimitive?.content
            ?: obj["msg"]?.jsonPrimitive?.content
            ?: obj["description"]?.jsonPrimitive?.content
        if (code != null && code != 0) {
            if (MiApiException.isAuthBusinessCode(code) || MiApiException.looksLikeAuthMessage(message)) {
                throw MiApiException.AuthExpired(
                    message = message?.takeIf { it.isNotBlank() } ?: "Mi auth error (code $code)",
                )
            }
        } else if (message != null && MiApiException.looksLikeAuthMessage(message)) {
            throw MiApiException.AuthExpired(message)
        }
    }

    private fun mapTransportException(e: Exception): MiApiException {
        if (e is MiApiException) return e
        val name = e::class.simpleName.orEmpty()
        val msg = e.message.orEmpty()
        val looksNetwork =
            name.contains("Timeout", ignoreCase = true) ||
                name.contains("UnknownHost", ignoreCase = true) ||
                name.contains("Socket", ignoreCase = true) ||
                name.contains("Unresolved", ignoreCase = true) ||
                name.contains("Connect", ignoreCase = true) ||
                msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("Network is unreachable", ignoreCase = true) ||
                msg.contains("failed to connect", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true)
        return if (looksNetwork) {
            MiApiException.Network(msg.ifBlank { "Network error" }, e)
        } else {
            MiApiException.Unexpected(msg.ifBlank { name.ifBlank { "Request failed" } }, e)
        }
    }

    /**
     * Convenience: POST with a Map payload (auto-serialized to JSON).
     */
    suspend fun post(
        path: String,
        signPath: String = path,
        payload: Map<String, Any?>,
    ): JsonElement {
        val jsonPayload = json.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            mapToJsonElement(payload),
        )
        return encryptedPost(path = path, signPath = signPath, payload = jsonPayload)
    }

    private fun buildCookieHeader(): String {
        return listOf(
            "serviceToken=${credentials.serviceToken}",
            "userId=${credentials.userId}",
            "locale=en",
            "auth_key=$AUTH_KEY",
        ).joinToString("; ")
    }

    private fun mapToJsonElement(map: Map<String, Any?>): JsonElement {
        val content = map.mapValues { (_, v) -> anyToJsonElement(v) }
        return kotlinx.serialization.json.JsonObject(content)
    }

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> kotlinx.serialization.json.JsonPrimitive(value)
        is Number -> kotlinx.serialization.json.JsonPrimitive(value)
        is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
        is Map<*, *> -> mapToJsonElement(value.mapKeys { it.key.toString() }.mapValues { it.value })
        is List<*> -> kotlinx.serialization.json.JsonArray(value.map { anyToJsonElement(it) })
        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
    }

    fun close() { httpClient.close() }

    companion object {
        private const val AUTH_KEY = "rwelJuWBFJxmbMKD"
        private const val DEFAULT_USER_AGENT = "APP/com.xiaomi.miwatch.pro APPV/3.49.1 " +
            "iosPassportSDK/4.2.64 iOS/18.7.8 MK/aVBob25lMTQsMw== " +
            "DEVT/aVBob25l DEVS/aU9T BRA/QXBwbGU= L/en_US miHSTS"

        private fun buildHealthHost(region: String): String = when (region) {
            "cn" -> "hlth.io.mi.com"
            else -> "$region.hlth.io.mi.com"
        }
    }
}
