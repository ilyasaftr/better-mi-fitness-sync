package com.bettermifitness.sync.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.theme.BrandColors
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.components.PrimaryButton
import com.bettermifitness.sync.ui.components.StickyCtaBar
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.koin.mp.KoinPlatform

/**
 * Home hub (parity with iOS SwiftUI HomeView):
 * account hero → optional banner → Setup → Activity → sticky Sync CTA.
 * No live health values — those live in Health Connect / Apple Health.
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
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
        viewModel.refreshHealthReadiness()
    }

    val healthName = state.healthServiceName.ifBlank { "Health" }
    val fullName = state.profile?.result?.name?.trim()?.takeIf { it.isNotEmpty() }
    val titleName = fullName ?: "Your account"
    val canUpdate = state.enabledMetricsCount > 0

    val primaryLabel = when {
        state.healthNeedsAction -> "Allow access"
        !canUpdate -> "Open Settings"
        state.isSyncing -> "Syncing…"
        else -> "Sync now"
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        AppIcon(
                            AppIcons.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        bottomBar = {
            StickyCtaBar(modifier = Modifier.navigationBarsPadding()) {
                PrimaryButton(
                    text = primaryLabel,
                    onClick = {
                        when {
                            state.healthNeedsAction -> viewModel.openHealthService()
                            !canUpdate -> onSettingsClick()
                            else -> {
                                if (!state.isSyncing) viewModel.startSyncIfIdle()
                                onSyncClick()
                            }
                        }
                    },
                    loading = state.isSyncing,
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
                .padding(horizontal = BrandSpacing.ScreenHorizontal)
                .padding(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AccountHero(
                name = titleName,
                subtitle = when {
                    state.profileError != null -> state.profileError ?: "Signed in"
                    state.profile != null -> "Mi Account · Connected"
                    else -> "Getting your profile…"
                },
                isError = state.profileError != null,
            )

            blockingBanner(state, healthName)?.let { banner ->
                BlockingBannerCard(banner)
            }

            SetupSection(
                state = state,
                healthName = healthName,
                onHealthClick = {
                    if (state.healthNeedsAction) viewModel.openHealthService()
                },
                onSyncOptionsClick = onSettingsClick,
            )

            ActivitySection(state = state)
        }
    }
}

@Composable
private fun AccountHero(name: String, subtitle: String, isError: Boolean) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BrandColors.Navy),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letter,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class Banner(val title: String, val detail: String, val error: Boolean)

private fun blockingBanner(state: HomeUiState, healthName: String): Banner? {
    if (state.healthNeedsAction) {
        return Banner(
            title = state.healthStatusTitle.ifBlank { "Allow access to $healthName" },
            detail = state.healthStatusDetail.ifBlank {
                "Grant write access so this app can write data to $healthName."
            },
            error = true,
        )
    }
    if (state.enabledMetricsCount == 0) {
        return Banner(
            title = "Nothing selected to sync",
            detail = "Choose metrics and range in Settings. Viewed data lives in $healthName.",
            error = false,
        )
    }
    return null
}

@Composable
private fun BlockingBannerCard(banner: Banner) {
    val accent = if (banner.error) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                banner.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            Text(
                banner.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupSection(
    state: HomeUiState,
    healthName: String,
    onHealthClick: () -> Unit,
    onSyncOptionsClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Setup")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                SetupRow(
                    icon = AppIcons.Favorite,
                    badgeColor = if (state.healthNeedsAction) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFFFF2D55) // Health-like pink
                    },
                    title = healthName,
                    trailing = when {
                        state.healthNeedsAction -> "Allow access"
                        state.healthReady -> "Ready"
                        else -> state.healthStatusTitle.ifBlank { "Checking…" }
                    },
                    trailingColor = if (state.healthNeedsAction) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    showChevron = state.healthNeedsAction,
                    onClick = if (state.healthNeedsAction) onHealthClick else null,
                )
                HorizontalDivider(Modifier.padding(start = 52.dp))
                SetupRow(
                    icon = AppIcons.Tune,
                    badgeColor = if (state.enabledMetricsCount == 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        BrandColors.Navy
                    },
                    title = "Sync options",
                    detail = planSummary(state),
                    detailColor = if (state.enabledMetricsCount == 0) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    showChevron = true,
                    onClick = onSyncOptionsClick,
                )
            }
        }
    }
}

private fun planSummary(state: HomeUiState): String {
    if (state.enabledMetricsCount == 0) return "None selected"
    val metrics = if (state.enabledMetricsCount == 1) {
        "1 metric"
    } else {
        "${state.enabledMetricsCount} metrics"
    }
    val days = if (state.rangeDays == 1) "1 day" else "${state.rangeDays} days"
    val auto = if (state.autoSync) "Auto-sync on" else "Manual"
    return "$metrics · $days · $auto"
}

@Composable
private fun SetupRow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    badgeColor: Color,
    title: String,
    detail: String? = null,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showChevron: Boolean,
    onClick: (() -> Unit)?,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = detailColor,
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = trailingColor,
                maxLines = 1,
            )
        }
        if (showChevron) {
            AppIcon(
                AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ActivitySection(state: HomeUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Activity")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                FootnoteRow(
                    title = "Last sync",
                    value = lastSyncValue(state),
                    valueColor = lastSyncColor(state),
                )
                HorizontalDivider(Modifier.padding(start = 14.dp))
                FootnoteRow(
                    title = "Background sync",
                    value = backgroundValue(state),
                    valueColor = if (state.lastBackgroundIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun lastSyncValue(state: HomeUiState): String {
    if (state.isSyncing) return "Syncing…"
    val whenLabel = state.lastSyncLabel
    if (whenLabel == "Never") return "Never"
    if (state.lastSyncIsError) return "$whenLabel · Failed"
    if (state.lastSyncIsWarning) return "$whenLabel · Partial"
    return whenLabel
}

@Composable
private fun lastSyncColor(state: HomeUiState): Color = when {
    state.isSyncing -> MaterialTheme.colorScheme.primary
    state.lastSyncIsError -> MaterialTheme.colorScheme.error
    state.lastSyncIsWarning -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun backgroundValue(state: HomeUiState): String {
    val label = state.lastBackgroundLabel
    return if (state.lastBackgroundIsError) "$label · Failed" else label
}

@Composable
private fun FootnoteRow(title: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}
