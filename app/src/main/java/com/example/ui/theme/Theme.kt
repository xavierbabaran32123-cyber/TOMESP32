package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MaskControllerColorScheme = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = DarkPlumOnAccent,
    primaryContainer = ControllerPanelCenter,
    onPrimaryContainer = LavenderInk,
    secondary = MintConnected,
    onSecondary = DarkEmeraldOnMint,
    secondaryContainer = ControllerPanel,
    onSecondaryContainer = LavenderInk,
    tertiary = StickKnobBorder,
    onTertiary = CosmosBg,
    background = CosmosBg,
    onBackground = LavenderInk,
    surface = ControllerPanel,
    onSurface = LavenderInk,
    surfaceVariant = ControllerPanelCenter,
    onSurfaceVariant = LavenderMuted,
    outline = ControllerBorder,
    outlineVariant = StickKnobBorder,
    error = CoralDisconnected,
    onError = CosmosBg,
)

@Composable
fun MaskControllerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MaskControllerColorScheme,
        typography = Typography,
        content = content,
    )
}
