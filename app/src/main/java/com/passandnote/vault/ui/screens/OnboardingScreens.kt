package com.passandnote.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.passandnote.vault.OnboardingRoute
import com.passandnote.vault.VaultAppState
import com.passandnote.vault.data.PasswordValidator
import com.passandnote.vault.ui.components.AppButton
import com.passandnote.vault.ui.components.AppButtonKind
import com.passandnote.vault.ui.components.AppSecureField
import com.passandnote.vault.ui.components.CardColumn
import com.passandnote.vault.ui.components.InfoCard
import com.passandnote.vault.ui.components.InfoCardKind
import com.passandnote.vault.ui.components.PasswordChecklist
import com.passandnote.vault.ui.components.SectionHeader
import com.passandnote.vault.ui.components.StepProgressIndicator
import com.passandnote.vault.ui.components.VaultRadius
import com.passandnote.vault.ui.components.VaultScaffold
import com.passandnote.vault.ui.components.VaultScreenContainer
import com.passandnote.vault.ui.components.VaultSpacing
import com.passandnote.vault.ui.theme.VaultAccent
import com.passandnote.vault.ui.theme.VaultAccentSoft
import com.passandnote.vault.ui.theme.VaultBackground
import com.passandnote.vault.ui.theme.VaultBorder
import com.passandnote.vault.ui.theme.VaultCard
import com.passandnote.vault.ui.theme.VaultPrimaryText
import com.passandnote.vault.ui.theme.VaultSecondaryText
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://passandnote.com/privacy"
private const val TERMS_AND_CONDITIONS_URL = "https://passandnote.com/terms"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeforeYouContinueScreen(onContinue: () -> Boolean) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf<String?>(null) }
    var acceptanceError by remember { mutableStateOf<String?>(null) }

    fun openLegalPage(url: String) {
        runCatching { uriHandler.openUri(url) }
            .onSuccess { linkError = null }
            .onFailure { linkError = "A browser could not be opened on this device." }
    }

    VaultScreenContainer(modifier = Modifier.statusBarsPadding()) {
        SectionHeader(
            "Before You Continue",
            "Review and accept the app terms before creating or restoring your vault.",
        )

        Column(
            Modifier.fillMaxWidth()
                .background(VaultCard, RoundedCornerShape(VaultRadius.md))
                .padding(VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            AcknowledgementRow(
                icon = AcknowledgementIcon.LOCAL_VAULT,
                title = "Local vault",
                message = "Pass&Note Vault stores your vault locally on this device and does not require an online account.",
            )
            AcknowledgementRow(
                icon = AcknowledgementIcon.MASTER_PASSWORD,
                title = "Master Password",
                message = "Your Master Password protects access to your vault and encrypted backup files.",
            )
            AcknowledgementRow(
                icon = AcknowledgementIcon.ENCRYPTED_BACKUP,
                title = "Encrypted backup",
                message = "You can create encrypted backups and keep them wherever you prefer.",
            )
            AcknowledgementRow(
                icon = AcknowledgementIcon.PRIVACY_AND_TERMS,
                title = "Privacy and terms",
                message = "Continuing means you agree to the Privacy Policy and Terms & Conditions.",
            )
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
        ) {
            Text(
                "By continuing, you agree to the Privacy Policy and Terms & Conditions.",
                style = MaterialTheme.typography.bodyMedium,
                color = VaultSecondaryText,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
            ) {
                TextButton(
                    onClick = { openLegalPage(PRIVACY_POLICY_URL) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultAccent,
                    )
                }
                TextButton(
                    onClick = { openLegalPage(TERMS_AND_CONDITIONS_URL) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        "Terms & Conditions",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultAccent,
                    )
                }
            }
        }

        if (linkError != null || acceptanceError != null) {
            Column(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
            ) {
                linkError?.let { InfoCard(InfoCardKind.WARNING, "Unable to open link", it) }
                acceptanceError?.let { InfoCard(InfoCardKind.WARNING, "Could not continue", it) }
            }
        }
        AppButton(
            "Continue",
            {
                acceptanceError = if (onContinue()) null
                else "Your acceptance could not be saved. Please try again."
            },
            icon = Icons.Rounded.CheckCircle,
        )
    }
}

