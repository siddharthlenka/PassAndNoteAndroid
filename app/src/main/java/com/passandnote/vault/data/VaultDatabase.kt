package com.passandnote.vault.data

import android.content.Context
import com.passandnote.vault.security.BiometricVaultKeyService
import com.passandnote.vault.security.PassNoteCrypto
import com.passandnote.vault.security.VaultKeyEnvelope
import com.passandnote.vault.security.VaultKeyEnvelopeStore
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

class VaultDatabase(private val context: Context) {
    private val metadata = VaultMetadata(context)
    private val envelopeStore = VaultKeyEnvelopeStore(context)
    private val biometricService = BiometricVaultKeyService(context, metadata)
    private var database: SQLiteDatabase? = null

    val isOpen: Boolean get() = database?.isOpen == true

    init {
        System.loadLibrary("sqlcipher")
    }

    @Synchronized
    fun create(masterPassword: String) {
        val vaultKey = PassNoteCrypto.randomBytes(PassNoteCrypto.KEY_LENGTH)
        try {
            val envelope = VaultKeyEnvelope.create(masterPassword, vaultKey)
            envelopeStore.save(envelope)
            openDatabase(vaultKey)
            metadata.prepareNewVaultLifecycle()
        } catch (error: Exception) {
            envelopeStore.delete()
            close()
            throw error
        } finally {
            vaultKey.fill(0)
        }
    }

    @Synchronized
    fun open(masterPassword: String) {
        val envelope = envelopeStore.read() ?: error("Missing vault key envelope.")
        val vaultKey = envelope.unwrap(masterPassword)
        try {
            openDatabase(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }

    @Synchronized
    fun open(vaultKey: ByteArray) {
        require(vaultKey.size == PassNoteCrypto.KEY_LENGTH)
        openDatabase(vaultKey)
    }

    fun recoverVaultKey(masterPassword: String): ByteArray {
        val envelope = envelopeStore.read() ?: error("Missing vault key envelope.")
        return envelope.unwrap(masterPassword)
    }

    @Synchronized
    fun changeMasterPassword(currentPassword: String, newPassword: String) {
        val vaultKey = recoverVaultKey(currentPassword)
        try {
            envelopeStore.save(VaultKeyEnvelope.create(newPassword, vaultKey))
        } finally {
            vaultKey.fill(0)
        }
    }

    @Synchronized
    fun close() {
        database?.close()
        database = null
    }

    @Synchronized
    fun resetUnusedVault() {
        check(metadata.canResetUnusedVault) { "An activated vault cannot be reset." }
        close()
        databaseFiles().forEach { file -> if (file.exists() && !file.delete()) error("Could not remove vault data.") }
        envelopeStore.delete()
        biometricService.disable()
        metadata.clearUnusedVaultLifecycle()
    }

    @Synchronized
    fun fetchPasswords(): List<PasswordEntry> {
        val result = mutableListOf<PasswordEntry>()
        requireDatabase().rawQuery(
            "SELECT id, accountName, username, password, createdAt FROM password_entries ORDER BY createdAt DESC",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PasswordEntry(
                    id = cursor.getString(0),
                    accountName = cursor.getString(1),
                    username = if (cursor.isNull(2)) null else cursor.getString(2),
                    password = cursor.getString(3),
                    createdAt = cursor.getDouble(4),
                )
            }
        }
        return result
    }

    @Synchronized
    fun insertPassword(accountName: String, username: String?, password: String) {
        insertPassword(requireDatabase(), PasswordEntry(accountName = accountName, username = username, password = password))
    }

    @Synchronized
    fun importPasswords(entries: List<PasswordEntry>) = transaction { db ->
        entries.forEach { insertPassword(db, it) }
    }

    @Synchronized
    fun deletePassword(id: String) {
        requireDatabase().execSQL("DELETE FROM password_entries WHERE id = ?", arrayOf(id))
    }

    @Synchronized
    fun deletePasswords(ids: Set<String>) = transaction { db ->
        ids.forEach { db.execSQL("DELETE FROM password_entries WHERE id = ?", arrayOf(it)) }
    }

    @Synchronized
    fun fetchNotes(): List<SecureNote> {
        val result = mutableListOf<SecureNote>()
        requireDatabase().rawQuery(
            "SELECT id, title, content, isTitleManuallyRenamed, createdAt, updatedAt FROM secure_notes ORDER BY updatedAt DESC",
            emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SecureNote(
                    id = cursor.getString(0),
                    title = cursor.getString(1),
                    content = cursor.getString(2),
                    isTitleManuallyRenamed = cursor.getInt(3) == 1,
                    createdAt = cursor.getDouble(4),
                    updatedAt = cursor.getDouble(5),
                )
            }
        }
        return result
    }

