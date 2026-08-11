package com.passandnote.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.passandnote.vault.VaultAppState
import com.passandnote.vault.VaultDestination
import com.passandnote.vault.data.AutoLockDuration
import com.passandnote.vault.data.ClipboardClearDuration
import com.passandnote.vault.data.PasswordValidator
import com.passandnote.vault.ui.components.AppButton
import com.passandnote.vault.ui.components.AppButtonKind
import com.passandnote.vault.ui.components.AppSecureField
import com.passandnote.vault.ui.components.FullScreenDialog
import com.passandnote.vault.ui.components.InfoCard
import com.passandnote.vault.ui.components.InfoCardKind
import com.passandnote.vault.ui.components.PasswordChecklist
import com.passandnote.vault.ui.components.SectionHeader
import com.passandnote.vault.ui.components.VaultRadius
import com.passandnote.vault.ui.components.VaultScaffold
import com.passandnote.vault.ui.components.VaultScreenContainer
import com.passandnote.vault.ui.components.VaultSpacing
import com.passandnote.vault.ui.theme.VaultAccent
import com.passandnote.vault.ui.theme.VaultBorder
import com.passandnote.vault.ui.theme.VaultCard
import com.passandnote.vault.ui.theme.VaultMutedText
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(state: VaultAppState) {
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(state.biometricService.isEnabled) }
    var biometricAction by remember { mutableStateOf<Boolean?>(null) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showClipboard by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    revision // causes settings metadata values to refresh after a completed action

    biometricAction?.let { enabling -> MasterPasswordConfirmationDialog(
        title = if (enabling) "Enable Quick Unlock" else "Disable Quick Unlock",
        message = "Enter your master password to confirm this security change.",
        onDismiss = { biometricAction = null },
        onConfirm = { password, report -> scope.launch {
            if (enabling) {
                val keyResult = state.verifiedVaultKey(password)
                if (keyResult.isFailure) report("The master password could not be verified.")
                else state.enableBiometric(keyResult.getOrThrow()) { message ->
                    biometricEnabled = state.biometricService.isEnabled
                    if (message == null && biometricEnabled) {
                        status = "Biometrics Quick Unlock is enabled."
                        error = null
                        biometricAction = null
                    } else report(message ?: "Biometric Quick Unlock could not be enabled.")
                }
            } else {
                val message = state.disableBiometric(password)
                biometricEnabled = state.biometricService.isEnabled
                if (message == null) {
                    status = "Biometrics Quick Unlock is disabled."
                    error = null
                    biometricAction = null
                } else report(message)
            }
        } },
    ) }
    if (showAutoLock) ChoiceDialog(
        title = "Auto-Lock",
        choices = AutoLockDuration.entries.map { it.title },
        onDismiss = { showAutoLock = false },
        onChoice = { index -> state.metadata.autoLockDuration = AutoLockDuration.entries[index]; showAutoLock = false; revision++ },
    )
    if (showClipboard) ChoiceDialog(
        title = "Clear Copied Passwords",
        choices = ClipboardClearDuration.entries.map { it.title },
        onDismiss = { showClipboard = false },
        onChoice = { index -> state.metadata.clipboardClearDuration = ClipboardClearDuration.entries[index]; showClipboard = false; revision++ },
    )
    if (showChangePassword) ChangeMasterPasswordDialog(state, onDismiss = { showChangePassword = false }) {
        status = "Master Password changed. Create a new full backup when possible."
        error = null
        showChangePassword = false
        revision++
    }
    if (showImport) VaultImportDialog(state, onDismiss = { showImport = false }) {
        status = "Vault data imported successfully."
        revision++
    }
    if (showExport) VaultExportDialog(state, onDismiss = { showExport = false }) {
        status = "Full encrypted vault backup completed."
        revision++
    }

    VaultScaffold(title = "Settings", onBack = state::back) {
        VaultScreenContainer {
            SectionHeader("Settings", "Manage vault security, privacy, backup, and app information.")
            status?.let { InfoCard(InfoCardKind.SUCCESS, "Settings updated", it) }
            error?.let { InfoCard(InfoCardKind.WARNING, "Settings error", it) }
            backupWarning(state)?.let { InfoCard(InfoCardKind.WARNING, "Backup needed", it) }

            SettingsGroup("SECURITY") {
                Row(Modifier.fillMaxWidth().padding(VaultSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(if (biometricEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Security)
                    Column(Modifier.weight(1f)) {
                        Text("Biometrics Quick Unlock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText)
                        Text(if (biometricEnabled) "Enabled" else "Off", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
                    }
                    Switch(biometricEnabled, { biometricAction = it })
                }
                HorizontalDivider(color = VaultBorder)
                SettingsValueRow("Auto-Lock", state.metadata.autoLockDuration.title, Icons.Rounded.Timer) { showAutoLock = true }
                HorizontalDivider(color = VaultBorder)
                SettingsNavigationRow("Change Master Password", Icons.Rounded.Key) { showChangePassword = true }
            }
            SettingsGroup("PRIVACY") {
                SettingsValueRow("Clear Copied Passwords", state.metadata.clipboardClearDuration.title, Icons.Rounded.ContentPaste) { showClipboard = true }
            }
            SettingsGroup("DATA & BACKUP") {
                SettingsNavigationRow("Import Vault", Icons.Rounded.Download) { showImport = true }
                HorizontalDivider(color = VaultBorder)
                SettingsNavigationRow("Export Vault", Icons.Rounded.Upload) { showExport = true }
                HorizontalDivider(color = VaultBorder)
                SettingsValueRow("Backup Status", state.metadata.backupStatus.title, Icons.Rounded.Security)
                HorizontalDivider(color = VaultBorder)
                SettingsValueRow(
                    "Last Full Backup",
                    if (state.metadata.lastFullBackupMillis == 0L) "Never" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.metadata.lastFullBackupMillis)),
                    Icons.Rounded.LockClock,
                )
            }
            SettingsGroup("ABOUT") {
                SettingsNavigationRow("About Pass&Note Vault", Icons.Rounded.Info) { state.navigate(VaultDestination.About) }
            }
        }
    }
}

private fun backupWarning(state: VaultAppState): String? {
    val metadata = state.metadata
    val never = metadata.lastFullBackupMillis == 0L
    if (!never && !metadata.backupUpdateRequired && metadata.unbackedPasswordCount == 0 && metadata.unbackedNoteCount == 0 && !metadata.masterPasswordChangedSinceBackup) return null
    val messages = mutableListOf<String>()
    if (never) messages += "You have not created a full backup yet."
    val changes = mutableListOf<String>()
    if (metadata.unbackedPasswordCount > 0) changes += "${metadata.unbackedPasswordCount} new ${if (metadata.unbackedPasswordCount == 1) "password" else "passwords"}"
    if (metadata.unbackedNoteCount > 0) changes += "${metadata.unbackedNoteCount} new ${if (metadata.unbackedNoteCount == 1) "note" else "notes"}"
    if (changes.isNotEmpty()) {
        val count = metadata.unbackedPasswordCount + metadata.unbackedNoteCount
        messages += "${changes.joinToString(" and ")} ${if (count == 1) "has" else "have"} not been included in a full backup."
    }
    if (metadata.masterPasswordChangedSinceBackup) messages += "Your Master Password changed after the last backup. Old exported vault files still require the old Master Password used when they were created."
    messages += "Please back up the vault so you do not lose your data if the app is uninstalled or your phone is lost."
    return messages.joinToString(" ")
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
        Column(Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md)), content = { content() })
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Icon(icon, null, tint = VaultAccent, modifier = Modifier.padding(end = VaultSpacing.md))
}

