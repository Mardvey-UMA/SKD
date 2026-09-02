package com.contentagg.parser.integration.rest.configservice.model

import com.fasterxml.jackson.annotation.JsonProperty

data class HabrConfigDto(
    @JsonProperty("base_url") val baseUrl: String?,
    @JsonProperty("api_endpoint") val apiEndpoint: String?,
    @JsonProperty("categories") val categories: List<String>?,
    @JsonProperty("min_rating") val minRating: Int?,
    @JsonProperty("max_items_per_batch") val maxItemsPerBatch: Int?,
    @JsonProperty("request_timeout_ms") val requestTimeoutMs: Int?,
    @JsonProperty("retry_count") val retryCount: Int?,
    @JsonProperty("rate_limit_per_minute") val rateLimitPerMinute: Int?,
)
