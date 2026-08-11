package com.passandnote.vault.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.passandnote.vault.data.VaultMetadata
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PassNoteCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val KEY_LENGTH = 32
    const val SALT_LENGTH = 32
    const val GCM_NONCE_LENGTH = 12
    const val GCM_TAG_BITS = 128
    private val random = SecureRandom()

    fun randomBytes(count: Int) = ByteArray(count).also(random::nextBytes)

    /** PBKDF2-HMAC-SHA256 using the exact UTF-8 password bytes used by the iOS app. */
    fun deriveKey(password: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty." }
        require(iterations > 0) { "Iterations must be positive." }

        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        passwordBytes.fill(0)

        val firstBlock = ByteBuffer.allocate(salt.size + 4)
            .put(salt)
            .putInt(1)
            .array()
        var u = mac.doFinal(firstBlock)
        firstBlock.fill(0)
        val result = u.copyOf()

        repeat(iterations - 1) {
            val next = mac.doFinal(u)
            u.fill(0)
            u = next
            for (index in result.indices) result[index] = (result[index].toInt() xor u[index].toInt()).toByte()
        }
        u.fill(0)
        return result
    }

    /** CryptoKit-compatible layout: 12-byte nonce || ciphertext || 16-byte tag. */
    fun sealCombined(plaintext: ByteArray, key: ByteArray, nonce: ByteArray = randomBytes(GCM_NONCE_LENGTH)): ByteArray {
        require(key.size == KEY_LENGTH)
        require(nonce.size == GCM_NONCE_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return nonce + cipher.doFinal(plaintext)
    }

    fun openCombined(combined: ByteArray, key: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH)
        require(combined.size >= GCM_NONCE_LENGTH + GCM_TAG_BITS / 8)
        val nonce = combined.copyOfRange(0, GCM_NONCE_LENGTH)
        val ciphertextAndTag = combined.copyOfRange(GCM_NONCE_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertextAndTag).also {
            nonce.fill(0)
            ciphertextAndTag.fill(0)
        }
    }
}

data class VaultKeyEnvelope(
    val version: Int,
    val salt: ByteArray,
    val iterations: Int,
    val wrappedVaultKey: ByteArray,
) {
    fun unwrap(masterPassword: String): ByteArray {
        require(version == CURRENT_VERSION) { "Unsupported vault envelope version." }
        val keyEncryptionKey = PassNoteCrypto.deriveKey(masterPassword, salt, iterations)
        return try {
            PassNoteCrypto.openCombined(wrappedVaultKey, keyEncryptionKey).also {
                require(it.size == PassNoteCrypto.KEY_LENGTH) { "Invalid vault key." }
            }
        } finally {
            keyEncryptionKey.fill(0)
        }
    }

    fun toJson(): String = JSONObject()
        .put("version", version)
        .put("salt", salt.base64())
        .put("iterations", iterations)
        .put("wrappedVaultKey", wrappedVaultKey.base64())
        .toString()

    companion object {
        const val CURRENT_VERSION = 1

        fun create(masterPassword: String, vaultKey: ByteArray): VaultKeyEnvelope {
            require(vaultKey.size == PassNoteCrypto.KEY_LENGTH)
            val salt = PassNoteCrypto.randomBytes(PassNoteCrypto.SALT_LENGTH)
            val keyEncryptionKey = PassNoteCrypto.deriveKey(masterPassword, salt)
            return try {
                VaultKeyEnvelope(
                    version = CURRENT_VERSION,
                    salt = salt,
                    iterations = PassNoteCrypto.DEFAULT_ITERATIONS,
                    wrappedVaultKey = PassNoteCrypto.sealCombined(vaultKey, keyEncryptionKey),
                )
            } finally {
                keyEncryptionKey.fill(0)
            }
        }

        fun fromJson(value: String): VaultKeyEnvelope {
            val json = JSONObject(value)
            return VaultKeyEnvelope(
                version = json.getInt("version"),
                salt = json.getString("salt").base64Bytes(),
                iterations = json.getInt("iterations"),
                wrappedVaultKey = json.getString("wrappedVaultKey").base64Bytes(),
            )
        }
    }
}

