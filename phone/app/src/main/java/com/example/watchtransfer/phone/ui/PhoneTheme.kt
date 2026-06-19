package com.example.watchtransfer.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScreenshotLightBlueScheme = lightColorScheme(
    primary = Color(0xFF3F5F9F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF253B66),
    secondary = Color(0xFF505B84),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F0FF),
    onSecondaryContainer = Color(0xFF293250),
    tertiary = Color(0xFF3F5F9F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEBFF),
    onTertiaryContainer = Color(0xFF253B66),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1D27),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1D27),
    surfaceVariant = Color(0xFFDCEBFF),
    onSurfaceVariant = Color(0xFF4E5875),
    outline = Color(0xFFAEB9D2),
    outlineVariant = Color(0xFFC9D4EA),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun WatchTransferPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScreenshotLightBlueScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

