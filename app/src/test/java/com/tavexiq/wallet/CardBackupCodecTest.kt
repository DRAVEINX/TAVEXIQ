package com.tavexiq.wallet

import org.json.JSONArray
import org.json.JSONObject
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
        assertEquals(cards, CardBackupCodec.decode(CardBackupCodec.encode(cards)))
    }

    @Test
    fun emptyBackupRoundTrips() {
        assertTrue(CardBackupCodec.decode(CardBackupCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun oldOrMissingFavoriteFieldDefaultsToFalse() {
        val json = """{"app":"TAVEXIQ","version":1,"cards":[{"id":7,"name":"Test","code":"abc123","format":"QR"}]}"""
        assertFalse(CardBackupCodec.decode(json).single().favorite)
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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCardId() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":[{"id":0,"name":"Test","code":"abc123","format":"QR"}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCardData() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":[{"id":1,"name":"","code":"","format":"QR"}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedBackupVersion() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":99,"cards":[]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanMaximumCardsOnExport() {
        val tooManyCards = (1L..501L).map { id ->
            LoyaltyCard(id, "Store $id", "CODE$id", "QR")
        }
        CardBackupCodec.encode(tooManyCards)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanMaximumCardsOnImport() {
        val items = JSONArray()
        for (id in 1..501) {
            items.put(JSONObject()
                .put("id", id)
                .put("name", "Store $id")
                .put("code", "CODE$id")
                .put("format", "QR"))
        }
        val json = JSONObject()
            .put("app", "TAVEXIQ")
            .put("version", 1)
            .put("cards", items)
            .toString()
        CardBackupCodec.decode(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedJsonWithConsistentException() {
        CardBackupCodec.decode("{not-json")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingCardsArray() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCardWithMissingRequiredField() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":[{"id":1,"name":"Test","format":"QR"}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonObjectCardEntry() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":["not-a-card"]}""")
    }
}