@Composable
private fun SettingsNavigationRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(VaultSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        SettingsIcon(icon)
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = VaultMutedText)
    }
}

@Composable
private fun SettingsValueRow(title: String, value: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(VaultSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = VaultSecondaryText)
    }
}

@Composable
private fun ChoiceDialog(title: String, choices: List<String>, onDismiss: () -> Unit, onChoice: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { choices.forEachIndexed { index, choice -> TextButton(onClick = { onChoice(index) }, modifier = Modifier.fillMaxWidth()) { Text(choice) } } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangeMasterPasswordDialog(state: VaultAppState, onDismiss: () -> Unit, onChanged: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val validation = PasswordValidator.validate(newPassword)
    FullScreenDialog(onDismiss) {
        VaultScreenContainer {
            SectionHeader("Change Master Password", "Enter your current Master Password, then create a new strong password.", large = false)
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                AppSecureField("Current Master Password", "Enter current password", current, { current = it; error = null })
                AppSecureField("New Master Password", "Enter new password", newPassword, { newPassword = it; error = null }, error = if (current.isNotEmpty() && current == newPassword) "New Master Password must be different from the current password." else null)
                AppSecureField("Confirm New Master Password", "Re-enter new password", confirm, { confirm = it; error = null }, error = if (confirm.isNotEmpty() && newPassword != confirm) "Passwords do not match." else null)
            }
            PasswordChecklist(validation, "New password strength")
            error?.let { InfoCard(InfoCardKind.WARNING, "Password change failed", it) }
            InfoCard(InfoCardKind.WARNING, "Backup password reminder", "Encrypted exports continue to use the Master Password that was active when each file was created. After changing your Master Password, remember the password used for older exports and create a new full vault backup.")
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
                AppButton("Cancel", onDismiss, modifier = Modifier.weight(1f), icon = Icons.Rounded.Close, kind = AppButtonKind.SECONDARY)
                AppButton("Change", {
                    scope.launch {
                        error = state.changeMasterPassword(current, newPassword)
                        if (error == null) onChanged() else current = ""
                    }
                }, modifier = Modifier.weight(1f), icon = Icons.Rounded.CheckCircle,
                    enabled = current.isNotEmpty() && validation.isStrong && confirm == newPassword && current != newPassword)
            }
        }
    }
}

