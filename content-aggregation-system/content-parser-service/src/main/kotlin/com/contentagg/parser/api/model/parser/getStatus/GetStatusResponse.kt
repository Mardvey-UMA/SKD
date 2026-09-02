package com.contentagg.parser.api.model.parser.getStatus

data class GetStatusResponse(
    val status: String,
    val activeSources: Int,
    val parsersByType: Map<String, Long>,
    val totalTasks: Long,
    val metrics: Map<String, Any?>?,
)
