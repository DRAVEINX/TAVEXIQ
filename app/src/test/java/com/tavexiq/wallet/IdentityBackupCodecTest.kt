package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdentityBackupCodecTest {
    private val sample = listOf(IdentityDocument(42L, "Rijbewijs", "Rijbewijs", "RDW", "31-12-2030", "Voorbeeld"))

    @Test fun roundTripPreservesMetadata() {
        val password = "veilig-wachtwoord".toCharArray()
        val encoded = IdentityBackupCodec.encode(sample, password)
        assertEquals(sample, IdentityBackupCodec.decode(encoded, password))
        assert(!encoded.contains("Rijbewijs"))
        assert(!encoded.contains("RDW"))
    }

    @Test fun wrongPasswordIsRejected() {
        val encoded = IdentityBackupCodec.encode(sample, "correct-password".toCharArray())
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(encoded, "wrong-password".toCharArray())
        }
    }

    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.encode(sample, "short".toCharArray())
        }
    }

    @Test fun duplicateIdsAreRejected() {
        val duplicate = listOf(sample.first(), sample.first().copy(name = "Paspoort"))
        assertThrows(IllegalArgumentException::class.java) {
            IdentityBackupCodec.decode(IdentityBackupCodec.encode(duplicate, "long-password".toCharArray()), "long-password".toCharArray())
        }
    }
}
