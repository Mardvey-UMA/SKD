package com.skd.userinteractions.domain

enum class ActionType {
    IMPRESSION, OPEN, CLOSE, LIKE, DISLIKE, BOOKMARK;

    companion object {
        private val LEGACY_MAP = mapOf(
            "VIEW" to IMPRESSION,
            "CLICK" to OPEN,
            "SCROLL_PAST" to CLOSE,
            "SAVE" to BOOKMARK,
            "HIDE" to DISLIKE,
            "SHARE" to BOOKMARK,
        )

        fun fromString(input: String): ActionType {
            val normalized = input.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: LEGACY_MAP[normalized]
                ?: throw IllegalArgumentException("Unknown action type: '$input'")
        }
    }
}
