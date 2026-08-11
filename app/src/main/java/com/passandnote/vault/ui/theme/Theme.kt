package com.passandnote.vault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VaultColorScheme = darkColorScheme(
    primary = VaultAccent,
    onPrimary = VaultPrimaryText,
    secondary = VaultAccent,
    background = VaultBackground,
    onBackground = VaultPrimaryText,
    surface = VaultCard,
    onSurface = VaultPrimaryText,
    surfaceVariant = VaultElevatedCard,
    onSurfaceVariant = VaultSecondaryText,
    error = VaultDanger,
    outline = VaultBorder,
)

@Composable
fun PassAndNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography = Typography,
        content = content,
    )
}
