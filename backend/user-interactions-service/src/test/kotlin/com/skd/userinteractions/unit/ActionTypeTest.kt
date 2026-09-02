package com.skd.userinteractions.unit

import com.skd.userinteractions.domain.ActionType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

@Tag("unit")
class ActionTypeTest {

    @Test
    fun `enum has exactly six canonical values`() {
        assertThat(ActionType.entries).hasSize(6)
    }

    @Test
    fun `enum contains exactly the canonical 6 values`() {
        val names = ActionType.entries.map { it.name }
        assertThat(names).containsExactlyInAnyOrder(
            "IMPRESSION", "OPEN", "CLOSE", "LIKE", "DISLIKE", "BOOKMARK"
        )
    }

    @Test
    fun `fromString canonical LIKE returns LIKE`() {
        assertThat(ActionType.fromString("LIKE")).isEqualTo(ActionType.LIKE)
    }

    @Test
    fun `fromString is case-insensitive for canonical names`() {
        assertThat(ActionType.fromString("like")).isEqualTo(ActionType.LIKE)
        assertThat(ActionType.fromString("Like")).isEqualTo(ActionType.LIKE)
        assertThat(ActionType.fromString("DISLIKE")).isEqualTo(ActionType.DISLIKE)
    }

    @Test
    fun `fromString trims whitespace`() {
        assertThat(ActionType.fromString(" LIKE ")).isEqualTo(ActionType.LIKE)
    }

    @ParameterizedTest
    @MethodSource("legacyMappings")
    fun `fromString maps legacy names to canonical values`(input: String, expected: ActionType) {
        assertThat(ActionType.fromString(input)).isEqualTo(expected)
    }

    @Test
    fun `fromString throws IllegalArgumentException for unknown value`() {
        assertThatThrownBy { ActionType.fromString("garbage") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `all canonical entries are enumerable via entries`() {
        val names = ActionType.entries.map { it.name }
        assertThat(names).contains("IMPRESSION", "OPEN", "CLOSE", "LIKE", "DISLIKE", "BOOKMARK")
    }

    companion object {
        @JvmStatic
        fun legacyMappings() = listOf(
            Arguments.of("VIEW", ActionType.IMPRESSION),
            Arguments.of("CLICK", ActionType.OPEN),
            Arguments.of("SCROLL_PAST", ActionType.CLOSE),
            Arguments.of("SAVE", ActionType.BOOKMARK),
            Arguments.of("HIDE", ActionType.DISLIKE),
            Arguments.of("SHARE", ActionType.BOOKMARK),
        )
    }
}
