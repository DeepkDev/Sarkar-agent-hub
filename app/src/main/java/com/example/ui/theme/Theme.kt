package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF001F28),
    primaryContainer = CyanDark,
    onPrimaryContainer = Color(0xFFE0F7FA),
    secondary = ElectricIndigo,
    onSecondary = Color(0xFF1E0842),
    secondaryContainer = IndigoDark,
    onSecondaryContainer = Color(0xFFEDE7F6),
    tertiary = NeonEmerald,
    onTertiary = Color(0xFF002213),
    background = VoidDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = CrimsonRuby,
    onError = Color.White
)

@Composable
fun AgentHubTheme(
    darkTheme: Boolean = true, // Force modern dark AI infrastructure theme by default
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
