package com.wanderwk.d3saveeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is always dark themed (Diablo III mood), matching the design handoff
// regardless of the system theme setting.
private val D3DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    onPrimary = BgBase,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = PrimaryAccent,
    secondary = SecondaryGold,
    onSecondary = BgBase,
    background = BgBase,
    onBackground = TextPrimary,
    surface = SurfaceContainer,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainerHigh1,
    onSurfaceVariant = TextMuted,
    outline = DividerOutline,
    error = ErrorOrange,
    onError = BgBase,
)

@Composable
fun D3SaveEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = D3DarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
