package com.contentagg.parser.processor.scheduler

import com.contentagg.parser.configuration.properties.scheduler.SchedulerProperties
import com.contentagg.parser.db.service.parsertask.ParserTaskService
import com.contentagg.parser.integration.rest.configservice.cache.ParserConfigService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ScheduleSourcesProcessor(
    private val parserConfigService: ParserConfigService,
    private val parserTaskService: ParserTaskService,
    private val schedulerProperties: SchedulerProperties,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ScheduleSourcesProcessor::class.java)
        private const val TASK_TYPE = "PARSE_SOURCE"
    }

    fun scheduleActiveSources() {
        val effectiveTypes = schedulerProperties.enabledSourceTypes
            .map { it.uppercase() }
            .toSet()
        if (effectiveTypes.isEmpty()) {
            log.warn("enabledSourceTypes is empty; no tasks will be scheduled")
            return
        }

        val allSources = parserConfigService.getActiveSources()
        val filtered = allSources.filter { src ->
            src.sourceType?.name?.uppercase() in effectiveTypes
        }

        var created = 0
        var skipped = 0
        for (source in filtered) {
            val rawId = source.id ?: continue
            val sourceTypeName = source.sourceType?.name ?: continue
            val inserted = try {
                parserTaskService.insertPendingIfAbsent(
                    sourceId = UUID.fromString(rawId),
                    sourceType = sourceTypeName,
                    taskType = TASK_TYPE,
                )
            } catch (e: Exception) {
                log.error(
                    "insertPendingIfAbsent failed sourceId={} sourceType={}: {}",
                    rawId, sourceTypeName, e.message, e,
                )
                false
            }
            if (inserted) created++ else skipped++
        }
        log.info(
            "ScheduleSourcesJob done: active={} filtered={} created={} skipped={} effectiveTypes={}",
            allSources.size, filtered.size, created, skipped, effectiveTypes,
        )
    }
}
