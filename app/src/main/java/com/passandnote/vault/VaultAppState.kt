package com.passandnote.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultCallback
import androidx.biometric.registerForAuthenticationResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.passandnote.vault.data.ClipboardClearDuration
import com.passandnote.vault.data.SecureNote
import com.passandnote.vault.data.VaultDatabase
import com.passandnote.vault.data.VaultMetadata
import com.passandnote.vault.security.BiometricVaultKeyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

enum class RootMode { BEFORE_CONTINUE, ONBOARDING, LOCKED, UNLOCKED }
enum class OnboardingRoute { WELCOME, CREATE_PASSWORD, REVIEW }

sealed interface VaultDestination {
    data object Home : VaultDestination
    data object Passwords : VaultDestination
    data object Notes : VaultDestination
    data object Settings : VaultDestination
    data object About : VaultDestination
    data class AboutDetail(val page: String) : VaultDestination
    data class NoteEditor(val note: SecureNote) : VaultDestination
}

class VaultAppState(
    private val activity: ComponentActivity,
    private val biometricAuthenticator: BiometricAuthenticator,
) {
    val context: Context = activity.applicationContext
    val metadata = VaultMetadata(context)
    val database = VaultDatabase(context)
    val biometricService = BiometricVaultKeyService(context, metadata)
    val clipboard = SecureClipboard(context, metadata)
    var rootMode by mutableStateOf(initialRootMode(metadata.hasAcceptedCurrentTerms, metadata.hasCreatedVault))
        private set
    var onboardingRoute by mutableStateOf(OnboardingRoute.WELCOME)
    var unlockSuccessMessage by mutableStateOf<String?>(null)
    var privacyShieldVisible by mutableStateOf(false)
        private set
    private var didEnterBackgroundSinceShield = false
    private var externalDocumentFlowInProgress = false
    private var biometricUnlockInProgress = false
    private val biometricOperations = SingleOperationGate()
    private var foregroundUnlockScheduled = false
    private var resumeNavigationAfterUnlock = false
    var isBusy by mutableStateOf(false)
    var appError by mutableStateOf<String?>(null)

    val destinations = mutableStateListOf<VaultDestination>(VaultDestination.Home)

    val currentDestination: VaultDestination get() = destinations.last()
    val canStartOver: Boolean get() = metadata.canResetUnusedVault
    val canUseBiometrics: Boolean
        get() = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun navigate(destination: VaultDestination) {
        destinations += destination
    }

    fun back(): Boolean {
        if (destinations.size <= 1) return false
        destinations.removeAt(destinations.lastIndex)
        return true
    }

    fun acceptTerms(): Boolean {
        if (!metadata.acceptCurrentTerms()) return false
        onboardingRoute = OnboardingRoute.WELCOME
        rootMode = if (metadata.hasCreatedVault) RootMode.LOCKED else RootMode.ONBOARDING
        return true
    }

    suspend fun createVault(masterPassword: String): String? = withContext(Dispatchers.IO) {
        runCatching { database.create(masterPassword) }.fold(
            onSuccess = {
                database.close()
                unlockSuccessMessage = "Vault created successfully. Enter your master password to unlock your vault."
                rootMode = RootMode.LOCKED
                onboardingRoute = OnboardingRoute.WELCOME
                null
            },
            onFailure = { "Could not create encrypted vault. Please try again." },
        )
    }

    suspend fun unlock(masterPassword: String): String? = withContext(Dispatchers.IO) {
        runCatching { database.open(masterPassword) }.fold(
            onSuccess = {
                completeSuccessfulUnlock()
                null
            },
            onFailure = { "Invalid master password or vault could not be opened." },
        )
    }

    fun unlockWithBiometrics(onResult: (String?) -> Unit): Boolean {
        val operationId = beginBiometricOperation() ?: return false
        biometricUnlockInProgress = true
        val cipher = try {
            biometricService.prepareUnlockCipher()
        } catch (error: Exception) {
            biometricUnlockInProgress = false
            finishBiometricOperation(operationId)
            Log.e(BIOMETRIC_LOG_TAG, "Unable to prepare the biometric vault-key cipher", error)
            biometricService.disable()
            onResult(BIOMETRIC_REENABLE_MESSAGE)
            return false
        }
        biometricAuthenticator.authenticate(
            cipher = cipher,
            title = "Unlock Pass&Note Vault",
            onSuccess = biometricSuccess@{ authenticatedCipher ->
                if (!ownsBiometricOperation(operationId)) return@biometricSuccess
                val vaultKey = try {
                    biometricService.completeUnlock(authenticatedCipher)
                } catch (error: Exception) {
                    biometricUnlockInProgress = false
                    finishBiometricOperation(operationId)
                    Log.e(BIOMETRIC_LOG_TAG, "Biometric authentication succeeded but vault-key unwrap failed", error)
                    biometricService.disable()
                    onResult(BIOMETRIC_REENABLE_MESSAGE)
                    return@biometricSuccess
                }
                try {
                    database.open(vaultKey)
                    biometricUnlockInProgress = false
                    finishBiometricOperation(operationId)
                    completeSuccessfulUnlock()
                    onResult(null)
                } catch (error: Exception) {
                    biometricUnlockInProgress = false
                    finishBiometricOperation(operationId)
                    Log.e(BIOMETRIC_LOG_TAG, "Biometric vault key could not open SQLCipher", error)
                    biometricService.disable()
                    onResult(BIOMETRIC_REENABLE_MESSAGE)
                } finally {
                    vaultKey.fill(0)
                }
            },
            onError = biometricError@{
                if (!ownsBiometricOperation(operationId)) return@biometricError
                biometricUnlockInProgress = false
                finishBiometricOperation(operationId)
                onResult("Biometric unlock was cancelled or unsuccessful. Enter your Master Password to unlock the vault.")
            },
        )
        return true
    }

    suspend fun verifiedVaultKey(masterPassword: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching { database.recoverVaultKey(masterPassword) }
    }

    fun enableBiometric(vaultKey: ByteArray, onResult: (String?) -> Unit) {
        if (!canUseBiometrics) {
            vaultKey.fill(0)
            onResult("Biometrics are not available or not enrolled on this device.")
            return
        }
        val operationId = beginBiometricOperation()
        if (operationId == null) {
            vaultKey.fill(0)
            onResult("A biometric authentication is already in progress. Complete or cancel it before trying again.")
            return
        }
        val cipher = try {
            biometricService.prepareEnableCipher()
        } catch (_: Exception) {
            vaultKey.fill(0)
            biometricService.disable()
            finishBiometricOperation(operationId)
            onResult("Biometrics are not available or not enrolled on this device.")
            return
        }
        biometricAuthenticator.authenticate(
            cipher = cipher,
            title = "Enable Biometric Quick Unlock",
            onSuccess = biometricSuccess@{ authenticatedCipher ->
                if (!ownsBiometricOperation(operationId)) {
                    vaultKey.fill(0)
                    return@biometricSuccess
                }
                try {
                    biometricService.completeEnable(authenticatedCipher, vaultKey)
                    check(biometricService.isEnabled) { "Biometric key storage is incomplete." }
                    finishBiometricOperation(operationId)
                    onResult(null)
                } catch (_: Exception) {
                    biometricService.disable()
                    finishBiometricOperation(operationId)
                    onResult("Biometric Quick Unlock could not be enabled.")
                } finally {
                    vaultKey.fill(0)
                }
            },
            onError = biometricError@{
                vaultKey.fill(0)
                if (!ownsBiometricOperation(operationId)) return@biometricError
                biometricService.disable()
                finishBiometricOperation(operationId)
                onResult("Biometric Quick Unlock could not be enabled.")
            },
        )
    }

    suspend fun disableBiometric(masterPassword: String): String? = withContext(Dispatchers.IO) {
        val key = runCatching { database.recoverVaultKey(masterPassword) }.getOrElse {
            return@withContext "The master password could not be verified. Biometric unlock is still enabled."
        }
        key.fill(0)
        biometricService.disable()
        null
    }

    suspend fun changeMasterPassword(currentPassword: String, newPassword: String): String? = withContext(Dispatchers.IO) {
        runCatching { database.changeMasterPassword(currentPassword, newPassword) }.fold(
            onSuccess = {
                metadata.markMasterPasswordChanged()
                null
            },
            onFailure = { "The current Master Password could not be verified." },
        )
    }

    suspend fun verifyMasterPassword(masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { database.recoverVaultKey(masterPassword).also { it.fill(0) } }.isSuccess
    }

    suspend fun resetUnusedVault(): String? = withContext(Dispatchers.IO) {
        runCatching { database.resetUnusedVault() }.fold(
            onSuccess = {
                rootMode = RootMode.ONBOARDING
                onboardingRoute = OnboardingRoute.WELCOME
                unlockSuccessMessage = null
                null
            },
            onFailure = { "Could not start over. Please try again." },
        )
    }

    fun lockVault() {
        lockVault(preserveNavigation = false)
    }

    private fun lockVault(preserveNavigation: Boolean) {
        database.close()
        rootMode = RootMode.LOCKED
        resumeNavigationAfterUnlock = preserveNavigation
        if (!preserveNavigation) resetNavigationToHome()
        unlockSuccessMessage = null
        privacyShieldVisible = false
        didEnterBackgroundSinceShield = false
    }

    fun completeRestore() {
        metadata.markVaultActivated()
        rootMode = RootMode.UNLOCKED
        resumeNavigationAfterUnlock = false
        resetNavigationToHome()
        unlockSuccessMessage = null
        didEnterBackgroundSinceShield = false
    }

    fun onInactive() {
        if (rootMode == RootMode.UNLOCKED) privacyShieldVisible = true
    }

    fun onBackground() {
        if (rootMode != RootMode.UNLOCKED) return
        clipboard.clearWhenBackgrounded()
        if (externalDocumentFlowInProgress) {
            privacyShieldVisible = true
            didEnterBackgroundSinceShield = false
            return
        }
        metadata.lastBackgroundMillis = System.currentTimeMillis()
        didEnterBackgroundSinceShield = true
        privacyShieldVisible = true
    }

    fun onForeground() {
        // Android's document provider is a separate Activity. Keep the shield up
        // until its Activity Result callback runs instead of locking the vault and
        // discarding the pending import/export UI behind it.
        if (externalDocumentFlowInProgress) return
        if (rootMode != RootMode.UNLOCKED || !privacyShieldVisible) {
            privacyShieldVisible = false
            didEnterBackgroundSinceShield = false
            return
        }
        if (!didEnterBackgroundSinceShield) {
            privacyShieldVisible = false
            return
        }
        val elapsed = (System.currentTimeMillis() - metadata.lastBackgroundMillis).coerceAtLeast(0)
        if (elapsed < metadata.autoLockDuration.thresholdMillis) {
            privacyShieldVisible = false
            didEnterBackgroundSinceShield = false
            return
        }
        if (biometricService.isEnabled) {
            if (biometricUnlockInProgress || foregroundUnlockScheduled) return
            foregroundUnlockScheduled = true
            Handler(Looper.getMainLooper()).postDelayed({
                foregroundUnlockScheduled = false
                if (rootMode != RootMode.UNLOCKED || !privacyShieldVisible || externalDocumentFlowInProgress) return@postDelayed
                val started = unlockWithBiometrics { message ->
                    if (message == null) {
                        privacyShieldVisible = false
                        didEnterBackgroundSinceShield = false
                    } else lockVault(preserveNavigation = true)
                }
                if (!started && rootMode == RootMode.UNLOCKED && privacyShieldVisible) {
                    lockVault(preserveNavigation = true)
                }
            }, 300)
        } else {
            lockVault(preserveNavigation = true)
        }
    }

    fun launchDocumentFlow(launch: () -> Unit) {
        externalDocumentFlowInProgress = true
        try {
            launch()
        } catch (error: Throwable) {
            finishDocumentFlow()
            throw error
        }
    }

    fun finishDocumentFlow() {
        externalDocumentFlowInProgress = false
        if (rootMode == RootMode.UNLOCKED) privacyShieldVisible = false
        didEnterBackgroundSinceShield = false
    }

    private fun completeSuccessfulUnlock() {
        val wasAlreadyUnlocked = rootMode == RootMode.UNLOCKED
        val shouldPreserveNavigation = wasAlreadyUnlocked || resumeNavigationAfterUnlock
        if (!wasAlreadyUnlocked && resumeNavigationAfterUnlock) refreshResumableDestination()
        metadata.markVaultActivated()
        rootMode = RootMode.UNLOCKED
        if (!shouldPreserveNavigation) resetNavigationToHome()
        resumeNavigationAfterUnlock = false
        unlockSuccessMessage = null
        didEnterBackgroundSinceShield = false
    }

    private fun refreshResumableDestination() {
        val index = destinations.lastIndex
        val editor = destinations.getOrNull(index) as? VaultDestination.NoteEditor ?: return
        val refreshedNote = database.fetchNotes().firstOrNull { it.id == editor.note.id }
        if (refreshedNote != null) {
            destinations[index] = VaultDestination.NoteEditor(refreshedNote)
        } else {
            destinations.removeAt(index)
            if (destinations.isEmpty()) destinations += VaultDestination.Home
        }
    }

    private fun resetNavigationToHome() {
        destinations.clear()
        destinations += VaultDestination.Home
    }

    private fun beginBiometricOperation(): SingleOperationGate.Token? =
        biometricOperations.tryBegin()

    private fun ownsBiometricOperation(operationId: SingleOperationGate.Token): Boolean =
        biometricOperations.owns(operationId)

    private fun finishBiometricOperation(operationId: SingleOperationGate.Token) {
        biometricOperations.finish(operationId)
    }

    private companion object {
        const val BIOMETRIC_LOG_TAG = "PassNoteBiometric"
        const val BIOMETRIC_REENABLE_MESSAGE =
            "Biometric Quick Unlock needs to be enabled again. Enter your Master Password, then re-enable Biometrics in Settings."
    }
}

