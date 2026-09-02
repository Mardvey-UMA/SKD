package com.contentagg.config.processor.vcru

import com.contentagg.config.enums.SourceType
import com.contentagg.config.exception.InvalidSourceException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class VcruSourceHelperTest {

    private lateinit var helper: VcruSourceHelper

    @BeforeEach
    fun setUp() {
        helper = VcruSourceHelper()
    }

    @Nested
    @DisplayName("validateVcruSourceType()")
    inner class ValidateVcruSourceTypeTests {

        @Test
        @DisplayName("VCRU source type passes validation without exception")
        fun validateVcruSourceType_Vcru_NoException() {
            assertDoesNotThrow {
                helper.validateVcruSourceType(SourceType.VCRU)
            }
        }

        @Test
        @DisplayName("non-VCRU source type throws InvalidSourceException")
        fun validateVcruSourceType_NonVcru_ThrowsInvalidSourceException() {
            assertThrows<InvalidSourceException> {
                helper.validateVcruSourceType(SourceType.HABR)
            }
        }
    }

    @Nested
    @DisplayName("buildVcruUrl()")
    inner class BuildVcruUrlTests {

        @Test
        @DisplayName("returns correct VC.RU URL pattern for alias")
        fun buildVcruUrl_ReturnsCorrectPattern() {
            val result = helper.buildVcruUrl("artem")
            assertEquals("https://vc.ru/artem", result)
        }
    }

    @Nested
    @DisplayName("buildParametersMap()")
    inner class BuildParametersMapTests {

        @Test
        @DisplayName("parameters map contains all required keys with correct values")
        fun buildParametersMap_ContainsAllRequiredKeys() {
            val result = helper.buildParametersMap(
                alias = "artem",
                parseImages = true,
                maxArticles = 50,
                sorting = "hotness",
            )

            assertEquals("artem", result["vcruAlias"])
            assertEquals("true", result["parseImages"])
            assertEquals("50", result["maxArticles"])
            assertEquals("hotness", result["sorting"])
        }

        @Test
        @DisplayName("null sorting defaults to 'new'")
        fun buildParametersMap_NullSorting_DefaultsToNew() {
            val result = helper.buildParametersMap(
                alias = "artem",
                parseImages = false,
                maxArticles = 20,
                sorting = null,
            )

            assertEquals("new", result["sorting"])
        }
    }
}
