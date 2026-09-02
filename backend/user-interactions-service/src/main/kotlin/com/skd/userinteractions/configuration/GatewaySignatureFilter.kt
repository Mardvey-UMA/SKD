package com.skd.userinteractions.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
    @Value("\${gateway.hmac-secret}") private val hmacSecret: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val signature = request.getHeader("X-Gateway-Signature")
        val userId = request.getHeader("X-User-Id")

        if (signature == null || userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing gateway headers")
            return
        }

        val roles = request.getHeader("X-User-Roles") ?: ""
        val subscriptionTier = request.getHeader("X-Subscription-Tier") ?: ""
        val requestId = request.getHeader("X-Request-Id") ?: ""

        val payload = "$userId|$roles|$subscriptionTier|$requestId"
        val expected = computeHmac(payload)

        if (!MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid gateway signature")
            return
        }

        val authorities = roles.split(",").filter { it.isNotBlank() }.map { SimpleGrantedAuthority(it.trim()) }
        val auth = UsernamePasswordAuthenticationToken(userId, null, authorities)
        SecurityContextHolder.getContext().authentication = auth

        filterChain.doFilter(request, response)
    }

    private fun computeHmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacSecret.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        return uri == "/health" || uri == "/actuator" || uri.startsWith("/actuator/")
    }
}
