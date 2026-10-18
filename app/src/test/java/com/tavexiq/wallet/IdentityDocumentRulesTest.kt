package com.tavexiq.wallet

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityDocumentRulesTest {
    private val today = LocalDate.of(2026, 10, 18)

    @Test fun blankDateIsOptional() {
        assertTrue(IdentityDocumentRules.validateExpiryDate(""))
        assertEquals(IdentityExpiryStatus.NOT_SET, IdentityDocumentRules.expiryStatus("", today))
    }

    @Test fun validLeapDayIsAccepted() {
        assertTrue(IdentityDocumentRules.validateExpiryDate("29-02-2028"))
    }

    @Test fun impossibleCalendarDateIsRejected() {
        assertFalse(IdentityDocumentRules.validateExpiryDate("31-02-2028"))
        assertFalse(IdentityDocumentRules.validateExpiryDate("29-02-2027"))
    }

    @Test fun expiredDateIsDetected() {
        assertEquals(IdentityExpiryStatus.EXPIRED, IdentityDocumentRules.expiryStatus("17-10-2026", today))
    }

    @Test fun expiryWithinThirtyDaysIsFlagged() {
        assertEquals(IdentityExpiryStatus.EXPIRING_SOON, IdentityDocumentRules.expiryStatus("17-11-2026", today))
        assertEquals(IdentityExpiryStatus.VALID, IdentityDocumentRules.expiryStatus("18-11-2026", today))
    }
}
