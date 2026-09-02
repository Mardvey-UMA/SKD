package com.contentagg.aggregator.db.repository.aggregatedcontent.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SourceTypeTest {

    @Nested
    @DisplayName("Enum Values")
    inner class EnumValuesTests {

        @Test
        @DisplayName("testAllValues_OnlyFourTypes")
        fun allValues_OnlyFourTypes() {
            val values = SourceType.entries
            assertEquals(4, values.size, "SourceType should have exactly 4 values")
        }

        @Test
        @DisplayName("testContainsAllExpectedTypes")
        fun testContainsAllExpectedTypes() {
            val values = SourceType.entries
            assertTrue(SourceType.HABR in values)
            assertTrue(SourceType.VCRU in values)
            assertTrue(SourceType.TELEGRAM in values)
            assertTrue(SourceType.RSS in values)
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
        @DisplayName("testFromName_ValidNames_ReturnCorrectEnum")
        fun fromName_ValidNames_ReturnCorrectEnum(name: String) {
            val result = SourceType.valueOf(name)
            assertNotNull(result)
            assertEquals(name, result.name)
        }

        @Test
        @DisplayName("testValueOf_InvalidName_ThrowsException")
        fun valueOf_InvalidName_ThrowsException() {
            assertThrows(IllegalArgumentException::class.java) {
                SourceType.valueOf("INVALID_TYPE")
            }
        }

        @Test
        @DisplayName("testValueOf_Lowercase_ThrowsException")
        fun valueOf_Lowercase_ThrowsException() {
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("habr") }
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("vcru") }
        }
    }

    @Nested
    @DisplayName("Compatibility Tests")
    inner class CompatibilityTests {

        @Test
        @DisplayName("testOldHabrTypes_DoNotExist")
        fun oldHabrTypes_DoNotExist() {
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("HABR_COMPANY") }
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("HABR_USER") }
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("HABR_HUB") }
        }

        @Test
        @DisplayName("testOldVcruTypes_DoNotExist")
        fun oldVcruTypes_DoNotExist() {
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("VCRU_NEWS") }
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("VCRU_ARTICLE") }
        }

        @Test
        @DisplayName("testOldTelegramType_DoesNotExist")
        fun oldTelegramType_DoesNotExist() {
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("TELEGRAM_CHANNEL") }
        }

        @Test
        @DisplayName("testOldRssType_DoesNotExist")
        fun oldRssType_DoesNotExist() {
            assertThrows(IllegalArgumentException::class.java) { SourceType.valueOf("RSS_FEED") }
        }
    }
}