data class AboutSection(val title: String, val body: String)

private val aboutTitles = linkedMapOf(
    "aboutApp" to "About the App",
    "comparison" to "Why Pass&Note Vault",
    "security" to "How Data Is Protected",
    "recovery" to "Master Password & Recovery",
    "exportImport" to "Export & Import Guide",
    "privacyPolicy" to "Privacy Policy",
    "terms" to "Terms & Conditions",
    "licenses" to "Open Source Licenses",
)

@Composable
fun AboutScreen(state: VaultAppState) {
    val packageInfo = remember {
        state.context.packageManager.getPackageInfo(state.context.packageName, 0)
    }
    val buildNumber = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    VaultScaffold(title = "About", onBack = state::back) {
        VaultScreenContainer {
            SectionHeader("Pass&Note Vault", "Version ${packageInfo.versionName ?: "Unknown"} • Build $buildNumber")
            AboutGroup("APP", listOf("aboutApp", "comparison"), state)
            AboutGroup("SECURITY & HELP", listOf("security", "recovery", "exportImport"), state)
            AboutGroup("LEGAL", listOf("privacyPolicy", "terms", "licenses"), state)
        }
    }
}

@Composable
private fun AboutGroup(title: String, pages: List<String>, state: VaultAppState) {
    SettingsGroup(title) {
        pages.forEachIndexed { index, page ->
            SettingsNavigationRow(aboutTitles.getValue(page), Icons.Rounded.Info) { state.navigate(VaultDestination.AboutDetail(page)) }
            if (index != pages.lastIndex) HorizontalDivider(color = VaultBorder)
        }
    }
}

@Composable
fun AboutDetailScreen(state: VaultAppState, page: String) {
    val title = aboutTitles[page] ?: "About"
    VaultScaffold(title = title, onBack = state::back) {
        VaultScreenContainer {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = VaultPrimaryText)
            aboutSections(page).forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, color = VaultPrimaryText)
                    Text(section.body, style = MaterialTheme.typography.bodyLarge, color = VaultSecondaryText)
                }
            }
        }
    }
}