private enum class AcknowledgementIcon {
    LOCAL_VAULT,
    MASTER_PASSWORD,
    ENCRYPTED_BACKUP,
    PRIVACY_AND_TERMS,
}

@Composable
private fun AcknowledgementRow(icon: AcknowledgementIcon, title: String, message: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(24.dp).height(24.dp), contentAlignment = Alignment.TopCenter) {
            when (icon) {
                AcknowledgementIcon.LOCAL_VAULT -> {
                    Icon(Icons.Outlined.Shield, null, tint = VaultAccent, modifier = Modifier.size(24.dp))
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = VaultAccent,
                        modifier = Modifier.align(Alignment.Center).size(9.dp),
                    )
                }
                AcknowledgementIcon.MASTER_PASSWORD ->
                    Icon(Icons.Rounded.Key, null, tint = VaultAccent, modifier = Modifier.size(22.dp))
                AcknowledgementIcon.ENCRYPTED_BACKUP -> EncryptedBackupIcon()
                AcknowledgementIcon.PRIVACY_AND_TERMS ->
                    Icon(Icons.Rounded.Description, null, tint = VaultAccent, modifier = Modifier.size(22.dp))
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = VaultPrimaryText,
            )
            Text(message, style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
        }
    }
}

@Composable
private fun EncryptedBackupIcon() {
    Canvas(Modifier.size(24.dp)) {
        val lineWidth = 1.8.dp.toPx()
        val outline = Stroke(width = lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val drive = Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.24f)
            lineTo(size.width * 0.77f, size.height * 0.24f)
            quadraticTo(size.width * 0.84f, size.height * 0.24f, size.width * 0.87f, size.height * 0.32f)
            lineTo(size.width * 0.95f, size.height * 0.64f)
            quadraticTo(size.width * 0.97f, size.height * 0.72f, size.width * 0.88f, size.height * 0.72f)
            lineTo(size.width * 0.17f, size.height * 0.72f)
            lineTo(size.width * 0.25f, size.height * 0.32f)
            quadraticTo(size.width * 0.26f, size.height * 0.27f, size.width * 0.27f, size.height * 0.24f)
        }
        drawPath(drive, color = VaultAccent, style = outline)
        drawLine(
            color = VaultAccent,
            start = Offset(size.width * 0.18f, size.height * 0.58f),
            end = Offset(size.width * 0.92f, size.height * 0.58f),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )

        val badgeCenter = Offset(size.width * 0.18f, size.height * 0.72f)
        val badgeRadius = size.minDimension * 0.20f
        drawCircle(VaultCard, badgeRadius + lineWidth * 0.65f, badgeCenter)
        drawCircle(VaultAccent, badgeRadius, badgeCenter, style = outline)
        drawLine(
            color = VaultAccent,
            start = Offset(badgeCenter.x - badgeRadius * 0.45f, badgeCenter.y),
            end = Offset(badgeCenter.x - badgeRadius * 0.10f, badgeCenter.y + badgeRadius * 0.35f),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = VaultAccent,
            start = Offset(badgeCenter.x - badgeRadius * 0.10f, badgeCenter.y + badgeRadius * 0.35f),
            end = Offset(badgeCenter.x + badgeRadius * 0.52f, badgeCenter.y - badgeRadius * 0.38f),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun OnboardingFlow(state: VaultAppState) {
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (restoring) {
        FullVaultRestoreDialog(state = state, resetUnusedVaultBeforeRestore = false, onDismiss = { restoring = false })
    }

    when (state.onboardingRoute) {
        OnboardingRoute.WELCOME -> WelcomeScreen(
            onCreate = { state.onboardingRoute = OnboardingRoute.CREATE_PASSWORD },
            onRestore = { restoring = true },
        )
        OnboardingRoute.CREATE_PASSWORD -> VaultScaffold(title = "", onBack = { state.onboardingRoute = OnboardingRoute.WELCOME }) {
            CreatePasswordScreen(
                masterPassword = masterPassword,
                confirmPassword = confirmPassword,
                onMasterPasswordChange = { masterPassword = it; error = null },
                onConfirmPasswordChange = { confirmPassword = it; error = null },
                onContinue = { state.onboardingRoute = OnboardingRoute.REVIEW },
            )
        }
        OnboardingRoute.REVIEW -> VaultScaffold(title = "", onBack = { state.onboardingRoute = OnboardingRoute.CREATE_PASSWORD }) {
            ReviewVaultScreen(
                masterPassword = masterPassword,
                error = error,
                loading = state.isBusy,
                onCreate = {
                    if (!PasswordValidator.validate(masterPassword).isStrong || masterPassword != confirmPassword) {
                        error = "Please complete all required vault setup steps."
                    } else scope.launch {
                        state.isBusy = true
                        error = state.createVault(masterPassword)
                        state.isBusy = false
                        if (error == null) {
                            masterPassword = ""
                            confirmPassword = ""
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeScreen(onCreate: () -> Unit, onRestore: () -> Unit) {
    VaultScreenContainer {
        Spacer(Modifier.size(VaultSpacing.xl))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VaultSpacing.lg)) {
            Box(Modifier.size(88.dp).background(VaultAccentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Security, null, tint = VaultAccent, modifier = Modifier.size(40.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                Text("Your Private Vault", style = MaterialTheme.typography.displayLarge, color = VaultPrimaryText, textAlign = TextAlign.Center)
                Text(
                    "Create a private offline vault for passwords and secure notes. No cloud. No account. No tracking.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = VaultSecondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
            InfoCard(
                InfoCardKind.INFO,
                "Offline by design",
                "Your vault stays on this device. No internet connection or server account is required.",
            )
            AppButton("Create New Vault", onCreate, icon = Icons.Rounded.AddCircle)
            AppButton("Restore From Backup", onRestore, icon = Icons.Rounded.FileDownload, kind = AppButtonKind.SECONDARY)
        }
        Spacer(Modifier.size(VaultSpacing.xl))
    }
}

@Composable
private fun CreatePasswordScreen(
    masterPassword: String,
    confirmPassword: String,
    onMasterPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val validation = PasswordValidator.validate(masterPassword)
    val mismatch = if (confirmPassword.isNotEmpty() && masterPassword != confirmPassword) "Passwords do not match." else null
    VaultScreenContainer {
        StepProgressIndicator(1, 2)
        SectionHeader("Create a strong password", "This password protects your encrypted vault.", large = false)
        Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)) {
            AppSecureField("Master password", "Enter master password", masterPassword, onMasterPasswordChange)
            AppSecureField("Confirm password", "Re-enter master password", confirmPassword, onConfirmPasswordChange, error = mismatch)
        }
        PasswordChecklist(validation)
        InfoCard(InfoCardKind.WARNING, "Recovery warning", "If you forget your password, your vault cannot be recovered.")
        AppButton(
            "Continue",
            onContinue,
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            enabled = validation.isStrong && confirmPassword.isNotEmpty() && masterPassword == confirmPassword,
        )
    }
}

@Composable
private fun ReviewVaultScreen(masterPassword: String, error: String?, loading: Boolean, onCreate: () -> Unit) {
    VaultScreenContainer {
        StepProgressIndicator(2, 2)
        SectionHeader("Review vault setup", "Confirm your vault settings before creating the encrypted database.", large = false)
        Column(
            Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md)).padding(VaultSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
        ) {
            ReviewRow("Master Password", if (PasswordValidator.validate(masterPassword).isStrong) "Strong" else "Weak")
            ReviewRow("Storage", "Offline encrypted database")
        }
        InfoCard(InfoCardKind.INFO, "Ready to create vault", "Your encrypted database will be created locally on this device.")
        if (error != null) InfoCard(InfoCardKind.WARNING, "Vault creation failed", error)
        AppButton("Create Secure Vault", onCreate, icon = Icons.Rounded.Lock, enabled = PasswordValidator.validate(masterPassword).isStrong, loading = loading)
    }
}

@Composable
private fun ReviewRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = VaultSecondaryText)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText)
    }
}

@Composable
fun UnlockScreen(state: VaultAppState) {
    var masterPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var usingPasswordFallback by remember { mutableStateOf(false) }
    var authenticating by remember { mutableStateOf(false) }
    var showStartOver by remember { mutableStateOf(false) }
    var showDisable by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val biometricEnabled = state.biometricService.isEnabled

    if (showRestore) FullVaultRestoreDialog(state, resetUnusedVaultBeforeRestore = true, onDismiss = { showRestore = false })

    if (showStartOver) AlertDialog(
        onDismissRequest = { showStartOver = false },
        title = { Text("Create a New Vault?") },
        text = { Text("This vault has not been used yet. Starting over will remove the current vault setup and let you create a new master password.") },
        confirmButton = { TextButton(onClick = {
            showStartOver = false
            scope.launch { error = state.resetUnusedVault() }
        }) { Text("Start Over") } },
        dismissButton = { TextButton(onClick = { showStartOver = false }) { Text("Cancel") } },
    )

    if (showDisable) AlertDialog(
        onDismissRequest = { showDisable = false },
        title = { Text("Disable Biometrics?") },
        text = { Text("Enter your master password first, then disable Biometrics quick unlock.") },
        confirmButton = { TextButton(onClick = {
            showDisable = false
            if (masterPassword.isEmpty()) error = "Enter your master password to disable Biometrics quick unlock."
            else scope.launch { error = state.disableBiometric(masterPassword) }
        }) { Text("Disable") } },
        dismissButton = { TextButton(onClick = { showDisable = false }) { Text("Cancel") } },
    )

    VaultScreenContainer {
        SectionHeader("Unlock Vault", "Enter your master password to decrypt and open your local vault.", large = false)
        state.unlockSuccessMessage?.let { InfoCard(InfoCardKind.SUCCESS, "Vault created", it) }
        AppSecureField("Master Password", "Enter master password", masterPassword, { masterPassword = it; error = null })
        if (biometricEnabled) {
            Row(
                Modifier.fillMaxWidth().background(VaultCard, RoundedCornerShape(VaultRadius.md))
                    .border(1.dp, VaultBorder, RoundedCornerShape(VaultRadius.md)).padding(VaultSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Biometrics Unlock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = VaultPrimaryText)
                    Text("Enabled", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
                }
                Switch(checked = true, onCheckedChange = { if (!it) showDisable = true })
            }
        }
        InfoCard(InfoCardKind.INFO, "Offline unlock", "Your vault is decrypted locally on this device. No server or internet connection is used.")
        error?.let { InfoCard(InfoCardKind.WARNING, "Unlock failed", it) }
        AppButton(
            "Unlock Vault",
            onClick = {
                if (biometricEnabled && !usingPasswordFallback) {
                    authenticating = true
                    val started = state.unlockWithBiometrics { message ->
                        authenticating = false
                        error = message
                        if (message != null) usingPasswordFallback = true
                    }
                    if (!started) authenticating = false
                } else if (masterPassword.isEmpty()) {
                    error = "Please enter your master password."
                } else scope.launch {
                    state.isBusy = true
                    error = state.unlock(masterPassword)
                    state.isBusy = false
                    if (error == null) masterPassword = ""
                }
            },
            icon = Icons.Rounded.LockOpen,
            enabled = (biometricEnabled && !usingPasswordFallback) || masterPassword.isNotEmpty(),
            loading = state.isBusy || authenticating,
        )
        if (state.canStartOver) {
            Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                Text("Having trouble with the password you just created?", style = MaterialTheme.typography.labelMedium, color = VaultSecondaryText)
                AppButton("Restore From Backup", { showRestore = true }, icon = Icons.Rounded.FileDownload, kind = AppButtonKind.SECONDARY)
                AppButton("Start Over", { showStartOver = true }, icon = Icons.Rounded.RestartAlt, kind = AppButtonKind.SECONDARY)
            }
        }
    }
}

@Composable
fun PrivacyShield() {
    Box(Modifier.fillMaxSize().background(VaultBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VaultSpacing.lg)) {
            Icon(Icons.Rounded.Security, null, tint = VaultAccent, modifier = Modifier.size(54.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
                Text("Pass&Note Vault", style = MaterialTheme.typography.headlineLarge, color = VaultPrimaryText)
                Text("Vault Protected", style = MaterialTheme.typography.bodyLarge, color = VaultSecondaryText)
            }
        }
    }
}
