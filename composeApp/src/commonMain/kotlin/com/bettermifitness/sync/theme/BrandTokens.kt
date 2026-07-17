package com.bettermifitness.sync.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand tokens derived from the app icon (navy field + orange sync arrows + ECG).
 * Principles: Apple HIG clarity + calm health utility; Material 3 implementation.
 */
object BrandColors {
    /** Icon orange — primary CTA / brand accent */
    val Orange = Color(0xFFFF6B35)
    val OrangeBright = Color(0xFFFF8F5E)
    val OrangeDeep = Color(0xFFCC5529)

    /** Icon navy family */
    val NavyDeep = Color(0xFF0B1524)
    val Navy = Color(0xFF0F2744)
    val NavyMid = Color(0xFF142033)
    val NavySoft = Color(0xFF1B2D45)

    val Ink = Color(0xFF0F2744)
    val InkMuted = Color(0xFF5A6B7D)
    val OnDark = Color(0xFFE8EEF6)
    val OnDarkMuted = Color(0xFFB0BDD0)

    val CanvasLight = Color(0xFFF4F6F9)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceVariantLight = Color(0xFFE8EDF3)

    // Calm status (Apple Health–like, not neon)
    val Success = Color(0xFF2E7D4F)
    val SuccessContainerLight = Color(0xFFE3F5EA)
    val SuccessContainerDark = Color(0xFF1A3D2A)
    val Caution = Color(0xFFB26A00)
    val CautionContainerLight = Color(0xFFFFF0D6)
    val CautionContainerDark = Color(0xFF3D2E10)
    val Danger = Color(0xFFC62828)
    val DangerContainerLight = Color(0xFFFDECEA)
    val DangerContainerDark = Color(0xFF4A1C1C)
    val Info = Color(0xFF1565C0)
    val InfoContainerLight = Color(0xFFE3F0FF)
    val InfoContainerDark = Color(0xFF1A2F4A)
}

@Immutable
object BrandShapes {
    val RadiusSm = 12.dp
    val RadiusMd = 16.dp
    val RadiusLg = 20.dp
    val Card = RoundedCornerShape(RadiusLg)
    val CardMd = RoundedCornerShape(RadiusMd)
    val Button = RoundedCornerShape(RadiusMd)
    val Chip = RoundedCornerShape(999.dp)
    val IconWell = RoundedCornerShape(RadiusSm)
}

object BrandSpacing {
    val ScreenHorizontal = 20.dp
    val Section = 20.dp
    val CardPadding = 18.dp
    val CtaHeight = 56.dp
}

/**
 * HIG-inspired type scale on default platform font.
 */
fun brandTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )
}
