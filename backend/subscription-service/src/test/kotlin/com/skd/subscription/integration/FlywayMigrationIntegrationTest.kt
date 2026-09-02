package com.skd.subscription.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

@Tag("integration")
class FlywayMigrationIntegrationTest : IntegrationTestBase() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `should create all tables via Flyway migration`() {
        val tables = jdbcTemplate.queryForList(
            """
            SELECT table_name FROM information_schema.tables
            WHERE table_schema = 'public'
            AND table_type = 'BASE TABLE'
            ORDER BY table_name
            """,
            String::class.java
        )

        assertThat(tables).contains(
            "plans",
            "subscriptions",
            "payments",
            "saved_payment_methods",
            "webhook_log",
            "outbox"
        )
    }

    @Test
    fun `should seed premium_monthly and premium_yearly plans`() {
        val planCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plans",
            Int::class.java
        )

        assertThat(planCount).isEqualTo(2)

        val monthlyPrice = jdbcTemplate.queryForObject(
            "SELECT price_kopecks FROM plans WHERE id = 'premium_monthly'",
            Int::class.java
        )
        assertThat(monthlyPrice).isEqualTo(29900)

        val yearlyPrice = jdbcTemplate.queryForObject(
            "SELECT price_kopecks FROM plans WHERE id = 'premium_yearly'",
            Int::class.java
        )
        assertThat(yearlyPrice).isEqualTo(299000)
    }

    @Test
    fun `should create indexes on subscriptions table`() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT indexname FROM pg_indexes
            WHERE tablename = 'subscriptions'
            AND schemaname = 'public'
            """,
            String::class.java
        )

        assertThat(indexes).contains("idx_sub_user", "idx_sub_expires")
    }

    @Test
    fun `should create indexes on payments table`() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT indexname FROM pg_indexes
            WHERE tablename = 'payments'
            AND schemaname = 'public'
            """,
            String::class.java
        )

        assertThat(indexes).contains("idx_pay_user", "idx_pay_pending", "idx_pay_external")
    }

    @Test
    fun `should create outbox table with unpublished index`() {
        val indexes = jdbcTemplate.queryForList(
            """
            SELECT indexname FROM pg_indexes
            WHERE tablename = 'outbox'
            AND schemaname = 'public'
            """,
            String::class.java
        )

        assertThat(indexes).contains("idx_outbox_unpublished")
    }
}
