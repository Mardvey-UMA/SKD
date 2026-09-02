package com.skd.userinteractions.api.model.interactions

data class InteractionBatchRequest(
    val events: List<InteractionEvent>
)
