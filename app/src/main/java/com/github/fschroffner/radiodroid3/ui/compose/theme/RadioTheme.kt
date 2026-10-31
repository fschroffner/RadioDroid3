package com.github.fschroffner.radiodroid3.ui.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RadioBlue = Color(0xFF125688)
val RadioAccent = Color(0xFF1D6293)
val RadioHighlight = Color(0xFFE0347F)

val DarkSurface = Color(0xFF232325)
val DarkBackground = Color(0xFF1A1A1B)

val LightColorScheme = lightColorScheme(
    primary = RadioBlue,
    secondary = RadioAccent,
    tertiary = RadioHighlight,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F9FA),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

val DarkColorScheme = darkColorScheme(
    primary = RadioAccent,
    secondary = RadioBlue,
    tertiary = RadioHighlight,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED)
)

@Composable
fun RadioDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
