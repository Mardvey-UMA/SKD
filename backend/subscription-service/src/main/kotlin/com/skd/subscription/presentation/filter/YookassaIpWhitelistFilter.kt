package com.skd.subscription.presentation.filter

import com.skd.subscription.configuration.YookassaProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress

@Component
class YookassaIpWhitelistFilter(
    private val yookassaProperties: YookassaProperties
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(YookassaIpWhitelistFilter::class.java)
    }

    private val allowedCidrs: List<CidrBlock> by lazy {
        yookassaProperties.webhook.allowedCidrs
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { CidrBlock.parse(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val clientIp = resolveClientIp(request)

        if (!isAllowed(clientIp)) {
            log.warn("Rejected webhook request from IP={}", clientIp)
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "IP not whitelisted")
            return
        }

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (yookassaProperties.webhook.devMode) return true
        return !request.requestURI.startsWith("/webhook/yookassa")
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            // Take the first IP in the X-Forwarded-For chain (client IP)
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr
    }

    private fun isAllowed(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            allowedCidrs.any { it.contains(addr) }
        } catch (e: Exception) {
            log.warn("Failed to parse IP={}", ip, e)
            false
        }
    }

    private data class CidrBlock(
        val network: ByteArray,
        val prefixLength: Int
    ) {
        fun contains(addr: InetAddress): Boolean {
            val addrBytes = addr.address
            if (addrBytes.size != network.size) return false

            val fullBytes = prefixLength / 8
            val remainingBits = prefixLength % 8

            for (i in 0 until fullBytes) {
                if (addrBytes[i] != network[i]) return false
            }

            if (remainingBits > 0 && fullBytes < network.size) {
                val mask = (0xFF shl (8 - remainingBits)) and 0xFF
                if ((addrBytes[fullBytes].toInt() and mask) != (network[fullBytes].toInt() and mask)) return false
            }

            return true
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CidrBlock) return false
            return prefixLength == other.prefixLength && network.contentEquals(other.network)
        }

        override fun hashCode(): Int {
            return 31 * network.contentHashCode() + prefixLength
        }

        companion object {
            fun parse(cidr: String): CidrBlock {
                val parts = cidr.split("/")
                val ip = parts[0]
                val prefix = if (parts.size > 1) parts[1].toInt() else 32
                val addr = InetAddress.getByName(ip)
                return CidrBlock(addr.address, prefix)
            }
        }
    }
}
