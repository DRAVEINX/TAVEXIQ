package com.tavexiq.wallet

import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected portable backup envelope for loyalty cards.
 *
 * Uses PBKDF2-HMAC-SHA256 and AES-256-GCM. The authenticated ciphertext
 * contains the existing CardBackupCodec payload, so card validation remains
 * centralized in that codec.
 */
internal object EncryptedCardBackupCodec {
    private const val FORMAT_VERSION = 1
    private const val KDF_ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 1_024
    private const val MAX_BACKUP_CHARS = 2_000_000
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val APP_ID = "TAVEXIQ_ENCRYPTED_BACKUP"

    fun encode(cards: List<LoyaltyCard>, password: CharArray): String {
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Gebruik voor de back-up een wachtwoord van 8 tot 1024 tekens."
        }

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        try {
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad(FORMAT_VERSION))

            val plaintext = CardBackupCodec.encode(cards).toByteArray(StandardCharsets.UTF_8)
            val ciphertext = try {
                cipher.doFinal(plaintext)
            } finally {
                // Clear the serialized card data as soon as encryption has finished.
                plaintext.fill(0)
            }
            return try {
                val envelope = JSONObject()
                    .put("app", APP_ID)
                    .put("version", FORMAT_VERSION)
                    .put("kdf", KDF)
                    .put("iterations", KDF_ITERATIONS)
                    .put("cipher", "AES-256-GCM")
                    .put("salt", Base64.getEncoder().encodeToString(salt))
                    .put("nonce", Base64.getEncoder().encodeToString(nonce))
                    .put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
                    .toString(2)
                require(envelope.length <= MAX_BACKUP_CHARS) {
                    "De back-up is te groot om te exporteren."
                }
                envelope
            } finally {
                // Clear raw ciphertext once its Base64 representation has been created.
                ciphertext.fill(0)
            }
        } finally {
            // Clear salt and nonce even if key setup, card validation, or encryption fails.
            salt.fill(0)
            nonce.fill(0)
        }
    }

    fun decode(envelope: String, password: CharArray): List<LoyaltyCard> {
        require(envelope.length <= MAX_BACKUP_CHARS) { "Het back-upbestand is te groot." }
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Het back-upwachtwoord moet 8 tot 1024 tekens bevatten."
        }

        val root = try {
            JSONObject(envelope)
        } catch (_: JSONException) {
            throw IllegalArgumentException("Het back-upbestand is geen geldige JSON of is beschadigd.")
        }
        require(root.optString("app") == APP_ID) { "Dit is geen versleutelde TAVEXIQ-back-up." }
        require(root.optInt("version", -1) == FORMAT_VERSION) { "Deze back-upversie wordt niet ondersteund." }
        require(root.optString("kdf") == KDF && root.optInt("iterations", -1) == KDF_ITERATIONS) {
            "De beveiligingsinstellingen van deze back-up worden niet ondersteund."
        }
        require(root.optString("cipher") == "AES-256-GCM") { "Het versleutelingsformaat wordt niet ondersteund." }

        val encodedSalt = requiredString(root, "salt")
        val encodedNonce = requiredString(root, "nonce")
        val encodedCiphertext = requiredString(root, "ciphertext")
        // Validate encoded lengths before Base64 decoding allocates byte arrays.
        require(
            encodedSalt.length <= 64 && encodedNonce.length <= 64 &&
                encodedSalt.length % 4 == 0 && encodedNonce.length % 4 == 0
        ) {
            "Het back-upbestand is beschadigd."
        }
        require(encodedCiphertext.length <= MAX_BACKUP_CHARS) {
            "Het back-upbestand is te groot."
        }
        // Reject impossible Base64 lengths before allocating decoded buffers.
        require(encodedCiphertext.length % 4 == 0) {
            "Het back-upbestand is beschadigd."
        }
        val salt = decodeBase64(encodedSalt, SALT_BYTES)
        val nonce = try {
            decodeBase64(encodedNonce, NONCE_BYTES)
        } catch (error: IllegalArgumentException) {
            salt.fill(0)
            throw error
        }
        val ciphertext = try {
            Base64.getDecoder().decode(encodedCiphertext)
        } catch (_: IllegalArgumentException) {
            salt.fill(0)
            nonce.fill(0)
            throw IllegalArgumentException("Het back-upbestand is beschadigd.")
        }
        if (ciphertext.size < 16) {
            ciphertext.fill(0)
            salt.fill(0)
            nonce.fill(0)
            throw IllegalArgumentException("Het back-upbestand is beschadigd.")
        }

        val plaintext = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad(FORMAT_VERSION))
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Wachtwoord onjuist of back-upbestand beschadigd.")
        } catch (_: GeneralSecurityException) {
            throw IllegalArgumentException("De back-up kon niet veilig worden ontsleuteld.")
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("De back-up kon niet veilig worden ontsleuteld.")
        } finally {
            // Clear temporary cryptographic buffers after decryption, including failure paths.
            ciphertext.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }

        return try {
            CardBackupCodec.decode(String(plaintext, StandardCharsets.UTF_8))
        } catch (_: JSONException) {
            throw IllegalArgumentException("De inhoud van de back-up is beschadigd.")
        } finally {
            // Minimize the time decrypted card data remains in a mutable byte array.
            plaintext.fill(0)
        }
    }

    private fun requiredString(root: JSONObject, key: String): String {
        return try {
            root.getString(key)
        } catch (_: JSONException) {
            throw IllegalArgumentException("Het back-upbestand mist een verplicht veld of is beschadigd.")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            try {
                SecretKeySpec(bytes, "AES")
            } finally {
                bytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeBase64(value: String, expectedSize: Int): ByteArray {
        val decoded = try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Het back-upbestand is beschadigd.")
        }
        if (decoded.size != expectedSize) {
            decoded.fill(0)
            throw IllegalArgumentException("Het back-upbestand is beschadigd.")
        }
        return decoded
    }

    private fun aad(version: Int): ByteArray = "$APP_ID:$version".toByteArray(StandardCharsets.UTF_8)
}
