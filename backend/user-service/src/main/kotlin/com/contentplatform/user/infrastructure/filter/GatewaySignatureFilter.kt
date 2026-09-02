package com.contentplatform.user.infrastructure.filter

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GatewaySignatureFilter(
    private val hmacSecret: String
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(GatewaySignatureFilter::class.java)
        private const val HEADER_SIGNATURE = "X-Gateway-Signature"
        private const val HEADER_USER_ID = "X-User-Id"
        private const val HEADER_USER_ROLES = "X-User-Roles"
        private const val HEADER_SUBSCRIPTION_TIER = "X-Subscription-Tier"
        private const val HEADER_REQUEST_ID = "X-Request-Id"
        private val objectMapper = ObjectMapper()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val signature = request.getHeader(HEADER_SIGNATURE)
        val userId = request.getHeader(HEADER_USER_ID)

        if (signature == null || userId == null) {
            log.debug("Missing gateway headers for {}", request.requestURI)
            writeUnauthorized(response, "Missing gateway headers")
            return
        }

        val roles = request.getHeader(HEADER_USER_ROLES) ?: ""
        val subscriptionTier = request.getHeader(HEADER_SUBSCRIPTION_TIER) ?: ""
        val requestId = request.getHeader(HEADER_REQUEST_ID) ?: ""

        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        val expectedSignature = computeHmac(payload)

        if (!MessageDigest.isEqual(signature.toByteArray(), expectedSignature.toByteArray())) {
            log.debug("Invalid HMAC signature for {}", request.requestURI)
            writeUnauthorized(response, "Invalid signature")
            return
        }

        val authorities = roles.split(",").filter { it.isNotBlank() }.map { SimpleGrantedAuthority(it.trim()) }
        val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)
        SecurityContextHolder.getContext().authentication = auth

        request.setAttribute(HEADER_USER_ID, userId)
        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/health") || uri.startsWith("/error") || uri.startsWith("/actuator")
    }

    /**
     * Public wrapper for testing shouldNotFilter (which is protected).
     */
    fun shouldNotFilterRequest(request: HttpServletRequest): Boolean {
        return shouldNotFilter(request)
    }

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    private fun writeUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        val body = mapOf(
            "error" to "UNAUTHORIZED",
            "message" to message,
            "timestamp" to Instant.now().toString()
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
