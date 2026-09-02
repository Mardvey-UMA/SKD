package com.contentplatform.feed.unit.cache

import com.contentplatform.feed.infrastructure.cache.CursorCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Base64

@Tag("unit")
class CursorCodecTest {

    private val codec = CursorCodec()

    @Nested
    inner class `encode` {

        @Test
        fun `should encode offset as base64 JSON with o field`() {
            val cursor = codec.encode(20)

            val decoded = String(Base64.getUrlDecoder().decode(cursor))
            assertThat(decoded).contains("\"o\"")
            assertThat(decoded).contains("20")
        }

        @Test
        fun `should encode zero offset`() {
            val cursor = codec.encode(0)

            val decoded = String(Base64.getUrlDecoder().decode(cursor))
            assertThat(decoded).contains("\"o\"")
            assertThat(decoded).contains("0")
        }

        @Test
        fun `should produce valid base64 string`() {
            val cursor = codec.encode(42)

            // Should not throw — must be decodable with URL-safe decoder
            val bytes = Base64.getUrlDecoder().decode(cursor)
            assertThat(bytes).isNotEmpty()
        }

        @Test
        fun `should encode without padding characters`() {
            val cursor = codec.encode(20)
            assertThat(cursor).doesNotContain("=")
        }

        @Test
        fun `should encode using URL-safe alphabet (no plus or slash)`() {
            // Test with various offsets to increase chance of hitting + or / in standard Base64
            val offsets = listOf(0, 1, 10, 100, 1000, 99999, 123456789)
            for (offset in offsets) {
                val cursor = codec.encode(offset)
                assertThat(cursor)
                    .describedAs("Cursor for offset $offset should not contain '+' or '/'")
                    .doesNotContain("+")
                    .doesNotContain("/")
            }
        }

        @Test
        fun `should encode and decode roundtrip`() {
            val original = 55
            val cursor = codec.encode(original)
            val result = codec.decode(cursor)

            assertThat(result).isEqualTo(original)
        }
    }

    @Nested
    inner class `decode` {

        @Test
        fun `should decode valid base64 JSON cursor to offset`() {
            val json = """{"o": 40}"""
            val cursor = Base64.getEncoder().encodeToString(json.toByteArray())

            val offset = codec.decode(cursor)

            assertThat(offset).isEqualTo(40)
        }

        @Test
        fun `should return 0 for null cursor`() {
            val offset = codec.decode(null)
            assertThat(offset).isEqualTo(0)
        }

        @Test
        fun `should return 0 for empty string cursor`() {
            val offset = codec.decode("")
            assertThat(offset).isEqualTo(0)
        }

        @Test
        fun `should return 0 for invalid base64`() {
            val offset = codec.decode("not-valid-base64!!!")
            assertThat(offset).isEqualTo(0)
        }

        @Test
        fun `should return 0 for valid base64 but invalid JSON`() {
            val cursor = Base64.getEncoder().encodeToString("not json".toByteArray())
            val offset = codec.decode(cursor)
            assertThat(offset).isEqualTo(0)
        }

        @Test
        fun `should return 0 when JSON is missing o field`() {
            val json = """{"x": 10}"""
            val cursor = Base64.getEncoder().encodeToString(json.toByteArray())

            val offset = codec.decode(cursor)
            assertThat(offset).isEqualTo(0)
        }

        @Test
        fun `should preserve negative offset`() {
            val json = """{"o": -5}"""
            val cursor = Base64.getEncoder().encodeToString(json.toByteArray())

            val offset = codec.decode(cursor)
            assertThat(offset).isEqualTo(-5)
        }

        @Test
        fun `should handle large offset values`() {
            val json = """{"o": 999999}"""
            val cursor = Base64.getEncoder().encodeToString(json.toByteArray())

            val offset = codec.decode(cursor)
            assertThat(offset).isEqualTo(999999)
        }

        @Test
        fun `should decode URL-safe base64 cursor`() {
            val json = """{"o": 75}"""
            val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

            val offset = codec.decode(cursor)

            assertThat(offset).isEqualTo(75)
        }

        @Test
        fun `should decode standard base64 cursor for backward compatibility`() {
            val json = """{"o": 100}"""
            val cursor = Base64.getEncoder().encodeToString(json.toByteArray())

            val offset = codec.decode(cursor)

            assertThat(offset).isEqualTo(100)
        }

        @Test
        fun `should decode URL-safe cursor without padding`() {
            // {"o":12345} in URL-safe Base64 without padding
            val json = """{"o":12345}"""
            val withPadding = Base64.getEncoder().encodeToString(json.toByteArray())
            val withoutPadding = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

            // Verify the standard-encoded version has padding (proving the test is meaningful)
            assertThat(withPadding).endsWith("=")

            // The URL-safe version without padding should still decode correctly
            val offset = codec.decode(withoutPadding)
            assertThat(offset).isEqualTo(12345)
        }
    }
}
