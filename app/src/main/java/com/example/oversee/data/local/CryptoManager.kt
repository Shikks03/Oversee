package com.example.oversee.data.local

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Handles ChaCha20-Poly1305 authenticated encryption and decryption.
 *
 * Format for encrypted data: nonce (12 bytes) || ciphertext || Poly1305 tag (16 bytes)
 * This is stored as Base64 when written to Firestore.
 *
 * Available natively on API 28+ via javax.crypto.
 */
object CryptoManager {

    private const val ALGORITHM = "ChaCha20-Poly1305"
    private const val KEY_ALGORITHM = "ChaCha20"
    private const val NONCE_SIZE = 12  // bytes, required by ChaCha20-Poly1305
    private const val KEY_SIZE = 32    // bytes (256-bit key)

    fun generateKey(): SecretKey {
        val keyBytes = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    fun keyFromBytes(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, KEY_ALGORITHM)

    /**
     * Encrypts plaintext bytes.
     * @return nonce (12 bytes) + ciphertext + Poly1305 tag (16 bytes)
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(nonce))
        val ciphertextAndTag = cipher.doFinal(plaintext)
        return nonce + ciphertextAndTag
    }

    /**
     * Decrypts data produced by [encrypt].
     * @param data nonce (12 bytes) + ciphertext + Poly1305 tag
     */
    fun decrypt(data: ByteArray, key: SecretKey): ByteArray {
        val nonce = data.copyOfRange(0, NONCE_SIZE)
        val ciphertextAndTag = data.copyOfRange(NONCE_SIZE, data.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(nonce))
        return cipher.doFinal(ciphertextAndTag)
    }

    /**
     * Encrypts a String and returns a Base64-encoded result safe for Firestore storage.
     */
    fun encryptString(plaintext: String, key: SecretKey): String {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8), key)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded string produced by [encryptString].
     */
    fun decryptString(base64Ciphertext: String, key: SecretKey): String {
        val data = Base64.decode(base64Ciphertext, Base64.NO_WRAP)
        return String(decrypt(data, key), Charsets.UTF_8)
    }
}