private fun aboutSections(page: String): List<AboutSection> = when (page) {
    "aboutApp" -> listOf(AboutSection("About Pass&Note Vault", "Pass&Note Vault is a privacy-focused offline vault for securely storing passwords and personal notes on your device. The app is designed around a simple principle: your vault belongs to you. Pass&Note Vault does not require a Pass&Note Vault account or a Pass&Note Vault server to access your vault. Your passwords and secure notes are stored locally in an encrypted database on your device. You can create encrypted export files to move or back up your information manually. You choose when to create an export and where to save it. Pass&Note Vault supports biometric quick unlock for convenience, while your Master Password remains the primary credential for sensitive security operations."))
    "comparison" -> listOf(
        AboutSection("Typical cloud password apps", "Many password managers are designed around accounts, cloud sync, subscriptions, browser extensions, and automatic cross-device access. Those features can be convenient, but they also mean users must trust more remote infrastructure, account security settings, and third-party service availability."),
        AboutSection("Pass&Note Vault", "Pass&Note Vault focuses on local-first storage for passwords and secure notes. Your vault data stays encrypted on this device, and the app does not require a Pass&Note Vault online account to unlock or manage your local vault."),
        AboutSection("Backups stay in your control", "Instead of automatic cloud sync, Pass&Note Vault lets you create encrypted export files when you choose. You decide where to store or share those files, and each encrypted export requires the Master Password that was active when the export was created."),
        AboutSection("Passwords and notes together", "Pass&Note Vault is built for people who want both password records and private notes in one offline vault. It keeps the core workflow simple: add records, protect them locally, export encrypted backups, and restore when needed."),
        AboutSection("Tradeoff", "Pass&Note Vault does not try to replace every cloud password manager feature. If you need automatic sync across many devices or browser autofill, a cloud-based manager may be more convenient. If you prefer an offline vault with manual encrypted backups and no required account, Pass&Note Vault is designed for that use case."),
    )
    "security" -> listOf(
        AboutSection("Local encrypted storage", "Passwords and secure notes are stored in an encrypted local database. The database is protected using a randomly generated encryption key created specifically for your vault."),
        AboutSection("Master Password", "Your Master Password is used to securely unlock access to your vault encryption key. Pass&Note Vault is designed so that your raw Master Password is not stored directly by the application."),
        AboutSection("Biometric Quick Unlock", "Biometrics can be enabled as a convenient way to unlock your vault. Biometric authentication does not replace your Master Password. Your device PIN or password is not used as a Pass&Note Vault password."),
        AboutSection("App-switcher privacy", "Pass&Note Vault hides sensitive application content whenever the app leaves the foreground so passwords and notes are not exposed through the app-switcher preview."),
        AboutSection("Encrypted backups", "Encrypted Pass&Note Vault exports use encryption separate from the local database encryption key. The Master Password active when the encrypted file is created is required to decrypt that file later."),
        AboutSection("Security limitations", "No software can guarantee absolute protection. Security may be reduced if a device is rooted, compromised by malicious software, physically controlled by an attacker while unlocked, or if another person obtains the credentials required to decrypt your vault or exported files."),
    )
    "recovery" -> listOf(
        AboutSection("Master Password", "Your Master Password is the primary credential protecting your Pass&Note Vault. Pass&Note Vault cannot recover or reveal a forgotten Master Password. If you forget it and biometric access is unavailable, your local encrypted vault may become permanently inaccessible."),
        AboutSection("Biometrics are not recovery", "Biometric Quick Unlock is a convenience feature and should not be treated as a recovery method for a forgotten Master Password."),
        AboutSection("Changing your Master Password", "Changing the Master Password used by your current vault does not automatically change the password protecting encrypted backup files you created previously. A backup created while Password A was active will continue to require Password A. If you later change your vault to Password B, new encrypted exports will use Password B. Create a new full encrypted vault backup after changing your Master Password."),
    )
    "exportImport" -> listOf(
        AboutSection(".pnpass", "An encrypted package containing one or more password records. Passwords can only be exported from Pass&Note Vault in encrypted form. A .pnpass file requires the Master Password that was active when the file was created."),
        AboutSection(".pnnote", "An encrypted package containing one or more secure notes. A .pnnote file requires the Master Password that was active when the file was created."),
        AboutSection(".pnvault", "A complete encrypted Pass&Note Vault backup containing passwords and secure notes. Use this format when you want to create a full backup or move your vault data to another device."),
        AboutSection(".txt", "A normal unencrypted text file containing a single note. A .txt export is readable by anyone who obtains access to that file."),
        AboutSection("Importing data", "Importing a password package, note package, or full vault backup into an existing vault adds the imported records to the current vault. Existing records are not automatically deleted. Importing the same file more than once may create duplicate entries. During encrypted import, Pass&Note Vault asks for the Master Password used when that export was created. This Backup Password may be different from the current vault Master Password."),
        AboutSection("Emergency Web Access", "A web version is available for emergency use at https://passandnote.com. You can use it to decrypt and read encrypted .pnvault, .pnpass, and .pnnote files when you do not have access to the app. Only open encrypted export files on devices and browsers you trust, and keep your Master Password private."),
    )
    "privacyPolicy" -> privacySections()
    "terms" -> termsSections()
    "licenses" -> licenseSections()
    else -> emptyList()
}

