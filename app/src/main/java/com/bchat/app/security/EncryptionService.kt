package com.bchat.app.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionService {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    // IMPORTANT: In a real app, do NOT hardcode the key. 
    // Use a key exchange or secure storage.
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private val KEY = "12345678901234567890123456789012".toByteArray() // 32 bytes for AES-256
    private val IV = "1234567890123456".toByteArray() // 16 bytes IV

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(KEY, "AES")
        val ivSpec = IvParameterSpec(IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keySpec = SecretKeySpec(KEY, "AES")
            val ivSpec = IvParameterSpec(IV)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.decode(encryptedText, Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted)
        } catch (e: Exception) {
            "Error: Could not decrypt message"
        }
    }
}
