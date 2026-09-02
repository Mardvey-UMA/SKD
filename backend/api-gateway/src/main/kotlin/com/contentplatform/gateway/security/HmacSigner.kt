package com.contentplatform.gateway.security

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Utility class that computes and verifies HMAC-SHA256 signatures.
 *
 * Payload format: "$userId|$roles|$tier|$requestId"
 */
class HmacSigner(private val secret: String) {

    fun sign(userId: String, roles: String, tier: String, requestId: String): String {
        val payload = "$userId|$roles|$tier|$requestId"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    fun verify(payload: String, signature: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
        return expected == signature
    }
}
