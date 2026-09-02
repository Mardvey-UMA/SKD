package com.contentplatform.user.integration.kafka

import com.contentplatform.user.integration.IntegrationTestBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.UUID

@Tag("integration")
class KafkaDltIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Value("\${spring.kafka.bootstrap-servers}")
    lateinit var bootstrapServers: String

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.execute("DELETE FROM outbox")
        jdbcTemplate.execute("DELETE FROM profiles")
    }

    @Test
    fun `poison pill with invalid UUID should route to DLT and not create profile`() {
        // Message with invalid UUID — causes IllegalArgumentException (non-retryable)
        val poisonPill = """
            {
              "event_type": "user.registered",
              "aggregate_type": "User",
              "aggregate_id": "bad-key",
              "payload": {
                "user_id": "not-a-valid-uuid",
                "email": "poison@example.com"
              }
            }
        """.trimIndent()

        kafkaTemplate.send("user.registered", "bad-key", poisonPill).get()

        val consumer = createDltConsumer()
        try {
            consumer.subscribe(listOf("user.registered.DLT"))

            val deadline = System.currentTimeMillis() + 30_000L
            val found = mutableListOf<String>()
            while (System.currentTimeMillis() < deadline && found.isEmpty()) {
                val polled = consumer.poll(Duration.ofMillis(1000))
                polled.forEach { found.add(it.value()) }
            }

            assertThat(found).`as`("Expected poison pill to be routed to DLT").isNotEmpty
        } finally {
            consumer.close()
        }

        // No profile should have been created
        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM profiles",
            Int::class.java
        )
        assertThat(count).isEqualTo(0)
    }

    private fun createDltConsumer(): KafkaConsumer<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "dlt-test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "true",
        )
        return KafkaConsumer(props)
    }
}
