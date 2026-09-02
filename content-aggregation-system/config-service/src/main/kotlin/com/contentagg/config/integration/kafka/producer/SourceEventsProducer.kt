package com.contentagg.config.integration.kafka.producer

import com.contentagg.config.enums.SourceType
import com.contentagg.config.integration.kafka.base.BaseProducer
import com.contentagg.config.proto.SourceConfigUpdatedEvent
import com.contentagg.config.proto.SourceEventType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class SourceEventsProducer(
    kafkaTemplate: KafkaTemplate<String, ByteArray>,
    @Value("\${spring.kafka.topics.source-config-updated}") private val topic: String
) : BaseProducer(kafkaTemplate) {

    companion object {
        private val log = LoggerFactory.getLogger(SourceEventsProducer::class.java)
    }

    init {
        log.info("SourceEventsProducer initialized with topic: {}", topic)
    }

    fun publishCreated(sourceId: String, sourceType: SourceType, name: String, isActive: Boolean?) {
        val event = SourceConfigUpdatedEvent.newBuilder()
            .setId(sourceId)
            .setEventType(SourceEventType.CREATED)
            .setSourceType(sourceType.name)
            .setName(name)
            .setIsActive(isActive == true)
            .setTimestampMs(System.currentTimeMillis())
            .build()
        send(topic, sourceId, event)
    }

    fun publishUpdated(sourceId: String, sourceType: SourceType, name: String, isActive: Boolean?) {
        val event = SourceConfigUpdatedEvent.newBuilder()
            .setId(sourceId)
            .setEventType(SourceEventType.UPDATED)
            .setSourceType(sourceType.name)
            .setName(name)
            .setIsActive(isActive == true)
            .setTimestampMs(System.currentTimeMillis())
            .build()
        send(topic, sourceId, event)
    }

    fun publishDeleted(sourceId: String, sourceType: SourceType, name: String) {
        val event = SourceConfigUpdatedEvent.newBuilder()
            .setId(sourceId)
            .setEventType(SourceEventType.DELETED)
            .setSourceType(sourceType.name)
            .setName(name)
            .setIsActive(false)
            .setTimestampMs(System.currentTimeMillis())
            .build()
        send(topic, sourceId, event)
    }
}
