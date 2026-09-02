package com.contentplatform.feed.security

import com.contentplatform.feed.configuration.InternalSecurityProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class InternalSignatureFilter(
    private val internalSecurityProperties: InternalSecurityProperties
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(InternalSignatureFilter::class.java)
        private val securityContextRepository = RequestAttributeSecurityContextRepository()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI

        val signature = request.getHeader("X-Internal-Signature")
        val timestamp = request.getHeader("X-Internal-Timestamp")

        if (signature == null || timestamp == null) {
            log.warn("Missing internal signature headers for request to {}", uri)
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_signature", "Missing internal signature headers")
            return
        }

        val timestampSeconds = timestamp.toLongOrNull()
        if (timestampSeconds == null) {
            log.warn("Invalid timestamp format for request to {}", uri)
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_signature", "Invalid timestamp")
            return
        }

        val drift = abs(Instant.now().epochSecond - timestampSeconds)
        if (drift > internalSecurityProperties.timestampToleranceSeconds) {
            log.warn("Stale internal request (drift={}s) for {}", drift, uri)
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "stale_request", "Timestamp out of tolerance")
            return
        }

        val userIdParam = request.getParameter("user_id") ?: ""
        val payload = "count|$userIdParam|$timestamp"
        val expected = computeHmac(payload, internalSecurityProperties.hmacSecret)

        if (!MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())) {
            log.warn("Invalid internal signature for {}", uri)
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_signature", "Invalid internal signature")
            return
        }

        val authentication = UsernamePasswordAuthenticationToken(
            "internal", null, listOf(SimpleGrantedAuthority("ROLE_INTERNAL"))
        )
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
        return !request.requestURI.startsWith("/internal/")
    }

    private fun writeJsonError(response: HttpServletResponse, status: Int, error: String, message: String) {
        response.status = status
        response.contentType = "application/json"
        response.writer.write("""{"error":"$error","message":"$message"}""")
    }

    private fun computeHmac(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }
}