class BiometricAuthenticator(activity: ComponentActivity) {
    private var pendingSuccess: ((Cipher) -> Unit)? = null
    private var pendingError: (() -> Unit)? = null

    private val launcher = activity.registerForAuthenticationResult(
        object : AuthenticationResultCallback {
            override fun onAuthResult(result: AuthenticationResult) {
                val success = pendingSuccess
                val error = pendingError
                pendingSuccess = null
                pendingError = null
                when (result) {
                    is AuthenticationResult.Success -> result.crypto?.cipher?.let { cipher ->
                        success?.invoke(cipher)
                    } ?: error?.invoke()
                    else -> error?.invoke()
                }
            }

            override fun onAuthAttemptFailed() {
                // Keep the system prompt open so the user can retry their fingerprint.
            }
        },
    )

    fun authenticate(
        cipher: Cipher,
        title: String,
        onSuccess: (Cipher) -> Unit,
        onError: () -> Unit,
    ) {
        if (pendingSuccess != null || pendingError != null) {
            onError()
            return
        }
        pendingSuccess = onSuccess
        pendingError = onError
        val request = AuthenticationRequest.Biometric.Builder(title)
            .setSubtitle("Confirm your identity with a strong biometric")
            .setMinStrength(
                AuthenticationRequest.Biometric.Strength.Class3(BiometricPrompt.CryptoObject(cipher)),
            )
            .setIsConfirmationRequired(false)
            .build()
        try {
            launcher.launch(request)
        } catch (error: Exception) {
            Log.e("PassNoteBiometric", "Unable to launch biometric authentication", error)
            pendingSuccess = null
            pendingError = null
            onError()
        }
    }
}

class SecureClipboard(
    context: Context,
    private val metadata: VaultMetadata,
) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastCopiedPassword: String? = null
    private val clearRunnable = Runnable { clearIfUnchanged() }

    fun copyPassword(password: String) {
        handler.removeCallbacks(clearRunnable)
        lastCopiedPassword = password
        val clip = ClipData.newPlainText("Password", password)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        manager.setPrimaryClip(clip)
        metadata.clipboardClearDuration.expirationMillis?.let { handler.postDelayed(clearRunnable, it) }
    }

    fun clearWhenBackgrounded() {
        if (metadata.clipboardClearDuration == ClipboardClearDuration.WHEN_BACKGROUND) clearIfUnchanged()
    }

    private fun clearIfUnchanged() {
        val expected = lastCopiedPassword ?: return
        val current = manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (current == expected) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.clearPrimaryClip()
            else manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        lastCopiedPassword = null
        handler.removeCallbacks(clearRunnable)
    }
}