private fun privacySections() = listOf(
    AboutSection("Effective Date", "[Insert release date]"),
    AboutSection("Overview", "Pass&Note Vault is designed as a privacy-focused local vault. This Privacy Policy explains how information is handled when you use Pass&Note Vault."),
    AboutSection("Vault Data", "Passwords, secure notes, and related vault information are stored locally in an encrypted database on your device. Pass&Note Vault does not require you to create a Pass&Note Vault online account in order to use the vault. Pass&Note Vault does not intentionally transmit the contents of your vault to developer-operated servers."),
    AboutSection("Master Password", "Pass&Note Vault is designed so that your raw Master Password is not stored directly by the application. Your Master Password is used as part of the cryptographic process that allows access to your encrypted vault. Pass&Note Vault cannot recover a forgotten Master Password."),
    AboutSection("Biometrics", "If you enable biometric quick unlock, biometric verification is performed using security functionality provided by the operating system. Pass&Note Vault does not receive or store your fingerprint image, facial image, or underlying biometric template. Biometric Quick Unlock is optional."),
    AboutSection("Exports and Backups", "Pass&Note Vault allows you to manually export encrypted password packages, encrypted note packages, encrypted full-vault backups, and unencrypted individual notes. You choose when an export occurs and where the resulting file is saved or shared. Files saved or shared through cloud drives, email, messaging services, storage providers, or other applications are subject to those services' privacy and security practices."),
    AboutSection("Unencrypted Note Exports", "An unencrypted .txt note is not protected by Pass&Note Vault encryption after export. Anyone who obtains access to that exported file may be able to read its contents."),
    AboutSection("Clipboard", "When you choose to copy a password, the value may temporarily be placed on the system clipboard so you can paste it into another application. Pass&Note Vault may use operating-system capabilities to limit how long copied password data remains available, according to your selected clipboard-protection setting."),
    AboutSection("Analytics, Advertising and Tracking", "Pass&Note Vault does not include advertising or behavioral tracking, and does not use vault contents for analytics."),
    AboutSection("Network and Cloud Features", "Pass&Note Vault does not provide automatic cloud synchronization in V1. If cloud synchronization, analytics, remote crash reporting, accounts, or other network services are introduced in a future version, this Privacy Policy must be updated."),
    AboutSection("Data Deletion", "Vault data can be deleted from within the application where deletion functionality is available. Deleting the application may remove application data stored in the app container, subject to operating-system behavior. Exported files saved elsewhere are outside the Pass&Note Vault app container and must be deleted separately."),
    AboutSection("Security", "Pass&Note Vault uses technical safeguards designed to protect locally stored vault information. However, no software, device, storage system, or encryption implementation can guarantee absolute security. Users are responsible for protecting their Master Password, their device, and exported backup files."),
    AboutSection("Children", "Pass&Note Vault is a general-purpose privacy utility and is not specifically designed to collect personal information from children. Because the application does not operate a Pass&Note Vault account service in V1, the developer does not intentionally request personal information from children through an account-registration process."),
    AboutSection("Changes", "This Privacy Policy may be updated when Pass&Note Vault features, data practices, or legal requirements change."),
    AboutSection("Contact", "Developer/Publisher: Siddharth Lenka\n\nContact: https://passandnote.com"),
)

