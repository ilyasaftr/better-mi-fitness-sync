package com.bettermifitness.sync.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.i18n.L10n
import com.bettermifitness.sync.data.repository.SyncState
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.ui.components.PrimaryButton
import com.bettermifitness.sync.ui.components.StickyCtaBar
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.koin.mp.KoinPlatform

/**
 * Sync screen (parity with iOS SyncView): metric list + sticky Sync now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<SyncViewModel>() }
    val state by viewModel.uiState.collectAsState()

    val healthName = state.healthServiceName.ifBlank { L10n.string(L10n.healthFallback) }
    val metrics = state.visibleMetrics
    val daysLabel = if (state.rangeDays == 1) L10n.string(L10n.syncLastOneDay) else L10n.stringFmt(L10n.syncLastNDays, state.rangeDays)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(L10n.string(L10n.syncTitle), fontWeight = FontWeight.SemiBold) },
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
        bottomBar = {
            StickyCtaBar(modifier = Modifier.navigationBarsPadding()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = if (state.isSyncing) L10n.string(L10n.homeSyncing) else L10n.string(L10n.syncNow),
                        onClick = viewModel::startSync,
                        enabled = state.healthAvailable && metrics.isNotEmpty() && !state.isSyncing,
                        loading = state.isSyncing,
                        icon = AppIcons.Sync,
                    )
                    if (!state.healthAvailable) {
                        TextButton(
                            onClick = viewModel::openHealthService,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(L10n.textFmt(L10n.syncOpenHealth, healthName))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BrandSpacing.ScreenHorizontal)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Last $daysLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.permissionError?.let { err ->
                CompactBanner(err, isError = true)
            }
            state.availabilityHint?.let { hint ->
                CompactBanner(hint, isError = false)
            }
            if (state.outcomeIsWarning) {
                state.outcomeMessage?.let { CompactBanner(it, isError = false) }
            }

            if (metrics.isEmpty()) {
                Text(
                    if (state.healthAvailable) {
                        L10n.string(L10n.syncNoMetrics)
                    } else {
                        L10n.string(L10n.syncPreparing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        metrics.forEachIndexed { index, metric ->
                            MetricRow(
                                metric = metric,
                                syncState = viewModel.stateFor(state.progress, metric.key),
                            )
                            if (index < metrics.lastIndex) {
                                HorizontalDivider(Modifier.padding(start = 48.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactBanner(text: String, isError: Boolean) {
    val color = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun MetricRow(metric: SyncMetric, syncState: SyncState) {
    val (label, color) = when (syncState) {
        is SyncState.InProgress -> L10n.string(L10n.syncStatusSyncing) to MaterialTheme.colorScheme.primary
        is SyncState.Success -> L10n.string(L10n.syncStatusDone) to MaterialTheme.colorScheme.primary
        is SyncState.Error -> L10n.string(L10n.syncStatusFailed) to MaterialTheme.colorScheme.error
        is SyncState.Idle -> L10n.string(L10n.syncStatusWaiting) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(
            AppIcons.forMetric(metric.key),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            L10n.metric(metric.key),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.width(88.dp),
        ) {
            when (syncState) {
                is SyncState.InProgress ->
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                is SyncState.Success ->
                    AppIcon(
                        AppIcons.CheckCircle,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                is SyncState.Error ->
                    AppIcon(
                        AppIcons.ErrorOutline,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                is SyncState.Idle -> { }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
            )
        }
    }
}
