package com.tavexiq.wallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts locally stored loyalty-card data with an AES-GCM key protected by Android Keystore.
 * Existing plaintext data is migrated only after the encrypted value is durably written.
 */
internal class EncryptedCardStorage(context: Context) {
    private val prefs = context.getSharedPreferences("tavexiq_local", Context.MODE_PRIVATE)

    @Synchronized
    fun loadCards(): List<LoyaltyCard> {
        val encrypted = prefs.getString(ENCRYPTED_KEY, null)
        if (encrypted != null) {
            // Also retry cleanup if an earlier migration/save could not remove the legacy value.
            val cards = decodeCards(decrypt(encrypted))
            removeLegacyPlaintext()
            return cards
        }

        // One-time migration for existing installs. Keep the legacy value if encryption fails.
        val legacy = prefs.getString(LEGACY_KEY, null) ?: "[]"
        val cards = decodeCards(legacy)
        val encoded = encrypt(legacy)
        check(prefs.edit().putString(ENCRYPTED_KEY, encoded).commit()) {
            "De versleutelde kaartgegevens konden niet worden opgeslagen."
        }
        removeLegacyPlaintext()
        return cards
    }

    @Synchronized
    fun saveCards(cards: List<LoyaltyCard>) {
        validateCards(cards)
        val array = JSONArray()
        cards.forEach { card ->
            array.put(JSONObject()
                .put("id", card.id)
                .put("name", card.name.trim())
                .put("code", card.code.trim())
                .put("format", card.format)
                .put("favorite", card.favorite))
        }
        val encrypted = encrypt(array.toString())
        check(prefs.edit().putString(ENCRYPTED_KEY, encrypted).commit()) {
            "De versleutelde kaartgegevens konden niet worden opgeslagen."
        }
        // Remove any leftover plaintext only after the encrypted write succeeds.
        removeLegacyPlaintext()
    }

    private fun validateCards(cards: List<LoyaltyCard>) {
        require(cards.size <= MAX_CARDS) { "Er zijn te veel opgeslagen kaarten." }
        cards.forEach { card ->
            require(card.id > 0L) { "Ongeldige kaart-ID." }
            require(LoyaltyCardRules.validate(card.name.trim(), card.code.trim()) == null) {
                "Een kaart bevat ongeldige gegevens."
            }
            require(card.format == "QR" || card.format == "CODE_128") { "Onbekend codeformaat." }
        }
        require(cards.map { it.id }.distinct().size == cards.size) {
            "Dubbele kaart-ID's in de opgeslagen gegevens."
        }
        require(cards.map { it.code.trim().lowercase(Locale.ROOT) }.distinct().size == cards.size) {
            "Dubbele kaartcodes in de opgeslagen gegevens."
        }
    }

    private fun removeLegacyPlaintext() {
        if (prefs.contains(LEGACY_KEY)) {
            check(prefs.edit().remove(LEGACY_KEY).commit()) {
                "De versleutelde gegevens zijn opgeslagen, maar oude leesbare kaartgegevens konden niet worden verwijderd."
            }
        }
    }

    private fun encrypt(plainText: String): String {
        val plaintext = plainText.toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val encrypted = try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.doFinal(plaintext)
        } finally {
            // Clear the temporary UTF-8 representation of the card data.
            plaintext.fill(0)
        }
        val iv = cipher.iv
        val combined = iv + encrypted
        return try {
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } finally {
            // Clear temporary IV and encrypted buffers after Base64 serialization.
            iv.fill(0)
            encrypted.fill(0)
            combined.fill(0)
        }
    }

    private fun decrypt(encoded: String): String {
        val combined = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("De versleutelde kaartgegevens zijn ongeldig of beschadigd.")
        }
        try {
            require(combined.size > IV_LENGTH + TAG_LENGTH_BYTES) {
                "De versleutelde kaartgegevens zijn ongeldig of beschadigd."
            }
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
                val plaintext = try {
                    cipher.doFinal(ciphertext)
                } catch (_: javax.crypto.AEADBadTagException) {
                    throw IllegalStateException("De versleutelde kaartgegevens konden niet worden gecontroleerd. De gegevens zijn niet overschreven.")
                }
                return try {
                    String(plaintext, StandardCharsets.UTF_8)
                } finally {
                    // Clear the temporary decrypted byte array as soon as the String is created.
                    plaintext.fill(0)
                }
            } finally {
                iv.fill(0)
                ciphertext.fill(0)
            }
        } finally {
            // Clear the combined Base64-decoded IV and ciphertext on every path.
            combined.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun decodeCards(json: String): List<LoyaltyCard> {
        val array = try {
            JSONArray(json)
        } catch (_: JSONException) {
            throw IllegalStateException("De opgeslagen kaartgegevens zijn ongeldig of beschadigd.")
        }
        require(array.length() <= MAX_CARDS) { "Er zijn te veel opgeslagen kaarten." }
        val cards = try {
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val id = item.getLong("id")
                val name = item.getString("name").trim()
                val code = item.getString("code").trim()
                val format = item.getString("format")
                require(id > 0L) { "Ongeldige kaart-ID." }
                require(LoyaltyCardRules.validate(name, code) == null) { "Een opgeslagen kaart bevat ongeldige gegevens." }
                require(format == "QR" || format == "CODE_128") { "Onbekend opgeslagen codeformaat." }
                LoyaltyCard(id, name, code, format, item.optBoolean("favorite", false))
            }
        } catch (_: JSONException) {
            throw IllegalStateException("De opgeslagen kaartgegevens zijn ongeldig of beschadigd.")
        }
        validateCards(cards)
        return cards
    }

    private companion object {
        const val LEGACY_KEY = "cards"
        const val ENCRYPTED_KEY = "cards_encrypted_v1"
        const val KEY_ALIAS = "tavexiq_loyalty_cards_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_BITS / 8
        const val MAX_CARDS = 500
    }
}
