package com.contentagg.config.db.service.source

import com.contentagg.config.exception.InvalidCursorException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID

/**
 * Opaque base64-encoded JSON cursor for catalog keyset pagination.
 * Payload: {"createdAt": "2026-04-16T10:00:00", "id": "uuid"}.
 */
data class CatalogCursor(
    val createdAt: LocalDateTime,
    val id: UUID,
) {
    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()
        private val encoder = Base64.getUrlEncoder().withoutPadding()
        private val decoder = Base64.getUrlDecoder()

        fun encode(cursor: CatalogCursor): String {
            val dto = CursorDto(createdAt = cursor.createdAt.toString(), id = cursor.id.toString())
            return encoder.encodeToString(mapper.writeValueAsBytes(dto))
        }

        fun decode(encoded: String): CatalogCursor {
            try {
                val bytes = decoder.decode(encoded)
                val dto = mapper.readValue<CursorDto>(bytes)
                return CatalogCursor(
                    createdAt = LocalDateTime.parse(dto.createdAt),
                    id = UUID.fromString(dto.id),
                )
            } catch (ex: IllegalArgumentException) {
                throw InvalidCursorException("Malformed cursor: ${ex.message}")
            } catch (ex: Exception) {
                throw InvalidCursorException("Invalid cursor payload: ${ex.message}")
            }
        }
    }

    data class CursorDto(val createdAt: String, val id: String)
}
