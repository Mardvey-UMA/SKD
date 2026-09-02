package com.contentplatform.auth.integration.db

import com.contentplatform.auth.integration.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@Tag("integration")
class DatabaseMigrationIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `flyway migration should create users table`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'users'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `flyway migration should create email_verification_tokens table`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'email_verification_tokens'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `flyway migration should create password_reset_tokens table`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'password_reset_tokens'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `flyway migration should create refresh_tokens table`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'refresh_tokens'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `flyway migration should create outbox table`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_name = 'outbox'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `users table should have unique constraint on email`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_name = 'users'
              AND constraint_type = 'UNIQUE'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `refresh_tokens table should have unique constraint on token_hash`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_name = 'refresh_tokens'
              AND constraint_type = 'UNIQUE'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `email_verification_tokens should have foreign key to users`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE table_name = 'email_verification_tokens'
              AND constraint_type = 'FOREIGN KEY'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `outbox table should have idx_outbox_unpublished partial index`() {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM pg_indexes
            WHERE tablename = 'outbox'
              AND indexname = 'idx_outbox_unpublished'
            """.trimIndent(),
            Int::class.java
        )
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `users table should have all expected columns`() {
        val columns = jdbcTemplate.queryForList(
            """
            SELECT column_name FROM information_schema.columns
            WHERE table_name = 'users'
            ORDER BY ordinal_position
            """.trimIndent()
        ).map { it["column_name"] as String }

        assertThat(columns).containsExactlyInAnyOrder(
            "id", "email", "password_hash", "email_verified",
            "subscription_tier", "created_at", "updated_at"
        )
    }
}
