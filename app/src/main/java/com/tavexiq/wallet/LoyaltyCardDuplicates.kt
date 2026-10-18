package com.tavexiq.wallet

/**
 * Returns true when another saved card already uses this code.
 * The card being edited can be excluded by its stable ID.
 */
internal fun hasDuplicateCardCode(
    cards: List<LoyaltyCard>,
    code: String,
    excludingId: Long? = null
): Boolean {
    val candidate = code.trim()
    if (candidate.isEmpty()) return false
    return cards.any { card ->
        card.id != excludingId && card.code.trim().equals(candidate, ignoreCase = true)
    }
}
