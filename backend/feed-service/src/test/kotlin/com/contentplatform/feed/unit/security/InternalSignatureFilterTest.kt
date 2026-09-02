package com.contentplatform.feed.unit.security

import com.contentplatform.feed.configuration.InternalSecurityProperties
import com.contentplatform.feed.security.InternalSignatureFilter
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Tag("unit")
class InternalSignatureFilterTest {

    private val secret = "test-internal-secret"
    private val props = InternalSecurityProperties().apply {
        hmacSecret = secret
        timestampToleranceSeconds = 30
    }
    private val filter = InternalSignatureFilter(props)

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    @Test
    fun `shouldNotFilter skips non-internal paths`() {
        val req1 = mockk<HttpServletRequest>()
        io.mockk.every { req1.requestURI } returns "/api/feed"
        assertThat(filter.shouldNotFilter(req1)).isTrue()

        val req2 = mockk<HttpServletRequest>()
        io.mockk.every { req2.requestURI } returns "/internal/source-additions/count"
        assertThat(filter.shouldNotFilter(req2)).isFalse()
    }

    @Test
    fun `rejects missing headers with 401`() {
        val req = MockHttpServletRequest("GET", "/internal/source-additions/count")
        val resp = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, resp, chain)

        assertThat(resp.status).isEqualTo(401)
        assertThat(resp.contentAsString).contains("invalid_signature")
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `rejects stale timestamp with 401`() {
        val req = MockHttpServletRequest("GET", "/internal/source-additions/count")
        val stale = Instant.now().epochSecond - 600
        val userId = "550e8400-e29b-41d4-a716-446655440000"
        req.addHeader("X-Internal-Signature", hmac("count|$userId|$stale"))
        req.addHeader("X-Internal-Timestamp", stale.toString())
        req.setParameter("user_id", userId)

        val resp = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, resp, chain)

        assertThat(resp.status).isEqualTo(401)
        assertThat(resp.contentAsString).contains("stale_request")
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `rejects invalid signature with 401`() {
        val req = MockHttpServletRequest("GET", "/internal/source-additions/count")
        val ts = Instant.now().epochSecond
        val userId = "550e8400-e29b-41d4-a716-446655440000"
        req.addHeader("X-Internal-Signature", "wrong-signature")
        req.addHeader("X-Internal-Timestamp", ts.toString())
        req.setParameter("user_id", userId)

        val resp = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, resp, chain)

        assertThat(resp.status).isEqualTo(401)
        assertThat(resp.contentAsString).contains("invalid_signature")
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `accepts valid signature and invokes chain`() {
        val req = MockHttpServletRequest("GET", "/internal/source-additions/count")
        val ts = Instant.now().epochSecond
        val userId = "550e8400-e29b-41d4-a716-446655440000"
        val signature = hmac("count|$userId|$ts")

        req.addHeader("X-Internal-Signature", signature)
        req.addHeader("X-Internal-Timestamp", ts.toString())
        req.setParameter("user_id", userId)

        val resp = MockHttpServletResponse()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(req, resp, chain)

        verify { chain.doFilter(req, resp) }
    }
}
