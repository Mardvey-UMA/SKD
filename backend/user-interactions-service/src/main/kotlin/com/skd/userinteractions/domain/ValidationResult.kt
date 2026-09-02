package com.skd.userinteractions.domain

import com.skd.userinteractions.api.model.interactions.InteractionEvent

data class ValidationResult(
    val accepted: List<InteractionEvent>,
    val rejectedCount: Int
)
