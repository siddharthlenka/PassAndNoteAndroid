package com.passandnote.vault

import com.passandnote.vault.security.PassNoteCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class CryptoCompatibilityTest {
    @Test
    fun pbkdf2MatchesCryptoKitVector() {
        val salt = ByteArray(32) { it.toByte() }
        val key = PassNoteCrypto.deriveKey("PassNote-Test-2026!", salt, 600_000)

        assertEquals(
            "7ff63979c0cee4ba94902727f4d8180f236e368138962a72c8c5225adce37d25",
            key.toHex(),
        )
        key.fill(0)
    }

    @Test
    fun aesGcmCombinedLayoutMatchesCryptoKitVector() {
        val salt = ByteArray(32) { it.toByte() }
        val key = PassNoteCrypto.deriveKey("PassNote-Test-2026!", salt, 600_000)
        val nonce = ByteArray(12) { it.toByte() }
        val plaintext = ByteArray(32) { it.toByte() }

        val combined = PassNoteCrypto.sealCombined(plaintext, key, nonce)
        assertEquals(
            "AAECAwQFBgcICQoLaQjt0rQFqBqmMwki3XQ24GGzHYA2CvM9Rdw3I/amM2SjkPBYnF9fC8GKQRF6ixfE",
            Base64.getEncoder().encodeToString(combined),
        )
        assertArrayEquals(plaintext, PassNoteCrypto.openCombined(combined, key))
        key.fill(0)
        combined.fill(0)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
