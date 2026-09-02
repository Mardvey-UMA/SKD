package com.contentagg.config.configuration

import com.contentagg.config.configuration.properties.KafkaProperties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
@EnableKafka
class KafkaConfiguration(private val kafkaProperties: KafkaProperties) {

    companion object {
        private val log = LoggerFactory.getLogger(KafkaConfiguration::class.java)
    }

    init {
        log.info("KafkaConfiguration initialized with bootstrap servers: {}", kafkaProperties.bootstrapServers)
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, ByteArray> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
                ProducerConfig.ACKS_CONFIG to kafkaProperties.producer.acks,
                ProducerConfig.RETRIES_CONFIG to kafkaProperties.producer.retries,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            )
        )

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, ByteArray> = KafkaTemplate(producerFactory())

    /**
     * Second producer factory for JSON-serialized topics (source.added).
     * Uses StringSerializer for value — payload is serialized to JSON string in the producer code.
     */
    @Bean
    fun jsonProducerFactory(): ProducerFactory<String, String> =
        DefaultKafkaProducerFactory(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.ACKS_CONFIG to kafkaProperties.producer.acks,
                ProducerConfig.RETRIES_CONFIG to kafkaProperties.producer.retries,
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 1,
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            )
        )

    @Bean
    fun jsonKafkaTemplate(): KafkaTemplate<String, String> = KafkaTemplate(jsonProducerFactory())

    /**
     * Consumer factory for self-consumption of `source.config.updated` used by
     * SourceConfigUpdatedCacheInvalidator to invalidate the Valkey catalog cache.
     */
    @Bean
    fun protoConsumerFactory(): ConsumerFactory<String, ByteArray> =
        DefaultKafkaConsumerFactory(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                ConsumerConfig.GROUP_ID_CONFIG to "config-catalog-cache-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true,
            )
        )

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, ByteArray> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, ByteArray>()
        factory.consumerFactory = protoConsumerFactory()
        return factory
    }
}