/** AndroidKeyStore-backed equivalent of the iOS ThisDeviceOnly Keychain envelope item. */
class VaultKeyEnvelopeStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("passnote_secure_envelope", Context.MODE_PRIVATE)

    fun save(envelope: VaultKeyEnvelope) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, envelopeStorageKey())
        val encrypted = cipher.iv + cipher.doFinal(envelope.toJson().toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString(ENVELOPE_VALUE, encrypted.base64()).commit()
        encrypted.fill(0)
    }

    fun read(): VaultKeyEnvelope? {
        val encoded = preferences.getString(ENVELOPE_VALUE, null) ?: return null
        val encrypted = encoded.base64Bytes()
        return try {
            val iv = encrypted.copyOfRange(0, PassNoteCrypto.GCM_NONCE_LENGTH)
            val ciphertext = encrypted.copyOfRange(PassNoteCrypto.GCM_NONCE_LENGTH, encrypted.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, envelopeStorageKey(), GCMParameterSpec(PassNoteCrypto.GCM_TAG_BITS, iv))
            val cleartext = cipher.doFinal(ciphertext)
            try {
                VaultKeyEnvelope.fromJson(String(cleartext, StandardCharsets.UTF_8))
            } finally {
                cleartext.fill(0)
                iv.fill(0)
                ciphertext.fill(0)
            }
        } finally {
            encrypted.fill(0)
        }
    }

    fun delete() {
        preferences.edit().remove(ENVELOPE_VALUE).commit()
    }

    private fun envelopeStorageKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ENVELOPE_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val builder = KeyGenParameterSpec.Builder(
            ENVELOPE_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        // Android 15 fixed historical unlocked-device key issues present on Android 12-14.
        if (Build.VERSION.SDK_INT >= 35) builder.setUnlockedDeviceRequired(true)

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(builder.build())
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ENVELOPE_KEY_ALIAS = "passnote-envelope-store-v1"
        const val ENVELOPE_VALUE = "vault.keyEnvelope"
    }
}

/**
 * Stores only an AES-GCM wrapped vault key. The wrapping key is auth-per-use,
 * BIOMETRIC_STRONG-only, and invalidated whenever biometric enrollment changes.
 */
class BiometricVaultKeyService(
    private val context: Context,
    private val metadata: VaultMetadata,
) {
    private val preferences = context.getSharedPreferences("passnote_biometric_key", Context.MODE_PRIVATE)

    val isEnabled: Boolean
        get() {
            if (!metadata.biometricQuickUnlockEnabled ||
                !preferences.contains(WRAPPED_KEY) ||
                !preferences.contains(WRAPPED_IV)
            ) return false
            return runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                    .containsAlias(BIOMETRIC_KEY_ALIAS)
            }.getOrDefault(false)
        }

    fun prepareEnableCipher(): Cipher {
        disable()
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        keyGenerator.init(builder.build())
        val key = keyGenerator.generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    fun completeEnable(cipher: Cipher, vaultKey: ByteArray) {
        require(vaultKey.size == PassNoteCrypto.KEY_LENGTH)
        val wrapped = cipher.doFinal(vaultKey)
        val iv = cipher.iv
        try {
            check(iv.size == PassNoteCrypto.GCM_NONCE_LENGTH) { "Invalid biometric wrapping IV." }
            check(
                preferences.edit()
                    .putString(WRAPPED_KEY, wrapped.base64())
                    .putString(WRAPPED_IV, iv.base64())
                    .commit(),
            ) { "Biometric vault key could not be persisted." }
            check(metadata.persistBiometricQuickUnlockEnabled(true)) {
                "Biometric preference could not be persisted."
            }
            check(isEnabled) { "Biometric key storage is incomplete." }
        } finally {
            wrapped.fill(0)
            iv.fill(0)
        }
    }

    fun prepareUnlockCipher(): Cipher {
        check(isEnabled) { "Biometric quick unlock is disabled." }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
            ?: run {
                disable()
                error("Biometric key is missing.")
            }
        val iv = preferences.getString(WRAPPED_IV, null)?.base64Bytes()
            ?: run {
                disable()
                error("Biometric key data is missing.")
            }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(PassNoteCrypto.GCM_TAG_BITS, iv))
            }
        } catch (error: Exception) {
            disable()
            throw error
        } finally {
            iv.fill(0)
        }
    }

    fun completeUnlock(cipher: Cipher): ByteArray {
        val wrapped = preferences.getString(WRAPPED_KEY, null)?.base64Bytes()
            ?: run {
                disable()
                error("Biometric key data is missing.")
            }
        return try {
            cipher.doFinal(wrapped).also {
                if (it.size != PassNoteCrypto.KEY_LENGTH) {
                    it.fill(0)
                    disable()
                    error("Invalid biometric vault key.")
                }
            }
        } finally {
            wrapped.fill(0)
        }
    }

    fun disable() {
        preferences.edit().clear().commit()
        metadata.persistBiometricQuickUnlockEnabled(false)
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val BIOMETRIC_KEY_ALIAS = "passnote-biometric-vault-key-v1"
        const val WRAPPED_KEY = "wrapped-vault-key"
        const val WRAPPED_IV = "wrapped-vault-key-iv"
    }
}

internal fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
internal fun String.base64Bytes(): ByteArray = Base64.decode(this, Base64.DEFAULT)
