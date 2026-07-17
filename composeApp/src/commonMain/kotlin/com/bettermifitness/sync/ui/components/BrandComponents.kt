package com.bettermifitness.sync.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bettermifitness.sync.theme.BrandColors
import com.bettermifitness.sync.theme.BrandShapes
import com.bettermifitness.sync.theme.BrandSpacing
import com.bettermifitness.sync.ui.icons.AppIcon
import com.bettermifitness.sync.ui.icons.AppIcons
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun statusToneColors(tone: StatusTone): ToneColors {
    // Prefer explicit brand status containers for calm Health-like tints
    return when (tone) {
        StatusTone.Neutral -> ToneColors(
            container = MaterialTheme.colorScheme.surfaceVariant,
            onContainer = MaterialTheme.colorScheme.onSurface,
            accent = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusTone.Success -> ToneColors(
            container = if (isDarkSurface()) BrandColors.SuccessContainerDark else BrandColors.SuccessContainerLight,
            onContainer = if (isDarkSurface()) BrandColors.OnDark else BrandColors.Success,
            accent = BrandColors.Success,
        )
        StatusTone.Warning -> ToneColors(
            container = if (isDarkSurface()) BrandColors.CautionContainerDark else BrandColors.CautionContainerLight,
            onContainer = if (isDarkSurface()) Color(0xFFFFE0A3) else Color(0xFF3D2500),
            accent = BrandColors.Caution,
        )
        StatusTone.Error -> ToneColors(
            container = if (isDarkSurface()) BrandColors.DangerContainerDark else BrandColors.DangerContainerLight,
            onContainer = if (isDarkSurface()) Color(0xFFFFDAD6) else Color(0xFF5C1010),
            accent = BrandColors.Danger,
        )
        StatusTone.Info -> ToneColors(
            container = if (isDarkSurface()) BrandColors.InfoContainerDark else BrandColors.InfoContainerLight,
            onContainer = if (isDarkSurface()) BrandColors.OnDark else BrandColors.Navy,
            accent = BrandColors.Info,
        )
    }
}

@Composable
private fun isDarkSurface(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    // Luminance-ish: dark navy backgrounds are low
    return (bg.red + bg.green + bg.blue) / 3f < 0.35f
}

@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = statusToneColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = BrandShapes.Card,
        color = colors.container,
    ) {
        Column(
            Modifier.padding(BrandSpacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: DrawableResource? = AppIcons.Sync,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(BrandSpacing.CtaHeight),
        shape = BrandShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            AppIcon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun StickyCtaBar(
    modifier: Modifier = Modifier,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BrandSpacing.ScreenHorizontal, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BrandSpacing.ScreenHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(BrandSpacing.Section),
        content = content,
    )
}

@Composable
fun IconWell(
    icon: DrawableResource,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = BrandShapes.IconWell,
        color = accent.copy(alpha = 0.14f),
        modifier = modifier.size(44.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(icon, null, Modifier.size(24.dp), tint = accent)
        }
    }
}
