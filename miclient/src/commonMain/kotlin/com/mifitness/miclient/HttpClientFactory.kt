package com.mifitness.miclient

import io.ktor.client.HttpClient

/** Platform HTTP client with the correct engine (OkHttp / Darwin). */
expect fun createPlatformHttpClient(): HttpClient
