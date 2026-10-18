package com.tavexiq.wallet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoyaltyCardDuplicatesTest {
    private val cards = listOf(
        LoyaltyCard(1L, "HEMA", "123456", "QR"),
        LoyaltyCard(2L, "Albert Heijn", "ABCD-99", "CODE_128")
    )

    @Test
    fun detectsExactDuplicateCode() {
        assertTrue(hasDuplicateCardCode(cards, "123456"))
    }

    @Test
    fun ignoresSurroundingWhitespaceAndCase() {
        assertTrue(hasDuplicateCardCode(cards, "  abcd-99 "))
    }

    @Test
    fun allowsDifferentCode() {
        assertFalse(hasDuplicateCardCode(cards, "999999"))
    }

    @Test
    fun allowsCurrentCardToKeepItsOwnCodeWhenEditing() {
        assertFalse(hasDuplicateCardCode(cards, "123456", excludingId = 1L))
    }

    @Test
    fun stillDetectsAnotherCardsCodeWhenEditing() {
        assertTrue(hasDuplicateCardCode(cards, "ABCD-99", excludingId = 1L))
    }

    @Test
    fun blankCodeIsNotReportedAsDuplicate() {
        assertFalse(hasDuplicateCardCode(cards, "   "))
    }
}
