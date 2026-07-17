package com.bettermifitness.sync.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bettermifitness.sync.data.MiSessionManager
import com.bettermifitness.sync.data.preferences.TokenStore
import com.bettermifitness.sync.ui.home.HomeScreen
import com.bettermifitness.sync.ui.login.LoginScreen
import com.bettermifitness.sync.ui.settings.SettingsScreen
import com.bettermifitness.sync.ui.sync.SyncScreen
import org.koin.compose.koinInject

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SYNC = "sync"
}

/**
 * Auth restore gate so we never flash Login while DataStore is still loading
 * a saved session (cold start / process death). Warm starts with an active in-memory
 * session skip straight to logged-in.
 */
private enum class AuthBootstrap {
    Loading,
    LoggedOut,
    LoggedIn,
}

@Composable
fun NavGraph() {
    val tokenStore = koinInject<TokenStore>()
    val sessionManager = koinInject<MiSessionManager>()

    // Warm resume: in-memory session still alive → skip Loading/Login entirely.
    var auth by remember {
        mutableStateOf(
            if (sessionManager.isActive) AuthBootstrap.LoggedIn else AuthBootstrap.Loading,
        )
    }

    // One-shot restore. Do not key off Flow(token) with initial=null — that treats
    // "not loaded yet" as "logged out" and flashes LoginScreen.
    LaunchedEffect(Unit) {
        if (auth == AuthBootstrap.LoggedIn && sessionManager.isActive) return@LaunchedEffect
        auth = restoreAuth(sessionManager, tokenStore)
    }

    when (auth) {
        AuthBootstrap.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        AuthBootstrap.LoggedOut -> {
            LoginScreen(
                onLoginSuccess = { auth = AuthBootstrap.LoggedIn },
            )
        }

        AuthBootstrap.LoggedIn -> {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onSyncClick = { navController.navigate(Routes.SYNC) },
                        onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                        onLogout = { auth = AuthBootstrap.LoggedOut },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.SYNC) {
                    SyncScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private suspend fun restoreAuth(
    sessionManager: MiSessionManager,
    tokenStore: TokenStore,
): AuthBootstrap {
    if (sessionManager.isActive) return AuthBootstrap.LoggedIn

    val credentials = tokenStore.loadCredentials()
    return if (credentials != null) {
        sessionManager.activate(credentials)
        AuthBootstrap.LoggedIn
    } else {
        AuthBootstrap.LoggedOut
    }
}
