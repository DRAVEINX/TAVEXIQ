package com.tavexiq.wallet

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Base64
import java.util.Locale

class EncryptedCardBackupCodecTest {
    private val password = "correct horse battery".toCharArray()
    private val cards = listOf(
        LoyaltyCard(11L, "HEMA", "12345678", "QR", true),
        LoyaltyCard(12L, "Albert Heijn", "AH-987", "CODE_128", false)
    )

    @Test
    fun encryptedExportDoesNotMutateCallerPassword() {
        val callerPassword = "correct horse battery".toCharArray()
        val originalPassword = callerPassword.copyOf()

        EncryptedCardBackupCodec.encode(cards, callerPassword)

        assertArrayEquals(originalPassword, callerPassword)
        callerPassword.fill('\u0000')
        originalPassword.fill('\u0000')
    }

    @Test
    fun encryptedRoundTripPreservesCardsAndFavorites() {
        val backup = EncryptedCardBackupCodec.encode(cards, password)
        assertEquals(cards, EncryptedCardBackupCodec.decode(backup, password))
        assertFalse(backup.contains("12345678"))
        assertFalse(backup.contains("Albert Heijn"))
    }

    @Test
    fun encryptedExportTrimsNameAndCodeWhitespace() {
        val input = listOf(LoyaltyCard(1L, "  HEMA  ", "  ABC123  ", "QR", true))
        val backup = EncryptedCardBackupCodec.encode(input, password)
        assertEquals(listOf(LoyaltyCard(1L, "HEMA", "ABC123", "QR", true)),
            EncryptedCardBackupCodec.decode(backup, password))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptedExportRejectsDuplicateCodesIgnoringCase() {
        EncryptedCardBackupCodec.encode(listOf(
            LoyaltyCard(1L, "First", "SAME-CODE", "QR"),
            LoyaltyCard(2L, "Second", "same-code", "CODE_128")
        ), password)
    }

    @Test
    fun encryptedDuplicateCodeValidationIsIndependentOfDeviceLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val duplicateCodes = listOf(
                LoyaltyCard(1L, "First", "ID-CARD", "QR"),
                LoyaltyCard(2L, "Second", "id-card", "CODE_128")
            )
            try {
                EncryptedCardBackupCodec.encode(duplicateCodes, password)
                throw AssertionError("Expected duplicate card codes to be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected: validation must not depend on the device locale.
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptedExportRejectsDuplicateIds() {
        EncryptedCardBackupCodec.encode(listOf(
            LoyaltyCard(1L, "First", "CODE-ONE", "QR"),
            LoyaltyCard(1L, "Second", "CODE-TWO", "QR")
        ), password)
    }

    @Test
    fun roundTripSupportsUnicodeAndSpecialCharacters() {
        val specialCards = listOf(
            LoyaltyCard(21L, "Café & Zo", "één-二-🙂", "QR", true),
            LoyaltyCard(22L, "Winkel \"Noord\"", "A+B/==", "CODE_128", false)
        )
        val backup = EncryptedCardBackupCodec.encode(specialCards, password)
        assertEquals(specialCards, EncryptedCardBackupCodec.decode(backup, password))
        assertFalse(backup.contains("Café & Zo"))
        assertFalse(backup.contains("één-二-🙂"))
    }

    @Test
    fun emptyWalletCanBeBackedUpAndRestored() {
        val backup = EncryptedCardBackupCodec.encode(emptyList(), password)
        assertEquals(emptyList<LoyaltyCard>(), EncryptedCardBackupCodec.decode(backup, password))
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
        EncryptedCardBackupCodec.decode(
            EncryptedCardBackupCodec.encode(cards, password),
            "incorrect password".toCharArray()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTamperedCiphertext() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
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
    fun rejectsCiphertextShorterThanGcmTag() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("ciphertext", Base64.getEncoder().encodeToString(ByteArray(15)))
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingSalt() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.remove("salt")
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingNonce() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.remove("nonce")
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingCiphertext() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.remove("ciphertext")
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedFormatVersion() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("version", 999)
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsChangedKdfIterations() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("iterations", 1)
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedCipher() {
        val root = JSONObject(EncryptedCardBackupCodec.encode(cards, password))
        root.put("cipher", "AES-CBC")
        EncryptedCardBackupCodec.decode(root.toString(), password)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedJson() {
        EncryptedCardBackupCodec.decode("{not-json", password)
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
