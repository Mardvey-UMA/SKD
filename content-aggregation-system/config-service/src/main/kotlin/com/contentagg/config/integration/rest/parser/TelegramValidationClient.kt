package com.contentagg.config.integration.rest.parser

import com.contentagg.config.configuration.properties.IntegrationProperties
import com.contentagg.config.configuration.properties.TelegramValidationProperties
import com.contentagg.config.exception.TelegramValidationUnavailableException
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

/**
 * Calls `POST /internal/telegram/validate` on content-parser-service to check whether
 * a Telegram channel is accessible before inserting a TELEGRAM source.
 *
 * Retry strategy (simple): up to `maxAttempts` attempts with `retryBackoffMs` pause.
 * On final failure → [TelegramValidationUnavailableException] (503).
 */
@Component
class TelegramValidationClient(
    @Qualifier("parserServiceRestTemplate") private val restTemplate: RestTemplate,
    private val integrationProperties: IntegrationProperties,
    private val telegramValidationProperties: TelegramValidationProperties,
) {

    companion object {
        private val log = LoggerFactory.getLogger(TelegramValidationClient::class.java)
        private const val INTERNAL_TOKEN_HEADER = "X-Internal-Token"
        private const val VALIDATE_PATH = "/internal/telegram/validate"
    }

    data class Request(@JsonProperty("channelUsername") val channelUsername: String)

    data class Response(
        val accessible: Boolean = false,
        val chatId: Long? = null,
        val title: String? = null,
        val reason: String? = null,
    )

    fun validate(channelUsername: String): Response {
        val url = integrationProperties.parserService.baseUrl + VALIDATE_PATH
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (integrationProperties.internalApiToken.isNotBlank()) {
                set(INTERNAL_TOKEN_HEADER, integrationProperties.internalApiToken)
            }
        }
        val entity = HttpEntity(Request(channelUsername), headers)

        var attempt = 0
        var lastError: Throwable? = null
        val maxAttempts = telegramValidationProperties.maxAttempts.coerceAtLeast(1)

        while (attempt < maxAttempts) {
            attempt++
            try {
                log.debug("POST {} attempt={} channel={}", url, attempt, channelUsername)
                val response = restTemplate.exchange(url, HttpMethod.POST, entity, Response::class.java)
                return response.body ?: Response(accessible = false, reason = "empty_response")
            } catch (ex: RestClientException) {
                lastError = ex
                log.warn("Telegram validation attempt {}/{} failed: {}", attempt, maxAttempts, ex.message)
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(telegramValidationProperties.retryBackoffMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw TelegramValidationUnavailableException("Interrupted")
                    }
                }
            }
        }
        throw TelegramValidationUnavailableException("parser-service validate failed: ${lastError?.message}")
    }
}
