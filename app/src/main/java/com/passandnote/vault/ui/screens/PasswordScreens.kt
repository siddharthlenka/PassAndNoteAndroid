package com.passandnote.vault.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passandnote.vault.VaultAppState
import com.passandnote.vault.backup.BackupException
import com.passandnote.vault.backup.BackupService
import com.passandnote.vault.backup.ExportContentType
import com.passandnote.vault.backup.ExportFileNames
import com.passandnote.vault.data.GeneratedPasswordLength
import com.passandnote.vault.data.PasswordEntry
import com.passandnote.vault.data.SecurePasswordGenerator
import com.passandnote.vault.ui.components.AppButton
import com.passandnote.vault.ui.components.AppButtonKind
import com.passandnote.vault.ui.components.AppSecureField
import com.passandnote.vault.ui.components.AppTextField
import com.passandnote.vault.ui.components.FullScreenDialog
import com.passandnote.vault.ui.components.InfoCard
import com.passandnote.vault.ui.components.InfoCardKind
import com.passandnote.vault.ui.components.SectionHeader
import com.passandnote.vault.ui.components.ToolbarTextButton
import com.passandnote.vault.ui.components.VaultRadius
import com.passandnote.vault.ui.components.VaultScaffold
import com.passandnote.vault.ui.components.VaultScreenContainer
import com.passandnote.vault.ui.components.VaultSpacing
import com.passandnote.vault.ui.theme.VaultAccent
import com.passandnote.vault.ui.theme.VaultBorder
import com.passandnote.vault.ui.theme.VaultCard
import com.passandnote.vault.ui.theme.VaultElevatedCard
import com.passandnote.vault.ui.theme.VaultMutedText
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText
import com.passandnote.vault.ui.theme.VaultSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PasswordVaultScreen(state: VaultAppState) {
    var entries by remember { mutableStateOf(emptyList<PasswordEntry>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var showDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        runCatching { withContext(Dispatchers.IO) { state.database.fetchPasswords() } }
            .onSuccess { entries = it; if (reload == 0) success = null }
            .onFailure { error = "Could not load saved passwords." }
    }

    if (showAdd) AddPasswordDialog(
        state = state,
        onDismiss = { showAdd = false },
        onSave = { account, username, password ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { state.database.insertPassword(account, username, password) } }
                    .onSuccess { state.metadata.markPasswordsAdded(); error = null; reload++ }
                    .onFailure { error = "Could not save password." }
            }
        },
    )
    if (showExport) PasswordExportDialog(state, entries, onDismiss = { showExport = false })
    if (showImport) PasswordImportDialog(state, onDismiss = { showImport = false }) { count ->
        success = "$count passwords imported."
        error = null
        reload++
    }
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Delete selected passwords?") },
        text = { Text("This will delete ${selected.size} selected password ${if (selected.size == 1) "record" else "records"}.") },
        confirmButton = { TextButton(onClick = {
            showDelete = false
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { state.database.deletePasswords(selected) } }
                    .onSuccess { selected = emptySet(); selecting = false; reload++ }
                    .onFailure { error = "Could not delete selected passwords." }
            }
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
    )

    VaultScaffold(
        title = "Passwords",
        onBack = state::back,
        actions = {
            if (entries.isNotEmpty()) ToolbarTextButton(if (selecting) "Cancel" else "Select") {
                selecting = !selecting
                if (!selecting) selected = emptySet()
            }
            if (selecting) {
                IconButton(onClick = { showDelete = true }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Rounded.Delete, "Delete", tint = if (selected.isNotEmpty()) VaultAccent else VaultMutedText)
                }
            } else {
                IconButton(onClick = { showImport = true }) { Icon(Icons.Rounded.Download, "Import", tint = VaultAccent) }
                IconButton(onClick = { showExport = true }, enabled = entries.isNotEmpty()) {
                    Icon(Icons.Rounded.Upload, "Export", tint = if (entries.isNotEmpty()) VaultAccent else VaultMutedText)
                }
                IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, "Add", tint = VaultAccent) }
            }
        },
    ) {
        VaultScreenContainer {
            SectionHeader("Password Vault", "Save account names and passwords locally inside your encrypted vault.")
            error?.let { InfoCard(InfoCardKind.WARNING, "Error", it) }
            success?.let { InfoCard(InfoCardKind.SUCCESS, "Import complete", it) }
            if (entries.isEmpty()) {
                InfoCard(InfoCardKind.INFO, "No passwords yet", "Tap the plus button on the top right to add your first password.")
            } else PasswordTable(entries, selecting, selected, { id ->
                selected = if (id in selected) selected - id else selected + id
            }, state)
        }
    }
}

