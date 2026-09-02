package com.contentplatform.user.infrastructure.client

import java.util.UUID

interface RecSystemClientPort {
    fun postOnboarding(userId: UUID, categories: List<String>, sourceContentIds: List<String>): Map<String, Any?>
    fun getCategories(locale: String): String
}
