package com.contentplatform.auth.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcPopulationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = request.getHeader("X-Request-Id")
        val userId = request.getHeader("X-User-Id")

        if (requestId != null) MDC.put("request_id", requestId)
        if (userId != null) MDC.put("user_id", userId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("request_id")
            MDC.remove("user_id")
        }
    }
}
