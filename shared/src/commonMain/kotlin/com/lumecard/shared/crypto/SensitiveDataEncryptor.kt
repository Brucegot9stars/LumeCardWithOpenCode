package com.lumecard.shared.crypto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SensitiveDataEncryptor(private val password: String) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val PBKDF2_ITERATIONS = 100_000
        const val KEY_LENGTH_BYTES = 32
        const val SALT_SIZE = 16
        const val NONCE_SIZE = 12
    }

    fun encrypt(plaintext: String): String {
        val salt = PlatformCryptor.randomBytes(SALT_SIZE)
        val nonce = PlatformCryptor.randomBytes(NONCE_SIZE)
        val key = PlatformCryptor.pbkdf2(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BYTES)
        val cipherText = PlatformCryptor.aes256GcmEncrypt(
            plaintext.encodeToByteArray(), key, nonce
        )
        val data = EncryptedData(
            salt = PlatformCryptor.encodeBase64(salt),
            nonce = PlatformCryptor.encodeBase64(nonce),
            cipherText = PlatformCryptor.encodeBase64(cipherText),
        )
        return json.encodeToString(data)
    }

    fun decrypt(encryptedJson: String): String {
        val data = json.decodeFromString<EncryptedData>(encryptedJson)
        if (data.algorithm != "AES-256-GCM") {
            throw SecurityException("Unsupported algorithm: ${data.algorithm}")
        }
        val key = PlatformCryptor.pbkdf2(
            password,
            PlatformCryptor.decodeBase64(data.salt),
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BYTES,
        )
        try {
            val plaintext = PlatformCryptor.aes256GcmDecrypt(
                PlatformCryptor.decodeBase64(data.cipherText),
                key,
                PlatformCryptor.decodeBase64(data.nonce),
            )
            return plaintext.decodeToString()
        } catch (e: Exception) {
            throw SecurityException("Decryption failed: ${e.message}", e)
        }
    }

    fun tryDecrypt(value: String): DecryptResult {
        if (!value.startsWith("{")) return DecryptResult.NotEncrypted(value)
        val data = try {
            json.decodeFromString<EncryptedData>(value)
        } catch (_: Exception) {
            return DecryptResult.NotEncrypted(value)
        }
        return try {
            DecryptResult.Decrypted(decrypt(value))
        } catch (e: Exception) {
            DecryptResult.Error(e.message ?: "Decryption failed")
        }
    }

    fun encryptSettings(settings: Map<String, String>): Map<String, String> {
        return settings.mapValues { (key, value) ->
            if (SensitiveKeys.isSensitive(key)) encrypt(value) else value
        }
    }

    fun decryptSettings(settings: Map<String, String>): Map<String, String> {
        return settings.mapValues { (key, value) ->
            if (SensitiveKeys.isSensitive(key)) {
                val result = tryDecrypt(value)
                when (result) {
                    is DecryptResult.Decrypted -> result.value
                    is DecryptResult.NotEncrypted -> value
                    is DecryptResult.Error -> throw SecurityException(
                        "Failed to decrypt '$key': ${result.message}"
                    )
                }
            } else value
        }
    }
}

sealed class DecryptResult {
    data class Decrypted(val value: String) : DecryptResult()
    data class NotEncrypted(val value: String) : DecryptResult()
    data class Error(val message: String) : DecryptResult()
}