@Composable
private fun PasswordTable(
    entries: List<PasswordEntry>,
    selecting: Boolean,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    state: VaultAppState,
) {
    var visible by remember { mutableStateOf(emptySet<String>()) }
    var copiedID by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md))
            .border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md)),
    ) {
        Row(
            Modifier.fillMaxWidth().background(VaultElevatedCard).padding(horizontal = VaultSpacing.md, vertical = VaultSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            if (selecting) Box(Modifier.width(28.dp))
            Text("Account", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText, modifier = Modifier.weight(1f))
            Text("Password", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText, modifier = Modifier.weight(1f))
            Box(Modifier.width(88.dp))
        }
        entries.forEachIndexed { index, entry ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = selecting) { onSelect(entry.id) }
                    .padding(horizontal = VaultSpacing.md, vertical = VaultSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) IconButton(onClick = { onSelect(entry.id) }, Modifier.width(28.dp).height(44.dp)) {
                    Icon(if (entry.id in selected) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircle, null,
                        tint = if (entry.id in selected) VaultAccent else VaultMutedText)
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.accountName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText)
                    entry.username?.takeIf(String::isNotEmpty)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
                    }
                }
                Text(
                    if (entry.id in visible) entry.password else "•".repeat(entry.password.length.coerceIn(8, 16)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = VaultSecondaryText,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Row(Modifier.width(88.dp)) {
                    IconButton(onClick = { visible = if (entry.id in visible) visible - entry.id else visible + entry.id }) {
                        Icon(if (entry.id in visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show", tint = VaultAccent)
                    }
                    IconButton(onClick = {
                        state.clipboard.copyPassword(entry.password)
                        copiedID = entry.id
                        scope.launch { delay(1_200); if (copiedID == entry.id) copiedID = null }
                    }) {
                        Icon(if (copiedID == entry.id) Icons.Rounded.Check else Icons.Rounded.ContentCopy, "Copy", tint = if (copiedID == entry.id) VaultSuccess else VaultAccent)
                    }
                }
            }
            if (index != entries.lastIndex) HorizontalDivider(color = VaultBorder)
        }
    }
}

@Composable
private fun AddPasswordDialog(
    state: VaultAppState,
    onDismiss: () -> Unit,
    onSave: (String, String?, String) -> Unit,
) {
    var account by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var length by remember { mutableStateOf(GeneratedPasswordLength.THIRTY_TWO) }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Add Password", "Create a new password record for your vault.", large = false)
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                AppTextField("Account name", "Example: Gmail, Instagram, GitHub", account, { account = it })
                AppTextField("Username (optional)", "Email, handle, or login ID", username, { username = it })
                AppSecureField("Password", "Enter or generate password", password, { password = it; copied = false })
            }
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                Text("Generate strong password", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    GeneratedPasswordLength.entries.forEach { option ->
                        Box(
                            Modifier.weight(1f).height(40.dp)
                                .background(if (length == option) VaultAccent else VaultCard, RoundedCornerShape(8.dp))
                                .clickable { length = option },
                            contentAlignment = Alignment.Center,
                        ) { Text(option.length.toString(), color = VaultPrimaryText, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    AppButton(
                        "Generate Password",
                        { password = SecurePasswordGenerator.generate(length); copied = false },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.AutoFixHigh,
                        kind = AppButtonKind.SECONDARY,
                    )
                    IconButton(
                        onClick = {
                            if (password.isNotEmpty()) {
                                state.clipboard.copyPassword(password)
                                copied = true
                                scope.launch { delay(1_200); copied = false }
                            }
                        },
                        modifier = Modifier.size(54.dp).background(VaultCard, RoundedCornerShape(VaultRadius.md))
                            .border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md)).alpha(if (password.isEmpty()) 0.45f else 1f),
                        enabled = password.isNotEmpty(),
                    ) { Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, "Copy", tint = if (copied) VaultSuccess else VaultAccent) }
                }
                InfoCard(
                    InfoCardKind.INFO,
                    "Strong password",
                    "Generated passwords include uppercase letters, lowercase letters, numbers, and special characters. Use 32 as the recommended default. Use 64 only when the website accepts long passwords.",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                AppButton("Save", {
                    val cleanAccount = account.trim()
                    val cleanUsername = username.trim().ifEmpty { null }
                    onSave(cleanAccount, cleanUsername, password)
                    onDismiss()
                }, icon = Icons.Rounded.Save, enabled = account.trim().isNotEmpty() && password.isNotEmpty())
                AppButton("Cancel", onDismiss, icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
            }
        }
    }
}

