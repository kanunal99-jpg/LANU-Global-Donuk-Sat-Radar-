package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LanuColorScheme = lightColorScheme(
    primary = Color(0xFF0F6B73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F1F1),
    onPrimaryContainer = Color(0xFF073B40),
    secondary = Color(0xFF315D69),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEF1),
    onSecondaryContainer = Color(0xFF102F38),
    tertiary = Color(0xFFC88727),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8C5),
    onTertiaryContainer = Color(0xFF412B06),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF142126),
    surface = Color.White,
    onSurface = Color(0xFF142126),
    surfaceVariant = Color(0xFFF0F4F5),
    onSurfaceVariant = Color(0xFF52646A),
    outline = Color(0xFF97A6AA),
    outlineVariant = Color(0xFFD8E0E2),
)

private val LanuTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

@Composable
fun LanuGlobalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LanuColorScheme,
        typography = LanuTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
