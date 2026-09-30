package com.ugnbt.spotlabdesktop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SpotlabColorScheme = darkColorScheme(
    primary = SpotlabBrand,
    onPrimary = Color.White,
    primaryContainer = SpotlabBrandHover,
    onPrimaryContainer = SpotlabCream,
    secondary = SpotlabCream,
    onSecondary = SpotlabRust,
    secondaryContainer = SpotlabRust,
    onSecondaryContainer = SpotlabCream,
    tertiary = SpotlabRust,
    onTertiary = SpotlabCream,
    background = SpotlabBackground,
    onBackground = SpotlabForeground,
    surface = SpotlabSurface,
    onSurface = SpotlabForeground,
    surfaceVariant = SpotlabSurfaceElevated,
    onSurfaceVariant = SpotlabMuted,
    surfaceContainer = SpotlabSurface,
    surfaceContainerHigh = SpotlabSurfaceElevated,
    surfaceContainerHighest = SpotlabBorder,
    outline = SpotlabBorder,
    outlineVariant = SpotlabBorder,
    error = SpotlabDanger,
    onError = Color.White,
    scrim = Color.Black,
)

private val SpotlabShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Same palette/shapes as the Android client's `SpotlabTheme` — dark only. */
@Composable
fun SpotlabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotlabColorScheme,
        typography = Typography(),
        shapes = SpotlabShapes,
        content = content,
    )
}
