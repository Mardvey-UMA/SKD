package com.skd.userinteractions.integration

import com.skd.userinteractions.configuration.InteractionsProperties
import org.assertj.core.api.Assertions.assertThat
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

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class InteractionsConfigurationTest {

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
    lateinit var interactionsProperties: InteractionsProperties

    @Test
    fun `batch configuration properties are bound`() {
        assertThat(interactionsProperties.batch.flushIntervalMs).isEqualTo(1000)
        assertThat(interactionsProperties.batch.maxEventsPerUser).isEqualTo(5)
    }

    @Test
    fun `validation configuration properties are bound`() {
        assertThat(interactionsProperties.validation.maxEventsPerBatch).isEqualTo(100)
        assertThat(interactionsProperties.validation.maxTimestampFutureSec).isEqualTo(60)
        assertThat(interactionsProperties.validation.maxTimestampAgeHours).isEqualTo(24)
    }

    @Test
    fun `partition configuration properties are bound`() {
        assertThat(interactionsProperties.partition.retentionMonths).isEqualTo(6)
    }
}
