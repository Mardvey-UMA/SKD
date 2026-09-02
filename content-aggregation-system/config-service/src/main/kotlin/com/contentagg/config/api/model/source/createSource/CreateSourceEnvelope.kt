package com.contentagg.config.api.model.source.createSource

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Generic envelope for POST source creation responses (Phase 2):
 *   { "source": {...}, "was_existing": boolean }
 * Used by the api/controller layer to wrap the per-type response payloads.
 */
data class CreateSourceEnvelope<T>(
    val source: T,
    @JsonProperty("was_existing") val wasExisting: Boolean,
)
