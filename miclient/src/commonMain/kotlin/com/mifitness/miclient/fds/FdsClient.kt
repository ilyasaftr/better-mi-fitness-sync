package com.mifitness.miclient.fds

import com.mifitness.miclient.api.MiApiException
import com.mifitness.miclient.api.MiDataClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Downloads and decrypts Mi Fitness FDS objects (sport GPS, records, …).
 *
 * Endpoint: `POST /healthapp/service/gen_download_url` (signed path `/service/gen_download_url`).
 */
class FdsClient(
    private val dataClient: MiDataClient,
    private val plainHttp: HttpClient = com.mifitness.miclient.createPlatformHttpClient(),
) {

    data class DownloadMeta(
        val url: String,
        val objectName: String,
        val objectKey: String,
        val method: String,
        val expireTime: Long?,
        val serverKey: String,
    )

    data class SportFileRequest(
        /** Device / app source id (`did` in sport JSON, column `sid` in local DB). */
        val sid: String,
        val timeSec: Long,
        val tzIn15Min: Int,
        /** `proto_type` from sport report (not always equal to `sport_type`). */
        val protoType: Int,
        /** [FdsKeys.FILE_TYPE_RECORD], [FdsKeys.FILE_TYPE_GPS], [FdsKeys.FILE_TYPE_RECOVER_RATE]. */
        val fileType: Int = FdsKeys.FILE_TYPE_GPS,
    )

    @Deprecated("Use SportFileRequest", ReplaceWith("SportFileRequest(sid, timeSec, tzIn15Min, protoType)"))
    data class SportGpsRequest(
        val sid: String,
        val timeSec: Long,
        val tzIn15Min: Int,
        val protoType: Int,
    )

    suspend fun genDownloadUrl(
        sid: String,
        timeSec: Long,
        suffix: String,
    ): DownloadMeta {
        val payload = mapOf(
            "did" to sid,
            "items" to listOf(
                mapOf(
                    "timestamp" to timeSec,
                    "suffix" to suffix,
                ),
            ),
        )
        val element = dataClient.post(
            path = "/healthapp/service/gen_download_url",
            signPath = "/service/gen_download_url",
            payload = payload,
        )
        val root = element.jsonObject
        val code = root["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (code != null && code != 0) {
            val msg = root["message"]?.jsonPrimitive?.contentOrNull
                ?: root["msg"]?.jsonPrimitive?.contentOrNull
                ?: "gen_download_url failed"
            throw MiApiException.Server(code, msg)
        }
        val result = root["result"]?.jsonObject
            ?: throw MiApiException.Unexpected("gen_download_url missing result")
        val key = FdsKeys.serverKey(suffix, timeSec)
        val entry = result[key]?.jsonObject
            ?: result.values.firstOrNull()?.jsonObject
            ?: throw MiApiException.Unexpected("gen_download_url: no entry for $key (keys=${result.keys})")
        val url = entry["url"]?.jsonPrimitive?.contentOrNull
            ?: throw MiApiException.Unexpected("gen_download_url: missing url")
        val objectKey = entry["obj_key"]?.jsonPrimitive?.contentOrNull
            ?: throw MiApiException.Unexpected("gen_download_url: missing obj_key")
        return DownloadMeta(
            url = url,
            objectName = entry["obj_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            objectKey = objectKey,
            method = entry["method"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            expireTime = entry["expires_time"]?.jsonPrimitive?.longOrNull,
            serverKey = key,
        )
    }

    suspend fun downloadDecrypted(meta: DownloadMeta): ByteArray {
        val body = plainHttp.get(meta.url).bodyAsText()
        if (body.isBlank()) {
            throw MiApiException.Unexpected("FDS download empty body")
        }
        return try {
            FdsAes.decryptBase64Ciphertext(meta.objectKey, body)
        } catch (e: Exception) {
            throw MiApiException.Unexpected("FDS AES decrypt failed: ${e.message}", e)
        }
    }

    /** Sport FDS file (record=0, GPS=2, recover=3). */
    suspend fun downloadSportFile(request: SportFileRequest): ByteArray {
        val suffix = FdsKeys.suffixForSportFile(
            sid = request.sid,
            timeSec = request.timeSec,
            tzIn15Min = request.tzIn15Min,
            protoType = request.protoType,
            fileType = request.fileType,
        )
        val meta = genDownloadUrl(request.sid, request.timeSec, suffix)
        return downloadDecrypted(meta)
    }

    /** Sport GPS file (`fileType = 2`). */
    suspend fun downloadSportGps(request: SportGpsRequest): ByteArray =
        downloadSportFile(
            SportFileRequest(
                sid = request.sid,
                timeSec = request.timeSec,
                tzIn15Min = request.tzIn15Min,
                protoType = request.protoType,
                fileType = FdsKeys.FILE_TYPE_GPS,
            ),
        )

    suspend fun downloadSportGps(request: SportFileRequest): ByteArray =
        downloadSportFile(request.copy(fileType = FdsKeys.FILE_TYPE_GPS))

    fun close() {
        plainHttp.close()
    }
}
