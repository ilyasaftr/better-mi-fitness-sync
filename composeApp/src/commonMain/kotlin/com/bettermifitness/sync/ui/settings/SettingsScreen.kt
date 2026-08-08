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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.i18n.L10n
import com.bettermifitness.sync.platform.appVersionLabel
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.ui.components.StatusBanner
import com.bettermifitness.sync.ui.components.StatusTone
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.koin.mp.KoinPlatform

private const val CREDIT_URL = "https://github.com/ilyasaftr"

/**
 * Settings (parity with iOS SettingsView).
 * User-visible chrome uses moko [L10n] (system language SSOT).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit = {}) {
    val viewModel = remember { KoinPlatform.getKoin().get<SettingsViewModel>() }
    val state by viewModel.uiState.collectAsState()
    val healthName = state.healthServiceName.ifBlank { L10n.string(L10n.healthFallback) }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLogout()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        L10n.string(L10n.settingsTitle),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        AppIcon(AppIcons.ArrowBack, contentDescription = L10n.string(L10n.back))
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
                            Text(L10n.stringFmt(L10n.openHealth, healthName))
                        }
                    },
                )
            }

            if (state.prefsReady) {
                HowFarBackCard(
                    rangeDays = state.rangeDays,
                    onRangeSelected = viewModel::setSyncRangeDays,
                )
                AutoSyncCard(
                    state = state,
                    onAutoSyncChange = viewModel::setAutoSync,
                    onTestBg = viewModel::runBackgroundRefreshTest,
                )
                WhatToSyncCard(
                    enabledMetrics = state.enabledMetrics,
                    onToggle = viewModel::setMetricEnabled,
                )
            } else {
                SettingsGroup(title = L10n.string(L10n.settingsSyncOptionsLoading)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            StatusCard(state)

            if (state.showShortcutsHelp) {
                ShortcutsHelpCard()
            }

            AccountCard(onLogout = viewModel::logout)
            AboutCard()

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(state: SettingsUiState) {
    SettingsGroup(title = L10n.string(L10n.settingsSectionStatus)) {
        StatusRow(label = L10n.string(L10n.settingsLastSync), value = state.lastSyncLabel)
        HorizontalDivider(
            Modifier.padding(start = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        StatusRow(
            label = L10n.string(L10n.settingsLastBackgroundSync),
            value = state.lastBackgroundSyncLabel,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WhatToSyncCard(
    enabledMetrics: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    SettingsGroup(title = L10n.string(L10n.settingsSectionMetrics)) {
        SyncMetric.entries.forEachIndexed { index, metric ->
            MetricRow(
                metric = metric,
                enabled = metric.key in enabledMetrics,
                onToggle = { on -> onToggle(metric.key, on) },
            )
            if (index < SyncMetric.entries.lastIndex) {
                HorizontalDivider(
                    Modifier.padding(start = 50.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun HowFarBackCard(rangeDays: Int, onRangeSelected: (Int) -> Unit) {
    val options = listOf(1, 7, 14, 30)
    SettingsGroup(title = L10n.string(L10n.settingsSectionSyncRange)) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            options.forEachIndexed { index, days ->
                val label = if (days == 1) {
                    L10n.string(L10n.settingsRange1Day)
                } else {
                    L10n.stringFmt(L10n.settingsRangeNDays, days)
                }
                SegmentedButton(
                    selected = rangeDays == days,
                    onClick = { onRangeSelected(days) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                    label = {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AutoSyncCard(
    state: SettingsUiState,
    onAutoSyncChange: (Boolean) -> Unit,
    onTestBg: () -> Unit,
) {
    SettingsGroup(title = L10n.string(L10n.settingsSectionAutoSync)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L10n.string(L10n.settingsBackgroundAutoSync),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Switch(
                checked = state.autoSync,
                onCheckedChange = onAutoSyncChange,
            )
        }

        if (state.bgRefreshLabel.isNotBlank()) {
            HorizontalDivider(
                Modifier.padding(start = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            Text(
                state.bgRefreshLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (state.canTestBgRefresh) {
            HorizontalDivider(
                Modifier.padding(start = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            TextButton(
                onClick = onTestBg,
                enabled = state.autoSync && !state.bgTestRunning,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                if (state.bgTestRunning) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(L10n.string(L10n.settingsTestBackgroundRefresh))
            }
            state.bgTestStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ShortcutsHelpCard() {
    SettingsGroup(title = L10n.string(L10n.settingsSectionShortcuts)) {
        Text(
            L10n.string(L10n.settingsShortcutsHelp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AccountCard(onLogout: () -> Unit) {
    SettingsGroup(title = L10n.string(L10n.settingsSectionAccount)) {
        TextButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            AppIcon(
                AppIcons.Logout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                L10n.string(L10n.settingsLogOut),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    val version = remember { appVersionLabel() }
    SettingsGroup(title = L10n.string(L10n.settingsSectionAbout)) {
        StatusRow(label = L10n.string(L10n.settingsVersion), value = version)
        HorizontalDivider(
            Modifier.padding(start = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(CREDIT_URL) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                L10n.string(L10n.settingsCredit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                L10n.string(L10n.settingsCreditName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = BrandShapes.Card,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun MetricRow(metric: SyncMetric, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            AppIcon(
                AppIcons.forMetric(metric.key),
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                L10n.metric(metric.key),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}
