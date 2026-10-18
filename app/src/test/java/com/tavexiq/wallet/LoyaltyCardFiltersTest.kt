package com.tavexiq.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class LoyaltyCardFiltersTest {
    private val cards = listOf(
        LoyaltyCard(1L, "Albert Heijn", "1111", "QR", favorite = true),
        LoyaltyCard(2L, "Jumbo", "2222", "CODE_128"),
        LoyaltyCard(3L, "HEMA", "3333", "QR", favorite = true)
    )

    @Test fun searchesNamesWithoutCaseSensitivity() {
        assertEquals(listOf("Albert Heijn"), filterLoyaltyCards(cards, "albert", false).map { it.name })
    }

    @Test fun trimsSearchWhitespace() {
        assertEquals(listOf("Jumbo"), filterLoyaltyCards(cards, "  Jumbo  ", false).map { it.name })
    }

    @Test fun showsOnlyFavoritesWhenRequested() {
        assertEquals(listOf("Albert Heijn", "HEMA"), filterLoyaltyCards(cards, "", true).map { it.name })
    }

    @Test fun combinesSearchAndFavorites() {
        assertEquals(listOf("HEMA"), filterLoyaltyCards(cards, "hema", true).map { it.name })
    }

    @Test fun returnsNoResultsForUnknownSearch() {
        assertEquals(emptyList<LoyaltyCard>(), filterLoyaltyCards(cards, "onbekend", false))
    }
}
