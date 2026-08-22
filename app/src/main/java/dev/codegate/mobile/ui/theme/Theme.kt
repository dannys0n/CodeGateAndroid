package dev.codegate.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CodeGateColorScheme = darkColorScheme(
    primary = CodeGatePrimary,
    onPrimary = CodeGateBackground,
    secondary = CodeGateHighlight,
    onSecondary = CodeGateBackground,
    background = CodeGateBackground,
    onBackground = CodeGateText,
    surface = CodeGateSurface,
    onSurface = CodeGateText,
    surfaceVariant = CodeGateSurfaceVariant,
    onSurfaceVariant = CodeGateTextSecondary,
    outline = CodeGateBorder,
    outlineVariant = CodeGateBorder,
    error = CodeGateError
)

@Composable
fun CodeGateAndroidTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CodeGateColorScheme,
        typography = CodeGateTypography,
        content = content
    )
}
