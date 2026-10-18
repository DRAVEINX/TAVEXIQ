package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdentityBackupCodecTest {
    private val sample = listOf(IdentityDocument(42L, "Rijbewijs", "Rijbewijs", "RDW", "31-12-2030", "Voorbeeld"))
    private val password = "veilig-wachtwoord".toCharArray()

    @Test fun roundTripPreservesMetadataWithoutExposingPlaintext() {
        val encoded = IdentityBackupCodec.encode(sample, password)
        assertEquals(sample, IdentityBackupCodec.decode(encoded, password))
        assertFalse(encoded.contains("Rijbewijs"))
        assertFalse(encoded.contains("RDW"))
        assertFalse(encoded.contains("Voorbeeld"))
    }

    @Test fun eachBackupUsesFreshSaltAndIv() {
        val first = IdentityBackupCodec.encode(sample, password).lines()
        val second = IdentityBackupCodec.encode(sample, password).lines()
        assertNotEquals(first[2], second[2])
        assertNotEquals(first[3], second[3])
    }

    @Test fun emptyVaultCanBeBackedUpAndRestored() {
        val encoded = IdentityBackupCodec.encode(emptyList(), password)
        assertEquals(emptyList<IdentityDocument>(), IdentityBackupCodec.decode(encoded, password))
    }

    @Test fun wrongPasswordIsRejected() {
        val encoded = IdentityBackupCodec.encode(sample, "correct-password".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(encoded, "wrong-password".toCharArray())
        }
    }

    @Test fun tamperedCiphertextIsRejected() {
        val encoded = IdentityBackupCodec.encode(sample, password)
        val lines = encoded.lines().toMutableList()
        val encrypted = lines[4]
        lines[4] = (if (encrypted.first() == 'A') "B" else "A") + encrypted.drop(1)
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(lines.joinToString("\n"), password)
        }
    }

    @Test fun unsupportedHeaderAndTruncatedBackupAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode("OTHER-FORMAT\n1\na\nb\nc", password)
        }
        val encoded = IdentityBackupCodec.encode(sample, password)
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(encoded.lines().take(4).joinToString("\n"), password)
        }
    }

    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.encode(sample, "short".toCharArray())
        }
    }

    @Test fun shortPasswordIsRejectedOnRestore() {
        val encoded = IdentityBackupCodec.encode(sample, password)
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(encoded, "short".toCharArray())
        }
    }

    @Test fun oversizedBackupIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode("x".repeat(1_500_001), password)
        }
    }

    @Test fun malformedBase64FieldsAreRejectedClearly() {
        val encoded = IdentityBackupCodec.encode(sample, password)
        val lines = encoded.lines().toMutableList()
        lines[2] = "not-valid-base64!"
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(lines.joinToString("\n"), password)
        }
    }

    @Test fun decodedSaltAndIvWithInvalidLengthsAreRejected() {
        val encoded = IdentityBackupCodec.encode(sample, password)
        val original = encoded.lines()

        val shortSalt = original.toMutableList().apply {
            this[2] = java.util.Base64.getEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(shortSalt.joinToString("\n"), password)
        }

        val shortIv = original.toMutableList().apply {
            this[3] = java.util.Base64.getEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(shortIv.joinToString("\n"), password)
        }
    }


    @Test fun tooManyDocumentsAreRejectedBeforeEncoding() {
        val tooMany = (1L..201L).map { IdentityDocument(it, "Document $it", "ID", "", "", "") }
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.encode(tooMany, password)
        }
    }

    @Test fun invalidExpiryDateIsRejectedBeforeEncoding() {
        val invalidDate = listOf(sample.first().copy(expiryDate = "31-02-2030"))
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.encode(invalidDate, password)
        }
    }

    @Test fun nonPositiveIdsAreRejectedBeforeEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.encode(listOf(sample.first().copy(id = 0L)), password)
        }
    }

    @Test fun duplicateIdsAreRejectedBeforeEncoding() {
        val duplicate = listOf(sample.first(), sample.first().copy(name = "Paspoort"))
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.encode(duplicate, password)
        }
    }

}
