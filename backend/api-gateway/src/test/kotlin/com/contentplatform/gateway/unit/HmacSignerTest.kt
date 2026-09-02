package com.contentplatform.gateway.unit

import com.contentplatform.gateway.security.HmacSigner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unit tests for HmacSigner utility.
 *
 * Verifies:
 * - Payload format is "$userId|$roles|$tier|$requestId"
 * - Signature is a valid Base64-encoded HMAC-SHA256
 * - Signature is deterministic (same input -> same output)
 * - Signature changes when any payload component changes
 * - Signature is verifiable with the known secret
 */
@Tag("unit")
class HmacSignerTest {

    private val secret = "test-secret-key-12345"
    private val signer = HmacSigner(secret)

    @Test
    fun `should produce Base64-encoded HMAC-SHA256 signature`() {
        val signature = signer.sign("user-1", "USER", "free", "req-1")

        assertThat(signature).isNotBlank()
        // Should be valid Base64
        val decoded = Base64.getDecoder().decode(signature)
        // HMAC-SHA256 produces 32 bytes
        assertThat(decoded).hasSize(32)
    }

    @Test
    fun `should compute signature from payload format userId pipe roles pipe tier pipe requestId`() {
        val userId = "550e8400-e29b-41d4-a716-446655440000"
        val roles = "USER,SUBSCRIBER"
        val tier = "premium"
        val requestId = "f47ac10b-58cc-4372-a567-0e02b2c3d479"

        val signature = signer.sign(userId, roles, tier, requestId)

        // Manually compute expected
        val payload = "$userId|$roles|$tier|$requestId"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val expected = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))

        assertThat(signature).isEqualTo(expected)
    }

    @Test
    fun `should be deterministic - same inputs produce same signature`() {
        val sig1 = signer.sign("user-1", "USER", "free", "req-1")
        val sig2 = signer.sign("user-1", "USER", "free", "req-1")

        assertThat(sig1).isEqualTo(sig2)
    }

    @Test
    fun `should produce different signature when userId changes`() {
        val sig1 = signer.sign("user-1", "USER", "free", "req-1")
        val sig2 = signer.sign("user-2", "USER", "free", "req-1")

        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `should produce different signature when roles change`() {
        val sig1 = signer.sign("user-1", "USER", "free", "req-1")
        val sig2 = signer.sign("user-1", "ADMIN", "free", "req-1")

        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `should produce different signature when tier changes`() {
        val sig1 = signer.sign("user-1", "USER", "free", "req-1")
        val sig2 = signer.sign("user-1", "USER", "premium", "req-1")

        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `should produce different signature when requestId changes`() {
        val sig1 = signer.sign("user-1", "USER", "free", "req-1")
        val sig2 = signer.sign("user-1", "USER", "free", "req-2")

        assertThat(sig1).isNotEqualTo(sig2)
    }

    @Test
    fun `should verify signature matches expected for known inputs`() {
        val userId = "test-user"
        val roles = "USER"
        val tier = "free"
        val requestId = "test-request"

        val signature = signer.sign(userId, roles, tier, requestId)

        // Verify by computing independently
        assertThat(signer.verify("$userId|$roles|$tier|$requestId", signature)).isTrue()
    }

    @Test
    fun `should reject tampered signature`() {
        val signature = signer.sign("user-1", "USER", "free", "req-1")
        val tamperedPayload = "user-1|ADMIN|free|req-1"

        assertThat(signer.verify(tamperedPayload, signature)).isFalse()
    }
}
