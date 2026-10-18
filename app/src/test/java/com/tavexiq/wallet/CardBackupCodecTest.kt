package com.tavexiq.wallet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
    fun exportTrimsNameAndCodeWhitespace() {
        val card = LoyaltyCard(1L, "  HEMA  ", "  ABC123  ", "QR", true)
        assertEquals(LoyaltyCard(1L, "HEMA", "ABC123", "QR", true),
            CardBackupCodec.decode(CardBackupCodec.encode(listOf(card))).single())
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
    fun rejectsDuplicateCodesIgnoringCase() {
        CardBackupCodec.decode("""{"app":"TAVEXIQ","version":1,"cards":[{"id":1,"name":"First","code":"ABC123","format":"QR"},{"id":2,"name":"Second","code":"abc123","format":"QR"}]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToExportDuplicateCodes() {
        CardBackupCodec.encode(listOf(
            LoyaltyCard(1L, "First", "SAME-CODE", "QR"),
            LoyaltyCard(2L, "Second", "same-code", "CODE_128")
        ))
    }


    @Test
    fun duplicateCodeValidationIsIndependentOfDeviceLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            val cards = listOf(
                LoyaltyCard(1L, "First", "ID-CARD", "QR"),
                LoyaltyCard(2L, "Second", "id-card", "CODE_128")
            )
            try {
                CardBackupCodec.encode(cards)
                throw AssertionError("Expected duplicate codes to be rejected")
            } catch (_: IllegalArgumentException) {
                // Duplicate detection must remain case-insensitive in every locale.
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToExportDuplicateIds() {
        CardBackupCodec.encode(listOf(
            LoyaltyCard(1L, "First", "CODE-ONE", "QR"),
            LoyaltyCard(1L, "Second", "CODE-TWO", "QR")
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToExportUnsupportedFormat() {
        CardBackupCodec.encode(listOf(LoyaltyCard(1L, "Test", "ABC123", "MAGIC")))
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
