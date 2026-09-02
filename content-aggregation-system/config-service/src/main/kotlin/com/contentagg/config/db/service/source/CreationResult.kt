package com.contentagg.config.db.service.source

import com.contentagg.config.db.repository.source.model.dto.SourceResponse

/**
 * Outcome of SourceService.create() with "was_existing" semantics (Phase 2).
 * - wasExisting=false → freshly inserted row
 * - wasExisting=true  → row already existed (or lost the unique-violation race)
 */
data class CreationResult(
    val source: SourceResponse,
    val wasExisting: Boolean,
)
