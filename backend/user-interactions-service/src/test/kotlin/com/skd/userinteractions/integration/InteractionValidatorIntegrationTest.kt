package com.skd.userinteractions.integration

import com.skd.userinteractions.api.model.interactions.InteractionBatchRequest
import com.skd.userinteractions.api.model.interactions.InteractionEvent
import com.skd.userinteractions.domain.InteractionValidator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class InteractionValidatorIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("interactions_db")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }

    @Autowired
    lateinit var validator: InteractionValidator

    private fun validEvent(
        contentId: String = UUID.randomUUID().toString(),
        actionType: String = "view",
        durationSec: Int? = null,
        timestamp: Instant = Instant.now().minusSeconds(30)
    ) = InteractionEvent(
        contentId = contentId,
        actionType = actionType,
        durationSec = durationSec,
        timestamp = timestamp
    )

    @Test
    fun `validator is injected from Spring context`() {
        assertThat(validator).isNotNull
    }

    @Test
    fun `validates batch using configuration properties`() {
        val events = (1..50).map { validEvent() }
        val request = InteractionBatchRequest(events = events)
        val result = validator.validate(request)

        assertThat(result.accepted).hasSize(50)
        assertThat(result.rejectedCount).isEqualTo(0)
    }

    @Test
    fun `rejects batch exceeding configured max size`() {
        val events = (1..101).map { validEvent() }
        val request = InteractionBatchRequest(events = events)

        assertThatThrownBy { validator.validate(request) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `filters out invalid events and returns correct counts`() {
        val valid1 = validEvent(actionType = "view")
        val valid2 = validEvent(actionType = "save")
        val invalidAction = validEvent(actionType = "not_an_action")
        val invalidTimestamp = validEvent(timestamp = Instant.now().plusSeconds(300))

        val request = InteractionBatchRequest(
            events = listOf(valid1, valid2, invalidAction, invalidTimestamp)
        )
        val result = validator.validate(request)

        assertThat(result.accepted).hasSize(2)
        assertThat(result.rejectedCount).isEqualTo(2)
    }

    @Test
    fun `uses configured timestamp tolerance from properties`() {
        // Within the configured 60s tolerance
        val withinTolerance = validEvent(timestamp = Instant.now().plusSeconds(50))
        // Beyond the configured 60s tolerance
        val beyondTolerance = validEvent(timestamp = Instant.now().plusSeconds(120))

        val request = InteractionBatchRequest(
            events = listOf(withinTolerance, beyondTolerance)
        )
        val result = validator.validate(request)

        assertThat(result.accepted).hasSize(1)
        assertThat(result.rejectedCount).isEqualTo(1)
    }
}
