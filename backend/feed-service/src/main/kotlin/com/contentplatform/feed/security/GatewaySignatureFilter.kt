package com.contentplatform.feed.security

import com.contentplatform.feed.configuration.GatewayProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GatewaySignatureFilter(
    private val gatewayProperties: GatewayProperties
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(GatewaySignatureFilter::class.java)
        private val securityContextRepository = RequestAttributeSecurityContextRepository()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI

        val signature = request.getHeader("X-Gateway-Signature")
        val userId = request.getHeader("X-User-Id")
        val roles = request.getHeader("X-User-Roles")
        val tier = request.getHeader("X-Subscription-Tier")
        val requestId = request.getHeader("X-Request-Id")

        if (signature == null || userId == null) {
            log.warn("Missing gateway headers for request to {}", uri)
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error":"FORBIDDEN","message":"Missing gateway headers"}""")
            return
        }

        val payload = "$userId|$roles|$tier|$requestId"
        val expected = computeHmac(payload, gatewayProperties.hmacSecret)

        if (!MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())) {
            log.warn("Invalid gateway signature for userId={}, request to {}", userId, uri)
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error":"FORBIDDEN","message":"Invalid gateway signature"}""")
            return
        }

        val authorities = roles?.split(",")
            ?.map { SimpleGrantedAuthority("ROLE_${it.trim()}") }
            ?: emptyList()

        val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
        val context: SecurityContext = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)

        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    public override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/health") || uri.startsWith("/internal/") || uri.startsWith("/actuator")
    }

    private fun computeHmac(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }
}
