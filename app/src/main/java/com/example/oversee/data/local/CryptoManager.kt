package com.example.oversee.data.local

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

    // =========================================================================
    // PASSWORD-BASED KEY DERIVATION (PBKDF2)
    // =========================================================================

    /**
     * Stretches a plaintext user password into a secure 256-bit AES Key Encryption Key (KEK).
     * We use the user's email as the salt so the math is consistent but unique per user.
     */
    fun deriveKeyEncryptionKey(password: String, email: String): SecretKey {
        // PBKDF2 applies the hashing algorithm 10,000 times to prevent brute-force attacks
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), email.toByteArray(Charsets.UTF_8), 10000, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    // =========================================================================
    // ENVELOPE ENCRYPTION (WRAP / UNWRAP)
    // =========================================================================

    /**
     * Encrypts the raw ChaCha20 SecretKey bytes using the derived Password KEK.
     * This is what you upload to Firestore instead of the raw ChaCha20 key!
     */
    fun wrapChaChaKeyForCloud(chaChaKey: SecretKey, kek: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Initialize with a secure random IV for AES-GCM
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, kek, IvParameterSpec(iv))

        val encryptedBytes = cipher.doFinal(chaChaKey.encoded)
        // Store IV + Encrypted Key together
        return Base64.encodeToString(iv + encryptedBytes, Base64.NO_WRAP)
    }

    /**
     * Decrypts the wrapped ChaCha20 key downloaded from Firestore using the Password KEK.
     */
    fun unwrapChaChaKeyFromCloud(wrappedKeyBase64: String, kek: SecretKey): SecretKey {
        val decoded = Base64.decode(wrappedKeyBase64, Base64.NO_WRAP)
        // Extract the 12-byte IV from the front
        val iv = decoded.copyOfRange(0, 12)
        val ciphertext = decoded.copyOfRange(12, decoded.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, IvParameterSpec(iv))

        val decryptedBytes = cipher.doFinal(ciphertext)
        return keyFromBytes(decryptedBytes)
    }
}