@Composable
private fun PasswordExportDialog(state: VaultAppState, entries: List<PasswordEntry>, onDismiss: () -> Unit) {
    var selectedMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmPassword by remember { mutableStateOf(false) }
    var pendingData by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exportEntries = if (selectedMode) entries.filter { it.id in selected } else entries
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        state.finishDocumentFlow()
        val data = pendingData
        if (uri != null && data != null) {
            runCatching { state.context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write") }
                .onSuccess { data.fill(0); pendingData = null; onDismiss() }
                .onFailure { error = "The password export could not be saved." }
        }
    }
    if (confirmPassword) MasterPasswordConfirmationDialog(
        title = "Confirm Master Password",
        message = "Enter your Master Password to export this data.",
        onDismiss = { confirmPassword = false },
        onConfirm = { password, report ->
            scope.launch {
                if (!state.verifyMasterPassword(password)) report("Incorrect Master Password or export could not be prepared.")
                else runCatching { withContext(Dispatchers.Default) { BackupService.makePasswordExport(exportEntries, password) } }
                    .onSuccess {
                        pendingData = it
                        confirmPassword = false
                        state.launchDocumentFlow { createDocument.launch(ExportFileNames.passwords()) }
                    }
                    .onFailure { report("Incorrect Master Password or export could not be prepared.") }
            }
        },
    )
    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Export Passwords", "Create one encrypted password package for the selected records.", large = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(false to "Export All", true to "Choose Passwords").forEach { (mode, title) ->
                    Box(Modifier.weight(1f).height(40.dp).background(if (selectedMode == mode) VaultAccent else VaultCard, RoundedCornerShape(8.dp))
                        .clickable { selectedMode = mode }, contentAlignment = Alignment.Center) {
                        Text(title, color = VaultPrimaryText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            InfoCard(InfoCardKind.WARNING, "Encrypted export only", "Passwords can be exported only as an encrypted backup. Your master password is required before export.")
            error?.let { InfoCard(InfoCardKind.WARNING, "Export failed", it) }
            if (selectedMode) {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                    TextButton(onClick = { selected = if (selected.size == entries.size) emptySet() else entries.mapTo(mutableSetOf()) { it.id } }) {
                        Text(if (selected.size == entries.size) "Deselect All" else "Select All", color = VaultAccent)
                    }
                    Column(Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md)).border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md))) {
                        entries.forEachIndexed { index, entry ->
                            Row(Modifier.fillMaxWidth().padding(VaultSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.accountName, color = VaultPrimaryText, fontWeight = FontWeight.SemiBold)
                                    Text(entry.username ?: "Password will be included in encrypted export", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
                                }
                                Switch(entry.id in selected, { checked -> selected = if (checked) selected + entry.id else selected - entry.id })
                            }
                            if (index != entries.lastIndex) HorizontalDivider(color = VaultBorder)
                        }
                    }
                }
            }
            InfoCard(InfoCardKind.INFO, "Export preview", "${exportEntries.size} password item(s) selected for encrypted export.")
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                AppButton("Export ${exportEntries.size}", { confirmPassword = true }, icon = Icons.Rounded.Lock, enabled = exportEntries.isNotEmpty())
                AppButton("Cancel", onDismiss, icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
            }
        }
    }
}

