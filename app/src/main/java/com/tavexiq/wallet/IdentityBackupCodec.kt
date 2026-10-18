package com.tavexiq.wallet

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-protected portable backup for ID metadata. No document scans or identity numbers are included. */
internal object IdentityBackupCodec {
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_DOCUMENTS = 200
    private const val MAX_BACKUP_CHARS = 1_500_000
    private const val PREFIX = "TAVEXIQ-ID-BACKUP"

    fun encode(documents: List<IdentityDocument>, password: CharArray): String {
        require(password.size >= 8) { "Gebruik een back-upwachtwoord van minimaal 8 tekens." }
        require(documents.size <= MAX_DOCUMENTS) { "Te veel documentvermeldingen voor een back-up." }
        require(documents.all { it.id > 0 }) { "Ongeldige document-ID." }
        require(documents.map { it.id }.distinct().size == documents.size) { "Dubbele document-ID's in de back-up." }
        val items = JSONArray()
        documents.forEach { d ->
            require(d.name.isNotBlank() && d.name.length <= 120) { "Ongeldige documentnaam." }
            require(d.type.length <= 80 && d.issuer.length <= 120 && d.expiryDate.length <= 20 && d.notes.length <= 500) {
                "Een documentvermelding bevat te lange gegevens."
            }
            require(IdentityDocumentRules.validateExpiryDate(d.expiryDate)) { "Ongeldige vervaldatum." }
            items.put(JSONObject().put("id", d.id).put("name", d.name).put("type", d.type)
                .put("issuer", d.issuer).put("expiryDate", d.expiryDate).put("notes", d.notes))
        }
        val json = JSONObject().put("app", "TAVEXIQ").put("kind", "identity-metadata")
            .put("version", VERSION).put("documents", items).toString()
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(PREFIX.toByteArray(StandardCharsets.UTF_8))
            val plaintextBytes = json.toByteArray(StandardCharsets.UTF_8)
            val encrypted = try {
                cipher.doFinal(plaintextBytes)
            } finally {
                plaintextBytes.fill(0)
            }
            try {
                "$PREFIX\n$VERSION\n${Base64.getEncoder().withoutPadding().encodeToString(salt)}\n${Base64.getEncoder().withoutPadding().encodeToString(iv)}\n${Base64.getEncoder().withoutPadding().encodeToString(encrypted)}"
            } finally {
                encrypted.fill(0)
            }
        } finally { key.encoded.fill(0) }
    }

    fun decode(backup: String, password: CharArray): List<IdentityDocument> {
        require(password.size >= 8) { "Gebruik een back-upwachtwoord van minimaal 8 tekens." }
        require(backup.length <= MAX_BACKUP_CHARS) { "De back-up is te groot." }
        val lines = backup.trim().lines()
        require(lines.size == 5 && lines[0] == PREFIX && lines[1] == VERSION.toString()) {
            "Dit is geen ondersteunde versleutelde TAVEXIQ-ID-back-up."
        }
        val (salt, iv, encrypted) = try {
            Triple(
                Base64.getDecoder().decode(lines[2]),
                Base64.getDecoder().decode(lines[3]),
                Base64.getDecoder().decode(lines[4])
            )
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("De back-up bevat ongeldige gecodeerde gegevens.")
        }
        val plaintext = try {
            require(salt.size == SALT_LENGTH && iv.size == IV_LENGTH && encrypted.size <= 1_000_000) {
                "De back-up is ongeldig of te groot."
            }
            val key = deriveKey(password, salt)
            try {
                try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                    cipher.updateAAD(PREFIX.toByteArray(StandardCharsets.UTF_8))
                    cipher.doFinal(encrypted)
                } catch (_: AEADBadTagException) {
                    throw IllegalArgumentException("Verkeerd back-upwachtwoord of beschadigd bestand.")
                }
            } finally {
                key.encoded.fill(0)
            }
        } finally {
            salt.fill(0)
            iv.fill(0)
            encrypted.fill(0)
        }
        val root = try {
            JSONObject(String(plaintext, StandardCharsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
        require(root.optString("app") == "TAVEXIQ" && root.optString("kind") == "identity-metadata" &&
            root.optInt("version", -1) == VERSION) { "Deze ID-back-up wordt niet ondersteund." }
        val items = root.getJSONArray("documents")
        require(items.length() <= MAX_DOCUMENTS) { "De back-up bevat te veel documentvermeldingen." }
        val documents = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val document = IdentityDocument(item.getLong("id"), item.getString("name").trim(),
                item.getString("type"), item.optString("issuer"), item.optString("expiryDate"), item.optString("notes"))
            require(document.id > 0 && document.name.isNotBlank() && document.name.length <= 120 &&
                document.type.length <= 80 && document.issuer.length <= 120 &&
                document.expiryDate.length <= 20 && document.notes.length <= 500) {
                "De back-up bevat ongeldige documentgegevens."
            }
            require(IdentityDocumentRules.validateExpiryDate(document.expiryDate)) {
                "De back-up bevat een ongeldige vervaldatum."
            }
            document
        }
        require(documents.map { it.id }.distinct().size == documents.size) { "Dubbele document-ID's in de back-up." }
        return documents
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try {
                // SecretKeySpec copies the supplied bytes, so clear the temporary key material.
                SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
