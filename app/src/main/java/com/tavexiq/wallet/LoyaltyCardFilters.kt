package com.tavexiq.wallet

internal fun filterLoyaltyCards(
    cards: List<LoyaltyCard>,
    query: String,
    favoritesOnly: Boolean
): List<LoyaltyCard> {
    val normalizedQuery = query.trim()
    return cards.filter { card ->
        (!favoritesOnly || card.favorite) &&
            (normalizedQuery.isEmpty() || card.name.contains(normalizedQuery, ignoreCase = true))
    }
}
