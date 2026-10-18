package com.tavexiq.wallet

import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable backup format. The exported file contains loyalty codes in readable form,
 * so the UI must warn users to store it somewhere private.
 */
internal object CardBackupCodec {
    private const val VERSION = 1
    private const val MAX_CARDS = 500

    fun encode(cards: List<LoyaltyCard>): String {
        require(cards.size <= MAX_CARDS) { "Te veel kaarten voor een back-up." }
        val items = JSONArray()
        cards.forEach { card ->
            items.put(JSONObject()
                .put("id", card.id)
                .put("name", card.name)
                .put("code", card.code)
                .put("format", card.format)
                .put("favorite", card.favorite))
        }
        return JSONObject()
            .put("app", "TAVEXIQ")
            .put("version", VERSION)
            .put("cards", items)
            .toString(2)
    }

    fun decode(json: String): List<LoyaltyCard> {
        val root = JSONObject(json)
        require(root.optString("app") == "TAVEXIQ") { "Dit bestand is geen TAVEXIQ-back-up." }
        require(root.optInt("version", -1) == VERSION) { "Deze back-upversie wordt niet ondersteund." }
        val items = root.getJSONArray("cards")
        require(items.length() <= MAX_CARDS) { "De back-up bevat te veel kaarten." }
        val cards = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val id = item.getLong("id")
            val name = item.getString("name").trim()
            val code = item.getString("code").trim()
            val format = item.getString("format")
            require(id > 0L) { "Ongeldige kaart-ID in de back-up." }
            require(LoyaltyCardRules.validate(name, code) == null) { "Een kaart in de back-up bevat ongeldige gegevens." }
            require(format == "QR" || format == "CODE_128") { "Onbekend codeformaat in de back-up." }
            LoyaltyCard(id, name, code, format, item.optBoolean("favorite", false))
        }
        require(cards.map { it.id }.distinct().size == cards.size) { "Dubbele kaart-ID's in de back-up." }
        return cards
    }
}
