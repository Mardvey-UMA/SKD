package com.contentagg.parser.configuration

import com.contentagg.parser.configuration.properties.KafkaProperties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@EnableKafka
@Configuration
class KafkaConfiguration(
    private val kafkaProperties: KafkaProperties,
    @Value("\${spring.kafka.consumer.back-off.interval:1000}") private val backOffInterval: Long,
    @Value("\${spring.kafka.consumer.back-off.max-attempts:3}") private val backOffMaxAttempts: Long,
) {

    companion object {
        private val log = LoggerFactory.getLogger(KafkaConfiguration::class.java)
    }

    // ==================== BACK-OFF ====================

    @Bean
    fun kafkaFixedBackOff(): FixedBackOff = FixedBackOff(backOffInterval, backOffMaxAttempts)

    // ==================== CONSUMER ====================

    @Bean
    fun consumerFactory(): DefaultKafkaConsumerFactory<String, ByteArray> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to kafkaProperties.groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )

        log.info(
            "Kafka consumer factory configured: bootstrapServers={}, groupId={}",
            kafkaProperties.bootstrapServers, kafkaProperties.groupId
        )

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, ByteArray> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, ByteArray>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setCommonErrorHandler(DefaultErrorHandler(kafkaFixedBackOff()))

        log.info("Kafka listener container factory configured: ackMode=MANUAL")

        return factory
    }

    @Bean
    fun configUpdateConsumerFactory(): DefaultKafkaConsumerFactory<String, ByteArray> {
        val configGroupId = kafkaProperties.consumer["config-group-id"] ?: "parser-config-group"
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProperties.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to configGroupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )

        log.info(
            "Kafka config-update consumer factory configured: bootstrapServers={}, groupId={}",
            kafkaProperties.bootstrapServers, configGroupId
        )

        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun configUpdateKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, ByteArray> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, ByteArray>()
        factory.consumerFactory = configUpdateConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setCommonErrorHandler(DefaultErrorHandler(kafkaFixedBackOff()))

        log.info("Kafka config-update listener container factory configured: ackMode=MANUAL")

        return factory
    }

}
