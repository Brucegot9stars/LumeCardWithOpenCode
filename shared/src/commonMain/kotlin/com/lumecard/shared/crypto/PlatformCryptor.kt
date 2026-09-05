package com.lumecard.shared.crypto

expect object PlatformCryptor {
    fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray
    fun aes256GcmEncrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
    fun aes256GcmDecrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray
    fun encodeBase64(data: ByteArray): String
    fun decodeBase64(s: String): ByteArray
    fun randomBytes(size: Int): ByteArray
}
