package com.tavexiq.wallet

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Password-protected export for loyalty cards. The legacy plaintext codec remains available for importing old backups. */
internal object EncryptedCardBackupCodec {
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_CARDS = 500
    private const val MAX_BACKUP_CHARS = 2_000_000
    private const val PREFIX = "TAVEXIQ-CARDS-BACKUP"

    fun encode(cards: List<LoyaltyCard>, password: CharArray): String {
        require(password.size >= 8) { "Gebruik een back-upwachtwoord van minimaal 8 tekens." }
        require(cards.size <= MAX_CARDS) { "Te veel kaarten voor een back-up." }
        require(cards.map { it.id }.distinct().size == cards.size) { "Dubbele kaart-ID's in de back-up." }
        val items = JSONArray()
        cards.forEach { card ->
            require(card.id > 0 && LoyaltyCardRules.validate(card.name, card.code) == null &&
                (card.format == "QR" || card.format == "CODE_128")) { "Een kaart bevat ongeldige gegevens." }
            items.put(JSONObject().put("id", card.id).put("name", card.name)
                .put("code", card.code).put("format", card.format).put("favorite", card.favorite))
        }
        val json = JSONObject().put("app", "TAVEXIQ").put("kind", "loyalty-cards")
            .put("version", VERSION).put("cards", items).toString()
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(PREFIX.toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))
            return "$PREFIX\n$VERSION\n" +
                Base64.getEncoder().withoutPadding().encodeToString(salt) + "\n" +
                Base64.getEncoder().withoutPadding().encodeToString(iv) + "\n" +
                Base64.getEncoder().withoutPadding().encodeToString(encrypted)
        } finally {
            password.fill('\u0000')
        }
    }

    fun decode(backup: String, password: CharArray): List<LoyaltyCard> {
        require(backup.length <= MAX_BACKUP_CHARS) { "Het back-upbestand is te groot." }
        val lines = backup.trim().lines()
        require(lines.size == 5 && lines[0] == PREFIX && lines[1] == VERSION.toString()) {
            "Dit is geen ondersteunde versleutelde TAVEXIQ-kaartenback-up."
        }
        val salt = Base64.getDecoder().decode(lines[2])
        val iv = Base64.getDecoder().decode(lines[3])
        val encrypted = Base64.getDecoder().decode(lines[4])
        require(salt.size == SALT_LENGTH && iv.size == IV_LENGTH && encrypted.size <= MAX_BACKUP_CHARS) {
            "De back-up is ongeldig of te groot."
        }
        val key = deriveKey(password, salt)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(PREFIX.toByteArray(StandardCharsets.UTF_8))
            cipher.doFinal(encrypted)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Verkeerd back-upwachtwoord of beschadigd bestand.")
        } finally {
            password.fill('\u0000')
        }
        val root = JSONObject(String(plaintext, StandardCharsets.UTF_8))
        require(root.optString("app") == "TAVEXIQ" && root.optString("kind") == "loyalty-cards" &&
            root.optInt("version", -1) == VERSION) { "Deze klantenkaartenback-up wordt niet ondersteund." }
        val items = root.getJSONArray("cards")
        require(items.length() <= MAX_CARDS) { "De back-up bevat te veel kaarten." }
        val cards = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val card = LoyaltyCard(item.getLong("id"), item.getString("name").trim(),
                item.getString("code").trim(), item.getString("format"), item.optBoolean("favorite", false))
            require(card.id > 0 && LoyaltyCardRules.validate(card.name, card.code) == null &&
                (card.format == "QR" || card.format == "CODE_128")) { "De back-up bevat ongeldige kaartgegevens." }
            card
        }
        require(cards.map { it.id }.distinct().size == cards.size) { "Dubbele kaart-ID's in de back-up." }
        return cards
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        require(password.size >= 8) { "Gebruik een back-upwachtwoord van minimaal 8 tekens." }
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