    @Synchronized
    fun createEmptyNote(): SecureNote {
        val note = SecureNote(title = "Untitled Note", content = "")
        insertNote(requireDatabase(), note)
        return note
    }

    @Synchronized
    fun importNotes(notes: List<SecureNote>) = transaction { db ->
        notes.forEach { insertNote(db, it) }
    }

    @Synchronized
    fun importFullVault(passwords: List<PasswordEntry>, notes: List<SecureNote>) = transaction { db ->
        passwords.forEach { insertPassword(db, it) }
        notes.forEach { insertNote(db, it) }
    }

    /** Returns true only for the first whitespace-empty -> nonempty content transition. */
    @Synchronized
    fun updateNoteContent(noteID: String, content: String): Boolean {
        val db = requireDatabase()
        val oldContent = db.rawQuery("SELECT content FROM secure_notes WHERE id = ?", arrayOf(noteID)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else ""
        }
        val wasEmpty = oldContent.trim().isEmpty()
        val isNowNonEmpty = content.trim().isNotEmpty()
        db.execSQL(
            """
            UPDATE secure_notes
            SET content = ?,
                title = CASE WHEN isTitleManuallyRenamed = 0 THEN ? ELSE title END,
                updatedAt = ?
            WHERE id = ?
            """.trimIndent(),
            arrayOf(content, SecureNoteTitleBuilder.makeTitle(content), nowSeconds(), noteID),
        )
        return wasEmpty && isNowNonEmpty
    }

    @Synchronized
    fun renameNote(noteID: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        requireDatabase().execSQL(
            "UPDATE secure_notes SET title = ?, isTitleManuallyRenamed = 1, updatedAt = ? WHERE id = ?",
            arrayOf(trimmed, nowSeconds(), noteID),
        )
    }

    @Synchronized
    fun deleteNote(noteID: String) {
        requireDatabase().execSQL("DELETE FROM secure_notes WHERE id = ?", arrayOf(noteID))
    }

    @Synchronized
    fun deleteNoteIfEmpty(noteID: String, content: String) {
        if (content.trim().isEmpty()) deleteNote(noteID)
    }

    private fun openDatabase(vaultKey: ByteArray) {
        if (database?.isOpen == true) return
        val file = databaseFile()
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, vaultKey, null, null, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON")
            migrate(db)
            database = db
        } catch (error: Exception) {
            db.close()
            throw error
        }
    }

    private fun migrate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS password_entries (
                id TEXT PRIMARY KEY NOT NULL,
                accountName TEXT NOT NULL,
                username TEXT,
                password TEXT NOT NULL,
                createdAt DOUBLE NOT NULL
            )
            """.trimIndent(),
        )
        val hasUsername = db.rawQuery("PRAGMA table_info(password_entries)", emptyArray()).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(nameIndex) == "username") found = true
            found
        }
        if (!hasUsername) db.execSQL("ALTER TABLE password_entries ADD COLUMN username TEXT")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS secure_notes (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                isTitleManuallyRenamed INTEGER NOT NULL DEFAULT 0,
                createdAt DOUBLE NOT NULL,
                updatedAt DOUBLE NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun insertPassword(db: SQLiteDatabase, entry: PasswordEntry) {
        db.execSQL(
            "INSERT INTO password_entries (id, accountName, username, password, createdAt) VALUES (?, ?, ?, ?, ?)",
            arrayOf(entry.id, entry.accountName, entry.username, entry.password, entry.createdAt),
        )
    }

    private fun insertNote(db: SQLiteDatabase, note: SecureNote) {
        db.execSQL(
            """
            INSERT INTO secure_notes (id, title, content, isTitleManuallyRenamed, createdAt, updatedAt)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(note.id, note.title, note.content, if (note.isTitleManuallyRenamed) 1 else 0, note.createdAt, note.updatedAt),
        )
    }

    private inline fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = requireDatabase()
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun requireDatabase() = database?.takeIf { it.isOpen } ?: error("Database is not open.")

    private fun databaseFile(): File = File(File(context.noBackupFilesDir, "PrivateVault"), "vault.sqlite")

    private fun databaseFiles(): List<File> {
        val file = databaseFile()
        return listOf(file, File(file.path + "-wal"), File(file.path + "-shm"), File(file.path + "-journal"))
    }

    private fun nowSeconds() = System.currentTimeMillis() / 1000.0
}
