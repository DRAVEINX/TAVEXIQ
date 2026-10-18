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

/** Stores only document metadata, encrypted at rest with a dedicated Android Keystore key. */
internal class EncryptedIdentityStorage(context: Context) {
    private val prefs = context.getSharedPreferences("tavexiq_identity_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<IdentityDocument> {
        val encoded = prefs.getString(DATA_KEY, null) ?: return emptyList()
        require(encoded.length <= MAX_ENCODED_CHARS) { "De opgeslagen kluisgegevens zijn te groot." }
        val array = JSONArray(decrypt(encoded))
        require(array.length() <= MAX_DOCUMENTS) { "Er zijn te veel documenten in de identiteitskluis." }
        val documents = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            IdentityDocument(
                id = item.getLong("id"),
                name = item.getString("name"),
                type = item.getString("type"),
                issuer = item.optString("issuer"),
                expiryDate = item.optString("expiryDate"),
                notes = item.optString("notes")
            )
        }
        validateDocuments(documents)
        return documents
    }

    @Synchronized
    fun save(documents: List<IdentityDocument>) {
        validateDocuments(documents)
        val array = JSONArray()
        documents.forEach { document ->
            array.put(JSONObject()
                .put("id", document.id)
                .put("name", document.name)
                .put("type", document.type)
                .put("issuer", document.issuer)
                .put("expiryDate", document.expiryDate)
                .put("notes", document.notes))
        }
        val encrypted = encrypt(array.toString())
        check(prefs.edit().putString(DATA_KEY, encrypted).commit()) {
            "De identiteitskluis kon niet veilig worden opgeslagen."
        }
    }

    private fun validateDocuments(documents: List<IdentityDocument>) {
        require(documents.size <= MAX_DOCUMENTS) { "Er zijn te veel documenten in de identiteitskluis." }
        documents.forEach { document ->
            require(document.id > 0L) { "Ongeldige document-ID." }
            require(document.name.isNotBlank() && document.name.length <= MAX_FIELD_LENGTH) {
                "De documentnaam is ongeldig."
            }
            require(document.type.isNotBlank() && document.type.length <= MAX_FIELD_LENGTH) {
                "Het documenttype is ongeldig."
            }
            require(document.issuer.length <= MAX_FIELD_LENGTH &&
                document.expiryDate.length <= MAX_FIELD_LENGTH &&
                document.notes.length <= MAX_FIELD_LENGTH) {
                "Een documentveld is te lang."
            }
            require(IdentityDocumentRules.validateExpiryDate(document.expiryDate)) {
                "De vervaldatum moet de vorm dd-MM-jjjj hebben."
            }
        }
        require(documents.map { it.id }.distinct().size == documents.size) {
            "Dubbele document-ID's in de identiteitskluis."
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val bytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        require(value.length <= MAX_ENCODED_CHARS) { "De opgeslagen kluisgegevens zijn te groot." }
        val combined = try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("De opgeslagen kluisgegevens zijn ongeldig.")
        }
        require(combined.size > IV_LENGTH + TAG_LENGTH_BYTES) { "De opgeslagen kluisgegevens zijn ongeldig." }
        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val DATA_KEY = "documents_encrypted_v1"
        const val KEY_ALIAS = "tavexiq_identity_metadata_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
        const val TAG_LENGTH_BYTES = TAG_BITS / 8
        const val MAX_DOCUMENTS = 500
        const val MAX_FIELD_LENGTH = 2_000
        const val MAX_ENCODED_CHARS = 2_000_000
    }
}
