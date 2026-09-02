package com.contentagg.aggregator.api.model.content

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.ceil

data class PageResponse<T>(
    @JsonProperty("content")
    val content: List<T>,

    @JsonProperty("page")
    val page: Int,

    @JsonProperty("size")
    val size: Int,

    @JsonProperty("total_elements")
    val totalElements: Long,

    @JsonProperty("total_pages")
    val totalPages: Int,

    @JsonProperty("first")
    val first: Boolean,

    @JsonProperty("last")
    val last: Boolean
) {
    companion object {
        fun <T> of(content: List<T>, page: Int, size: Int, totalElements: Long): PageResponse<T> {
            val totalPages = if (size > 0) ceil(totalElements.toDouble() / size).toInt() else 0
            return PageResponse(
                content = content,
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                first = page == 0,
                last = page >= totalPages - 1
            )
        }
    }
}
