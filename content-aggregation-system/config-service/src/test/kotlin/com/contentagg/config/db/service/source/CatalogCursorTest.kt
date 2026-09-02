package com.contentagg.config.db.service.source

import com.contentagg.config.exception.InvalidCursorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class CatalogCursorTest {

    @Test
    fun `encode then decode round-trips the cursor`() {
        val cursor = CatalogCursor(
            createdAt = LocalDateTime.of(2026, 4, 16, 10, 0, 0),
            id = UUID.fromString("a0000000-0000-0000-0000-000000000001"),
        )

        val encoded = CatalogCursor.encode(cursor)
        val decoded = CatalogCursor.decode(encoded)

        assertThat(decoded).isEqualTo(cursor)
    }

    @Test
    fun `decode garbage cursor throws InvalidCursorException`() {
        assertThatThrownBy { CatalogCursor.decode("!!!not-valid-base64!!!") }
            .isInstanceOf(InvalidCursorException::class.java)
    }

    @Test
    fun `decode malformed payload throws InvalidCursorException`() {
        val bytes = "\"not_json\"".toByteArray()
        val badPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        assertThatThrownBy { CatalogCursor.decode(badPayload) }
            .isInstanceOf(InvalidCursorException::class.java)
    }
}