private fun termsSections() = listOf(
    AboutSection("Effective Date", "[Insert release date]"),
    AboutSection("Acceptance", "By downloading, installing, or using Pass&Note Vault, you agree to these Terms & Conditions. If you do not agree with these terms, do not use the application."),
    AboutSection("Purpose", "Pass&Note Vault is a software utility designed to allow users to locally store and manage passwords, secure notes, and encrypted backup files."),
    AboutSection("Your Master Password", "You are responsible for creating, remembering, and protecting your Master Password. Pass&Note Vault is designed so that the developer cannot recover a forgotten Master Password. Loss of the Master Password may result in permanent loss of access to encrypted vault data or encrypted backup files."),
    AboutSection("Biometric Unlock", "Biometric Quick Unlock may be offered as an optional convenience feature. It is not a replacement or recovery mechanism for the Master Password."),
    AboutSection("Your Data", "You are responsible for the information you store in Pass&Note Vault. You should maintain appropriate encrypted backups of information that you cannot afford to lose."),
    AboutSection("Backup Files", "Encrypted export files require the Master Password that was used when the export was created. If you change your Master Password, previously created encrypted backups may continue to require the previous password."),
    AboutSection("Unencrypted Exports", "Pass&Note Vault may allow individual notes to be exported as unencrypted text files. Once exported in unencrypted form, those files are no longer protected by Pass&Note Vault encryption."),
    AboutSection("Third-Party Storage and Services", "When you choose to save or share exported files using a cloud drive, another storage service, email, messaging software, or another application, your use of that service is subject to the provider's own terms and privacy practices."),
    AboutSection("Prohibited Use", "You may not use Pass&Note Vault in a manner that violates applicable law, infringes the rights of others, interferes with the operation of the application, or attempts to distribute malicious modifications of the software."),
    AboutSection("Security Limitations", "Pass&Note Vault is designed to provide strong privacy and security protections, but no software can guarantee absolute security. Security may be reduced on rooted, compromised, modified, or maliciously controlled devices."),
    AboutSection("Availability", "Pass&Note Vault may be updated, modified, or discontinued. Compatibility may change as operating systems, hardware, security APIs, and third-party dependencies evolve."),
    AboutSection("No Warranty", "To the extent permitted by applicable law, Pass&Note Vault is provided on an as available basis without guarantees that the application will always operate without interruption, errors, or data loss."),
    AboutSection("Limitation of Responsibility", "To the extent permitted by applicable law, the developer is not responsible for losses resulting from forgotten Master Passwords, deleted vaults, lost devices, compromised devices, lost or damaged backup files, unencrypted exports, third-party storage providers, or actions taken by users outside the application's control. This section should be reviewed by qualified legal counsel before commercial release."),
    AboutSection("Changes", "These Terms & Conditions may be updated when application functionality, distribution requirements, or applicable law change."),
    AboutSection("Contact", "Developer/Publisher: Siddharth Lenka\n\nContact: https://passandnote.com"),
)

private fun licenseSections() = listOf(
    AboutSection("Open Source Software", "The Android edition of Pass&Note Vault includes AndroidX Jetpack libraries and SQLCipher for Android. The Android database repository mirrors the iOS GRDB behavior with direct parameterized SQL because GRDB.swift is a Swift-only library."),
    AboutSection("SQLCipher", "Copyright (c) 2025, ZETETIC LLC. All rights reserved. Redistribution and use in source and binary forms, with or without modification, are permitted subject to the SQLCipher license included with the dependency. THIS SOFTWARE IS PROVIDED BY ZETETIC LLC 'AS IS' WITHOUT WARRANTIES; ZETETIC LLC SHALL NOT BE LIABLE FOR DAMAGES ARISING FROM ITS USE."),
    AboutSection("AndroidX", "AndroidX libraries are Copyright The Android Open Source Project and are licensed under the Apache License, Version 2.0. You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0."),
)
