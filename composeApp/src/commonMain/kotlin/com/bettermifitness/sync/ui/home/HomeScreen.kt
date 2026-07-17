package com.bettermifitness.sync.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.components.AvatarCircle
import com.bettermifitness.sync.ui.components.BrandCard
import com.bettermifitness.sync.ui.components.IconWell
import com.bettermifitness.sync.ui.components.PrimaryButton
import com.bettermifitness.sync.ui.components.StatusChip
import com.bettermifitness.sync.ui.components.StatusTone
import com.bettermifitness.sync.ui.components.StickyCtaBar
import com.bettermifitness.sync.ui.components.statusToneColors
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.koin.mp.KoinPlatform

/**
 * Home: one status story + one primary action. No duplicate CTAs.
 * - Settings lives only in the top bar
 * - Update lives only in the bottom bar
 * - Hero is status only (optional link to open Health app when needed)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSyncClick: () -> Unit, onSettingsClick: () -> Unit, onLogout: () -> Unit) {
    val viewModel = remember { KoinPlatform.getKoin().get<HomeViewModel>() }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            viewModel.consumeLoggedOut()
            onLogout()
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshHealthReadiness() }

    val healthName = state.healthServiceName.ifBlank { "Health" }
    val fullName = state.profile?.result?.name?.trim()?.takeIf { it.isNotEmpty() }
    // Full name as title (no “Hi”, no first-name parsing). Layout handles length.
    val titleName = fullName ?: "Your account"
    val hero = buildHero(state, healthName)

    val canUpdate = state.enabledMetricsCount > 0
    val primaryLabel = when {
        state.healthNeedsAction -> "Allow access"
        !canUpdate -> "Open Settings"
        else -> "Sync"
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Better Mi Fitness Sync", style = MaterialTheme.typography.titleLarge)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    TextButton(onClick = onSettingsClick) { Text("Settings") }
                    IconButton(onClick = viewModel::logout) {
                        AppIcon(
                            AppIcons.Logout,
                            contentDescription = "Log out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            StickyCtaBar(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                PrimaryButton(
                    text = primaryLabel,
                    onClick = {
                        when {
                            !canUpdate && !state.healthNeedsAction -> onSettingsClick()
                            else -> onSyncClick()
                        }
                    },
                    // Settings-bound CTA: no sync icon (avoids looking like a second "Sync")
                    icon = if (!canUpdate && !state.healthNeedsAction) null else AppIcons.Sync,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BrandSpacing.ScreenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(BrandSpacing.Section),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                AvatarCircle(letter = titleName)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            state.profile != null -> "Mi Account connected"
                            state.profileError != null -> "Signed in"
                            else -> "Getting your profile…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Status only — no second Update / Settings buttons here
            HeroStatusCard(
                tone = hero.tone,
                title = hero.title,
                detail = hero.detail,
                whenLabel = hero.whenLabel,
                // Only action that is NOT the bottom CTA: open system Health app
                openHealthLabel = if (state.healthNeedsAction) "Open $healthName" else null,
                onOpenHealth = viewModel::openHealthService,
            )

            // Read-only snapshot. Change things via Settings (top bar) — not a second "Edit"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = BrandShapes.Card,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.padding(BrandSpacing.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Synchronization settings",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            label = if (state.enabledMetricsCount == 0) {
                                "No items"
                            } else {
                                "${state.enabledMetricsCount} items"
                            },
                            tone = if (state.enabledMetricsCount == 0) {
                                StatusTone.Warning
                            } else {
                                StatusTone.Neutral
                            },
                        )
                        StatusChip(
                            label = if (state.rangeDays == 1) "1 day" else "${state.rangeDays} days",
                            tone = StatusTone.Neutral,
                        )
                        StatusChip(
                            label = if (state.autoSync) "Auto on" else "Manual only",
                            tone = if (state.autoSync) StatusTone.Success else StatusTone.Neutral,
                        )
                    }
                    Text(
                        buildSetupHint(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class HeroContent(
    val tone: StatusTone,
    val title: String,
    val detail: String,
    val whenLabel: String?,
)

private fun buildHero(state: HomeUiState, healthName: String): HeroContent = when {
    state.healthNeedsAction -> HeroContent(
        tone = StatusTone.Error,
        title = state.healthStatusTitle.ifBlank { "Allow access to $healthName" },
        detail = state.healthStatusDetail.ifBlank {
            "Use the button below to continue — or open $healthName if you prefer."
        },
        whenLabel = null,
    )
    state.enabledMetricsCount == 0 -> HeroContent(
        tone = StatusTone.Warning,
        title = "Nothing selected yet",
        detail = "Open Settings to choose what to sync (heart rate, steps, sleep, and more).",
        whenLabel = null,
    )
    state.lastSyncIsError -> HeroContent(
        tone = StatusTone.Error,
        title = state.lastSyncStatusTitle,
        detail = state.lastSyncDetail,
        whenLabel = "Last try · ${state.lastSyncLabel}",
    )
    state.lastSyncIsWarning -> HeroContent(
        tone = StatusTone.Warning,
        title = state.lastSyncStatusTitle,
        detail = state.lastSyncDetail,
        whenLabel = "Last sync · ${state.lastSyncLabel}",
    )
    state.lastSyncStatusTitle == "You're up to date" ||
        state.lastSyncStatusTitle == "All set" ||
        state.lastSyncStatusTitle == "Success" ->
        HeroContent(
            tone = StatusTone.Success,
            title = "You're up to date",
            // Keep short — long sentences wrap poorly in the status card
            detail = state.lastSyncDetail.ifBlank { "Synced to $healthName" },
            whenLabel = "Synced ${state.lastSyncLabel}",
        )
    else -> HeroContent(
        tone = StatusTone.Info,
        title = "Ready to sync",
        detail = "Sync ${
            if (state.rangeDays == 1) "today" else "the last ${state.rangeDays} days"
        } to $healthName.",
        whenLabel = if (state.lastSyncLabel != "Never") {
            "Last sync · ${state.lastSyncLabel}"
        } else {
            null
        },
    )
}

@Composable
private fun HeroStatusCard(
    tone: StatusTone,
    title: String,
    detail: String,
    whenLabel: String?,
    openHealthLabel: String?,
    onOpenHealth: () -> Unit,
) {
    val colors = statusToneColors(tone)
    val icon = when (tone) {
        StatusTone.Success -> AppIcons.CheckCircle
        StatusTone.Error, StatusTone.Warning -> AppIcons.ErrorOutline
        else -> AppIcons.Sync
    }

    BrandCard(tone = tone) {
        Row(verticalAlignment = Alignment.Top) {
            IconWell(icon = icon, accent = colors.accent)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onContainer,
                )
                if (whenLabel != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        whenLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onContainer.copy(alpha = 0.92f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Secondary path only — opens system Health, not the same as bottom CTA
        if (openHealthLabel != null) {
            TextButton(
                onClick = onOpenHealth,
                modifier = Modifier.padding(start = 0.dp),
            ) {
                Text(openHealthLabel)
            }
        }
    }
}

private fun buildSetupHint(state: HomeUiState): String {
    val range = if (state.rangeDays == 1) "today" else "the last ${state.rangeDays} days"
    return when {
        state.enabledMetricsCount == 0 ->
            "Change this anytime in Settings."
        state.autoSync ->
            "Syncs $range. Auto-sync runs in the background when possible."
        else ->
            "Syncs $range. Change items or auto-sync in Settings."
    }
}


