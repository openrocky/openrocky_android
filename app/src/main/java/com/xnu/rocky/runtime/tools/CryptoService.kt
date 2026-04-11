//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.tools

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoService {
    fun execute(operation: String, input: String, key: String?): String {
        return try {
            when (operation) {
                "sha256" -> {
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
                }
                "md5" -> {
                    val digest = MessageDigest.getInstance("MD5")
                    digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
                }
                "hmac-sha256", "hmac_sha256" -> {
                    val k = key ?: return "Key required for HMAC"
                    val mac = Mac.getInstance("HmacSHA256")
                    mac.init(SecretKeySpec(k.toByteArray(), "HmacSHA256"))
                    mac.doFinal(input.toByteArray()).joinToString("") { "%02x".format(it) }
                }
                "base64-encode", "base64_encode" -> Base64.encodeToString(input.toByteArray(), Base64.NO_WRAP)
                "base64-decode", "base64_decode" -> String(Base64.decode(input, Base64.DEFAULT))
                "aes-encrypt", "aes_encrypt" -> {
                    val k = key ?: return "Key required for AES"
                    val keyBytes = k.toByteArray().copyOf(16) // AES-128
                    val iv = ByteArray(16)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                    Base64.encodeToString(cipher.doFinal(input.toByteArray()), Base64.NO_WRAP)
                }
                "aes-decrypt", "aes_decrypt" -> {
                    val k = key ?: return "Key required for AES"
                    val keyBytes = k.toByteArray().copyOf(16)
                    val iv = ByteArray(16)
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
                    String(cipher.doFinal(Base64.decode(input, Base64.DEFAULT)))
                }
                else -> "Unknown operation: $operation. Supported: sha256, md5, hmac-sha256, base64-encode, base64-decode, aes-encrypt, aes-decrypt"
            }
        } catch (e: Exception) {
            "Crypto error: ${e.message}"
        }
    }
}
