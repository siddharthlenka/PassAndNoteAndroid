package com.passandnote.vault.data

import java.security.SecureRandom
import java.util.UUID

data class PasswordEntry(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val accountName: String,
    val username: String? = null,
    val password: String,
    val createdAt: Double = System.currentTimeMillis() / 1000.0,
)

data class SecureNote(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val title: String,
    val content: String,
    val isTitleManuallyRenamed: Boolean = false,
    val createdAt: Double = System.currentTimeMillis() / 1000.0,
    val updatedAt: Double = System.currentTimeMillis() / 1000.0,
)

data class PasswordValidationResult(
    val hasMinimumLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasNumber: Boolean,
    val hasSpecialCharacter: Boolean,
) {
    val isStrong: Boolean
        get() = hasMinimumLength && hasUppercase && hasLowercase && hasNumber && hasSpecialCharacter
}

object PasswordValidator {
    fun validate(password: String) = PasswordValidationResult(
        hasMinimumLength = password.codePointCount(0, password.length) >= 12,
        hasUppercase = Regex("[A-Z]").containsMatchIn(password),
        hasLowercase = Regex("[a-z]").containsMatchIn(password),
        hasNumber = Regex("[0-9]").containsMatchIn(password),
        hasSpecialCharacter = Regex("[^A-Za-z0-9]").containsMatchIn(password),
    )
}

object SecureNoteTitleBuilder {
    fun makeTitle(content: String): String {
        val line = content.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
        return (line ?: "Untitled Note").take(60)
    }
}

enum class GeneratedPasswordLength(val length: Int) {
    SIXTEEN(16), THIRTY_TWO(32), SIXTY_FOUR(64),
}

object SecurePasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val NUMBERS = "0123456789"
    private const val SPECIAL = "!@#$%^&*()-_=+[]{};:,.?/"
    private const val ALL = UPPERCASE + LOWERCASE + NUMBERS + SPECIAL
    private val random = SecureRandom()

    fun generate(length: GeneratedPasswordLength): String {
        val chars = mutableListOf(
            UPPERCASE.randomSecure(),
            LOWERCASE.randomSecure(),
            NUMBERS.randomSecure(),
            SPECIAL.randomSecure(),
        )
        repeat(length.length - chars.size) { chars += ALL.randomSecure() }
        for (index in chars.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = chars[index]
            chars[index] = chars[other]
            chars[other] = value
        }
        return chars.joinToString("")
    }

    private fun String.randomSecure(): Char = this[random.nextInt(length)]
}

enum class AutoLockDuration(val rawValue: String, val title: String, val thresholdMillis: Long) {
    IMMEDIATELY("immediately", "Immediately", 0),
    THIRTY_SECONDS("thirtySeconds", "After 30 Seconds", 30_000),
    ONE_MINUTE("oneMinute", "After 1 Minute", 60_000),
    FIVE_MINUTES("fiveMinutes", "After 5 Minutes", 300_000);

    companion object {
        fun fromRaw(value: String?) = entries.firstOrNull { it.rawValue == value } ?: IMMEDIATELY
    }
}

enum class ClipboardClearDuration(val rawValue: String, val title: String, val expirationMillis: Long?) {
    THIRTY_SECONDS("thirtySeconds", "After 30 Seconds", 30_000),
    ONE_MINUTE("oneMinute", "After 1 Minute", 60_000),
    TWO_MINUTES("twoMinutes", "After 2 Minutes", 120_000),
    WHEN_BACKGROUND("whenAppGoesToBackground", "When App Goes to Background", null);

    companion object {
        fun fromRaw(value: String?) = entries.firstOrNull { it.rawValue == value } ?: THIRTY_SECONDS
    }
}

enum class BackupStatus(val title: String) {
    NEVER_BACKED_UP("Never Backed Up"),
    UP_TO_DATE("Up to Date"),
    UPDATE_REQUIRED("Backup Update Required"),
}
