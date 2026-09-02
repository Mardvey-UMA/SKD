package com.contentagg.config.integration.kafka.base

import com.google.protobuf.Message
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult

abstract class BaseProducer(
    protected val kafkaTemplate: KafkaTemplate<String, ByteArray>
) {

    companion object {
        private val log = LoggerFactory.getLogger(BaseProducer::class.java)
    }

    protected fun send(topic: String, key: String, message: Message) {
        log.info("Sending message to topic={} key={}", topic, key)

        val record = ProducerRecord(topic, key, message.toByteArray())

        kafkaTemplate.send(record).whenComplete { result: SendResult<String, ByteArray>?, ex: Throwable? ->
            if (ex != null) {
                log.error("Failed to send message to topic={} key={}", topic, key, ex)
            } else {
                log.debug(
                    "Message sent to topic={} partition={} offset={}",
                    topic,
                    result!!.recordMetadata.partition(),
                    result.recordMetadata.offset()
                )
            }
        }
    }
}
