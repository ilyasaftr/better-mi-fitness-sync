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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

// Login is not in NavHost, so Back from Home cannot pop to Login.
// Only logout sets LoggedOut.
private enum class AuthBootstrap {
    Loading,
    LoggedOut,
    LoggedIn,
}

@Composable
fun NavGraph() {
    val tokenStore = koinInject<TokenStore>()
    val sessionManager = koinInject<MiSessionManager>()
    val lifecycleOwner = LocalLifecycleOwner.current

    var auth by remember {
        mutableStateOf(
            if (sessionManager.isActive) AuthBootstrap.LoggedIn else AuthBootstrap.Loading,
        )
    }

    // Cold start: load saved credentials once.
    LaunchedEffect(Unit) {
        if (auth == AuthBootstrap.LoggedIn && sessionManager.isActive) return@LaunchedEffect
        auth = restoreAuth(sessionManager, tokenStore)
    }

    // Coming back to the app: keep LoggedIn if session is still active.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (sessionManager.isActive) {
                if (auth != AuthBootstrap.LoggedIn) auth = AuthBootstrap.LoggedIn
                return@repeatOnLifecycle
            }
            if (auth == AuthBootstrap.LoggedIn) {
                auth = restoreAuth(sessionManager, tokenStore)
            }
        }
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
                        onLogout = { auth = AuthBootstrap.LoggedOut },
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
    return if (credentials != null &&
        credentials.serviceToken.isNotBlank() &&
        credentials.userId.isNotBlank() &&
        credentials.ssecurity.isNotBlank()
    ) {
        sessionManager.activate(credentials)
        AuthBootstrap.LoggedIn
    } else {
        AuthBootstrap.LoggedOut
    }
}
