package com.passandnote.vault.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.passandnote.vault.VaultAppState
import com.passandnote.vault.VaultDestination
import com.passandnote.vault.backup.BackupException
import com.passandnote.vault.backup.BackupService
import com.passandnote.vault.backup.ExportContentType
import com.passandnote.vault.backup.ExportFileNames
import com.passandnote.vault.data.SecureNote
import com.passandnote.vault.data.SecureNoteTitleBuilder
import com.passandnote.vault.ui.components.AppButton
import com.passandnote.vault.ui.components.AppButtonKind
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
import com.passandnote.vault.ui.theme.VaultField
import com.passandnote.vault.ui.theme.VaultMutedText
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class NoteExportMode { ENCRYPTED, PLAIN_TEXT }

@Composable
fun SecureNotesScreen(state: VaultAppState) {
    var notes by remember { mutableStateOf(emptyList<SecureNote>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var actionNote by remember { mutableStateOf<SecureNote?>(null) }
    var renameNote by remember { mutableStateOf<SecureNote?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteNote by remember { mutableStateOf<SecureNote?>(null) }
    var exportChoiceNote by remember { mutableStateOf<SecureNote?>(null) }
    var plainTextWarning by remember { mutableStateOf<SecureNote?>(null) }
    var exportNotes by remember { mutableStateOf(emptyList<SecureNote>()) }
    var exportMode by remember { mutableStateOf(NoteExportMode.ENCRYPTED) }
    var confirmExportPassword by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var exportFilename by remember { mutableStateOf(ExportFileNames.singleNote()) }
    var importData by remember { mutableStateOf<ByteArray?>(null) }
    var confirmImportPassword by remember { mutableStateOf(false) }
    var pendingImportedNotes by remember { mutableStateOf(emptyList<SecureNote>()) }
    var showImportPreview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        runCatching { withContext(Dispatchers.IO) { state.database.fetchNotes() } }
            .onSuccess { notes = it }
            .onFailure { error = "Could not load notes." }
    }

    val encryptedSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        state.finishDocumentFlow()
        val data = pendingExport
        if (uri != null && data != null) {
            runCatching { state.context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write") }
                .onFailure { error = "The note export could not be saved." }
            data.fill(0)
            pendingExport = null
        }
    }
    val textSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        state.finishDocumentFlow()
        val data = pendingExport
        if (uri != null && data != null) {
            runCatching { state.context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to write") }
                .onFailure { error = "The note export could not be saved." }
            data.fill(0)
            pendingExport = null
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        state.finishDocumentFlow()
        if (uri != null) scope.launch {
            runCatching {
                val name = uriDisplayName(state.context, uri).lowercase(Locale.US)
                val bytes = withContext(Dispatchers.IO) { state.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read") }
                name to bytes
            }.onSuccess { (name, bytes) ->
                when {
                    name.endsWith(".pnnote") -> { importData = bytes; confirmImportPassword = true }
                    name.endsWith(".txt") -> runCatching { BackupService.plaintextImportedNote(bytes) }
                        .onSuccess { imported ->
                            val empty = withContext(Dispatchers.IO) { state.database.createEmptyNote() }
                            state.navigate(VaultDestination.NoteEditor(empty.copy(content = imported.content, title = imported.title)))
                        }.onFailure { error = (it as? BackupException)?.message ?: "Import failed. Please try again." }
                    else -> error = "This file type cannot be imported here."
                }
            }.onFailure { error = "Import failed. Please try again." }
        }
    }

    actionNote?.let { note -> NoteActionsDialog(
        note,
        onDismiss = { actionNote = null },
        onOpen = { state.navigate(VaultDestination.NoteEditor(note)); actionNote = null },
        onRename = { renameNote = note; renameText = note.title; actionNote = null },
        onExport = { exportChoiceNote = note; actionNote = null },
        onDelete = { deleteNote = note; actionNote = null },
    ) }
    renameNote?.let { note -> AlertDialog(
        onDismissRequest = { renameNote = null },
        title = { Text("Rename note") },
        text = { OutlinedTextField(renameText, { renameText = it }, placeholder = { Text("Note title") }) },
        confirmButton = { TextButton(onClick = {
            scope.launch { withContext(Dispatchers.IO) { state.database.renameNote(note.id, renameText) }; reload++ }
            renameNote = null
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renameNote = null }) { Text("Cancel") } },
    ) }
    deleteNote?.let { note -> AlertDialog(
        onDismissRequest = { deleteNote = null },
        title = { Text("Delete note?") },
        text = { Text("This will delete \"${note.title}\".") },
        confirmButton = { TextButton(onClick = {
            scope.launch { withContext(Dispatchers.IO) { state.database.deleteNote(note.id) }; reload++ }
            deleteNote = null
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteNote = null }) { Text("Cancel") } },
    ) }
    exportChoiceNote?.let { note -> AlertDialog(
        onDismissRequest = { exportChoiceNote = null },
        title = { Text("Export Note") },
        text = { Text("Choose an export format for this note.") },
        confirmButton = {
            Column {
                TextButton(onClick = {
                    exportNotes = listOf(note); exportMode = NoteExportMode.ENCRYPTED; exportFilename = ExportFileNames.singleNote()
                    exportChoiceNote = null; confirmExportPassword = true
                }) { Text("Export Encrypted (.pnnote)") }
                TextButton(onClick = { plainTextWarning = note; exportChoiceNote = null }) { Text("Export Unencrypted (.txt)") }
            }
        },
        dismissButton = { TextButton(onClick = { exportChoiceNote = null }) { Text("Cancel") } },
    ) }
    plainTextWarning?.let { note -> AlertDialog(
        onDismissRequest = { plainTextWarning = null },
        title = { Text("Unencrypted Export") },
        text = { Text("This creates a readable text file that is not protected by encryption. Anyone with access to this file can read its contents.") },
        confirmButton = { TextButton(onClick = {
            exportNotes = listOf(note); exportMode = NoteExportMode.PLAIN_TEXT; exportFilename = ExportFileNames.plainText(note)
            plainTextWarning = null; confirmExportPassword = true
        }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { plainTextWarning = null }) { Text("Cancel") } },
    ) }
    if (confirmExportPassword) MasterPasswordConfirmationDialog(
        title = "Confirm Master Password",
        message = if (exportMode == NoteExportMode.ENCRYPTED) "Enter your Master Password to export this data." else "Enter your Master Password to export this note as readable text.",
        onDismiss = { confirmExportPassword = false },
        onConfirm = { password, report -> scope.launch {
            if (!state.verifyMasterPassword(password)) report("Incorrect Master Password or export could not be prepared.")
            else runCatching { withContext(Dispatchers.Default) {
                if (exportMode == NoteExportMode.ENCRYPTED) BackupService.makeNoteExport(exportNotes, password)
                else exportNotes.first().content.toByteArray(Charsets.UTF_8)
            } }.onSuccess {
                pendingExport = it; confirmExportPassword = false
                state.launchDocumentFlow {
                    if (exportMode == NoteExportMode.ENCRYPTED) encryptedSaver.launch(exportFilename) else textSaver.launch(exportFilename)
                }
            }.onFailure { report("Incorrect Master Password or export could not be prepared.") }
        } },
    )
    if (confirmImportPassword) MasterPasswordConfirmationDialog(
        title = "Backup Password",
        message = "Enter the Master Password that was used when this backup was created.",
        passwordTitle = "Backup Password",
        placeholder = "Enter backup password",
        onDismiss = { confirmImportPassword = false },
        onConfirm = { password, report ->
            val bytes = importData ?: return@MasterPasswordConfirmationDialog report("Choose a note package first.")
            scope.launch {
                runCatching { withContext(Dispatchers.Default) { BackupService.decrypt(bytes, password, ExportContentType.NOTES) } }
                    .onSuccess { pendingImportedNotes = it.notes; confirmImportPassword = false; showImportPreview = true }
                    .onFailure { report((it as? BackupException)?.message ?: "Import failed. Please try again.") }
            }
        },
    )
    if (showImportPreview) AlertDialog(
        onDismissRequest = { showImportPreview = false },
        title = { Text("Import Notes") },
        text = { Text(if (pendingImportedNotes.size == 1) "\"${pendingImportedNotes.first().title}\"" else "${pendingImportedNotes.size} notes found. Importing the same backup more than once may create duplicate entries.") },
        confirmButton = { TextButton(onClick = {
            showImportPreview = false
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { state.database.importNotes(pendingImportedNotes) } }
                    .onSuccess {
                        state.metadata.markNotesAdded(pendingImportedNotes.size)
                        success = "${pendingImportedNotes.size} notes imported."
                        reload++
                    }.onFailure { error = "Could not import notes." }
            }
        }) { Text(if (pendingImportedNotes.size == 1) "Import Note" else "Import ${pendingImportedNotes.size} Notes") } },
        dismissButton = { TextButton(onClick = { showImportPreview = false; pendingImportedNotes = emptyList() }) { Text("Cancel") } },
    )

    VaultScaffold(
        title = if (selectionMode) "Select Notes" else "Notes",
        onBack = state::back,
        actions = {
            if (selectionMode) ToolbarTextButton("Cancel") { selectionMode = false; selected = emptySet() }
            else {
                IconButton(onClick = {
                    state.launchDocumentFlow {
                        importer.launch(arrayOf("application/octet-stream", "application/json", "text/plain", "*/*"))
                    }
                }) {
                    Icon(Icons.Rounded.Download, "Import", tint = VaultAccent)
                }
                ToolbarTextButton("Select", notes.isNotEmpty()) { selectionMode = true }
                IconButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { state.database.createEmptyNote() } }
                            .onSuccess { state.navigate(VaultDestination.NoteEditor(it)) }
                            .onFailure { error = "Could not create note." }
                    }
                }) { Icon(Icons.Rounded.Add, "Add", tint = VaultAccent) }
            }
        },
    ) {
        VaultScreenContainer {
            SectionHeader(
                if (selectionMode) "Select Notes" else "Secure Notes",
                if (selectionMode) "Choose notes to include in one encrypted export." else "Create private notes stored inside your encrypted vault.",
            )
            error?.let { InfoCard(InfoCardKind.WARNING, "Error", it) }
            success?.let { InfoCard(InfoCardKind.SUCCESS, "Import complete", it) }
            if (notes.isEmpty()) InfoCard(InfoCardKind.INFO, "No notes yet", "Tap the plus button on the top right to create your first secure note.")
            else if (selectionMode) {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                    TextButton(onClick = { selected = if (selected.size == notes.size) emptySet() else notes.mapTo(mutableSetOf()) { it.id } }) {
                        Text(if (selected.size == notes.size) "Deselect All" else "Select All", color = VaultAccent)
                    }
                    notes.forEach { note ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { SecureNoteCard(note, onClick = { selected = if (note.id in selected) selected - note.id else selected + note.id }, onLongClick = {}) }
                            Switch(note.id in selected, { checked -> selected = if (checked) selected + note.id else selected - note.id })
                        }
                    }
                }
                AppButton("Export ${selected.size}", {
                    exportNotes = notes.filter { it.id in selected }
                    exportMode = NoteExportMode.ENCRYPTED
                    exportFilename = if (selected.size == notes.size) ExportFileNames.allNotes() else ExportFileNames.selectedNotes(selected.size)
                    confirmExportPassword = true
                }, icon = Icons.Rounded.Lock, enabled = selected.isNotEmpty())
            } else Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                notes.forEach { note -> SecureNoteCard(
                    note,
                    onClick = { state.navigate(VaultDestination.NoteEditor(note)) },
                    onLongClick = { actionNote = note },
                ) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SecureNoteCard(note: SecureNote, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md))
            .border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(VaultSpacing.md),
        verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(note.title, style = MaterialTheme.typography.titleLarge, color = VaultPrimaryText, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = VaultMutedText)
        }
        Text(
            note.content.trim().ifEmpty { "Empty note" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (note.content.trim().isEmpty()) VaultMutedText else VaultSecondaryText,
            maxLines = 2,
        )
        Text(
            "Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date((note.updatedAt * 1000).toLong()))}",
            style = MaterialTheme.typography.labelMedium,
            color = VaultMutedText,
        )
    }
}

