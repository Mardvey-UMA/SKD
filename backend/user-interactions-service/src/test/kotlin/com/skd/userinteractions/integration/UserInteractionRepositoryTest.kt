package com.skd.userinteractions.integration

import com.skd.userinteractions.domain.UserInteraction
import com.skd.userinteractions.repository.UserInteractionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class UserInteractionRepositoryTest {

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
    lateinit var repository: UserInteractionRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `can insert a single user interaction`() {
        val userId = UUID.randomUUID()
        val contentId = UUID.randomUUID()
        val clientTs = Instant.parse("2026-04-03T12:00:00Z")
        val serverTs = Instant.parse("2026-04-03T12:00:01Z")

        val interaction = UserInteraction(
            id = null,
            userId = userId,
            contentId = contentId,
            actionType = "view",
            durationSec = 30,
            clientTs = clientTs,
            serverTs = serverTs
        )

        repository.save(interaction)

        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM user_interactions WHERE user_id = ?",
            Long::class.java,
            userId
        )
        assertThat(count).isEqualTo(1L)
    }

    @Test
    fun `can batch insert multiple user interactions`() {
        val userId = UUID.randomUUID()
        val serverTs = Instant.parse("2026-04-15T10:00:00Z")

        val interactions = (1..5).map { i ->
            UserInteraction(
                id = null,
                userId = userId,
                contentId = UUID.randomUUID(),
                actionType = "click",
                durationSec = null,
                clientTs = serverTs.minusSeconds(i.toLong()),
                serverTs = serverTs
            )
        }

        repository.saveAll(interactions)

        val count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM user_interactions WHERE user_id = ?",
            Long::class.java,
            userId
        )
        assertThat(count).isEqualTo(5L)
    }

    @Test
    fun `insert with server_ts in april lands in correct partition`() {
        val userId = UUID.randomUUID()
        val serverTs = Instant.parse("2026-04-20T15:30:00Z")

        val interaction = UserInteraction(
            id = null,
            userId = userId,
            contentId = UUID.randomUUID(),
            actionType = "save",
            durationSec = null,
            clientTs = serverTs,
            serverTs = serverTs
        )

        repository.save(interaction)

        val countInPartition = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM user_interactions_2026_04 WHERE user_id = ?",
            Long::class.java,
            userId
        )
        assertThat(countInPartition).isEqualTo(1L)
    }

    @Test
    fun `querying by user_id returns correct records`() {
        val targetUserId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val serverTs = Instant.parse("2026-04-10T08:00:00Z")

        val targetInteractions = (1..3).map { i ->
            UserInteraction(
                id = null,
                userId = targetUserId,
                contentId = UUID.randomUUID(),
                actionType = "view",
                durationSec = i * 10,
                clientTs = serverTs.minusSeconds(i.toLong()),
                serverTs = serverTs
            )
        }

        val otherInteraction = UserInteraction(
            id = null,
            userId = otherUserId,
            contentId = UUID.randomUUID(),
            actionType = "click",
            durationSec = null,
            clientTs = serverTs,
            serverTs = serverTs
        )

        repository.saveAll(targetInteractions + otherInteraction)

        val results = repository.findByUserId(targetUserId)

        assertThat(results).hasSize(3)
        assertThat(results).allMatch { it.userId == targetUserId }
    }

    @Test
    fun `can save entity with scroll_depth and metadata`() {
        val userId = UUID.randomUUID()
        val serverTs = Instant.parse("2026-04-20T10:00:00Z")

        val interaction = UserInteraction(
            id = null,
            userId = userId,
            contentId = UUID.randomUUID(),
            actionType = "CLOSE",
            durationSec = null,
            clientTs = serverTs,
            serverTs = serverTs,
            scrollDepth = 0.5f,
            metadata = mapOf("foo" to "bar")
        )

        repository.save(interaction)

        val row = jdbcTemplate.queryForMap(
            "SELECT scroll_depth, metadata::text FROM user_interactions WHERE user_id = ?",
            userId
        )
        assertThat((row["scroll_depth"] as Number).toFloat()).isEqualTo(0.5f)
        assertThat(row["metadata"].toString()).contains("foo")
        assertThat(row["metadata"].toString()).contains("bar")
    }

    @Test
    fun `can save entity with null scroll_depth and null metadata`() {
        val userId = UUID.randomUUID()
        val serverTs = Instant.parse("2026-04-20T11:00:00Z")

        val interaction = UserInteraction(
            id = null,
            userId = userId,
            contentId = UUID.randomUUID(),
            actionType = "IMPRESSION",
            durationSec = null,
            clientTs = serverTs,
            serverTs = serverTs
        )

        repository.save(interaction)

        val row = jdbcTemplate.queryForMap(
            "SELECT scroll_depth, metadata FROM user_interactions WHERE user_id = ?",
            userId
        )
        assertThat(row["scroll_depth"]).isNull()
        assertThat(row["metadata"]).isNull()
    }

    @Test
    fun `insert with null duration_sec persists correctly`() {
        val userId = UUID.randomUUID()
        val serverTs = Instant.parse("2026-04-05T09:00:00Z")

        val interaction = UserInteraction(
            id = null,
            userId = userId,
            contentId = UUID.randomUUID(),
            actionType = "scroll_past",
            durationSec = null,
            clientTs = serverTs,
            serverTs = serverTs
        )

        repository.save(interaction)

        val durationSec = jdbcTemplate.queryForObject(
            "SELECT duration_sec FROM user_interactions WHERE user_id = ?",
            Integer::class.java,
            userId
        )
        assertThat(durationSec).isNull()
    }
}
