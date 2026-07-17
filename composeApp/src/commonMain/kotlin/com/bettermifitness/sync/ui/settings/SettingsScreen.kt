package com.bettermifitness.sync.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.ui.components.StatusBanner
import com.bettermifitness.sync.ui.components.StatusChip
import com.bettermifitness.sync.ui.components.StatusTone
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.koin.mp.KoinPlatform

/**
 * Settings for everyone: what to sync, how far back, and a simple automatic switch.
 * No separate “foreground / background run” dashboards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<SettingsViewModel>() }
    val state by viewModel.uiState.collectAsState()
    val healthName = state.healthServiceName.ifBlank { "Health" }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = BrandSpacing.ScreenHorizontal, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(BrandSpacing.Section),
        ) {
            if (state.healthNeedsAction) {
                StatusBanner(
                    title = state.healthStatusTitle,
                    detail = state.healthStatusDetail,
                    tone = StatusTone.Error,
                    actions = {
                        OutlinedButton(
                            onClick = viewModel::openHealthService,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open $healthName")
                        }
                    },
                )
            }

            LastSyncCard(state)

            if (state.enabledMetrics.isEmpty()) {
                StatusBanner(
                    title = "Nothing selected",
                    detail = "Turn on at least one item so Sync has something to do.",
                    tone = StatusTone.Warning,
                )
            }

            WhatToSyncCard(
                healthName = healthName,
                enabledMetrics = state.enabledMetrics,
                onToggle = viewModel::setMetricEnabled,
            )
            HowFarBackCard(
                rangeDays = state.rangeDays,
                onRangeSelected = viewModel::setSyncRangeDays,
            )
            AutoSyncCard(state = state, onAutoSyncChange = viewModel::setAutoSync)

            if (state.showShortcutsHelp) {
                ShortcutsHelpCard()
            }

            CreditCard()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LastSyncCard(state: SettingsUiState) {
    SimpleCard {
        Text(
            "Last sync",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                state.lastSyncStatusTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (state.lastSyncIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (state.lastSyncIsError) {
                StatusChip("Needs attention", StatusTone.Error)
            }
        }
        Text(
            state.lastSyncLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            state.lastSyncDetail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WhatToSyncCard(
    healthName: String,
    enabledMetrics: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    SimpleCard {
        Text(
            "What to sync",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Choose what goes into $healthName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${enabledMetrics.size} of ${SyncMetric.entries.size} on",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        SyncMetric.entries.forEachIndexed { index, metric ->
            MetricRow(
                metric = metric,
                enabled = metric.key in enabledMetrics,
                onToggle = { on -> onToggle(metric.key, on) },
            )
            if (index < SyncMetric.entries.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun HowFarBackCard(rangeDays: Int, onRangeSelected: (Int) -> Unit) {
    SimpleCard {
        Text(
            "How far back",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "How many days to include when you sync",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "1 day", 7 to "7 days", 30 to "30 days").forEach { (days, label) ->
                FilterChip(
                    selected = rangeDays == days,
                    onClick = { onRangeSelected(days) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun AutoSyncCard(state: SettingsUiState, onAutoSyncChange: (Boolean) -> Unit) {
    SimpleCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    "Auto-sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "When on, your phone may quietly sync recent data so Health stays roughly current. " +
                        "It won’t always run on a perfect schedule — that’s normal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.autoSync,
                onCheckedChange = onAutoSyncChange,
            )
        }
        Spacer(Modifier.height(10.dp))
        StatusChip(
            label = if (state.autoSync) "On" else "Off — only when you tap Sync",
            tone = if (state.autoSync) StatusTone.Success else StatusTone.Neutral,
        )

        if (state.showBackgroundRefreshDetails && state.autoSync) {
            val restricted = state.bgRefreshLabel.contains("off", ignoreCase = true) ||
                state.bgRefreshLabel.contains("restricted", ignoreCase = true)
            if (restricted) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Tip: in iPhone Settings, allow Background App Refresh for this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ShortcutsHelpCard() {
    SimpleCard {
        Text(
            "Daily schedule (optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Want a full sync every morning? Use the Shortcuts app:\n\n" +
                "1. New Automation → Time of Day\n" +
                "2. Add the Sync action for Better Mi Fitness Sync\n" +
                "3. Turn off “Ask Before Running” if you like\n\n" +
                "Or say: “Hey Siri, Sync Better Mi Fitness Sync”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreditCard() {
    val uriHandler = LocalUriHandler.current
    SimpleCard {
        Text(
            "Credit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Ilyasa Fathur Rahman (ilyasaftr)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/ilyasaftr")
            },
        )
    }
}

@Composable
private fun SimpleCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BrandShapes.Card,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(BrandSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun MetricRow(metric: SyncMetric, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            AppIcon(
                metric.iconRes,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                metric.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
