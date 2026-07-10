package com.lumecard.shared.crypto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SensitiveDataEncryptorTest {

    @Test
    fun `encrypt then decrypt returns original`() {
        val password = "test-password-123"
        val plain = "sk-ant-api-xxx-hidden-key-abc123"
        val encryptor = SensitiveDataEncryptor(password)
        val encrypted = encryptor.encrypt(plain)
        val decrypted = encryptor.decrypt(encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun `different salts produce different ciphertext`() {
        val password = "my-password"
        val plain = "secret-api-key"
        val encryptor = SensitiveDataEncryptor(password)
        val a = encryptor.encrypt(plain)
        val b = encryptor.encrypt(plain)
        assertNotEquals(a, b, "Same plaintext with different salt/nonce must differ")
    }

    @Test
    fun `wrong password fails`() {
        val plain = "my-api-key"
        val encrypted = SensitiveDataEncryptor("correct-password").encrypt(plain)
        assertFailsWith<SecurityException> {
            SensitiveDataEncryptor("wrong-password").decrypt(encrypted)
        }
    }

    @Test
    fun `tampered ciphertext fails`() {
        val password = "test-password"
        val plain = "sensitive-data"
        val encrypted = SensitiveDataEncryptor(password).encrypt(plain)
        val data = Json.decodeFromString<EncryptedData>(encrypted)
        val tampered = data.copy(
            cipherText = data.cipherText.dropLast(1) + "A"
        )
        val tamperedJson = Json.encodeToString(tampered)
        assertFailsWith<SecurityException> {
            SensitiveDataEncryptor(password).decrypt(tamperedJson)
        }
    }

    @Test
    fun `tampered nonce fails`() {
        val password = "test-password"
        val plain = "sensitive-data"
        val encrypted = SensitiveDataEncryptor(password).encrypt(plain)
        val data = Json.decodeFromString<EncryptedData>(encrypted)
        val tampered = data.copy(
            nonce = PlatformCryptor.encodeBase64(PlatformCryptor.randomBytes(12))
        )
        val tamperedJson = Json.encodeToString(tampered)
        assertFailsWith<SecurityException> {
            SensitiveDataEncryptor(password).decrypt(tamperedJson)
        }
    }

    @Test
    fun `encryptSettings only encrypts sensitive keys`() {
        val password = "test-password"
        val encryptor = SensitiveDataEncryptor(password)
        val settings = mapOf(
            "ai_provider_api_key" to "my-api-key",
            "daily_goal" to "20",
            "review_mode" to "fsrs",
            "refresh_token" to "my-refresh-token",
            "webdav_configs" to """[{"id":"1","name":"My WebDAV","url":"https://example.com","username":"user","password":"secret","isDefault":true}]""",
        )
        val encrypted = encryptor.encryptSettings(settings)
        assertEquals(settings.size, encrypted.size)
        assertTrue(encrypted["ai_provider_api_key"]!!.startsWith("{"))
        assertEquals("20", encrypted["daily_goal"])
        assertEquals("fsrs", encrypted["review_mode"])
        assertTrue(encrypted["refresh_token"]!!.startsWith("{"))
        assertTrue(
            encrypted["webdav_configs"]!!.startsWith("{"),
            "webdav_configs must be encrypted",
        )
    }

    @Test
    fun `decryptSettings roundtrip`() {
        val password = "test-password"
        val encryptor = SensitiveDataEncryptor(password)
        val original = mapOf(
            "ai_provider_api_key" to "my-api-key",
            "daily_goal" to "20",
            "refresh_token" to "my-refresh-token",
            "webdav_configs" to """[{"id":"1","name":"My WebDAV","url":"https://example.com","username":"user","password":"secret","isDefault":true}]""",
        )
        val encrypted = encryptor.encryptSettings(original)
        val decrypted = encryptor.decryptSettings(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `tryDecrypt returns original for plaintext`() {
        val encryptor = SensitiveDataEncryptor("any-password")
        val result = encryptor.tryDecrypt("plain-text-value")
        assertTrue(result is DecryptResult.NotEncrypted)
        assertEquals("plain-text-value", (result as DecryptResult.NotEncrypted).value)
    }

    @Test
    fun `randomBytes produces different values`() {
        val a = PlatformCryptor.randomBytes(32)
        val b = PlatformCryptor.randomBytes(32)
        assertContentEquals(a, a)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `base64 roundtrip`() {
        val data = "Hello, 世界!".encodeToByteArray()
        val encoded = PlatformCryptor.encodeBase64(data)
        val decoded = PlatformCryptor.decodeBase64(encoded)
        assertContentEquals(data, decoded)
    }

    @Test
    fun `pbkdf2 produces consistent output`() {
        val password = "test-password"
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val key = PlatformCryptor.pbkdf2(password, salt, 1000, 32)
        assertEquals(32, key.size)
        val key2 = PlatformCryptor.pbkdf2(password, salt, 1000, 32)
        assertContentEquals(key, key2, "PBKDF2 must be deterministic")
    }

    @Test
    fun `different passwords produce different keys`() {
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val keyA = PlatformCryptor.pbkdf2("password-a", salt, 1000, 32)
        val keyB = PlatformCryptor.pbkdf2("password-b", salt, 1000, 32)
        assertNotEquals(keyA.toList(), keyB.toList())
    }

    @Test
    fun `deterministic encryption cross-platform`() {
        val password = "cross-platform-password"
        val salt = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val nonce = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val plaintext = "Hello, Cross-Platform World!"

        val key = PlatformCryptor.pbkdf2(password, salt, 100_000, 32)
        val cipherText = PlatformCryptor.aes256GcmEncrypt(plaintext.encodeToByteArray(), key, nonce)

        val data = EncryptedData(
            salt = PlatformCryptor.encodeBase64(salt),
            nonce = PlatformCryptor.encodeBase64(nonce),
            cipherText = PlatformCryptor.encodeBase64(cipherText),
        )
        val json = Json.encodeToString(data)
        val parsed = Json.decodeFromString<EncryptedData>(json)

        val key2 = PlatformCryptor.pbkdf2(
            password,
            PlatformCryptor.decodeBase64(parsed.salt),
            100_000,
            32,
        )
        val decrypted = PlatformCryptor.aes256GcmDecrypt(
            PlatformCryptor.decodeBase64(parsed.cipherText),
            key2,
            PlatformCryptor.decodeBase64(parsed.nonce),
        )

        assertEquals(plaintext, decrypted.decodeToString())
    }

    @Test
    fun `non-ascii password works`() {
        val password = "密码测试123!@#"
        val plain = "my-api-key-with-non-ascii-数据"
        val encryptor = SensitiveDataEncryptor(password)
        val encrypted = encryptor.encrypt(plain)
        val decrypted = encryptor.decrypt(encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun `json transport schema is stable`() {
        val password = "json-schema-test"
        val encryptor = SensitiveDataEncryptor(password)
        val encrypted = encryptor.encrypt("test-value")
        val json = Json { prettyPrint = false }
        val parsed = json.decodeFromString<EncryptedData>(encrypted)
        assertEquals(1, parsed.version)
        assertEquals("AES-256-GCM", parsed.algorithm)
        assertTrue(parsed.salt.isNotBlank())
        assertTrue(parsed.nonce.isNotBlank())
        assertTrue(parsed.cipherText.isNotBlank())
        val reEncoded = json.encodeToString(parsed)
        val decrypted = encryptor.decrypt(reEncoded)
        assertEquals("test-value", decrypted)
    }
}
