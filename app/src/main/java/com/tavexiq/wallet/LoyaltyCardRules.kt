package com.tavexiq.wallet

/** Input rules for merchant loyalty cards; this is not a payment-card feature. */
internal object LoyaltyCardRules {
    const val MAX_CODE_LENGTH = 500

    fun validate(name: String, code: String): String? = when {
        name.isBlank() -> "Vul een kaartnaam in."
        code.isBlank() -> "Vul een kaartcode in."
        code.length > MAX_CODE_LENGTH -> "De code is te lang (maximaal $MAX_CODE_LENGTH tekens)."
        else -> null
    }

    fun maskCode(value: String): String =
        if (value.length <= 6) value else "•••• " + value.takeLast(4)
}
