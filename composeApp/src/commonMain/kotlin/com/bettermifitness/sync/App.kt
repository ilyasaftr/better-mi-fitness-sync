package com.bettermifitness.sync

import androidx.compose.runtime.Composable
import com.bettermifitness.sync.navigation.NavGraph
import com.bettermifitness.sync.theme.BetterMiFitnessSyncTheme

@Composable
fun App() {
    BetterMiFitnessSyncTheme {
        NavGraph()
    }
}
