package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = SlateSecondary,
    onSecondary = EmeraldOnPrimary,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = InventoryAmber,
    onTertiary = EmeraldOnPrimary,
    tertiaryContainer = InventoryAmberBg,
    onTertiaryContainer = InventoryAmber,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = PayableRed,
    onError = EmeraldOnPrimary,
    errorContainer = PayableRedBg,
    onErrorContainer = PayableRed
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = EmeraldOnPrimaryDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = InventoryAmberDark,
    onTertiary = EmeraldOnPrimaryDark,
    tertiaryContainer = InventoryAmberBgDark,
    onTertiaryContainer = InventoryAmberDark,
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = PayableRedDark,
    onError = EmeraldOnPrimaryDark,
    errorContainer = PayableRedBgDark,
    onErrorContainer = PayableRedDark
)

val TradeLedgerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TradeLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TradeLedgerShapes,
        content = content
    )
}
