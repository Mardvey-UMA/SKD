package com.contentplatform.gateway.health

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

/**
 * Registers the GET /health functional endpoint.
 */
@Configuration
class HealthRouter(
    private val healthHandler: HealthHandler,
    private val objectMapper: ObjectMapper
) {

    @Bean
    fun healthRouterFunction(): RouterFunction<ServerResponse> = router {
        GET("/health") {
            healthHandler.checkHealth().flatMap { healthResponse ->
                val status = if (healthResponse.status == "ok") HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
                ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(healthResponse)
            }
        }
    }
}
