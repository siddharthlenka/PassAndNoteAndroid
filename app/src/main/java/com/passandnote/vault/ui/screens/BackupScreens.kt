package com.passandnote.vault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.passandnote.vault.VaultAppState
import com.passandnote.vault.backup.BackupException
import com.passandnote.vault.backup.BackupService
import com.passandnote.vault.backup.DecryptedBackup
import com.passandnote.vault.backup.ExportContentType
import com.passandnote.vault.backup.ExportFileNames
import com.passandnote.vault.data.PasswordValidator
import com.passandnote.vault.ui.components.AppButton
import com.passandnote.vault.ui.components.AppButtonKind
import com.passandnote.vault.ui.components.AppSecureField
import com.passandnote.vault.ui.components.FullScreenDialog
import com.passandnote.vault.ui.components.InfoCard
import com.passandnote.vault.ui.components.InfoCardKind
import com.passandnote.vault.ui.components.SectionHeader
import com.passandnote.vault.ui.components.VaultRadius
import com.passandnote.vault.ui.components.VaultScreenContainer
import com.passandnote.vault.ui.components.VaultSpacing
import com.passandnote.vault.ui.theme.VaultCard
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FullVaultRestoreDialog(
    state: VaultAppState,
    resetUnusedVaultBeforeRestore: Boolean,
    onDismiss: () -> Unit,
) {
    var encryptedData by remember { mutableStateOf<ByteArray?>(null) }
    var backup by remember { mutableStateOf<DecryptedBackup?>(null) }
    var confirmBackupPassword by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        state.finishDocumentFlow()
        if (uri != null) scope.launch {
            runCatching {
                if (!uriDisplayName(state.context, uri).lowercase(Locale.US).endsWith(".pnvault")) throw BackupException("This file type cannot be imported here.")
                withContext(Dispatchers.IO) { state.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read") }
            }.onSuccess { encryptedData = it; confirmBackupPassword = true; error = null }
                .onFailure { error = (it as? BackupException)?.message ?: "Import failed. Please try again." }
        }
    }
    if (confirmBackupPassword) MasterPasswordConfirmationDialog(
        title = "Backup Password",
        message = "Enter the Master Password that was used when this backup was created.",
        passwordTitle = "Backup Password",
        placeholder = "Enter backup password",
        onDismiss = { confirmBackupPassword = false },
        onConfirm = { password, report ->
            val bytes = encryptedData ?: return@MasterPasswordConfirmationDialog report("Choose a vault backup first.")
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { BackupService.decrypt(bytes, password, ExportContentType.FULL_VAULT) } }
                    .onSuccess { backup = it; confirmBackupPassword = false; error = null }
                    .onFailure { report((it as? BackupException)?.message ?: "Import failed. Please try again.") }
            }
        },
    )

    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Restore From Backup", "Restore a full encrypted Pass&Note Vault backup into a new local vault.", large = false)
            val currentBackup = backup
            if (currentBackup == null) {
                InfoCard(
                    InfoCardKind.INFO,
                    "Full backups only",
                    "Choose a .pnvault file. The Backup Password decrypts the file; your new Master Password protects this device's new vault.",
                )
                AppButton(
                    "Choose .pnvault File",
                    { state.launchDocumentFlow { picker.launch(arrayOf("application/octet-stream", "application/json", "*/*")) } },
                    icon = Icons.Rounded.FileDownload,
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md)).padding(VaultSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
                ) {
                    Text("Restore Pass&Note Vault Backup", style = MaterialTheme.typography.titleLarge, color = VaultPrimaryText)
                    SummaryRow("Passwords", currentBackup.passwords.size.toString())
                    SummaryRow("Secure Notes", currentBackup.notes.size.toString())
                    SummaryRow("Backup created", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date((currentBackup.container.createdAt * 1000).toLong())))
                }
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                    Text("Create New Local Master Password", style = MaterialTheme.typography.titleLarge, color = VaultPrimaryText)
                    AppSecureField("New Master Password", "Enter new master password", masterPassword, { masterPassword = it; error = null })
                    AppSecureField(
                        "Confirm New Master Password",
                        "Re-enter new master password",
                        confirmPassword,
                        { confirmPassword = it; error = null },
                        error = if (confirmPassword.isNotEmpty() && confirmPassword != masterPassword) "Passwords do not match." else null,
                    )
                    InfoCard(InfoCardKind.INFO, "New local vault key", "This device will generate a new random vault key. The Backup Password is not reused as your local credential unless you choose the same password.")
                }
            }
            error?.let { InfoCard(InfoCardKind.WARNING, "Restore failed", it) }
            if (currentBackup != null) AppButton(
                "Restore Vault",
                onClick = {
                    restoring = true
                    scope.launch {
                        var created = false
                        runCatching {
                            withContext(Dispatchers.IO) {
                                if (resetUnusedVaultBeforeRestore) state.database.resetUnusedVault()
                                state.database.create(masterPassword)
                                created = true
                                state.database.importFullVault(currentBackup.passwords, currentBackup.notes)
                            }
                        }.onSuccess {
                            restoring = false
                            state.completeRestore()
                            masterPassword = ""; confirmPassword = ""
                            onDismiss()
                        }.onFailure { failure ->
                            if (created && state.metadata.canResetUnusedVault) runCatching { withContext(Dispatchers.IO) { state.database.resetUnusedVault() } }
                            restoring = false
                            error = (failure as? BackupException)?.message ?: "Import failed. Please try again."
                        }
                    }
                },
                icon = Icons.Rounded.Restore,
                enabled = PasswordValidator.validate(masterPassword).isStrong && masterPassword == confirmPassword,
                loading = restoring,
            )
            AppButton("Cancel", onDismiss, icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
        }
    }
}

