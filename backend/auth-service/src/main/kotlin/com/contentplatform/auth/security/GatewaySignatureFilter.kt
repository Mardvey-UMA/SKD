package com.contentplatform.auth.security

import com.contentplatform.auth.configuration.GatewayProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class GatewaySignatureFilter(
    private val gatewayProperties: GatewayProperties
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(GatewaySignatureFilter::class.java)

        private val PUBLIC_PATHS = setOf(
            "/health",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/verify",
            "/api/auth/resend-verification",
            "/api/auth/refresh",
            "/api/auth/password/reset-request",
            "/api/auth/password/reset"
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return PUBLIC_PATHS.any { uri.startsWith(it) } || uri.startsWith("/.well-known/") || uri.startsWith("/actuator")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val signature = request.getHeader("X-Gateway-Signature")
        val userId = request.getHeader("X-User-Id")
        val roles = request.getHeader("X-User-Roles")
        val tier = request.getHeader("X-Subscription-Tier")
        val requestId = request.getHeader("X-Request-Id")

        if (signature == null || userId == null) {
            log.warn("Missing gateway headers for request to {}", request.requestURI)
            sendForbidden(response, "Missing gateway headers")
            return
        }

        val payload = "$userId|$roles|$tier|$requestId"
        val expected = computeHmac(payload, gatewayProperties.hmacSecret)

        if (!MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())) {
            log.warn("Invalid gateway signature for request to {}", request.requestURI)
            sendForbidden(response, "Invalid gateway signature")
            return
        }

        val authorities = roles
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map { SimpleGrantedAuthority("ROLE_$it") }
            ?: emptyList()

        val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    private fun sendForbidden(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write("""{"error":"FORBIDDEN","message":"$message"}""")
    }

    private fun computeHmac(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }
}
