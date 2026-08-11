package com.passandnote.vault.backup

import android.util.Base64
import com.passandnote.vault.data.PasswordEntry
import com.passandnote.vault.data.SecureNote
import com.passandnote.vault.data.SecureNoteTitleBuilder
import com.passandnote.vault.security.PassNoteCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ExportContentType(val rawValue: String) {
    PASSWORDS("passwords"), NOTES("notes"), FULL_VAULT("fullVault");

    companion object {
        fun fromRaw(rawValue: String) = entries.firstOrNull { it.rawValue == rawValue }
            ?: throw BackupException("This backup type cannot be imported here.")
    }
}

data class EncryptedExportContainer(
    val contentType: ExportContentType,
    val itemCount: Int,
    val createdAt: Double,
    val salt: ByteArray,
    val iterations: Int,
    val sealedPayload: ByteArray,
)

data class DecryptedBackup(
    val container: EncryptedExportContainer,
    val passwords: List<PasswordEntry>,
    val notes: List<SecureNote>,
)

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupService {
    const val MAXIMUM_IMPORT_SIZE = 25 * 1024 * 1024
    private const val MAGIC = "PASSNOTE"
    private const val FORMAT_VERSION = 1
    private const val PAYLOAD_VERSION = 1

    fun makePasswordExport(entries: List<PasswordEntry>, masterPassword: String): ByteArray {
        val payload = JSONObject()
            .put("passwords", passwordArray(entries))
            .put("payloadVersion", PAYLOAD_VERSION)
        return encrypt(payload, ExportContentType.PASSWORDS, entries.size, masterPassword)
    }

    fun makeNoteExport(notes: List<SecureNote>, masterPassword: String): ByteArray {
        val payload = JSONObject()
            .put("notes", noteArray(notes))
            .put("payloadVersion", PAYLOAD_VERSION)
        return encrypt(payload, ExportContentType.NOTES, notes.size, masterPassword)
    }

    fun makeFullVaultExport(
        passwords: List<PasswordEntry>,
        notes: List<SecureNote>,
        masterPassword: String,
    ): ByteArray {
        val payload = JSONObject()
            .put("notes", noteArray(notes))
            .put("passwords", passwordArray(passwords))
            .put("payloadVersion", PAYLOAD_VERSION)
        return encrypt(payload, ExportContentType.FULL_VAULT, passwords.size + notes.size, masterPassword)
    }

    fun decrypt(data: ByteArray, backupPassword: String, expectedType: ExportContentType): DecryptedBackup {
        if (data.size > MAXIMUM_IMPORT_SIZE) throw BackupException("This backup file is too large to import.")
        val outer = try {
            JSONObject(String(data, StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw BackupException("This is not a valid Pass&Note Vault backup file.", error)
        }

        try {
            if (outer.getString("magic") != MAGIC) throw BackupException("This is not a valid Pass&Note Vault backup file.")
            if (outer.getInt("formatVersion") != FORMAT_VERSION) throw BackupException("This backup was created with an unsupported export format.")
            val contentType = ExportContentType.fromRaw(outer.getString("contentType"))
            if (contentType != expectedType) throw BackupException("This backup type cannot be imported here.")
            val encryption = outer.getJSONObject("encryption")
            if (encryption.getString("algorithm") != "AES-256-GCM" || encryption.getInt("version") != 1) {
                throw BackupException("This backup uses unsupported encryption settings.")
            }
            val kdf = outer.getJSONObject("kdf")
            val iterations = kdf.getInt("iterations")
            if (kdf.getString("algorithm") != "PBKDF2-HMAC-SHA256" ||
                kdf.getInt("version") != 1 ||
                kdf.getInt("keyLength") != PassNoteCrypto.KEY_LENGTH ||
                iterations !in 1..10_000_000
            ) throw BackupException("This backup uses unsupported encryption settings.")

            val count = outer.getInt("itemCount")
            if (count < 0) throw BackupException("This is not a valid Pass&Note Vault backup file.")
            val salt = outer.getString("salt").decodeBase64()
            if (salt.size != PassNoteCrypto.SALT_LENGTH) throw BackupException("This backup uses unsupported encryption settings.")
            val sealedPayload = outer.getString("sealedPayload").decodeBase64()
            val container = EncryptedExportContainer(
                contentType = contentType,
                itemCount = count,
                createdAt = parseIso8601(outer.getString("createdAt")),
                salt = salt,
                iterations = iterations,
                sealedPayload = sealedPayload,
            )
            val key = PassNoteCrypto.deriveKey(backupPassword, salt, iterations)
            val plaintext = try {
                PassNoteCrypto.openCombined(sealedPayload, key)
            } catch (error: Exception) {
                throw BackupException("Unable to decrypt this backup. Check the Backup Password or verify that the file is not corrupted.", error)
            } finally {
                key.fill(0)
            }

            val payload = try {
                JSONObject(String(plaintext, StandardCharsets.UTF_8))
            } catch (error: Exception) {
                throw BackupException("This backup contains invalid data.", error)
            } finally {
                plaintext.fill(0)
            }
            if (payload.getInt("payloadVersion") != PAYLOAD_VERSION) {
                throw BackupException("This backup was created with an unsupported export format.")
            }

            val passwords = if (contentType == ExportContentType.NOTES) emptyList() else parsePasswords(payload.getJSONArray("passwords"))
            val notes = if (contentType == ExportContentType.PASSWORDS) emptyList() else parseNotes(payload.getJSONArray("notes"))
            if (passwords.isEmpty() && notes.isEmpty()) throw BackupException("This backup does not contain any records to import.")
            return DecryptedBackup(container, passwords, notes)
        } catch (error: BackupException) {
            throw error
        } catch (error: Exception) {
            throw BackupException("This is not a valid Pass&Note Vault backup file.", error)
        }
    }

    fun plaintextImportedNote(data: ByteArray): SecureNote {
        val content = try {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()
        } catch (error: Exception) {
            throw BackupException("This text file is empty.", error)
        }
        if (content.trim().isEmpty()) throw BackupException("This text file is empty.")
        return SecureNote(title = SecureNoteTitleBuilder.makeTitle(content), content = content)
    }

    private fun encrypt(payload: JSONObject, contentType: ExportContentType, itemCount: Int, password: String): ByteArray {
        val salt = PassNoteCrypto.randomBytes(PassNoteCrypto.SALT_LENGTH)
        val key = PassNoteCrypto.deriveKey(password, salt)
        val payloadData = payload.toString().toByteArray(StandardCharsets.UTF_8)
        val sealedPayload = try {
            PassNoteCrypto.sealCombined(payloadData, key)
        } finally {
            key.fill(0)
            payloadData.fill(0)
        }
        val container = JSONObject()
            .put("contentType", contentType.rawValue)
            .put("createdAt", iso8601(System.currentTimeMillis() / 1000.0))
            .put("encryption", JSONObject().put("algorithm", "AES-256-GCM").put("version", 1))
            .put("formatVersion", FORMAT_VERSION)
            .put("itemCount", itemCount)
            .put("kdf", JSONObject()
                .put("algorithm", "PBKDF2-HMAC-SHA256")
                .put("iterations", PassNoteCrypto.DEFAULT_ITERATIONS)
                .put("keyLength", PassNoteCrypto.KEY_LENGTH)
                .put("version", 1))
            .put("magic", MAGIC)
            .put("salt", salt.encodeBase64())
            .put("sealedPayload", sealedPayload.encodeBase64())
        salt.fill(0)
        sealedPayload.fill(0)
        return container.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun passwordArray(entries: List<PasswordEntry>) = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject()
                .put("accountName", entry.accountName)
                .put("createdAt", iso8601(entry.createdAt))
                .put("password", entry.password)
                .also { if (entry.username != null) it.put("username", entry.username) })
        }
    }

    private fun noteArray(notes: List<SecureNote>) = JSONArray().apply {
        notes.forEach { note ->
            put(JSONObject()
                .put("content", note.content)
                .put("createdAt", iso8601(note.createdAt))
                .put("isTitleManuallyRenamed", note.isTitleManuallyRenamed)
                .put("title", note.title)
                .put("updatedAt", iso8601(note.updatedAt)))
        }
    }

    private fun parsePasswords(array: JSONArray): List<PasswordEntry> = List(array.length()) { index ->
        val json = array.getJSONObject(index)
        val account = json.getString("accountName").trim()
        val username = if (json.has("username") && !json.isNull("username")) json.getString("username").trim().ifEmpty { null } else null
        val password = json.getString("password")
        if (account.isEmpty() || password.isEmpty()) throw BackupException("This backup contains invalid data.")
        PasswordEntry(accountName = account, username = username, password = password, createdAt = parseIso8601(json.getString("createdAt")))
    }

    private fun parseNotes(array: JSONArray): List<SecureNote> = List(array.length()) { index ->
        val json = array.getJSONObject(index)
        val content = json.getString("content")
        val rawTitle = json.getString("title").trim()
        SecureNote(
            title = rawTitle.ifEmpty { SecureNoteTitleBuilder.makeTitle(content) },
            content = content,
            isTitleManuallyRenamed = json.getBoolean("isTitleManuallyRenamed"),
            createdAt = parseIso8601(json.getString("createdAt")),
            updatedAt = parseIso8601(json.getString("updatedAt")),
        )
    }

    private fun iso8601(epochSeconds: Double): String = isoFormatter().format(Date((epochSeconds * 1000).toLong()))

    private fun parseIso8601(value: String): Double {
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        patterns.forEach { pattern ->
            runCatching { parser(pattern).parse(value) }.getOrNull()?.let { return it.time / 1000.0 }
        }
        throw BackupException("This backup contains invalid data.")
    }

    private fun isoFormatter() = parser("yyyy-MM-dd'T'HH:mm:ss'Z'")

    private fun parser(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    private fun ByteArray.encodeBase64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeBase64() = Base64.decode(this, Base64.DEFAULT)
}

object ExportFileNames {
    private fun dateStamp() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun passwords() = "PassNote_Passwords_${dateStamp()}.pnpass"
    fun singleNote() = "PassNote_Note_${dateStamp()}.pnnote"
    fun selectedNotes(count: Int) = "PassNote_Notes_${count}_${dateStamp()}.pnnote"
    fun allNotes() = "PassNote_AllNotes_${dateStamp()}.pnnote"
    fun fullVault() = "PassAndNote_Backup_${dateStamp()}.pnvault"
    fun plainText(note: SecureNote): String {
        val base = note.title.trim().ifEmpty { "PassNote_Note" }
        val sanitized = base.replace(Regex("[/\\\\?%*|\"<>:\\r\\n]+"), "-").trim().ifEmpty { "PassNote_Note" }
        return "${sanitized}_${dateStamp()}.txt"
    }
}
