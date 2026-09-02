package com.skd.subscription.presentation.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${gateway.hmac-secret}") private val secret: String
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(GatewaySignatureFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val signature = request.getHeader("X-Gateway-Signature")
        val userId = request.getHeader("X-User-Id")

        if (signature == null || userId == null) {
            log.warn("Missing gateway headers on path={}", request.requestURI)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing gateway headers")
            return
        }

        val roles = request.getHeader("X-User-Roles")
        val tier = request.getHeader("X-Subscription-Tier")
        val requestId = request.getHeader("X-Request-Id")

        val payload = "$userId|$roles|$tier|$requestId"
        val expected = computeHmac(payload, secret)

        if (!MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())) {
            log.warn("Invalid gateway signature on path={}", request.requestURI)
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid gateway signature")
            return
        }

        val authorities = roles?.split(",")
            ?.map { SimpleGrantedAuthority(it.trim()) }
            ?: emptyList()

        val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri.startsWith("/webhook/yookassa") || uri.startsWith("/health") || uri.startsWith("/actuator")
    }

    private fun computeHmac(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }
}
