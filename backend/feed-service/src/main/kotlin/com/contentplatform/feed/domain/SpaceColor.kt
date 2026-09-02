package com.contentplatform.feed.domain

enum class SpaceColor {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    TEAL,
    BLUE,
    PURPLE,
    PINK;

    companion object {
        fun fromString(value: String): SpaceColor {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid color: $value. Allowed: ${entries.joinToString(",")}")
        }
    }
}