@Composable
fun VaultImportDialog(state: VaultAppState, onDismiss: () -> Unit, onImported: () -> Unit) {
    var encryptedData by remember { mutableStateOf<ByteArray?>(null) }
    var backup by remember { mutableStateOf<DecryptedBackup?>(null) }
    var confirmPassword by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        state.finishDocumentFlow()
        if (uri != null) scope.launch {
            runCatching {
                if (!uriDisplayName(state.context, uri).lowercase(Locale.US).endsWith(".pnvault")) throw BackupException("This file type cannot be imported here.")
                withContext(Dispatchers.IO) { state.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read") }
            }.onSuccess { encryptedData = it; confirmPassword = true }
                .onFailure { error = (it as? BackupException)?.message ?: "Import failed. Please try again." }
        }
    }
    if (confirmPassword) MasterPasswordConfirmationDialog(
        title = "Backup Password",
        message = "Enter the Master Password that was used when this backup was created.",
        passwordTitle = "Backup Password",
        placeholder = "Enter backup password",
        onDismiss = { confirmPassword = false },
        onConfirm = { password, report ->
            val bytes = encryptedData ?: return@MasterPasswordConfirmationDialog report("Choose a vault backup first.")
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { BackupService.decrypt(bytes, password, ExportContentType.FULL_VAULT) } }
                    .onSuccess { backup = it; confirmPassword = false; showPreview = true }
                    .onFailure { report((it as? BackupException)?.message ?: "Import failed. Please try again.") }
            }
        },
    )
    if (showPreview) AlertDialog(
        onDismissRequest = { showPreview = false },
        title = { Text("Import Vault Backup") },
        text = { Text("This backup contains:\n\n${backup?.passwords?.size ?: 0} Passwords\n${backup?.notes?.size ?: 0} Secure Notes\n\nThese items will be added to your current vault. Existing items will not be deleted.") },
        confirmButton = { TextButton(onClick = {
            val current = backup ?: return@TextButton
            showPreview = false
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { state.database.importFullVault(current.passwords, current.notes) } }
                    .onSuccess {
                        state.metadata.markPasswordsAdded(current.passwords.size)
                        state.metadata.markNotesAdded(current.notes.size)
                        onImported(); onDismiss()
                    }.onFailure { error = "Import failed. Please try again." }
            }
        }) { Text("Import") } },
        dismissButton = { TextButton(onClick = { showPreview = false }) { Text("Cancel") } },
    )
    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Import Vault", "Merge an encrypted full backup into your current vault.", large = false)
            InfoCard(InfoCardKind.WARNING, "Adds to current vault", "Imported passwords and notes will be added as new records. Existing items will not be deleted.")
            error?.let { InfoCard(InfoCardKind.WARNING, "Import failed", it) }
            AppButton(
                "Choose .pnvault File",
                { state.launchDocumentFlow { picker.launch(arrayOf("application/octet-stream", "application/json", "*/*")) } },
                icon = Icons.Rounded.FileDownload,
            )
            AppButton("Cancel", onDismiss, icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
        }
    }
}

@Composable
fun VaultExportDialog(state: VaultAppState, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    var passwords by remember { mutableStateOf(state.database.fetchPasswords()) }
    var notes by remember { mutableStateOf(state.database.fetchNotes()) }
    var confirmPassword by remember { mutableStateOf(false) }
    var pendingData by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        state.finishDocumentFlow()
        val data = pendingData
        if (uri != null && data != null) {
            runCatching { state.context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write") }
                .onSuccess {
                    data.fill(0); pendingData = null
                    state.metadata.markFullBackupCompleted()
                    onCompleted(); onDismiss()
                }.onFailure { error = "The vault export could not be saved." }
        }
    }
    if (confirmPassword) MasterPasswordConfirmationDialog(
        title = "Confirm Master Password",
        message = "Enter your Master Password to export this data.",
        onDismiss = { confirmPassword = false },
        onConfirm = { password, report -> scope.launch {
            if (!state.verifyMasterPassword(password)) report("Incorrect Master Password or export could not be prepared.")
            else runCatching { withContext(Dispatchers.Default) { BackupService.makeFullVaultExport(passwords, notes, password) } }
                .onSuccess {
                    pendingData = it
                    confirmPassword = false
                    state.launchDocumentFlow { saver.launch(ExportFileNames.fullVault()) }
                }
                .onFailure { report("Incorrect Master Password or export could not be prepared.") }
        } },
    )
    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Export Vault", "Create one encrypted backup of all passwords and secure notes.", large = false)
            Column(Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md)).padding(VaultSpacing.md), verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                SummaryRow("Passwords", passwords.size.toString())
                SummaryRow("Secure Notes", notes.size.toString())
            }
            InfoCard(InfoCardKind.WARNING, "Master password required", "This backup will require the Master Password used when it was created to restore later.")
            error?.let { InfoCard(InfoCardKind.WARNING, "Export failed", it) }
            AppButton("Continue", { confirmPassword = true }, icon = Icons.AutoMirrored.Rounded.ArrowForward, enabled = passwords.isNotEmpty() || notes.isNotEmpty())
            AppButton("Cancel", onDismiss, icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
        }
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = VaultSecondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VaultPrimaryText, fontWeight = FontWeight.SemiBold)
    }
}
