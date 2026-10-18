package com.tavexiq.wallet

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64

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
    fun rejectsShortPasswordOnImport() {
        EncryptedCardBackupCodec.decode(
            EncryptedCardBackupCodec.encode(cards, password),
            "short".toCharArray()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPassword() {
        val backup = EncryptedCardBackupCodec.encode(cards, password)
        EncryptedCardBackupCodec.decode(backup, "incorrect password".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTamperedCiphertext() {
        val backup = EncryptedCardBackupCodec.encode(cards, password)
        val root = JSONObject(backup)
        val bytes = Base64.getDecoder().decode(root.getString("ciphertext"))
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        root.put("ciphertext", Base64.getEncoder().encodeToString(bytes))
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedSalt() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("salt", "not-valid-base64!")
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSaltWithWrongLength() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("salt", Base64.getEncoder().encodeToString(ByteArray(8)))
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonceWithWrongLength() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("nonce", Base64.getEncoder().encodeToString(ByteArray(8)))
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedCiphertextBase64() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("ciphertext", "not-valid-base64!")
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedBackupBeforeParsing() {
        EncryptedCardBackupCodec.decode(" ".repeat(2_000_001), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnotherAppEnvelope() {
        EncryptedCardBackupCodec.decode("""{"app":"OTHER","version":1}""", password)
    }
}
