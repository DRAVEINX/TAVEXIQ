package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EncryptedCardBackupCodecTest {
    private val sample = listOf(
        LoyaltyCard(11L, "HEMA", "12345678", "QR", true),
        LoyaltyCard(12L, "Albert Heijn", "AH-987", "CODE_128", false)
    )

    @Test fun roundTripPreservesFieldsAndHidesCardCodes() {
        val encoded = EncryptedCardBackupCodec.encode(sample, "veilig-wachtwoord".toCharArray())
        assertEquals(sample, EncryptedCardBackupCodec.decode(encoded, "veilig-wachtwoord".toCharArray()))
        assertFalse(encoded.contains("HEMA"))
        assertFalse(encoded.contains("12345678"))
        assertFalse(encoded.contains("AH-987"))
    }

    @Test fun eachBackupUsesFreshSaltAndIv() {
        val first = EncryptedCardBackupCodec.encode(sample, "veilig-wachtwoord".toCharArray()).lines()
        val second = EncryptedCardBackupCodec.encode(sample, "veilig-wachtwoord".toCharArray()).lines()
        assertNotEquals(first[2], second[2])
        assertNotEquals(first[3], second[3])
    }

    @Test fun emptyBackupRoundTrips() {
        assertEquals(emptyList<LoyaltyCard>(),
            EncryptedCardBackupCodec.decode(EncryptedCardBackupCodec.encode(emptyList(), "veilig-wachtwoord".toCharArray()),
                "veilig-wachtwoord".toCharArray()))
    }

    @Test fun wrongPasswordAndTamperingAreRejected() {
        val encoded = EncryptedCardBackupCodec.encode(sample, "correct-password".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCardBackupCodec.decode(encoded, "wrong-password".toCharArray())
        }
        val lines = encoded.lines().toMutableList()
        lines[4] = (if (lines[4].first() == 'A') "B" else "A") + lines[4].drop(1)
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCardBackupCodec.decode(lines.joinToString("\n"), "correct-password".toCharArray())
        }
    }

    @Test fun rejectsShortPasswordAndDuplicateIds() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCardBackupCodec.encode(sample, "short".toCharArray())
        }
        val duplicate = listOf(sample.first(), sample.first().copy(name = "Andere winkel"))
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedCardBackupCodec.encode(duplicate, "veilig-wachtwoord".toCharArray())
        }
    }
}
