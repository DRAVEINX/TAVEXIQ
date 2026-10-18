package com.tavexiq.wallet

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

internal enum class IdentityExpiryStatus { NOT_SET, INVALID, EXPIRED, EXPIRING_SOON, VALID }

internal object IdentityDocumentRules {
    private val formatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun parseExpiryDate(value: String): LocalDate? {
        if (value.isBlank()) return null
        return try {
            LocalDate.parse(value.trim(), formatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun expiryStatus(value: String, today: LocalDate = LocalDate.now()): IdentityExpiryStatus {
        if (value.isBlank()) return IdentityExpiryStatus.NOT_SET
        val expiry = parseExpiryDate(value) ?: return IdentityExpiryStatus.INVALID
        return when {
            expiry.isBefore(today) -> IdentityExpiryStatus.EXPIRED
            !expiry.isAfter(today.plusDays(30)) -> IdentityExpiryStatus.EXPIRING_SOON
            else -> IdentityExpiryStatus.VALID
        }
    }

    fun validateExpiryDate(value: String): Boolean =
        value.isBlank() || parseExpiryDate(value) != null
}
