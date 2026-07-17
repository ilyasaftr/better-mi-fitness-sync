package com.bettermifitness.sync.ui.sync

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.data.repository.SyncState
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.SyncMetric
import com.bettermifitness.sync.ui.components.PrimaryButton
import com.bettermifitness.sync.ui.components.StatusBanner
import com.bettermifitness.sync.ui.components.StatusChip
import com.bettermifitness.sync.ui.components.StatusTone
import com.bettermifitness.sync.ui.components.statusToneColors
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.koin.mp.KoinPlatform

/**
 * One update flow: progress + list of items + one button. No technical dashboards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<SyncViewModel>() }
    val state by viewModel.uiState.collectAsState()

    val rangeLabel = if (state.rangeDays == 1) "today" else "the last ${state.rangeDays} days"
    val healthName = state.healthServiceName.ifBlank { "Health" }
    val metrics = state.visibleMetrics
    val states = metrics.map { viewModel.stateFor(state.progress, it.key) }
    val done = states.count { it is SyncState.Success || it is SyncState.Error }
    val success = states.count { it is SyncState.Success }
    val failed = states.count { it is SyncState.Error }
    val total = metrics.size
    val progressFraction = if (total == 0) 0f else done.toFloat() / total.toFloat()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Sync", fontWeight = FontWeight.SemiBold) },
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
            Text(
                "Sync $rangeLabel from Mi Fitness to $healthName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.healthAvailable || state.availabilityHint != null) {
                StatusBanner(
                    title = if (!state.healthAvailable) {
                        "$healthName isn’t available"
                    } else {
                        "Please allow access"
                    },
                    detail = state.availabilityHint
                        ?: "We need permission to save your activity.",
                    tone = StatusTone.Error,
                    actions = {
                        Button(onClick = viewModel::openHealthService) {
                            Text("Open $healthName")
                        }
                    },
                )
            }

            state.permissionError?.let { error ->
                StatusBanner(
                    title = "Couldn’t get access",
                    detail = error,
                    tone = StatusTone.Error,
                )
            }

            state.outcomeMessage?.let { message ->
                StatusBanner(
                    title = if (state.outcomeIsWarning) "Partly finished" else "All done",
                    detail = message,
                    tone = if (state.outcomeIsWarning) StatusTone.Warning else StatusTone.Success,
                )
            }

            if (metrics.isEmpty()) {
                StatusBanner(
                    title = "Nothing selected",
                    detail = "Go back and choose what to sync in Settings.",
                    tone = StatusTone.Warning,
                )
            } else {
                // Overall progress
                if (state.isSyncing || done > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BrandShapes.Card,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            Modifier.padding(BrandSpacing.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                when {
                                    state.isSyncing -> "Syncing… $done of $total"
                                    failed > 0 -> "Finished · $success ok, $failed failed"
                                    else -> "Finished · $success of $total"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LinearProgressIndicator(
                                progress = {
                                    if (state.isSyncing) {
                                        progressFraction.coerceAtLeast(0.05f)
                                    } else {
                                        progressFraction
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                        }
                    }
                }

                metrics.forEach { metric ->
                    ItemRow(metric, viewModel.stateFor(state.progress, metric.key))
                }
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = if (state.isSyncing) "Syncing…" else "Sync",
                onClick = viewModel::startSync,
                enabled = state.healthAvailable && metrics.isNotEmpty(),
                loading = state.isSyncing,
            )

            Text(
                "Safe to sync more than once — we won’t double-count the same days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ItemRow(metric: SyncMetric, state: SyncState) {
    val tone = when (state) {
        is SyncState.Success -> StatusTone.Success
        is SyncState.Error -> StatusTone.Error
        is SyncState.InProgress -> StatusTone.Info
        is SyncState.Idle -> StatusTone.Neutral
    }
    val colors = statusToneColors(tone)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BrandShapes.CardMd,
        color = colors.container,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        metric.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colors.onContainer,
                    )
                    Text(
                        when (state) {
                            is SyncState.Idle -> "Waiting"
                            is SyncState.InProgress -> "Syncing…"
                            is SyncState.Success ->
                                if (state.count == 0) "Nothing new" else "Saved ${state.count}"
                            is SyncState.Error -> state.message.take(80)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onContainer.copy(alpha = 0.85f),
                        maxLines = 2,
                    )
                }
            }
            when (state) {
                is SyncState.InProgress ->
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                is SyncState.Success ->
                    AppIcon(
                        AppIcons.CheckCircle,
                        null,
                        Modifier.size(20.dp),
                        tint = colors.accent,
                    )
                is SyncState.Error ->
                    StatusChip("Failed", StatusTone.Error)
                is SyncState.Idle ->
                    StatusChip("Soon", StatusTone.Neutral)
            }
        }
    }
}
