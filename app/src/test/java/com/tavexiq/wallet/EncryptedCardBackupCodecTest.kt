package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncryptedCardBackupCodecTest {
    private val password = "correct horse battery".toCharArray()
    private val cards = listOf(
        LoyaltyCard(11L, "HEMA", "12345678", "QR", true),
        LoyaltyCard(12L, "Albert Heijn", "AH-987", "CODE_128", false)
    )

    @Test
    fun encryptedRoundTripPreservesCardsAndFavorites() {
        val backup = EncryptedCardBackupCodec.encode(cards, password)
        assertEquals(cards, EncryptedCardBackupCodec.decode(backup, password))
        assertFalse(backup.contains("12345678"))
        assertFalse(backup.contains("Albert Heijn"))
    }

    @Test
    fun eachExportUsesFreshSaltAndNonce() {
        val first = EncryptedCardBackupCodec.encode(cards, password)
        val second = EncryptedCardBackupCodec.encode(cards, password)
        assertNotEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortPasswordOnExport() {
        EncryptedCardBackupCodec.encode(cards, "short".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPassword() {
        val backup = EncryptedCardBackupCodec.encode(cards, password)
        EncryptedCardBackupCodec.decode(backup, "incorrect password".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTamperedCiphertext() {
        val backup = EncryptedCardBackupCodec.encode(cards, password)
        val root = org.json.JSONObject(backup)
        val bytes = java.util.Base64.getDecoder().decode(root.getString("ciphertext"))
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        root.put("ciphertext", java.util.Base64.getEncoder().encodeToString(bytes))
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnotherAppEnvelope() {
        EncryptedCardBackupCodec.decode("""{"app":"OTHER","version":1}""", password)
    }
}
