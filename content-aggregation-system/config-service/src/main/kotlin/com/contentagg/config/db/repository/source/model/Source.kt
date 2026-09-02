package com.contentagg.config.db.repository.source.model
import com.fasterxml.jackson.annotation.JsonProperty

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Spring Data JDBC entity mapping to config.sources table.
 * Immutable data class — Spring Data JDBC 3.x supports UUID @Id with null for auto-generation.
 */
@Table("sources")
data class Source(

    @Id
    val id: UUID?,

    @Column("source_type")
    val sourceType: String,

    val name: String,

    val url: String?,

    @Column("update_frequency_minutes")
    val updateFrequencyMinutes: Int?,

    @Column("is_active")
    @JsonProperty("isActive") val isActive: Boolean?,

    /**
     * JSONB column stored as String.
     * Converted to/from Map in service layer via JsonConversionService.
     */
    val parameters: String?,

    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime?,

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: LocalDateTime?

) {
    companion object {
        /**
         * Factory method for creating new (unsaved) instances.
         * Pass null for id — the database generates a UUID via DEFAULT gen_random_uuid().
         * Pass null for createdAt and updatedAt — Spring auditing populates them on save.
         */
        fun newSource(
            sourceType: String,
            name: String,
            url: String?,
            updateFrequencyMinutes: Int?,
            isActive: Boolean?,
            parameters: String?
        ): Source = Source(
            id = null,
            sourceType = sourceType,
            name = name,
            url = url,
            updateFrequencyMinutes = updateFrequencyMinutes,
            isActive = isActive,
            parameters = parameters,
            createdAt = null,
            updatedAt = null
        )
    }
}
