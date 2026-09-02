package com.skd.userinteractions.integration

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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class UserInteractionsSchemaExtensionTest {

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
    lateinit var jdbcTemplate: JdbcTemplate

    // ---- Column existence tests ----

    @Test
    fun `feed_request_id column exists as UUID nullable`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'feed_request_id'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("feed_request_id")
        assertThat(row["data_type"].toString()).isEqualTo("uuid")
        assertThat(row["is_nullable"].toString()).isEqualToIgnoringCase("YES")
    }

    @Test
    fun `position_in_feed column exists as smallint nullable`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'position_in_feed'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("position_in_feed")
        assertThat(row["data_type"].toString()).isEqualTo("smallint")
        assertThat(row["is_nullable"].toString()).isEqualToIgnoringCase("YES")
    }

    @Test
    fun `device_type column exists as varchar nullable`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'device_type'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("device_type")
        assertThat(row["data_type"].toString()).isEqualTo("character varying")
        assertThat(row["character_maximum_length"].toString()).isEqualTo("32")
        assertThat(row["is_nullable"].toString()).isEqualToIgnoringCase("YES")
    }

    @Test
    fun `app_version column exists as varchar nullable`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, character_maximum_length, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'app_version'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("app_version")
        assertThat(row["data_type"].toString()).isEqualTo("character varying")
        assertThat(row["character_maximum_length"].toString()).isEqualTo("32")
        assertThat(row["is_nullable"].toString()).isEqualToIgnoringCase("YES")
    }

    @Test
    fun `ab_bucket column exists as smallint with default 0`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, column_default, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'ab_bucket'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("ab_bucket")
        assertThat(row["data_type"].toString()).isEqualTo("smallint")
        assertThat(row["column_default"].toString()).isEqualTo("0")
    }

    @Test
    fun `scroll_depth column exists as real nullable`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'scroll_depth'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("scroll_depth")
        assertThat(row["data_type"].toString()).isEqualTo("real")
        assertThat(row["is_nullable"].toString()).isEqualToIgnoringCase("YES")
    }

    @Test
    fun `metadata column exists as jsonb nullable`() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT column_name, data_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = 'user_interactions'
              AND column_name  = 'metadata'
            """.trimIndent()
        )
        assertThat(row["column_name"]).isEqualTo("metadata")
        assertThat(row["data_type"].toString()).isEqualTo("jsonb")
        assertThat(row["is_nullable"].toString()).isEqualToIgnoringCase("YES")
    }

    // ---- Index existence tests ----

    @Test
    fun `idx_ui_feed_request partial index exists with WHERE clause`() {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE schemaname = 'interactions'
              AND indexname = 'idx_ui_feed_request'
            """.trimIndent()
        )
        assertThat(rows).hasSize(1)
        val indexDef = rows[0]["indexdef"].toString()
        assertThat(indexDef).containsIgnoringCase("WHERE")
        assertThat(indexDef).containsIgnoringCase("feed_request_id")
    }

    @Test
    fun `idx_ui_user_time composite index exists with user_id and server_ts`() {
        val rows = jdbcTemplate.queryForList(
            """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE schemaname = 'interactions'
              AND indexname = 'idx_ui_user_time'
            """.trimIndent()
        )
        assertThat(rows).hasSize(1)
        val indexDef = rows[0]["indexdef"].toString()
        assertThat(indexDef).containsIgnoringCase("user_id")
        assertThat(indexDef).containsIgnoringCase("server_ts")
    }

    // ---- Insert behaviour tests ----

    @Test
    fun `insert old-style row without new fields succeeds, new columns are null except ab_bucket`() {
        val userId = UUID.randomUUID()
        val serverTs = Timestamp.from(Instant.parse("2026-04-19T10:00:00Z"))

        jdbcTemplate.update(
            """
            INSERT INTO interactions.user_interactions
                (user_id, content_id, action_type, duration_sec, client_ts, server_ts)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            userId,
            UUID.randomUUID(),
            "view",
            10,
            serverTs,
            serverTs
        )

        val row = jdbcTemplate.queryForMap(
            "SELECT feed_request_id, position_in_feed, device_type, app_version, ab_bucket FROM interactions.user_interactions WHERE user_id = ?",
            userId
        )
        assertThat(row["feed_request_id"]).isNull()
        assertThat(row["position_in_feed"]).isNull()
        assertThat(row["device_type"]).isNull()
        assertThat(row["app_version"]).isNull()
        assertThat(row["ab_bucket"].toString()).isEqualTo("0")
    }

    @Test
    fun `insert new-style row with all 5 new fields populated persists correctly`() {
        val userId = UUID.randomUUID()
        val feedRequestId = UUID.randomUUID()
        val serverTs = Timestamp.from(Instant.parse("2026-04-19T11:00:00Z"))

        jdbcTemplate.update(
            """
            INSERT INTO interactions.user_interactions
                (user_id, content_id, action_type, duration_sec, client_ts, server_ts,
                 feed_request_id, position_in_feed, device_type, app_version, ab_bucket)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            userId,
            UUID.randomUUID(),
            "click",
            null,
            serverTs,
            serverTs,
            feedRequestId,
            5.toShort(),
            "android",
            "1.2.3",
            1.toShort()
        )

        val row = jdbcTemplate.queryForMap(
            """
            SELECT feed_request_id, position_in_feed, device_type, app_version, ab_bucket
            FROM interactions.user_interactions WHERE user_id = ?
            """.trimIndent(),
            userId
        )
        assertThat(row["feed_request_id"].toString()).isEqualTo(feedRequestId.toString())
        assertThat(row["position_in_feed"].toString()).isEqualTo("5")
        assertThat(row["device_type"]).isEqualTo("android")
        assertThat(row["app_version"]).isEqualTo("1.2.3")
        assertThat(row["ab_bucket"].toString()).isEqualTo("1")
    }

    // ---- Partition propagation tests ----

    @Test
    fun `new columns are propagated to partition user_interactions_2026_04`() {
        verifyNewColumnsInPartition("user_interactions_2026_04")
    }

    @Test
    fun `new columns are propagated to partition user_interactions_2026_05`() {
        verifyNewColumnsInPartition("user_interactions_2026_05")
    }

    @Test
    fun `new columns are propagated to partition user_interactions_default`() {
        verifyNewColumnsInPartition("user_interactions_default")
    }

    private fun verifyNewColumnsInPartition(partitionName: String) {
        val columnNames = jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'interactions'
              AND table_name   = ?
            """.trimIndent(),
            String::class.java,
            partitionName
        )
        assertThat(columnNames)
            .describedAs("Partition '$partitionName' should contain all new columns")
            .contains("feed_request_id", "position_in_feed", "device_type", "app_version", "ab_bucket")
    }
}
