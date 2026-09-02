package com.contentagg.config.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for SourceType enum in config-service.
 * Verifies the simplified 4-type enum (HABR, VCRU, TELEGRAM, RSS).
 */
class SourceTypeTest {

    @Nested
    @DisplayName("Enum Values")
    inner class EnumValuesTests {

        @Test
        @DisplayName("testAllValues_OnlyFourTypes")
        fun allValues_OnlyFourTypes() {
            val values = SourceType.values()
            assertEquals(4, values.size, "SourceType should have exactly 4 values")
        }

        @Test
        @DisplayName("testHabr_Exists")
        fun habr_Exists() {
            assertNotNull(SourceType.HABR)
        }

        @Test
        @DisplayName("testVcru_Exists")
        fun vcru_Exists() {
            assertNotNull(SourceType.VCRU)
        }

        @Test
        @DisplayName("testTelegram_Exists")
        fun telegram_Exists() {
            assertNotNull(SourceType.TELEGRAM)
        }

        @Test
        @DisplayName("testRss_Exists")
        fun rss_Exists() {
            assertNotNull(SourceType.RSS)
        }
    }

    @Nested
    @DisplayName("Name Tests")
    inner class NameTests {

        @Test
        @DisplayName("testName_MatchesExpectedValues")
        fun testName_MatchesExpectedValues() {
            assertEquals("HABR", SourceType.HABR.name)
            assertEquals("VCRU", SourceType.VCRU.name)
            assertEquals("TELEGRAM", SourceType.TELEGRAM.name)
            assertEquals("RSS", SourceType.RSS.name)
        }

        @Test
        @DisplayName("testOrdinal_MatchesExpectedOrder")
        fun testOrdinal_MatchesExpectedOrder() {
            assertEquals(0, SourceType.HABR.ordinal)
            assertEquals(1, SourceType.VCRU.ordinal)
            assertEquals(2, SourceType.TELEGRAM.ordinal)
            assertEquals(3, SourceType.RSS.ordinal)
        }
    }

    @Nested
    @DisplayName("ValueOf Tests")
    inner class ValueOfTests {

        @ParameterizedTest
        @ValueSource(strings = ["HABR", "VCRU", "TELEGRAM", "RSS"])
        @DisplayName("testValueOf_ValidNames_ReturnsCorrectEnum")
        fun valueOf_ValidNames_ReturnsCorrectEnum(name: String) {
            val result = SourceType.valueOf(name)
            assertNotNull(result)
            assertEquals(name, result.name)
        }

        @Test
        @DisplayName("testValueOf_InvalidName_ThrowsException")
        fun valueOf_InvalidName_ThrowsException() {
            assertThrows<IllegalArgumentException> {
                SourceType.valueOf("INVALID_TYPE")
            }
        }

        @Test
        @DisplayName("testValueOf_OldHabrTypes_ThrowException")
        fun valueOf_OldHabrTypes_ThrowException() {
            assertThrows<IllegalArgumentException> { SourceType.valueOf("HABR_COMPANY") }
            assertThrows<IllegalArgumentException> { SourceType.valueOf("HABR_USER") }
            assertThrows<IllegalArgumentException> { SourceType.valueOf("HABR_HUB") }
        }

        @Test
        @DisplayName("testValueOf_OldVcruTypes_ThrowException")
        fun valueOf_OldVcruTypes_ThrowException() {
            assertThrows<IllegalArgumentException> { SourceType.valueOf("VCRU_NEWS") }
            assertThrows<IllegalArgumentException> { SourceType.valueOf("VCRU_ARTICLE") }
        }

        @Test
        @DisplayName("testValueOf_OldTelegramType_ThrowsException")
        fun valueOf_OldTelegramType_ThrowsException() {
            assertThrows<IllegalArgumentException> { SourceType.valueOf("TELEGRAM_CHANNEL") }
        }

        @Test
        @DisplayName("testValueOf_OldRssType_ThrowsException")
        fun valueOf_OldRssType_ThrowsException() {
            assertThrows<IllegalArgumentException> { SourceType.valueOf("RSS_FEED") }
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    inner class ToStringTests {

        @Test
        @DisplayName("testToString_MatchesName")
        fun testToString_MatchesName() {
            assertEquals("HABR", SourceType.HABR.toString())
            assertEquals("VCRU", SourceType.VCRU.toString())
            assertEquals("TELEGRAM", SourceType.TELEGRAM.toString())
            assertEquals("RSS", SourceType.RSS.toString())
        }
    }
}
