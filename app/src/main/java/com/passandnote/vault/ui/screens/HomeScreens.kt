package com.passandnote.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.passandnote.vault.VaultAppState
import com.passandnote.vault.VaultDestination
import com.passandnote.vault.ui.components.AppButton
import com.passandnote.vault.ui.components.AppButtonKind
import com.passandnote.vault.ui.components.CircleIcon
import com.passandnote.vault.ui.components.ClickableCard
import com.passandnote.vault.ui.components.SectionHeader
import com.passandnote.vault.ui.components.VaultScaffold
import com.passandnote.vault.ui.components.VaultScreenContainer
import com.passandnote.vault.ui.components.VaultSpacing
import com.passandnote.vault.ui.theme.VaultMutedText
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText

@Composable
fun VaultContent(state: VaultAppState) {
    when (val destination = state.currentDestination) {
        VaultDestination.Home -> VaultHomeScreen(state)
        VaultDestination.Passwords -> PasswordVaultScreen(state)
        VaultDestination.Notes -> SecureNotesScreen(state)
        VaultDestination.Settings -> SettingsScreen(state)
        VaultDestination.About -> AboutScreen(state)
        is VaultDestination.AboutDetail -> AboutDetailScreen(state, destination.page)
        is VaultDestination.NoteEditor -> NoteEditorScreen(state, destination.note)
    }
}

@Composable
private fun VaultHomeScreen(state: VaultAppState) {
    VaultScaffold(title = "Vault") {
        VaultScreenContainer {
            SectionHeader("Vault Unlocked", "Your private offline vault is ready.")
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                VaultModuleCard(
                    icon = { CircleIcon(Icons.Rounded.Key) },
                    title = "Passwords",
                    message = "Store account names and passwords securely.",
                    onClick = { state.navigate(VaultDestination.Passwords) },
                )
                VaultModuleCard(
                    icon = { CircleIcon(Icons.Rounded.Note) },
                    title = "Notes",
                    message = "Create private notes inside your encrypted vault.",
                    onClick = { state.navigate(VaultDestination.Notes) },
                )
                VaultModuleCard(
                    icon = { CircleIcon(Icons.Rounded.Settings) },
                    title = "Settings",
                    message = "Manage vault preferences, backup, and security.",
                    onClick = { state.navigate(VaultDestination.Settings) },
                )
            }
            AppButton("Lock Vault", state::lockVault, icon = Icons.Rounded.Lock, kind = AppButtonKind.SECONDARY)
        }
    }
}

@Composable
private fun VaultModuleCard(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    onClick: () -> Unit,
) {
    ClickableCard(onClick) {
        icon()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = VaultPrimaryText)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = VaultSecondaryText)
        }
        Spacer(Modifier.fillMaxWidth().weight(0.001f))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForwardIos,
            null,
            tint = VaultMutedText,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}
