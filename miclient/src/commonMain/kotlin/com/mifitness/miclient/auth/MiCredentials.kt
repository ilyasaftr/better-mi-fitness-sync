package com.mifitness.miclient.auth

/**
 * Credentials needed for encrypted Mi Fitness API calls and session refresh.
 *
 * [passToken] is long-lived (passport); [serviceToken] + [ssecurity] are short-lived per SID.
 * [cUserId] is Xiaomi's encrypted user id used in health cookies (APK `encryptedUserId`).
 */
data class MiCredentials(
    val userId: String,
    val ssecurity: String,
    val serviceToken: String,
    val passToken: String,
    val deviceId: String,
    val region: String,
    val cUserId: String = "",
)
