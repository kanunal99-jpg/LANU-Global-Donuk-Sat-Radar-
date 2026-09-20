package com.lanu.globaldonuksatisradari

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LanuColorScheme = lightColorScheme(
    primary = Color(0xFF0F6B73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F1F0),
    onPrimaryContainer = Color(0xFF073B40),
    secondary = Color(0xFF315D69),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8EE),
    onSecondaryContainer = Color(0xFF102F38),
    tertiary = Color(0xFFC88727),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1B3),
    onTertiaryContainer = Color(0xFF412B06),
    background = Color(0xFFF5F8F9),
    onBackground = Color(0xFF142126),
    surface = Color.White,
    onSurface = Color(0xFF142126),
    surfaceVariant = Color(0xFFE7EFF1),
    onSurfaceVariant = Color(0xFF4B5F65),
    outline = Color(0xFF8A9DA2),
)

@Composable
fun LanuGlobalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LanuColorScheme,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
