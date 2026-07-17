package com.bettermifitness.sync

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.bettermifitness.sync.health.HealthConnectPermissionBridge
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val permissionResults = Channel<Set<String>>(capacity = Channel.BUFFERED)

    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionResults.trySend(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        HealthConnectPermissionBridge.requestPermissions = { permissions ->
            requestPermissionLauncher.launch(permissions)
            permissionResults.receiveAsFlow().first()
        }
        HealthConnectPermissionBridge.openHealthConnect = {
            openHealthConnectOrStore()
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        HealthConnectPermissionBridge.requestPermissions = null
        HealthConnectPermissionBridge.openHealthConnect = null
        super.onDestroy()
    }

    private fun openHealthConnectOrStore() {
        val status = HealthConnectClient.getSdkStatus(this)
        try {
            when (status) {
                HealthConnectClient.SDK_AVAILABLE,
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                -> {
                    // Settings screen for Health Connect (or provider update flow).
                    startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                }
                else -> {
                    // Install Health Connect from Play Store.
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.google.android.apps.healthdata"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata",
                                ),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata",
                        ),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
                // No browser / Play Store — ignore.
            }
        }
    }
}
