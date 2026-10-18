package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LoyaltyCardRulesTest {
    @Test fun rejectsBlankName() {
        assertEquals("Vul een kaartnaam in.", LoyaltyCardRules.validate(" ", "12345"))
    }

    @Test fun rejectsBlankCode() {
        assertEquals("Vul een kaartcode in.", LoyaltyCardRules.validate("Winkel", " "))
    }

    @Test fun rejectsCodeOverLimit() {
        assertNotNull(LoyaltyCardRules.validate("Winkel", "x".repeat(501)))
    }

    @Test fun acceptsCodeAtMaximumLength() {
        assertNull(LoyaltyCardRules.validate("Winkel", "x".repeat(LoyaltyCardRules.MAX_CODE_LENGTH)))
    }

    @Test fun acceptsUnicodeAndWhitespaceAroundValidName() {
        assertNull(LoyaltyCardRules.validate("Café & winkel 🛍️", "AB-123"))
    }

    @Test fun acceptsValidLoyaltyCard() {
        assertNull(LoyaltyCardRules.validate("Winkel", "123456"))
    }

    @Test fun masksLongCodesButKeepsShortCodesReadable() {
        assertEquals("•••• 7890", LoyaltyCardRules.maskCode("1234567890"))
        assertEquals("123456", LoyaltyCardRules.maskCode("123456"))
    }

    @Test fun maskingDoesNotChangeTheUnderlyingInput() {
        val original = "CUSTOMER-CODE-1234"
        LoyaltyCardRules.maskCode(original)
        assertEquals("CUSTOMER-CODE-1234", original)
    }

    @Test fun masksLongCodeToOnlyItsLastFourCharacters() {
        assertEquals("•••• 1234", LoyaltyCardRules.maskCode("VERY-LONG-PRIVATE-CODE-1234"))
    }
}
