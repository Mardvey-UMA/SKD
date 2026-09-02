package com.contentplatform.feed.infrastructure.client

import com.contentplatform.feed.infrastructure.client.model.BlockedSourcesListResponse
import com.contentplatform.feed.infrastructure.client.model.ColdStartResponse
import com.contentplatform.feed.infrastructure.client.model.ExtendedRecommendationsResponse
import com.contentplatform.feed.infrastructure.client.model.RecommendationsRequest
import com.contentplatform.feed.infrastructure.client.model.RecommendationsResponse
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

class RecSystemClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(RecSystemClient::class.java)
    }

    fun getRecommendations(userId: UUID, count: Int): List<UUID>? {
        val request = RecommendationsRequest(userId = userId, count = count)
        return postRecommendations(userId, request)
    }

    fun getRecommendationsWithBreakdown(userId: UUID, count: Int): ExtendedRecommendationsResponse? {
        val request = RecommendationsRequest(userId = userId, count = count)
        return try {
            val responseBody = restClient.post()
                .uri("/recommendations?include_breakdown=true")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(request))
                .retrieve()
                .onStatus({ status -> status == HttpStatus.NOT_FOUND }) { _, _ ->
                    throw NotFoundException()
                }
                .onStatus({ status -> status.is5xxServerError }) { _, response ->
                    throw RecSystemClientException("rec-system returned ${response.statusCode}")
                }
                .body(String::class.java)
            if (responseBody == null) null
            else objectMapper.readValue(responseBody, ExtendedRecommendationsResponse::class.java)
        } catch (e: NotFoundException) {
            log.debug("rec-system returned 404 for userId={} (breakdown)", userId)
            null
        } catch (e: RecSystemClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw RecSystemClientException("rec-system error (breakdown): ${e.statusCode}", e)
            }
        } catch (e: Exception) {
            throw RecSystemClientException("Failed to get recommendations with breakdown for userId=$userId", e)
        }
    }

    fun getRecommendationsNarrow(userId: UUID, count: Int, includeSourceIds: List<UUID>): List<UUID>? {
        val request = RecommendationsRequest(
            userId = userId,
            count = count,
            includeSourceIds = includeSourceIds,
            rankingMode = "NARROW"
        )
        return postRecommendations(userId, request)
    }

    private fun postRecommendations(userId: UUID, request: RecommendationsRequest): List<UUID>? {
        return try {
            val responseBody = restClient.post()
                .uri("/recommendations")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(request))
                .retrieve()
                .onStatus({ status -> status == HttpStatus.NOT_FOUND }) { _, _ ->
                    throw NotFoundException()
                }
                .onStatus({ status -> status.is5xxServerError }) { _, response ->
                    throw RecSystemClientException("rec-system returned ${response.statusCode}")
                }
                .body(String::class.java)
            if (responseBody == null) null
            else {
                val response: RecommendationsResponse = objectMapper.readValue(responseBody)
                response.items
            }
        } catch (e: NotFoundException) {
            log.debug("rec-system returned 404 for userId={}", userId)
            null
        } catch (e: RecSystemClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw RecSystemClientException("rec-system error: ${e.statusCode}", e)
            }
        } catch (e: Exception) {
            throw RecSystemClientException("Failed to get recommendations for userId=$userId", e)
        }
    }

    fun getColdStart(count: Int): List<UUID> {
        return try {
            val responseBody = restClient.get()
                .uri("/recommendations/cold-start?count=$count")
                .retrieve()
                .onStatus({ status -> status.is5xxServerError }) { _, response ->
                    throw RecSystemClientException("rec-system cold-start returned ${response.statusCode}")
                }
                .body(String::class.java)
            if (responseBody == null) emptyList()
            else {
                val response: ColdStartResponse = objectMapper.readValue(responseBody)
                response.items
            }
        } catch (e: RecSystemClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            throw RecSystemClientException("rec-system cold-start error: ${e.statusCode}", e)
        } catch (e: Exception) {
            throw RecSystemClientException("Failed to get cold start", e)
        }
    }

    fun blockSource(userId: UUID, sourceId: UUID) {
        try {
            restClient.post()
                .uri("/users/$userId/blocked-sources")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(mapOf("source_id" to sourceId.toString())))
                .retrieve()
                .onStatus({ status -> status.isError }) { _, response ->
                    throw RecSystemClientException("rec-system block returned ${response.statusCode}")
                }
                .toBodilessEntity()
        } catch (e: RecSystemClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            throw RecSystemClientException("rec-system block error: ${e.statusCode}", e)
        } catch (e: Exception) {
            throw RecSystemClientException("Failed to block source for userId=$userId", e)
        }
    }

    fun unblockSource(userId: UUID, sourceId: UUID) {
        try {
            restClient.delete()
                .uri("/users/$userId/blocked-sources/$sourceId")
                .retrieve()
                .onStatus({ status -> status.isError && status != HttpStatus.NOT_FOUND }) { _, response ->
                    throw RecSystemClientException("rec-system unblock returned ${response.statusCode}")
                }
                .toBodilessEntity()
        } catch (e: RecSystemClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                return
            }
            throw RecSystemClientException("rec-system unblock error: ${e.statusCode}", e)
        } catch (e: Exception) {
            throw RecSystemClientException("Failed to unblock source for userId=$userId", e)
        }
    }

    fun listBlockedSources(userId: UUID): BlockedSourcesListResponse {
        return try {
            val responseBody = restClient.get()
                .uri("/users/$userId/blocked-sources")
                .retrieve()
                .onStatus({ status -> status.isError }) { _, response ->
                    throw RecSystemClientException("rec-system listBlockedSources returned ${response.statusCode}")
                }
                .body(String::class.java)
            if (responseBody.isNullOrBlank()) BlockedSourcesListResponse()
            else objectMapper.readValue(responseBody)
        } catch (e: RecSystemClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            throw RecSystemClientException("rec-system listBlockedSources error: ${e.statusCode}", e)
        } catch (e: Exception) {
            throw RecSystemClientException("Failed to list blocked sources for userId=$userId", e)
        }
    }

    private class NotFoundException : RuntimeException()
}