@Composable
private fun PasswordImportDialog(state: VaultAppState, onDismiss: () -> Unit, onImported: (Int) -> Unit) {
    var data by remember { mutableStateOf<ByteArray?>(null) }
    var confirmPassword by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(emptyList<PasswordEntry>()) }
    var showPreview by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        state.finishDocumentFlow()
        if (uri != null) scope.launch {
            runCatching {
                val name = uriDisplayName(state.context, uri)
                if (!name.lowercase(Locale.US).endsWith(".pnpass")) throw BackupException("This file type cannot be imported here.")
                withContext(Dispatchers.IO) { state.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read") }
            }.onSuccess { data = it; confirmPassword = true; error = null }
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
            val bytes = data ?: return@MasterPasswordConfirmationDialog report("Choose a password package first.")
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { BackupService.decrypt(bytes, password, ExportContentType.PASSWORDS) } }
                    .onSuccess { pending = it.passwords; confirmPassword = false; showPreview = true }
                    .onFailure { report((it as? BackupException)?.message ?: "Import failed. Please try again.") }
            }
        },
    )
    if (showPreview) AlertDialog(
        onDismissRequest = { showPreview = false },
        title = { Text("Import Passwords") },
        text = { Text("${pending.size} passwords found. Importing the same backup more than once may create duplicate entries.") },
        confirmButton = { TextButton(onClick = {
            showPreview = false
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { state.database.importPasswords(pending) } }
                    .onSuccess { state.metadata.markPasswordsAdded(pending.size); onImported(pending.size); onDismiss() }
                    .onFailure { error = "Could not import passwords." }
            }
        }) { Text("Import ${pending.size} Passwords") } },
        dismissButton = { TextButton(onClick = { showPreview = false; pending = emptyList(); data = null }) { Text("Cancel") } },
    )
    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Import Passwords", "Import an encrypted Pass&Note Vault password package.", large = false)
            InfoCard(InfoCardKind.INFO, "Backup password required", "Enter the Master Password that was used when this backup was created. Imported passwords are added as new records.")
            error?.let { InfoCard(InfoCardKind.WARNING, "Import failed", it) }
            AppButton(
                "Choose .pnpass File",
                { state.launchDocumentFlow { picker.launch(arrayOf("application/octet-stream", "application/json", "*/*")) } },
                icon = Icons.Rounded.FileDownload,
            )
            AppButton("Cancel", onDismiss, icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
        }
    }
}

@Composable
fun MasterPasswordConfirmationDialog(
    title: String,
    message: String,
    passwordTitle: String = "Master Password",
    placeholder: String = "Enter master password",
    onDismiss: () -> Unit,
    onConfirm: (String, (String) -> Unit) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    val reportFailure: (String) -> Unit = { message ->
        error = message
        password = ""
        isBusy = false
    }
    FullScreenDialog(onDismiss = { if (!isBusy) onDismiss() }) {
        VaultScreenContainer {
            SectionHeader(title, message, large = false)
            AppSecureField(passwordTitle, placeholder, password, { password = it; error = null }, error = error)
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                AppButton(
                    "Cancel",
                    onDismiss,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Close,
                    kind = AppButtonKind.SECONDARY,
                    enabled = !isBusy,
                )
                AppButton(
                    "Continue",
                    {
                        if (!isBusy) {
                            isBusy = true
                            error = null
                            onConfirm(password, reportFailure)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Check,
                    enabled = password.isNotEmpty(),
                    loading = isBusy,
                )
            }
        }
    }
}

internal fun uriDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0) ?: uri.lastPathSegment.orEmpty()
    }
    return uri.lastPathSegment.orEmpty()
}
