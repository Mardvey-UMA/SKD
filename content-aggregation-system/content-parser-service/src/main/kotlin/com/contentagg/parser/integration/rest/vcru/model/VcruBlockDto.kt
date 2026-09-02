package com.contentagg.parser.integration.rest.vcru.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class VcruBlockDto(
    @JsonProperty("type") val type: String?,
    @JsonProperty("data") val data: VcruBlockDataDto?,
    @JsonProperty("cover") val cover: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VcruBlockDataDto(
    @JsonProperty("text") val text: String?,
    @JsonProperty("level") val level: Int?,
    @JsonProperty("items") val items: JsonNode?,
    @JsonProperty("type") val type: String?
) {
    /**
     * Extract media items from the items node.
     * items[] can contain objects (media items) or strings (list items).
     * This method returns only objects that have an "image" field.
     */
    fun getMediaItems(): List<VcruBlockMediaItemDto> {
        val node = items ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.filter { it.isObject && it.has("image") }
            .map { item ->
                VcruBlockMediaItemDto(
                    title = item.get("title")?.asText(),
                    image = item.get("image")?.let { img ->
                        VcruBlockImageDto(
                            type = img.get("type")?.asText(),
                            data = img.get("data")?.let { d ->
                                VcruBlockImageDataDto(
                                    uuid = d.get("uuid")?.asText(),
                                    width = d.get("width")?.asInt(),
                                    height = d.get("height")?.asInt(),
                                    size = d.get("size")?.asLong(),
                                    type = d.get("type")?.asText()
                                )
                            }
                        )
                    }
                )
            }
    }

    /**
     * Extract string items from the items node (for list blocks).
     * Returns text content of each item — either from "title" field or the string itself.
     */
    fun getStringItems(): List<String> {
        val node = items ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.map { item ->
            when {
                item.isTextual -> item.asText()
                item.isObject -> item.get("title")?.asText() ?: item.get("text")?.asText() ?: item.toString()
                else -> item.toString()
            }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class VcruBlockMediaItemDto(
    @JsonProperty("title") val title: String?,
    @JsonProperty("image") val image: VcruBlockImageDto?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VcruBlockImageDto(
    @JsonProperty("type") val type: String?,
    @JsonProperty("data") val data: VcruBlockImageDataDto?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VcruBlockImageDataDto(
    @JsonProperty("uuid") val uuid: String?,
    @JsonProperty("width") val width: Int?,
    @JsonProperty("height") val height: Int?,
    @JsonProperty("size") val size: Long?,
    @JsonProperty("type") val type: String?
)
