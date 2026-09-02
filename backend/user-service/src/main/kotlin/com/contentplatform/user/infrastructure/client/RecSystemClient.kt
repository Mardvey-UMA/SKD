package com.contentplatform.user.infrastructure.client

import com.contentplatform.user.configuration.RecSystemProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

open class RecSystemClient(
    private val restClient: RestClient,
    private val properties: RecSystemProperties
) : RecSystemClientPort {

    companion object {
        private val log = LoggerFactory.getLogger(RecSystemClient::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    override fun postOnboarding(userId: UUID, categories: List<String>, sourceContentIds: List<String>): Map<String, Any?> {
        val body = mapOf(
            "user_id" to userId.toString(),
            "categories" to categories,
            "source_content_ids" to sourceContentIds
        )

        var lastException: HttpClientErrorException? = null
        repeat(properties.retryMaxAttempts) { attempt ->
            try {
                val result = restClient.post()
                    .uri("/onboarding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map::class.java) as? Map<String, Any?>
                return result ?: emptyMap()
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode == HttpStatus.NOT_FOUND) {
                    log.warn("rec-system /onboarding returned 404, attempt={}/{}", attempt + 1, properties.retryMaxAttempts)
                    lastException = ex
                    if (attempt < properties.retryMaxAttempts - 1 && properties.retryDelayMs > 0) {
                        Thread.sleep(properties.retryDelayMs)
                    }
                } else {
                    throw ex
                }
            }
        }

        throw RecSystemException(
            "rec-system /onboarding failed after ${properties.retryMaxAttempts} attempts with 404",
            lastException
        )
    }

    override fun getCategories(locale: String): String {
        return restClient.get()
            .uri("/categories?locale={locale}", locale)
            .retrieve()
            .body(String::class.java)
            ?: ""
    }
}
