package com.tavexiq.wallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
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
            return decodeCards(decrypt(encrypted))
        }

        // One-time migration for existing installs. Keep the legacy value if encryption fails.
        val legacy = prefs.getString(LEGACY_KEY, null) ?: "[]"
        val cards = decodeCards(legacy)
        val encoded = encrypt(legacy)
        check(prefs.edit().putString(ENCRYPTED_KEY, encoded).commit()) {
            "De versleutelde kaartgegevens konden niet worden opgeslagen."
        }
        prefs.edit().remove(LEGACY_KEY).commit()
        return cards
    }

    @Synchronized
    fun saveCards(cards: List<LoyaltyCard>) {
        val array = JSONArray()
        cards.forEach { card ->
            array.put(JSONObject()
                .put("id", card.id)
                .put("name", card.name)
                .put("code", card.code)
                .put("format", card.format)
                .put("favorite", card.favorite))
        }
        val encrypted = encrypt(array.toString())
        check(prefs.edit().putString(ENCRYPTED_KEY, encrypted).commit()) {
            "De versleutelde kaartgegevens konden niet worden opgeslagen."
        }
        // Remove any leftover plaintext only after the encrypted write succeeds.
        prefs.edit().remove(LEGACY_KEY).commit()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH) { "Versleutelde kaartgegevens zijn ongeldig." }
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
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
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            LoyaltyCard(item.getLong("id"), item.getString("name"), item.getString("code"), item.getString("format"), item.optBoolean("favorite", false))
        }
    }

    private companion object {
        const val LEGACY_KEY = "cards"
        const val ENCRYPTED_KEY = "cards_encrypted_v1"
        const val KEY_ALIAS = "tavexiq_loyalty_cards_v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
