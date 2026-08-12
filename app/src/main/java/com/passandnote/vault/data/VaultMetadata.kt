package com.passandnote.vault.data

import android.content.Context
import android.content.SharedPreferences
import com.passandnote.vault.CURRENT_TERMS_VERSION
import com.passandnote.vault.hasAcceptedCurrentTerms
import kotlin.math.max

class VaultMetadata(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("passnote_vault_metadata", Context.MODE_PRIVATE)

    var hasCreatedVault: Boolean
        get() = preferences.getBoolean(HAS_CREATED_VAULT, false)
        set(value) = preferences.edit().putBoolean(HAS_CREATED_VAULT, value).apply()

    val hasAcceptedCurrentTerms: Boolean
        get() = hasAcceptedCurrentTerms(preferences.getInt(TERMS_ACCEPTED_VERSION, 0))

    fun acceptCurrentTerms(): Boolean =
        preferences.edit().putInt(TERMS_ACCEPTED_VERSION, CURRENT_TERMS_VERSION).commit()

    var vaultHasBeenActivated: Boolean
        get() = if (!activationMetadataInitialized) true else preferences.getBoolean(VAULT_ACTIVATED, false)
        set(value) {
            preferences.edit()
                .putBoolean(ACTIVATION_INITIALIZED, true)
                .putBoolean(VAULT_ACTIVATED, value)
                .apply()
        }

    val canResetUnusedVault: Boolean
        get() = hasCreatedVault && activationMetadataInitialized && !vaultHasBeenActivated

    val biometricQuickUnlockEnabled: Boolean
        get() = preferences.getBoolean(BIOMETRIC_ENABLED, false)

    fun persistBiometricQuickUnlockEnabled(value: Boolean): Boolean =
        preferences.edit().putBoolean(BIOMETRIC_ENABLED, value).commit()

    var autoLockDuration: AutoLockDuration
        get() = AutoLockDuration.fromRaw(preferences.getString(AUTO_LOCK_DURATION, null))
        set(value) = preferences.edit().putString(AUTO_LOCK_DURATION, value.rawValue).apply()

    var clipboardClearDuration: ClipboardClearDuration
        get() = ClipboardClearDuration.fromRaw(preferences.getString(CLIPBOARD_DURATION, null))
        set(value) = preferences.edit().putString(CLIPBOARD_DURATION, value.rawValue).apply()

    var lastBackgroundMillis: Long
        get() = preferences.getLong(LAST_BACKGROUND_DATE, 0L)
        set(value) = preferences.edit().putLong(LAST_BACKGROUND_DATE, value).apply()

    var lastFullBackupMillis: Long
        get() = preferences.getLong(LAST_FULL_BACKUP_DATE, 0L)
        set(value) = preferences.edit().putLong(LAST_FULL_BACKUP_DATE, value).apply()

    var backupUpdateRequired: Boolean
        get() = preferences.getBoolean(BACKUP_UPDATE_REQUIRED, false)
        set(value) = preferences.edit().putBoolean(BACKUP_UPDATE_REQUIRED, value).apply()

    var unbackedPasswordCount: Int
        get() = preferences.getInt(UNBACKED_PASSWORD_COUNT, 0)
        set(value) = preferences.edit().putInt(UNBACKED_PASSWORD_COUNT, max(0, value)).apply()

    var unbackedNoteCount: Int
        get() = preferences.getInt(UNBACKED_NOTE_COUNT, 0)
        set(value) = preferences.edit().putInt(UNBACKED_NOTE_COUNT, max(0, value)).apply()

    var masterPasswordChangedSinceBackup: Boolean
        get() = preferences.getBoolean(MASTER_PASSWORD_CHANGED, false)
        set(value) = preferences.edit().putBoolean(MASTER_PASSWORD_CHANGED, value).apply()

    val backupStatus: BackupStatus
        get() = when {
            backupUpdateRequired -> BackupStatus.UPDATE_REQUIRED
            lastFullBackupMillis == 0L -> BackupStatus.NEVER_BACKED_UP
            else -> BackupStatus.UP_TO_DATE
        }

    fun prepareNewVaultLifecycle() {
        preferences.edit()
            .putBoolean(HAS_CREATED_VAULT, true)
            .putBoolean(ACTIVATION_INITIALIZED, true)
            .putBoolean(VAULT_ACTIVATED, false)
            .apply()
    }

    fun markVaultActivated() {
        vaultHasBeenActivated = true
    }

    fun clearUnusedVaultLifecycle() {
        preferences.edit()
            .putBoolean(HAS_CREATED_VAULT, false)
            .putBoolean(ACTIVATION_INITIALIZED, false)
            .putBoolean(VAULT_ACTIVATED, false)
            .apply()
    }

    fun markPasswordsAdded(count: Int = 1) {
        if (count <= 0) return
        unbackedPasswordCount += count
        backupUpdateRequired = true
    }

    fun markNotesAdded(count: Int = 1) {
        if (count <= 0) return
        unbackedNoteCount += count
        backupUpdateRequired = true
    }

    fun markMasterPasswordChanged() {
        masterPasswordChangedSinceBackup = true
        backupUpdateRequired = true
    }

    fun markFullBackupCompleted(nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putLong(LAST_FULL_BACKUP_DATE, nowMillis)
            .putBoolean(BACKUP_UPDATE_REQUIRED, false)
            .putInt(UNBACKED_PASSWORD_COUNT, 0)
            .putInt(UNBACKED_NOTE_COUNT, 0)
            .putBoolean(MASTER_PASSWORD_CHANGED, false)
            .apply()
    }

    private val activationMetadataInitialized: Boolean
        get() = preferences.getBoolean(ACTIVATION_INITIALIZED, false)

    companion object {
        private const val HAS_CREATED_VAULT = "hasCreatedVault"
        private const val TERMS_ACCEPTED_VERSION = "termsAcceptedVersion"
        private const val VAULT_ACTIVATED = "vaultHasBeenActivated"
        private const val ACTIVATION_INITIALIZED = "vaultActivationMetadataInitialized"
        private const val BIOMETRIC_ENABLED = "biometricQuickUnlockEnabled"
        private const val AUTO_LOCK_DURATION = "autoLockDuration"
        private const val CLIPBOARD_DURATION = "clipboardClearDuration"
        private const val LAST_BACKGROUND_DATE = "lastBackgroundDate"
        private const val LAST_FULL_BACKUP_DATE = "lastFullBackupDate"
        private const val BACKUP_UPDATE_REQUIRED = "backupUpdateRequired"
        private const val UNBACKED_PASSWORD_COUNT = "unbackedUpPasswordChangeCount"
        private const val UNBACKED_NOTE_COUNT = "unbackedUpNoteChangeCount"
        private const val MASTER_PASSWORD_CHANGED = "masterPasswordChangedSinceBackup"
    }
}
