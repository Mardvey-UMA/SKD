package com.contentplatform.feed.infrastructure.client

import com.contentplatform.feed.infrastructure.client.model.ContentBatchRequest
import com.contentplatform.feed.infrastructure.client.model.ContentBatchResponse
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

class ContentAggregatorClient(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = LoggerFactory.getLogger(ContentAggregatorClient::class.java)
    }

    fun getContentBatch(
        ids: List<String>,
        includeRelated: Boolean = true,
        relatedLimit: Int = 5
    ): ContentBatchResponse {
        val request = ContentBatchRequest(
            ids = ids,
            includeRelated = includeRelated,
            relatedLimit = relatedLimit
        )
        return try {
            val responseBody = restClient.post()
                .uri("/api/v1/content/batch")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(request))
                .retrieve()
                .onStatus({ status -> status.is5xxServerError }) { _, response ->
                    throw ContentAggregatorClientException("content-aggregator returned ${response.statusCode}")
                }
                .body(String::class.java)
            if (responseBody == null) ContentBatchResponse()
            else objectMapper.readValue(responseBody)
        } catch (e: ContentAggregatorClientException) {
            throw e
        } catch (e: RestClientResponseException) {
            throw ContentAggregatorClientException("content-aggregator error: ${e.statusCode}", e)
        } catch (e: Exception) {
            throw ContentAggregatorClientException("Failed to get content batch", e)
        }
    }
}
