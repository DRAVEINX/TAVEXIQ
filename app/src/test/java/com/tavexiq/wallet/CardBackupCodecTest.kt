package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardBackupCodecTest {
    @Test
    fun roundTripPreservesCardFieldsAndFavorites() {
        val cards = listOf(
            LoyaltyCard(11L, "HEMA", "12345678", "QR", true),
            LoyaltyCard(12L, "Albert Heijn", "AH-987", "CODE_128", false)
        )

        val restored = CardBackupCodec.decode(CardBackupCodec.encode(cards))

        assertEquals(cards, restored)
    }

    @Test
    fun emptyBackupRoundTrips() {
        assertTrue(CardBackupCodec.decode(CardBackupCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun oldOrMissingFavoriteFieldDefaultsToFalse() {
        val json = """{"app":"TAVEXIQ","version":1,"cards":[{"id":7,"name":"Test","code":"abc123","format":"QR"}]}"""

        val restored = CardBackupCodec.decode(json)

        assertFalse(restored.single().favorite)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBackupFromAnotherApp() {
        CardBackupCodec.decode("""{"app":"Other","version":1,"cards":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedBarcodeFormat() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":[{"id":1,"name":"Test","code":"abc","format":"MAGIC"}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateIds() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":[{"id":1,"name":"First","code":"abc","format":"QR"},{"id":1,"name":"Second","code":"def","format":"QR"}]}""")
    }
}