@Composable
private fun NoteActionsDialog(
    note: SecureNote,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title) },
        text = {
            Column {
                ActionRow("Open", Icons.Rounded.OpenInNew, onOpen)
                ActionRow("Rename", Icons.Rounded.Edit, onRename)
                ActionRow("Export", Icons.Rounded.Upload, onExport)
                ActionRow("Delete", Icons.Rounded.Delete, onDelete)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActionRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(vertical = VaultSpacing.sm), horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
        Icon(icon, null, tint = VaultAccent)
        Text(title, color = VaultPrimaryText)
    }
}

@Composable
fun NoteEditorScreen(state: VaultAppState, note: SecureNote) {
    var content by remember(note.id) { mutableStateOf(note.content) }

    fun commit(deleteIfEmpty: Boolean = false) {
        runCatching {
            val createdContent = state.database.updateNoteContent(note.id, content)
            if (createdContent) state.metadata.markNotesAdded()
            if (deleteIfEmpty) state.database.deleteNoteIfEmpty(note.id, content)
        }
    }

    LaunchedEffect(content) {
        delay(700)
        withContext(Dispatchers.IO) { commit() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, note.id) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) commit() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            commit(deleteIfEmpty = true)
        }
    }
    BackHandler {
        commit(deleteIfEmpty = true)
        state.back()
    }

    VaultScaffold(title = SecureNoteTitleBuilder.makeTitle(content), onBack = {
        commit(deleteIfEmpty = true)
        state.back()
    }) {
        VaultScreenContainer {
            InfoCard(InfoCardKind.INFO, "Autosave enabled", "Your note title is generated from the first line. Changes are saved automatically.")
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
                placeholder = { Text("Start writing your private note...", color = VaultMutedText) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = VaultPrimaryText),
                shape = RoundedCornerShape(VaultRadius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = VaultCard,
                    unfocusedContainerColor = VaultCard,
                    focusedBorderColor = VaultBorder,
                    unfocusedBorderColor = VaultBorder,
                    cursorColor = VaultAccent,
                ),
            )
        }
    }
}
