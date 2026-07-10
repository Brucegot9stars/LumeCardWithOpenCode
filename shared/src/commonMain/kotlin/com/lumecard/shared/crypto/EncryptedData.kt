package com.lumecard.shared.crypto

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedData(
    val version: Int = 1,
    val algorithm: String = "AES-256-GCM",
    val salt: String,
    val nonce: String,
    val cipherText: String,
)
