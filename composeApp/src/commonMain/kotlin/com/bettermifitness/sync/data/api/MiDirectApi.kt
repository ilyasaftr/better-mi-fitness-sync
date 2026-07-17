package com.bettermifitness.sync.data.api

import com.mifitness.miclient.api.MiDataClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Direct-to-Mi implementation of the data API. Uses [MiDataClient] to make
 * encrypted calls to Mi's health endpoints — no Go proxy needed.
 *
 * Exposes the same [getDataByTime] and [getLatest] methods that
 * [HealthRepository] depends on, returning the same [FitnessResponse] shapes.
 */
class MiDirectApi(private val client: MiDataClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches paginated fitness data by time range (newest-first).
     * Maps Mi's raw encrypted response to [FitnessResponse].
     */
    suspend fun getDataByTime(
        key: String,
        from: String,
        to: String,
        nextKey: String? = null,
    ): FitnessResponse<HeartRateEntry> {
        val payload = buildMap<String, Any?> {
            put("key", key)
            put("start_time", parseToUnix(from))
            put("end_time", parseToUnix(to))
            put("reverse", true)
            if (!nextKey.isNullOrEmpty()) put("next_key", nextKey)
        }
        val result = client.post(
            path = "/app/v1/data/get_fitness_data_by_time",
            payload = payload,
        )
        return parseDataListResponse(result)
    }

    /**
     * Fetches the latest N records for one or more keys.
     */
    suspend fun getLatest(keys: String, limit: Int = 10): FitnessResponse<SleepEntry> {
        val params = keys.split(",").map { k ->
            mapOf("key" to k.trim(), "limit" to limit)
        }
        val result = client.post(
            path = "/app/v1/data/get_latest_fitness_data",
            payload = mapOf("params" to params),
        )
        return parseLatestResponse(result)
    }

    /**
     * Sport / workout sessions (paginated). Separate from fitness-by-time keys.
     * Endpoint used by mi-fitness-data-bridge and Mi Fitness cloud.
     */
    suspend fun getSportRecordsByTime(
        from: String,
        to: String,
        maxPages: Int = 40,
    ): List<SportRecordEntry> {
        val all = mutableListOf<SportRecordEntry>()
        var next: String? = null
        var pages = 0
        while (pages < maxPages) {
            val payload = buildMap<String, Any?> {
                put("start_time", parseToUnix(from))
                put("end_time", parseToUnix(to))
                put("limit", 50)
                if (!next.isNullOrEmpty()) put("next_key", next)
            }
            val result = client.post(
                path = "/app/v1/data/get_sport_records_by_time",
                payload = payload,
            )
            val resultObj = result.jsonObject["result"]?.jsonObject ?: break
            val list = resultObj["sport_records"]?.jsonArray.orEmpty()
            for (item in list) {
                val obj = item.jsonObject
                val valueEl = obj["value"]
                val valueStr = when {
                    valueEl == null -> ""
                    valueEl.jsonPrimitive.isString -> valueEl.jsonPrimitive.content
                    else -> valueEl.toString()
                }
                all += SportRecordEntry(
                    key = obj["key"]?.jsonPrimitive?.content,
                    time = obj["time"]?.jsonPrimitive?.long ?: 0,
                    value = valueStr,
                    category = obj["category"]?.jsonPrimitive?.content,
                )
            }
            pages++
            val hasMore = resultObj["has_more"]?.jsonPrimitive?.boolean ?: false
            val nextKey = resultObj["next_key"]?.jsonPrimitive?.content
            if (!hasMore || nextKey.isNullOrEmpty()) break
            next = nextKey
        }
        return all
    }

    /**
     * Fetches user profile (decrypted from Mi's endpoint).
     */
    suspend fun getMe(): MeResponse {
        val result = client.encryptedPost(
            host = client.healthHost,
            path = "/healthapp/user/get_miot_user_profile",
            signPath = "/user/get_miot_user_profile",
            payload = "{}",
        )
        val obj = result.jsonObject
        return MeResponse(
            code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull(),
            message = obj["message"]?.jsonPrimitive?.content,
            result = parseUserProfile(obj["result"]),
        )
    }

    private fun parseDataListResponse(json: JsonElement): FitnessResponse<HeartRateEntry> {
        val obj = json.jsonObject
        val resultObj = obj["result"]?.jsonObject ?: return FitnessResponse()
        val dataList = resultObj["data_list"]?.jsonArray?.map { item ->
            val entry = item.jsonObject
            HeartRateEntry(
                key = entry["key"]?.jsonPrimitive?.content,
                time = entry["time"]?.jsonPrimitive?.long ?: 0,
                value = entry["value"]?.jsonPrimitive?.content ?: "",
            )
        } ?: emptyList()
        return FitnessResponse(
            result = FitnessResult(
                dataList = dataList,
                hasMore = resultObj["has_more"]?.jsonPrimitive?.boolean ?: false,
                nextKey = resultObj["next_key"]?.jsonPrimitive?.content,
            ),
            code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull(),
            message = obj["message"]?.jsonPrimitive?.content,
        )
    }

    private fun parseLatestResponse(json: JsonElement): FitnessResponse<SleepEntry> {
        val obj = json.jsonObject
        val resultObj = obj["result"]?.jsonObject ?: return FitnessResponse()
        val dataList = resultObj["data_list"]?.jsonArray?.map { item ->
            val entry = item.jsonObject
            SleepEntry(
                key = entry["key"]?.jsonPrimitive?.content,
                time = entry["time"]?.jsonPrimitive?.long ?: 0,
                value = entry["value"]?.jsonPrimitive?.content ?: "",
            )
        } ?: emptyList()
        return FitnessResponse(
            result = FitnessResult(dataList = dataList),
            code = obj["code"]?.jsonPrimitive?.content?.toIntOrNull(),
            message = obj["message"]?.jsonPrimitive?.content,
        )
    }

    private fun parseUserProfile(element: JsonElement?): UserProfile? {
        val obj = element?.jsonObject ?: return null
        return UserProfile(
            name = obj["name"]?.jsonPrimitive?.content,
            sex = obj["sex"]?.jsonPrimitive?.content,
            age = obj["age"]?.jsonPrimitive?.content?.toIntOrNull(),
            height = obj["height"]?.jsonPrimitive?.content?.toIntOrNull(),
            dailyStepGoal = obj["daily_step_goal"]?.jsonPrimitive?.content?.toIntOrNull(),
        )
    }

    /** Parses ISO-8601, YYYY-MM-DD, or unix seconds string to unix timestamp. */
    private fun parseToUnix(dateStr: String): Long {
        return try {
            kotlin.time.Instant.parse(dateStr).epochSeconds
        } catch (_: Exception) {
            try {
                // YYYY-MM-DD → treat as midnight UTC via Instant.parse with time suffix
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    kotlin.time.Instant.parse("${dateStr}T00:00:00Z").epochSeconds
                } else dateStr.toLongOrNull() ?: 0L
            } catch (_: Exception) { dateStr.toLongOrNull() ?: 0L }
        }
    }

    fun close() { client.close() }
}
