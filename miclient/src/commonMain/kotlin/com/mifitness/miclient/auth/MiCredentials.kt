package com.mifitness.miclient.auth

/**
 * The complete set of credentials needed to make encrypted Mi Fitness API calls.
 * Obtained from [MiAuth.completeLogin] after the user logs in via the WebView.
 */
data class MiCredentials(
    val userId: String,
    val ssecurity: String,
    val serviceToken: String,
    val passToken: String,
    val deviceId: String,
    val region: String,
)
