package com.contentplatform.feed.infrastructure.cache

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class CursorCodec {

    private val objectMapper = ObjectMapper()

    fun encode(offset: Int): String {
        val json = """{"o":$offset}"""
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
    }

    fun decode(cursor: String?): Int {
        if (cursor.isNullOrBlank()) return 0
        return try {
            val json = String(Base64.getUrlDecoder().decode(cursor))
            val node = objectMapper.readTree(json)
            node.get("o")?.asInt(0) ?: 0
        } catch (e: Exception) {
            try {
                val json = String(Base64.getDecoder().decode(cursor))
                val node = objectMapper.readTree(json)
                node.get("o")?.asInt(0) ?: 0
            } catch (e2: Exception) {
                0
            }
        }
    }
}
