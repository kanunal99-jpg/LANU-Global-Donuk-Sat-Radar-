package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LanuColorScheme = lightColorScheme(
    primary = Color(0xFF0F6B73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EFF0),
    onPrimaryContainer = Color(0xFF073B40),
    secondary = Color(0xFF315D69),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDECEF),
    onSecondaryContainer = Color(0xFF102F38),
    tertiary = Color(0xFFC88727),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4B8),
    onTertiaryContainer = Color(0xFF412B06),
    background = Color(0xFFF6F8F9),
    onBackground = Color(0xFF142126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF142126),
    surfaceVariant = Color(0xFFE8EFF1),
    onSurfaceVariant = Color(0xFF4B5F65),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FA),
    surfaceContainer = Color(0xFFF1F5F6),
    surfaceContainerHigh = Color(0xFFEBF1F2),
    surfaceContainerHighest = Color(0xFFE4ECEE),
    outline = Color(0xFF87999E),
    outlineVariant = Color(0xFFC8D4D7),
    surfaceTint = Color(0xFF0F6B73),
)

private val LanuTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
    ),
)

@Composable
fun LanuGlobalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LanuColorScheme,
        typography = LanuTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
