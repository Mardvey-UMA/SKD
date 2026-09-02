package com.contentagg.parser.integration.rest.habr.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class HabrCommentsDto(
    @JsonProperty("comments") val comments: Map<String, HabrCommentDto>?,
    @JsonProperty("threads") val threads: List<HabrThreadDto>?,
    @JsonProperty("lastCommentTimestamp") val lastCommentTimestamp: Long?,
    @JsonProperty("pinnedCommentIds") val pinnedCommentIds: List<String>?,
) {
    data class HabrCommentDto(
        @JsonProperty("id") val id: String?,
        @JsonProperty("parentId") val parentId: String?,
        @JsonProperty("level") val level: Int?,
        @JsonProperty("timePublished") val timePublished: Instant?,
        @JsonProperty("timeChanged") val timeChanged: Instant?,
        @JsonProperty("score") val score: Int?,
        @JsonProperty("message") val message: String?,
        @JsonProperty("author") val author: HabrAuthorDto?,
        @JsonProperty("isAuthor") val isAuthor: Boolean?,
        @JsonProperty("isPostAuthor") val isPostAuthor: Boolean?,
        @JsonProperty("children") val children: List<String>?,
        @JsonProperty("isPinned") val isPinned: Boolean?,
    )

    data class HabrThreadDto(
        @JsonProperty("id") val id: String?,
        @JsonProperty("count") val count: Int?,
        @JsonProperty("lastCommentId") val lastCommentId: String?,
        @JsonProperty("lastCommentTimePublished") val lastCommentTimePublished: Instant?,
    )
}
