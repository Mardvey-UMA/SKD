package com.skd.userinteractions.configuration

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
        try {
            request.getHeader("X-Request-Id")?.let { MDC.put("request_id", it) }
            request.getHeader("X-User-Id")?.let { MDC.put("user_id", it) }
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("request_id")
            MDC.remove("user_id")
        }
    }
}
