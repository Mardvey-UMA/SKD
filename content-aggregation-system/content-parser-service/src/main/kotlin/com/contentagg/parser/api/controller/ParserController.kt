package com.contentagg.parser.api.controller

import com.contentagg.parser.api.ParserApi
import com.contentagg.parser.api.model.parser.getStatus.GetStatusResponse
import com.contentagg.parser.db.service.parsertask.ParserTaskService
import com.contentagg.parser.integration.rest.configservice.cache.ParserConfigService
import com.contentagg.parser.processor.scheduler.ScheduleSourcesProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class ParserController(
    private val scheduleSourcesProcessor: ScheduleSourcesProcessor,
    private val parserConfigService: ParserConfigService,
    private val parserTaskService: ParserTaskService,
) : ParserApi {

    companion object {
        private val log = LoggerFactory.getLogger(ParserController::class.java)
    }

    override fun parseAllSources(): ResponseEntity<Map<String, String>> {
        log.info("Manual trigger: schedule tasks for all active sources")
        scheduleSourcesProcessor.scheduleActiveSources()
        return ResponseEntity.accepted().body(
            mapOf(
                "status" to "accepted",
                "message" to "Tasks scheduled for all active sources",
            )
        )
    }

    override fun getStatus(): ResponseEntity<GetStatusResponse> {
        log.debug("Fetching parser status")
        return try {
            val sources = parserConfigService.getActiveSources()
            val parsersByType = sources
                .groupBy { it.sourceType?.name ?: "UNKNOWN" }
                .mapValues { it.value.size.toLong() }
            val response = GetStatusResponse(
                status = "operational",
                activeSources = sources.size,
                parsersByType = parsersByType,
                totalTasks = parserTaskService.count(),
                metrics = mapOf(
                    "activeSources" to sources.size,
                    "parsersByType" to parsersByType,
                    "totalTasks" to parserTaskService.count(),
                ),
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            log.error("Failed to get parser status: {}", e.message, e)
            val response = GetStatusResponse(
                status = "error",
                activeSources = 0,
                parsersByType = emptyMap(),
                totalTasks = 0L,
                metrics = mapOf("error" to (e.message ?: "Unknown error")),
            )
            ResponseEntity.ok(response)
        }
    }

    override fun health(): ResponseEntity<Map<String, String>> {
        return try {
            parserConfigService.getActiveSources()
            ResponseEntity.ok(
                mapOf(
                    "status" to "UP",
                    "service" to "content-parser-service",
                )
            )
        } catch (e: Exception) {
            log.error("Health check failed: {}", e.message, e)
            ResponseEntity.ok(
                mapOf(
                    "status" to "DOWN",
                    "service" to "content-parser-service",
                )
            )
        }
    }
}
