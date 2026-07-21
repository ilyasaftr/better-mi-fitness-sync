package com.mifitness.miclient.fds

import com.mifitness.miclient.crypto.Hash
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * FDS object-key suffix construction reverse-engineered from
 * `FitnessFDSUploader.genFdsUrlItem` / `genDataIdKeyBytes`.
 *
 * Request body for `healthapp/service/gen_download_url`:
 * ```json
 * { "did": "<sid>", "items": [ { "timestamp": <sec>, "suffix": "<suffix>" } ] }
 * ```
 *
 * Response is keyed by [serverKey] = `suffix + "_" + timestamp`.
 */
@OptIn(ExperimentalEncodingApi::class)
object FdsKeys {
    /** Android Base64 flags: URL_SAFE | NO_PADDING | NO_WRAP = 11. */
    private val urlSafeNoPad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    const val FILE_TYPE_RECORD = 0
    const val FILE_TYPE_REPORT = 1
    const val FILE_TYPE_GPS = 2
    const val FILE_TYPE_RECOVER_RATE = 3

    /** dataType bit for sport (vs daily). */
    const val DATA_TYPE_SPORT = 1

    /**
     * Packs type bits into the single byte used by FDS key material:
     * `(dataType << 7) + (sportType << 2) + (dailyType << 2) + fileType`.
     */
    fun genDataTypeByte(
        dataType: Int,
        sportType: Int = 0,
        dailyType: Int = 0,
        fileType: Int,
    ): Int {
        return ((dataType shl 7) + (sportType shl 2) + (dailyType shl 2) + fileType) and 0xFF
    }

    /**
     * 6-byte little-endian key material:
     * `int32 timeSec` + `tzIn15Min` + `genDataTypeByte`.
     * Note: **does not** include the report `version` field.
     */
    fun dataIdKeyBytes(
        timeSec: Long,
        tzIn15Min: Int,
        dataTypeByte: Int,
    ): ByteArray {
        val t = timeSec.toInt()
        return byteArrayOf(
            (t and 0xFF).toByte(),
            ((t ushr 8) and 0xFF).toByte(),
            ((t ushr 16) and 0xFF).toByte(),
            ((t ushr 24) and 0xFF).toByte(),
            (tzIn15Min and 0xFF).toByte(),
            (dataTypeByte and 0xFF).toByte(),
        )
    }

    /** Sport FDS key material. Uses [protoType] as sportType and [fileType] (0/2/3). */
    fun sportDataIdKeyBytes(
        timeSec: Long,
        tzIn15Min: Int,
        protoType: Int,
        fileType: Int,
    ): ByteArray {
        val typeByte = genDataTypeByte(
            dataType = DATA_TYPE_SPORT,
            sportType = protoType,
            fileType = fileType,
        )
        return dataIdKeyBytes(timeSec, tzIn15Min, typeByte)
    }

    /** FDS GPS key material for a sport report. Uses [protoType] as sportType. */
    fun gpsDataIdKeyBytes(
        timeSec: Long,
        tzIn15Min: Int,
        protoType: Int,
    ): ByteArray = sportDataIdKeyBytes(timeSec, tzIn15Min, protoType, FILE_TYPE_GPS)

    /**
     * `suffix = Base64URL(dataIdKeyBytes) + "_" + Base64URL(SHA1(sid UTF-8))`
     */
    fun suffix(sid: String, dataIdKey: ByteArray): String {
        val left = urlSafeNoPad.encode(dataIdKey)
        val right = urlSafeNoPad.encode(Hash.sha1(sid.encodeToByteArray()))
        return "${left}_$right"
    }

    fun suffixForSportFile(
        sid: String,
        timeSec: Long,
        tzIn15Min: Int,
        protoType: Int,
        fileType: Int,
    ): String = suffix(sid, sportDataIdKeyBytes(timeSec, tzIn15Min, protoType, fileType))

    fun suffixForGps(
        sid: String,
        timeSec: Long,
        tzIn15Min: Int,
        protoType: Int,
    ): String = suffixForSportFile(sid, timeSec, tzIn15Min, protoType, FILE_TYPE_GPS)

    /** Map key in gen_download_url result: `suffix_timestamp`. */
    fun serverKey(suffix: String, timeSec: Long): String = "${suffix}_$timeSec"

    fun encodeUrlSafeNoPad(bytes: ByteArray): String = urlSafeNoPad.encode(bytes)

    fun decodeUrlSafe(text: String): ByteArray =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(text)
}
