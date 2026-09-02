package com.skd.userinteractions.integration

import com.skd.userinteractions.job.CreateNextPartitionJob
import com.skd.userinteractions.job.DropOldPartitionsJob
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.quartz.JobExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.YearMonth

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PartitionJobsIntegrationTest {

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
    lateinit var createNextPartitionJob: CreateNextPartitionJob

    @Autowired
    lateinit var dropOldPartitionsJob: DropOldPartitionsJob

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `CreateNextPartitionJob is wired in Spring context`() {
        assertThat(createNextPartitionJob).isNotNull
    }

    @Test
    fun `DropOldPartitionsJob is wired in Spring context`() {
        assertThat(dropOldPartitionsJob).isNotNull
    }

    @Test
    fun `CreateNextPartitionJob creates partition in real database`() {
        val context = mockk<JobExecutionContext>(relaxed = true)

        createNextPartitionJob.execute(context)

        // Verify the partition exists by querying pg_catalog
        val targetMonth = YearMonth.now().plusMonths(2)
        val expectedName = "user_interactions_${targetMonth.year}_${String.format("%02d", targetMonth.monthValue)}"

        val partitions = jdbcTemplate.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'interactions' AND tablename LIKE 'user_interactions_%'",
            String::class.java
        )

        assertThat(partitions).contains(expectedName)
    }

    @Test
    fun `CreateNextPartitionJob is idempotent`() {
        val context = mockk<JobExecutionContext>(relaxed = true)

        // Run twice, should not throw
        createNextPartitionJob.execute(context)
        createNextPartitionJob.execute(context)

        val targetMonth = YearMonth.now().plusMonths(2)
        val expectedName = "user_interactions_${targetMonth.year}_${String.format("%02d", targetMonth.monthValue)}"

        val partitions = jdbcTemplate.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'interactions' AND tablename LIKE 'user_interactions_%'",
            String::class.java
        )

        assertThat(partitions.count { it == expectedName }).isEqualTo(1)
    }

    @Test
    fun `DropOldPartitionsJob runs without errors when no old partitions exist`() {
        val context = mockk<JobExecutionContext>(relaxed = true)

        // Should not throw - no old partitions to drop
        dropOldPartitionsJob.execute(context)
    }
}
