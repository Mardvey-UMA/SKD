package com.contentplatform.feed.infrastructure.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class FeedItemDetail(
    @JsonProperty("content_id") val contentId: UUID,
    @JsonProperty("raw_content_id") val rawContentId: UUID? = null,
    @JsonProperty("final_score") val finalScore: Double? = null,
    @JsonProperty("scoring_components") val scoringComponents: Map<String, Double>? = null,
    @JsonProperty("rerank_score") val rerankScore: Double? = null,
    @JsonProperty("filtered_out_by") val filteredOutBy: String? = null
)
